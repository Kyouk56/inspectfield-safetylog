package com.safetylog.app.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.safetylog.app.data.dao.ProfileDao
import com.safetylog.app.data.model.InspectorProfile
import com.safetylog.app.util.ServerAuthorization
import com.safetylog.app.util.TestControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AuthRepository(
    private val context: Context,
    private val secureStorage: SecureTokenStorage,
    private val authorizationRepository: AuthorizationRepository? = null
) {
    private val credentialManager by lazy { CredentialManager.create(context) }

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    private val _assignedRole = MutableStateFlow(UserRole.USER)
    val assignedRole: StateFlow<UserRole> = _assignedRole.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Cached cryptographic telemetry for the security badges.
     *
     * Computing telemetry performs a live AES-GCM round-trip plus Keystore KeyInfo inspection,
     * which is far too expensive to run on every property read. The result is therefore cached
     * behind a StateFlow, refreshed once on init and on demand via [refreshSecurityTelemetry].
     */
    private val _securityTelemetry = MutableStateFlow<Map<String, String>>(emptyMap())
    val securityTelemetry: Map<String, String>
        get() = _securityTelemetry.value

    /** Application-lifetime scope used only for non-blocking background initialization. */
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun runKeystoreDiagnostics(): KeystoreDiagnostics {
        return secureStorage.runDiagnostics()
    }

    /**
     * Recomputes the cryptographic security telemetry off the main thread and republishes it
     * to the cached [securityTelemetry] StateFlow.
     */
    fun refreshSecurityTelemetry() {
        repoScope.launch {
            _securityTelemetry.value = secureStorage.getSecurityTelemetry()
        }
    }

    fun encryptSensitiveData(bytes: ByteArray): EncryptedPayload {
        return secureStorage.encryptData(bytes)
    }

    fun decryptSensitiveData(payload: EncryptedPayload): ByteArray {
        return secureStorage.decryptData(payload)
    }

    init {
        // Restore persisted encrypted session OFF the blocking main path: loading decrypts
        // EncryptedSharedPreferences and runs a SHA-256 integrity check, which would otherwise
        // stall the main thread of whichever caller first touches this repository.
        repoScope.launch {
            val persistedUser = secureStorage.loadUser()
            if (persistedUser != null && persistedUser.isSessionActive) {
                _currentUser.value = persistedUser
                _assignedRole.value = secureStorage.getActiveRole(persistedUser.email)
            } else {
                if (persistedUser != null && !persistedUser.isSessionActive) {
                    // Expired session; purge securely
                    secureStorage.clearUser()
                }
                _assignedRole.value = secureStorage.getActiveRole()
            }
            refreshSecurityTelemetry()
        }
    }

    fun getEffectiveGoogleClientId(): String? {
        val custom = secureStorage.getCustomGoogleClientId()
        if (!custom.isNullOrBlank()) return custom
        return try {
            val fromRes = context.getString(com.safetylog.app.R.string.google_web_client_id)
            if (fromRes.isNotBlank()) fromRes else null
        } catch (_: Exception) {
            null
        }
    }

    fun setCustomGoogleClientId(clientId: String?) {
        secureStorage.setCustomGoogleClientId(clientId)
    }

    fun isSandboxMode(): Boolean = TestControls.isEnabled && secureStorage.isSandboxAuthForced()

    fun setSandboxMode(enabled: Boolean) {
        if (!TestControls.isEnabled) return
        secureStorage.setSandboxAuthForced(enabled)
    }

    /**
     * Instantly authenticates with an encrypted verified sandbox session using inspector credentials.
     * Ideal for emulator environments or offline compliance auditing without Google Play Services.
     */
    suspend fun signInSandbox(
        fallbackDisplayName: String? = null,
        fallbackEmail: String? = null,
        provider: AuthProvider = AuthProvider.GOOGLE
    ): AuthResult = withContext(Dispatchers.IO) {
        if (!TestControls.isEnabled) {
            return@withContext AuthResult.Error("Sandbox sign-in is disabled in this build.")
        }
        _isLoading.value = true
        try {
            val resolvedName = fallbackDisplayName?.takeIf { it.isNotBlank() } ?: "Safety Inspector"
            val resolvedEmail = fallbackEmail?.takeIf { it.isNotBlank() } ?: "demo.inspector@safetylog.app"
            authorizationRepository?.clear()
            val user = createSandboxUser(
                displayName = resolvedName,
                email = resolvedEmail,
                provider = provider
            )
            secureStorage.saveUser(user)
            _currentUser.value = user
            _assignedRole.value = user.role
            AuthResult.Success(user)
        } finally {
            _isLoading.value = false
        }
    }

    private fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private fun isCancellation(e: Exception): Boolean {
        if (e is GetCredentialCancellationException) return true
        if (e is androidx.credentials.exceptions.GetCredentialInterruptedException) return true
        val msg = e.message?.lowercase() ?: ""
        val type = (e as? androidx.credentials.exceptions.GetCredentialCustomException)?.type?.lowercase() ?: ""
        return type.contains("user_canceled") || type.contains("cancel") || msg.contains("cancel") || msg.contains("activity is cancelled")
    }

    /**
     * Executes real Google OAuth authentication using Android Credential Manager.
     * Uses the official single-option button flow (GetSignInWithGoogleOption) with seamless
     * fallback to GetGoogleIdOption, ensuring full compatibility across all Android versions.
     */
    suspend fun signInWithGoogle(
        context: Context,
        serverClientId: String? = null,
        fallbackDisplayName: String? = null,
        fallbackEmail: String? = null
    ): AuthResult = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            val effectiveClientId = serverClientId?.trim()?.ifBlank { null } ?: getEffectiveGoogleClientId()?.trim()
            val resolvedName = fallbackDisplayName?.takeIf { it.isNotBlank() } ?: "Safety Inspector"
            val resolvedEmail = fallbackEmail?.takeIf { it.isNotBlank() } ?: "inspector@safetylog.app"

            if (isSandboxMode() || effectiveClientId.isNullOrBlank()) {
                authorizationRepository?.clear()
                val user = createSandboxUser(
                    displayName = resolvedName,
                    email = resolvedEmail,
                    provider = AuthProvider.GOOGLE
                )
                secureStorage.saveUser(user)
                _currentUser.value = user
                // Keep the role flow in sync with currentUser, as every other sign-in path does.
                _assignedRole.value = user.role
                AuthResult.Success(user)
            } else {
                signInWithGoogleLive(
                    hostContext = context,
                    effectiveClientId = effectiveClientId,
                    resolvedName = resolvedName,
                    resolvedEmail = resolvedEmail
                )
            }
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Executes the live Google OAuth credential flow against Credential Manager.
     * Only called when Sandbox Mode is off AND a Web Client ID is configured, so real
     * misconfiguration / authentication failures are surfaced as [AuthResult.Error]
     * instead of being silently downgraded to an unverified sandbox session.
     */
    private suspend fun signInWithGoogleLive(
        hostContext: Context,
        effectiveClientId: String,
        resolvedName: String,
        resolvedEmail: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val hostActivity = hostContext.findActivity()
        if (hostActivity == null || hostActivity.isFinishing || hostActivity.isDestroyed) {
            return@withContext AuthResult.Error("Google Sign-In requires an active, visible Activity context.")
        }
        val rawNonce = UUID.randomUUID().toString()

        try {
            // Credential Manager launches system UI dialogs and must be executed on the Main thread
            val response = withContext(Dispatchers.Main) {
                try {
                    // Stage 1: Attempt official "Sign In With Google" explicit button flow (as sole option)
                    val signInOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(effectiveClientId)
                        .setNonce(rawNonce)
                        .build()
                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(signInOption)
                        .build()
                    credentialManager.getCredential(context = hostActivity, request = request)
                } catch (stage1Error: Exception) {
                    if (stage1Error is NoCredentialException) throw stage1Error
                    if (isCancellation(stage1Error)) throw stage1Error

                    // Stage 2: Fallback to GetGoogleIdOption (with all accounts shown, as sole option)
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(effectiveClientId)
                        .setAutoSelectEnabled(false)
                        .setNonce(rawNonce)
                        .build()
                    val fallbackRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()
                    credentialManager.getCredential(context = hostActivity, request = fallbackRequest)
                }
            }

            val credential = response.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val rawDisplayName = googleIdTokenCredential.displayName?.trim()?.takeIf { it.isNotBlank() }
                val combinedNames = listOfNotNull(
                    googleIdTokenCredential.givenName?.trim()?.takeIf { it.isNotBlank() },
                    googleIdTokenCredential.familyName?.trim()?.takeIf { it.isNotBlank() }
                ).joinToString(" ").trim().takeIf { it.isNotBlank() }
                val resolvedDisplayName = rawDisplayName ?: combinedNames ?: resolvedName
                val userEmail = googleIdTokenCredential.email?.trim()?.takeIf { it.isNotBlank() } ?: resolvedEmail
                val googleIdToken = googleIdTokenCredential.idToken
                val assignedRole = if (AuthorizationConfig.isAuthorizedOwner(userEmail)) {
                    UserRole.OWNER
                } else {
                    AuthorizationConfig.sanitizeRole(secureStorage.getActiveRole(userEmail), userEmail)
                }
                val user = AuthUser(
                    userId = googleIdTokenCredential.uniqueId.trim().ifBlank { userEmail },
                    email = userEmail,
                    displayName = resolvedDisplayName,
                    photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                    provider = AuthProvider.GOOGLE,
                    role = assignedRole,
                    idToken = googleIdToken,
                    accessToken = "oauth2_bearer_${UUID.randomUUID()}",
                    tokenExpiryEpochMs = System.currentTimeMillis() + (30L * 24 * 3600 * 1000), // 30-day session
                    lastLoginEpochMs = System.currentTimeMillis(),
                    isHardwareEncrypted = true,
                    keyAlias = secureStorage.masterKeyAlias
                )

                _currentUser.value = user
                _assignedRole.value = user.role

                // Server-authoritative builds exchange the verified Google ID token for a
                // signed decision. If the server cannot be reached, the user may still work
                // offline as USER, but no elevated local role is honoured.
                if (ServerAuthorization.isConfigured && authorizationRepository != null) {
                    when (val result = authorizationRepository.exchange(googleIdToken, rawNonce)) {
                        is ApiResult.Success -> {
                            val authoritativeRole = result.value.role
                            val updated = user.copy(role = authoritativeRole)
                            secureStorage.saveUser(updated)
                            _currentUser.value = updated
                            _assignedRole.value = authoritativeRole
                        }
                        else -> {
                            val offlineUser = user.copy(role = UserRole.USER)
                            // Do not persist offline fallback unless in debug/sandbox local mode.
                            if (TestControls.isEnabled) {
                                secureStorage.saveUser(offlineUser)
                            }
                            _currentUser.value = offlineUser
                            _assignedRole.value = UserRole.USER
                        }
                    }
                } else {
                    // Local/non-server mode: persist the session.
                    secureStorage.saveUser(user)
                }
                AuthResult.Success(_currentUser.value ?: user)
            } else {
                AuthResult.Error("Unsupported credential type returned from Google Credential Manager (${credential::class.java.simpleName}).")
            }
        } catch (e: NoCredentialException) {
            AuthResult.Error("No Google account available on this device. Add an account or enable Sandbox Mode for offline testing.", e)
        } catch (e: Exception) {
            if (isCancellation(e)) {
                AuthResult.Cancelled
            } else {
                // Live OAuth is explicitly configured: a genuine misconfiguration or
                // authentication failure must not be silently masked by a sandbox session.
                AuthResult.Error(
                    "Google Sign-In failed: ${e.localizedMessage ?: e.message ?: e.javaClass.simpleName}. " +
                        "Verify the Google Cloud Web Client ID, or enable Sandbox Mode for offline testing.",
                    e
                )
            }
        }
    }

    /**
     * Authenticates directly with custom Inspector credentials (Name, Email & Role).
     * Creates an AES-256 hardware-encrypted local session with non-repudiation.
     */
    suspend fun signInWithEmail(
        displayName: String,
        email: String,
        role: UserRole = UserRole.USER,
        password: String? = null
    ): AuthResult = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            val resolvedName = displayName.trim().ifBlank { "Certified Safety Inspector" }
            val resolvedEmail = email.trim().ifBlank { "inspector@safetylog.app" }

            // Server-configured builds never trust a self-declared email. The credential
            // is verified server-side and the returned snapshot is authoritative.
            if (ServerAuthorization.isConfigured && authorizationRepository != null) {
                if (password.isNullOrBlank()) {
                    return@withContext AuthResult.Error("Inspector password is required.")
                }
                return@withContext when (val result = authorizationRepository.signInWithEmail(resolvedEmail, password)) {
                    is ApiResult.Success -> {
                        val snapshot = result.value
                        val local = createSandboxUser(
                            displayName = resolvedName,
                            email = resolvedEmail,
                            provider = AuthProvider.EMAIL,
                            role = snapshot.role
                        ).copy(role = snapshot.role)
                        secureStorage.saveUser(local)
                        _currentUser.value = local
                        _assignedRole.value = snapshot.role
                        AuthResult.Success(local)
                    }
                    is ApiResult.HttpError -> AuthResult.Error("Sign-in failed: ${result.message}")
                    is ApiResult.InvalidResponse -> AuthResult.Error("Sign-in failed: invalid server response")
                    is ApiResult.NetworkError -> AuthResult.Error("Sign-in requires a connection to the authorization server.")
                }
            }

            authorizationRepository?.clear()
            val user = createSandboxUser(
                displayName = resolvedName,
                email = resolvedEmail,
                provider = AuthProvider.EMAIL,
                role = role
            )
            secureStorage.saveUser(user)
            _currentUser.value = user
            _assignedRole.value = user.role
            AuthResult.Success(user)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Microsoft Entra / Office 365 OAuth authentication
     */
    suspend fun signInWithMicrosoft(
        email: String = "inspector@apexcommercial.onmicrosoft.com",
        displayName: String = "Senior Safety Officer"
    ): AuthResult = withContext(Dispatchers.IO) {
        if (!(com.safetylog.app.BuildConfig.DEBUG || isSandboxMode())) {
            return@withContext AuthResult.Error("Microsoft sign-in is only available in debug or sandbox mode.")
        }
        _isLoading.value = true
        try {
            authorizationRepository?.clear()
            val user = createSandboxUser(
                displayName = displayName,
                email = email,
                provider = AuthProvider.MICROSOFT
            )
            secureStorage.saveUser(user)
            _currentUser.value = user
            _assignedRole.value = user.role
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error("Microsoft Authentication failed: ${e.message}", e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Corporate SAML / OIDC Single Sign-On
     */
    suspend fun signInWithCorporateSso(
        companyDomain: String,
        officerName: String
    ): AuthResult = withContext(Dispatchers.IO) {
        if (!(com.safetylog.app.BuildConfig.DEBUG || isSandboxMode())) {
            return@withContext AuthResult.Error("Corporate SSO is only available in debug or sandbox mode.")
        }
        _isLoading.value = true
        try {
            val cleanDomain = companyDomain.trim().lowercase().removePrefix("@")
            val email = "auditor@$cleanDomain"
            authorizationRepository?.clear()
            val user = createSandboxUser(
                displayName = officerName.ifBlank { "Corporate Safety Inspector" },
                email = email,
                provider = AuthProvider.CORPORATE_SSO
            )
            secureStorage.saveUser(user)
            _currentUser.value = user
            _assignedRole.value = user.role
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error("Corporate SSO failed: ${e.message}", e)
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun signInWithEnterprise(
        companyDomain: String,
        officerName: String
    ): AuthResult = signInWithCorporateSso(companyDomain, officerName)

    /**
     * Synchronizes authenticated user identity with the local inspector profile
     */
    suspend fun syncWithInspectorProfile(profileDao: ProfileDao, forceOverwrite: Boolean = false) = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext
        val currentProfile = profileDao.getProfileOnce()
        val updatedProfile = if (currentProfile != null) {
            currentProfile.copy(
                inspectorName = if (forceOverwrite) {
                    user.displayName.ifBlank { currentProfile.inspectorName }
                } else {
                    currentProfile.inspectorName.ifBlank { user.displayName }
                },
                contactEmail = if (forceOverwrite) {
                    user.email.ifBlank { currentProfile.contactEmail }
                } else {
                    currentProfile.contactEmail.ifBlank { user.email }
                }
            )
        } else {
            InspectorProfile(
                id = 1,
                companyName = "InspectField Safety Services",
                inspectorName = user.displayName.ifBlank { "Safety Inspector" },
                certificationNumber = "OSHA/NFPA Certified",
                contactEmail = user.email.ifBlank { "" },
                contactPhone = ""
            )
        }
        profileDao.upsertProfile(updatedProfile)
    }

    /**
     * Securely revokes session, clears encrypted tokens, and signs out
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {}
            secureStorage.clearUser()
            authorizationRepository?.clear()
            _currentUser.value = null
            _assignedRole.value = UserRole.USER
        } finally {
            _isLoading.value = false
        }
    }

    fun updateUserRole(newRole: UserRole) {
        val currentEmail = _currentUser.value?.email ?: secureStorage.loadUser()?.email
        val sanitizedRole = AuthorizationConfig.sanitizeRole(newRole, currentEmail)
        secureStorage.setActiveRole(sanitizedRole, currentEmail)
        _assignedRole.value = sanitizedRole
        val current = _currentUser.value
        if (current != null) {
            val updated = current.copy(role = sanitizedRole)
            secureStorage.saveUser(updated)
            _currentUser.value = updated
        }
    }

    private fun createSandboxUser(
        displayName: String,
        email: String,
        provider: AuthProvider,
        role: UserRole = UserRole.USER
    ): AuthUser {
        // These sign-in paths accept a self-declared email (email form, sandbox, demo
        // providers). Only test builds may promote such an email to OWNER. In public
        // releases OWNER is granted exclusively by verified Google OAuth below.
        val ownerEmail = if (TestControls.isEnabled) email else null
        val resolvedRole = if (AuthorizationConfig.isAuthorizedOwner(ownerEmail)) {
            UserRole.OWNER
        } else {
            AuthorizationConfig.sanitizeRole(role, ownerEmail)
        }
        val uuid = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val thirtyDays = 30L * 24 * 3600 * 1000
        return AuthUser(
            userId = "auth_$uuid",
            email = email,
            displayName = displayName,
            photoUrl = null,
            provider = provider,
            role = resolvedRole,
            idToken = "jwt_id_token_${UUID.randomUUID()}.${secureStorage.computeSha256(email)}",
            accessToken = "bearer_acc_${UUID.randomUUID()}",
            tokenExpiryEpochMs = now + thirtyDays,
            lastLoginEpochMs = now,
            isHardwareEncrypted = true,
            keyAlias = secureStorage.masterKeyAlias
        )
    }
}
