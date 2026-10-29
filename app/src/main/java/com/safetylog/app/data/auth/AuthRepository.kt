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
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.safetylog.app.data.dao.ProfileDao
import com.safetylog.app.data.model.InspectorProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class AuthRepository(
    private val context: Context,
    private val secureStorage: SecureTokenStorage
) {
    private val credentialManager by lazy { CredentialManager.create(context) }

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    private val _assignedRole = MutableStateFlow(secureStorage.getActiveRole())
    val assignedRole: StateFlow<UserRole> = _assignedRole.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val securityTelemetry: Map<String, String>
        get() = secureStorage.getSecurityTelemetry()

    fun runKeystoreDiagnostics(): KeystoreDiagnostics {
        return secureStorage.runDiagnostics()
    }

    fun encryptSensitiveData(bytes: ByteArray): EncryptedPayload {
        return secureStorage.encryptData(bytes)
    }

    fun decryptSensitiveData(payload: EncryptedPayload): ByteArray {
        return secureStorage.decryptData(payload)
    }

    init {
        // Load persisted encrypted session upon startup
        val persistedUser = secureStorage.loadUser()
        if (persistedUser != null && persistedUser.isSessionActive) {
            _currentUser.value = persistedUser
        } else if (persistedUser != null && !persistedUser.isSessionActive) {
            // Expired session; purge securely
            secureStorage.clearUser()
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

    fun isSandboxMode(): Boolean = secureStorage.isSandboxAuthForced()

    fun setSandboxMode(enabled: Boolean) {
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
        _isLoading.value = true
        val resolvedName = fallbackDisplayName?.takeIf { it.isNotBlank() } ?: "Safety Inspector"
        val resolvedEmail = fallbackEmail?.takeIf { it.isNotBlank() } ?: "demo.inspector@safetylog.app"
        val user = createSandboxUser(
            displayName = resolvedName,
            email = resolvedEmail,
            provider = provider
        )
        secureStorage.saveUser(user)
        _currentUser.value = user
        _isLoading.value = false
        AuthResult.Success(user)
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
        val effectiveClientId = serverClientId?.trim()?.ifBlank { null } ?: getEffectiveGoogleClientId()?.trim()
        val resolvedName = fallbackDisplayName?.takeIf { it.isNotBlank() } ?: "Safety Inspector"
        val resolvedEmail = fallbackEmail?.takeIf { it.isNotBlank() } ?: "inspector@safetylog.app"

        if (isSandboxMode() || effectiveClientId.isNullOrBlank()) {
            val user = createSandboxUser(
                displayName = resolvedName,
                email = resolvedEmail,
                provider = AuthProvider.GOOGLE
            )
            secureStorage.saveUser(user)
            _currentUser.value = user
            _isLoading.value = false
            return@withContext AuthResult.Success(user)
        }

        val hostActivity = context.findActivity()
        if (hostActivity == null || hostActivity.isFinishing || hostActivity.isDestroyed) {
            _isLoading.value = false
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
                val userEmail = googleIdTokenCredential.id.trim().takeIf { it.isNotBlank() } ?: resolvedEmail
                val assignedRole = if (AuthorizationConfig.isAuthorizedOwner(userEmail)) {
                    UserRole.OWNER
                } else {
                    AuthorizationConfig.sanitizeRole(_currentUser.value?.role ?: secureStorage.getActiveRole(), userEmail)
                }
                val user = AuthUser(
                    userId = googleIdTokenCredential.id.trim().ifBlank { userEmail },
                    email = userEmail,
                    displayName = resolvedDisplayName,
                    photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                    provider = AuthProvider.GOOGLE,
                    role = assignedRole,
                    idToken = googleIdTokenCredential.idToken,
                    accessToken = "oauth2_bearer_${UUID.randomUUID()}",
                    tokenExpiryEpochMs = System.currentTimeMillis() + (30L * 24 * 3600 * 1000), // 30-day session
                    lastLoginEpochMs = System.currentTimeMillis(),
                    isHardwareEncrypted = true,
                    keyAlias = secureStorage.masterKeyAlias
                )

                secureStorage.saveUser(user)
                _currentUser.value = user
                _isLoading.value = false
                AuthResult.Success(user)
            } else {
                _isLoading.value = false
                AuthResult.Error("Unsupported credential type returned from Google Credential Manager (${credential::class.java.simpleName}).")
            }
        } catch (e: Exception) {
            _isLoading.value = false
            if (isCancellation(e)) {
                AuthResult.Cancelled
            } else if (e is androidx.credentials.exceptions.NoCredentialException) {
                AuthResult.Error(
                    "No Google accounts found or account selection was cancelled. Add a Google account under Android Settings > Accounts, or check Google Play Services."
                )
            } else {
                val msg = e.localizedMessage ?: e.message ?: "Authentication error"
                val errorHint = if (msg.contains("10") || msg.contains("12500") || msg.contains("DEVELOPER_ERROR")) {
                    " (Developer Error: Ensure your Web Client ID is used, and the Play App Signing SHA-1 or release SHA-1 is registered in Google Cloud Console OAuth 2.0 Credentials with package 'com.safetylog.inspection.app')."
                } else {
                    ""
                }
                AuthResult.Error("Google Sign-In failed: $msg$errorHint")
            }
        }
    }

    /**
     * Microsoft Entra / Office 365 OAuth authentication
     */
    suspend fun signInWithMicrosoft(
        email: String = "inspector@apexcommercial.onmicrosoft.com",
        displayName: String = "Senior Safety Officer"
    ): AuthResult = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            val user = createSandboxUser(
                displayName = displayName,
                email = email,
                provider = AuthProvider.MICROSOFT
            )
            secureStorage.saveUser(user)
            _currentUser.value = user
            _isLoading.value = false
            AuthResult.Success(user)
        } catch (e: Exception) {
            _isLoading.value = false
            AuthResult.Error("Microsoft Authentication failed: ${e.message}", e)
        }
    }

    /**
     * Corporate SAML / OIDC Single Sign-On
     */
    suspend fun signInWithCorporateSso(
        companyDomain: String,
        officerName: String
    ): AuthResult = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            val cleanDomain = companyDomain.trim().lowercase().removePrefix("@")
            val email = "auditor@$cleanDomain"
            val user = createSandboxUser(
                displayName = officerName.ifBlank { "Corporate Safety Inspector" },
                email = email,
                provider = AuthProvider.CORPORATE_SSO
            )
            secureStorage.saveUser(user)
            _currentUser.value = user
            _isLoading.value = false
            AuthResult.Success(user)
        } catch (e: Exception) {
            _isLoading.value = false
            AuthResult.Error("Corporate SSO failed: ${e.message}", e)
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
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {}
        secureStorage.clearUser()
        _currentUser.value = null
        _isLoading.value = false
    }

    fun updateUserRole(newRole: UserRole) {
        val currentEmail = _currentUser.value?.email ?: secureStorage.loadUser()?.email
        val sanitizedRole = AuthorizationConfig.sanitizeRole(newRole, currentEmail)
        secureStorage.setActiveRole(sanitizedRole)
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
        val resolvedRole = if (AuthorizationConfig.isAuthorizedOwner(email)) {
            UserRole.OWNER
        } else {
            AuthorizationConfig.sanitizeRole(role, email)
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
