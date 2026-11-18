package com.safetylog.app.data.auth

import com.safetylog.app.util.ServerAuthorization
import com.safetylog.app.util.TestControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the server-signed authorization decision. When the server is configured its
 * snapshot overrides every locally derived role / entitlement. Privileged mutations
 * require a fresh server round-trip (fail-closed offline); read-only UI may use the
 * cached signed snapshot.
 */
class AuthorizationRepository(
    private val api: AuthorizationApi,
    private val secureStorage: SecureTokenStorage,
    private val publicKeySpkiBase64: String = ServerAuthorization.PUBLIC_KEY_SPKI_BASE64
) {
    private val _snapshot = MutableStateFlow<AuthorizationSnapshot?>(null)
    val snapshot: StateFlow<AuthorizationSnapshot?> = _snapshot.asStateFlow()

    private var refreshJob: Job? = null

    /** Keep the signed decision fresh while the app is alive, so long sessions don't
     * silently lose their authoritative role/tier after the snapshot TTL expires. */
    fun startAutoRefresh(scope: CoroutineScope) {
        if (!ServerAuthorization.isConfigured) return
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                val current = _snapshot.value ?: continue
                val result = refresh()
                if (current.isExpired && result is ApiResult.HttpError && result.code == 401) break
            }
        }
    }

    fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /** True when a server decision is available and actually configured for this build. */
    val usesServerAuthority: Boolean
        get() = ServerAuthorization.isConfigured && _snapshot.value != null

    /** Debug/test builds may fall back to local-only behavior when no server is configured. */
    val allowsLocalFallback: Boolean
        get() = TestControls.isEnabled && !ServerAuthorization.isConfigured

    fun restoreCachedSnapshot() {
        val token = secureStorage.getAuthorizationSnapshot() ?: return
        val parsed = AuthorizationSnapshot.parseAndVerify(token, publicKeySpkiBase64)
        if (parsed != null) {
            // Retain even expired snapshots so refresh/grace evaluation can occur.
            _snapshot.value = parsed
        } else {
            secureStorage.setAuthorizationSnapshot(null)
        }
    }

    suspend fun exchange(idToken: String, nonce: String?): ApiResult<AuthorizationSnapshot> =
        when (val result = api.exchange(idToken, nonce)) {
            is ApiResult.Success -> store(result.value)
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
            is ApiResult.InvalidResponse -> result
        }

    suspend fun signInWithEmail(email: String, password: String): ApiResult<AuthorizationSnapshot> =
        when (val result = api.signInWithEmail(email, password)) {
            is ApiResult.Success -> store(result.value)
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
            is ApiResult.InvalidResponse -> result
        }

    suspend fun refresh(): ApiResult<AuthorizationSnapshot> {
        val current = _snapshot.value
            ?: return ApiResult.InvalidResponse("No authorization snapshot to refresh")
        return when (val result = api.refresh(current.token)) {
            is ApiResult.Success -> store(result.value)
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
            is ApiResult.InvalidResponse -> result
        }
    }

    suspend fun updateTeam(members: List<TeamMember>): ApiResult<AuthorizationSnapshot> {
        val current = _snapshot.value
            ?: return ApiResult.InvalidResponse("No authorization snapshot")
        return when (val result = api.updateTeam(members, current.token)) {
            is ApiResult.Success -> store(result.value)
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
            is ApiResult.InvalidResponse -> result
        }
    }

    suspend fun verifyPurchase(purchaseToken: String, productId: String): ApiResult<AuthorizationSnapshot> {
        val current = _snapshot.value
            ?: return ApiResult.InvalidResponse("No authorization snapshot")
        return when (val result = api.verifyPurchase(purchaseToken, productId, current.token)) {
            is ApiResult.Success -> store(result.value)
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
            is ApiResult.InvalidResponse -> result
        }
    }

    /**
     * Fail-closed gate for role / billing / membership mutations. A cached snapshot is
     * deliberately NOT sufficient: the server must confirm the caller's current authority.
     */
    suspend fun authorizePrivilegedAction(): Boolean {
        if (allowsLocalFallback) return true
        if (!ServerAuthorization.isConfigured) return false
        return refresh() is ApiResult.Success
    }

    fun clear() {
        _snapshot.value = null
        secureStorage.setAuthorizationSnapshot(null)
        // Keep refreshJob alive so auto-refresh can recover on subsequent sign-in.
    }

    private companion object {
        const val AUTO_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
    }

    private fun store(token: String): ApiResult<AuthorizationSnapshot> {
        val parsed = AuthorizationSnapshot.parseAndVerify(token, publicKeySpkiBase64)
            ?: return ApiResult.InvalidResponse("Server returned an invalid signed snapshot")
        secureStorage.setAuthorizationSnapshot(token)
        _snapshot.value = parsed
        return ApiResult.Success(parsed)
    }
}
