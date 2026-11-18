package com.safetylog.app.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Small result type so callers can distinguish "server denied" from "server unreachable". */
sealed class ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>()
    data class HttpError(val code: Int, val message: String) : ApiResult<Nothing>()
    data class NetworkError(val error: Throwable) : ApiResult<Nothing>()
    data class InvalidResponse(val message: String) : ApiResult<Nothing>()
}

data class TransportResponse(val code: Int, val body: String?)

/** Injectable transport — keeps the API client unit-testable without a real server. */
interface AuthorizationTransport {
    suspend fun request(method: String, path: String, body: String?, bearer: String?): TransportResponse
}

/** Minimal HttpURLConnection transport; avoids adding an HTTP dependency to the app. */
class HttpAuthorizationTransport(private val baseUrl: String) : AuthorizationTransport {
    override suspend fun request(method: String, path: String, body: String?, bearer: String?): TransportResponse =
        withContext(Dispatchers.IO) {
            val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("Accept", "application/json")
                if (!bearer.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $bearer")
                if (body != null) {
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use(BufferedReader::readText)
                TransportResponse(code, text)
            } finally {
                connection.disconnect()
            }
        }
}

/** Typed client for the SafetyLog authorization worker. Never parses client-provided roles. */
class AuthorizationApi(private val transport: AuthorizationTransport) {

    suspend fun exchange(idToken: String, nonce: String?): ApiResult<String> {
        val body = JSONObject()
            .put("idToken", idToken)
            .apply { if (!nonce.isNullOrBlank()) put("nonce", nonce) }
            .toString()
        return snapshotFrom(transport.request("POST", "/v1/auth/exchange", body, null))
    }

    suspend fun signInWithEmail(email: String, password: String): ApiResult<String> {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()
        return snapshotFrom(transport.request("POST", "/v1/auth/email", body, null))
    }

    suspend fun refresh(snapshot: String): ApiResult<String> =
        snapshotFrom(transport.request("POST", "/v1/auth/refresh", null, snapshot))

    suspend fun updateTeam(members: List<TeamMember>, snapshot: String): ApiResult<String> {
        val array = org.json.JSONArray()
        members.forEach { member ->
            array.put(
                JSONObject()
                    .put("id", member.id)
                    .put("email", member.email)
                    .put("name", member.name)
                    .put("role", member.role.name)
                    .put("isActive", member.isActive)
                    .put("addedEpochMs", member.addedEpochMs)
            )
        }
        return snapshotFrom(transport.request("PUT", "/v1/admin/team", JSONObject().put("members", array).toString(), snapshot))
    }

    suspend fun verifyPurchase(purchaseToken: String, productId: String, snapshot: String): ApiResult<String> {
        val body = JSONObject()
            .put("purchaseToken", purchaseToken)
            .put("productId", productId)
            .toString()
        return snapshotFrom(transport.request("POST", "/v1/purchases/verify", body, snapshot))
    }

    private fun snapshotFrom(response: TransportResponse): ApiResult<String> {
        if (response.code !in 200..299) {
            val message = response.body?.let { runCatching { JSONObject(it).optString("error") }.getOrNull() }.orEmpty()
            return ApiResult.HttpError(response.code, message.ifBlank { "HTTP ${response.code}" })
        }
        val raw = response.body ?: return ApiResult.InvalidResponse("Empty response")
        return runCatching { JSONObject(raw).getString("snapshot") }
            .fold(onSuccess = { ApiResult.Success(it) }, onFailure = { ApiResult.InvalidResponse("Missing snapshot") })
    }
}
