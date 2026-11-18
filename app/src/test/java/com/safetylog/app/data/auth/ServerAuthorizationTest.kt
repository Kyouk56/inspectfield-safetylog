package com.safetylog.app.data.auth

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class ServerAuthorizationTest {

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun sign(claims: String, key: java.security.PrivateKey = keyPair.private): String {
        val input = "${encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}")}.${encode(claims)}"
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(key)
            update(input.toByteArray(Charsets.US_ASCII))
        }.sign()
        return "$input.${Base64.getUrlEncoder().withoutPadding().encodeToString(signature)}"
    }

    private fun claims(role: String = "MANAGER", expOffset: Long = 900) = JSONObject()
        .put("v", 1)
        .put("sub", "google-123")
        .put("email", "inspector@example.com")
        .put("role", role)
        .put("tier", "ENTERPRISE")
        .put("effectiveTier", "ENTERPRISE")
        .put("paymentStatus", "ACTIVE")
        .put("seats", 12)
        .put("team", org.json.JSONArray().put(JSONObject()
            .put("id", "member-1")
            .put("email", "tech@example.com")
            .put("role", "USER")
            .put("isActive", true)))
        .put("iat", System.currentTimeMillis() / 1000)
        .put("exp", System.currentTimeMillis() / 1000 + expOffset)
        .toString()

    @Test
    fun signedSnapshotIsVerifiedAndParsed() {
        val snapshot = AuthorizationSnapshot.parseAndVerify(sign(claims()), publicKeyBase64)
        assertNotNull(snapshot)
        assertEquals("inspector@example.com", snapshot!!.email)
        assertEquals(UserRole.MANAGER, snapshot.role)
        assertEquals(SubscriptionTier.ENTERPRISE, snapshot.tier)
        assertEquals(12, snapshot.seats)
        assertEquals(1, snapshot.team.size)
        assertEquals("tech@example.com", snapshot.team.first().email)
        assertFalse(snapshot.isExpired)
    }

    @Test
    fun tamperedSnapshotIsRejected() {
        val original = sign(claims(role = "USER"))
        val parts = original.split(".")
        // Replace the payload with one claiming OWNER but keep the original signature.
        val forgedPayload = encode(claims(role = "OWNER"))
        val forged = "${parts[0]}.$forgedPayload.${parts[2]}"
        assertNull(AuthorizationSnapshot.parseAndVerify(forged, publicKeyBase64))
    }

    @Test
    fun wrongPublicKeyAndMalformedTokensAreRejected() {
        val token = sign(claims())
        val otherKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertNull(AuthorizationSnapshot.parseAndVerify(token, Base64.getEncoder().encodeToString(otherKey.public.encoded)))
        assertNull(AuthorizationSnapshot.parseAndVerify("not.a.jwt", publicKeyBase64))
        assertNull(AuthorizationSnapshot.parseAndVerify(token, ""))
    }

    @Test
    fun expiredSnapshotIsFlagged() {
        val snapshot = AuthorizationSnapshot.parseAndVerify(sign(claims(expOffset = -10)), publicKeyBase64)
        assertNotNull(snapshot)
        assertTrue(snapshot!!.isExpired)
    }

    @Test
    fun apiMapsSuccessAndFailures() = runBlocking {
        val token = sign(claims())
        val successTransport = FakeTransport(TransportResponse(200, "{\"snapshot\":\"$token\"}"))
        val api = AuthorizationApi(successTransport)
        val result = api.exchange("google-id-token", "nonce-123")
        assertTrue(result is ApiResult.Success)
        val parsed = AuthorizationSnapshot.parseAndVerify((result as ApiResult.Success).value, publicKeyBase64)
        assertEquals(UserRole.MANAGER, parsed!!.role)
        assertTrue(successTransport.lastBody!!.contains("nonce-123"))

        val denied = AuthorizationApi(FakeTransport(TransportResponse(401, "{\"error\":\"unauthorized\"}")))
        val deniedResult = denied.refresh(token)
        assertTrue(deniedResult is ApiResult.HttpError)
        assertEquals(401, (deniedResult as ApiResult.HttpError).code)

        val malformed = AuthorizationApi(FakeTransport(TransportResponse(200, "{}")))
        assertTrue(malformed.refresh(token) is ApiResult.InvalidResponse)
    }

    @Test
    fun failClosedPolicyRequiresConfigSnapshotAndConnectivity() {
        assertTrue(AuthorizationPolicy.privilegedActionAllowed(false, false, true, false, true))
        assertFalse(AuthorizationPolicy.privilegedActionAllowed(false, false, true, false, false))
        assertFalse(AuthorizationPolicy.privilegedActionAllowed(true, false, true, false, false))
        assertFalse(AuthorizationPolicy.privilegedActionAllowed(true, true, true, false, false))
        assertFalse(AuthorizationPolicy.privilegedActionAllowed(true, true, false, false, false))
        assertTrue(AuthorizationPolicy.privilegedActionAllowed(true, true, false, true, false))
    }

    private class FakeTransport(private val response: TransportResponse) : AuthorizationTransport {
        var lastBody: String? = null
        var lastBearer: String? = null
        override suspend fun request(method: String, path: String, body: String?, bearer: String?): TransportResponse {
            lastBody = body
            lastBearer = bearer
            return response
        }
    }
}
