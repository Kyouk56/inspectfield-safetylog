package com.safetylog.app.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.safetylog.app.data.BillingRepository
import com.safetylog.app.data.auth.*
import com.safetylog.app.data.dao.ProfileDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AuthUiState(
    val currentUser: AuthUser? = null,
    val isLoading: Boolean = false,
    val isRunningDiagnostics: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val securityTelemetry: Map<String, String> = emptyMap(),
    val keystoreDiagnostics: KeystoreDiagnostics? = null,
    val canUseCompanyOAuth: Boolean = false,
    val paymentStatus: PaymentStatus = PaymentStatus.ACTIVE,
    val subscriptionTier: SubscriptionTier = SubscriptionTier.FREE,
    val companyOAuthConfig: CompanyOAuthConfig = CompanyOAuthConfig(),
    val googleClientId: String? = null,
    val isSandboxMode: Boolean = false
) {
    val isAuthenticated: Boolean get() = currentUser != null
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val profileDao: ProfileDao,
    private val billingRepository: BillingRepository? = null
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _successMessage = MutableStateFlow<String?>(null)
    private val _keystoreDiagnostics = MutableStateFlow<KeystoreDiagnostics?>(null)
    private val _isRunningDiagnostics = MutableStateFlow(false)
    private val _isSandboxMode = MutableStateFlow(authRepository.isSandboxMode())

    val uiState: StateFlow<AuthUiState> = combine(
        authRepository.currentUser,
        authRepository.isLoading,
        _errorMessage,
        _successMessage,
        combine(_keystoreDiagnostics, _isRunningDiagnostics) { diag, running -> Pair(diag, running) },
        billingRepository?.canUseCompanyOAuth ?: MutableStateFlow(false),
        billingRepository?.paymentStatus ?: MutableStateFlow(PaymentStatus.ACTIVE),
        billingRepository?.currentTier ?: MutableStateFlow(SubscriptionTier.FREE),
        billingRepository?.companyOAuthConfig ?: MutableStateFlow(CompanyOAuthConfig()),
        _isSandboxMode
    ) { args: Array<Any?> ->
        val user = args[0] as? AuthUser
        val loading = args[1] as Boolean
        val error = args[2] as? String
        val success = args[3] as? String
        @Suppress("UNCHECKED_CAST")
        val diagPair = args[4] as Pair<KeystoreDiagnostics?, Boolean>
        val (diagnostics, runningDiag) = diagPair
        val canCompanyOAuth = args[5] as Boolean
        val payStatus = args[6] as PaymentStatus
        val tier = args[7] as SubscriptionTier
        val companyConfig = args[8] as CompanyOAuthConfig
        val sandboxActive = args[9] as Boolean

        AuthUiState(
            currentUser = user,
            isLoading = loading,
            isRunningDiagnostics = runningDiag,
            errorMessage = error,
            successMessage = success,
            securityTelemetry = authRepository.securityTelemetry,
            keystoreDiagnostics = diagnostics,
            canUseCompanyOAuth = canCompanyOAuth,
            paymentStatus = payStatus,
            subscriptionTier = tier,
            companyOAuthConfig = companyConfig,
            googleClientId = authRepository.getEffectiveGoogleClientId(),
            isSandboxMode = sandboxActive
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuthUiState(
            currentUser = authRepository.currentUser.value,
            securityTelemetry = authRepository.securityTelemetry,
            canUseCompanyOAuth = billingRepository?.canUseCompanyOAuth?.value ?: false,
            paymentStatus = billingRepository?.paymentStatus?.value ?: PaymentStatus.ACTIVE,
            subscriptionTier = billingRepository?.currentTier?.value ?: SubscriptionTier.FREE,
            companyOAuthConfig = billingRepository?.companyOAuthConfig?.value ?: CompanyOAuthConfig(),
            googleClientId = authRepository.getEffectiveGoogleClientId(),
            isSandboxMode = authRepository.isSandboxMode()
        )
    )

    init {
        runKeystoreDiagnostics(showSuccessSnackbar = false)
    }

    fun runKeystoreDiagnostics(showSuccessSnackbar: Boolean = true) {
        viewModelScope.launch {
            _isRunningDiagnostics.value = true
            val diag = withContext(Dispatchers.Default) {
                authRepository.runKeystoreDiagnostics()
            }
            _keystoreDiagnostics.value = diag
            _isRunningDiagnostics.value = false
            if (diag.isFunctional) {
                if (showSuccessSnackbar) {
                    _successMessage.value = "Keystore verified: Hardware AES-256 cipher operational (${diag.latencyMs}ms)"
                }
            } else {
                _errorMessage.value = "Keystore alert: ${diag.errorMessage ?: "Hardware check failed"}"
            }
        }
    }

    fun signInWithGoogle(context: Context, serverClientId: String? = null) {
        viewModelScope.launch {
            _errorMessage.value = null
            val profile = profileDao.getProfileOnce()
            val fallbackName = profile?.inspectorName?.takeIf { it.isNotBlank() } ?: "Safety Inspector"
            val fallbackEmail = profile?.contactEmail?.takeIf { it.isNotBlank() } ?: ""
            when (val result = authRepository.signInWithGoogle(
                context = context,
                serverClientId = serverClientId,
                fallbackDisplayName = fallbackName,
                fallbackEmail = fallbackEmail
            )) {
                is AuthResult.Success -> {
                    _successMessage.value = "Securely signed in as ${result.user.displayName}"
                    authRepository.syncWithInspectorProfile(profileDao)
                }
                is AuthResult.Error -> {
                    _errorMessage.value = result.message
                }
                is AuthResult.Cancelled -> {
                    // User cancelled prompt
                }
            }
        }
    }

    fun saveCustomGoogleClientId(clientId: String?) {
        authRepository.setCustomGoogleClientId(clientId)
        _successMessage.value = if (clientId.isNullOrBlank()) "Cleared custom Google Client ID" else "Saved Google OAuth Client ID"
    }

    fun setSandboxMode(enabled: Boolean) {
        authRepository.setSandboxMode(enabled)
        _isSandboxMode.value = enabled
        _successMessage.value = if (enabled) "Sandbox Mode enabled (local offline session)" else "Live Google Cloud OAuth enabled"
    }

    fun signInSandbox() {
        viewModelScope.launch {
            _errorMessage.value = null
            val profile = profileDao.getProfileOnce()
            val fallbackName = profile?.inspectorName?.takeIf { it.isNotBlank() } ?: "Safety Inspector"
            val fallbackEmail = profile?.contactEmail?.takeIf { it.isNotBlank() } ?: ""
            when (val result = authRepository.signInSandbox(
                fallbackDisplayName = fallbackName,
                fallbackEmail = fallbackEmail,
                provider = AuthProvider.GOOGLE
            )) {
                is AuthResult.Success -> {
                    _successMessage.value = "Verified local session active: ${result.user.displayName}"
                    authRepository.syncWithInspectorProfile(profileDao)
                }
                is AuthResult.Error -> {
                    _errorMessage.value = result.message
                }
                is AuthResult.Cancelled -> {}
            }
        }
    }

    fun getEffectiveGoogleClientId(): String? = authRepository.getEffectiveGoogleClientId()

    fun signInWithMicrosoft(email: String = "officer@apexcommercial.onmicrosoft.com") {
        viewModelScope.launch {
            _errorMessage.value = null
            when (val result = authRepository.signInWithMicrosoft(email = email)) {
                is AuthResult.Success -> {
                    _successMessage.value = "Microsoft SSO connected: ${result.user.displayName}"
                    authRepository.syncWithInspectorProfile(profileDao)
                }
                is AuthResult.Error -> {
                    _errorMessage.value = result.message
                }
                is AuthResult.Cancelled -> {}
            }
        }
    }

    fun signInWithCorporateSso(domain: String, officerName: String) {
        viewModelScope.launch {
            _errorMessage.value = null

            // Self-service verification: Company OAuth is locked behind Enterprise!
            if (billingRepository != null && !billingRepository.canUseCompanyOAuth.value) {
                _errorMessage.value = "Enterprise Subscription Required: Corporate SSO and Company OAuth domains require an active Enterprise subscription ($0.50/seat/mo). Please upgrade below."
                return@launch
            }

            if (domain.isBlank()) {
                _errorMessage.value = "Please enter a corporate domain (e.g. acmefire.com)"
                return@launch
            }
            when (val result = authRepository.signInWithCorporateSso(domain, officerName)) {
                is AuthResult.Success -> {
                    _successMessage.value = "Corporate SSO connected for $domain"
                    authRepository.syncWithInspectorProfile(profileDao)
                }
                is AuthResult.Error -> {
                    _errorMessage.value = result.message
                }
                is AuthResult.Cancelled -> {}
            }
        }
    }

    fun signInWithEnterprise(domain: String, officerName: String) {
        signInWithCorporateSso(domain, officerName)
    }

    fun setSubscriptionTier(tier: SubscriptionTier, seats: Int = 5) {
        billingRepository?.setTier(tier)
        if (tier == SubscriptionTier.ENTERPRISE) {
            billingRepository?.setEnterpriseSeats(seats)
        }
    }

    fun launchBillingFlow(activity: android.app.Activity, tier: SubscriptionTier, seats: Int = 5) {
        if (billingRepository != null) {
            billingRepository.launchBillingFlow(activity, tier, seats)
        } else {
            setSubscriptionTier(tier, seats)
        }
    }

    fun cancelSubscription() {
        billingRepository?.cancelSubscription()
    }

    fun markPaymentFailed() {
        billingRepository?.markPaymentFailed()
    }

    fun reactivateSubscription() {
        billingRepository?.reactivateSubscription()
    }

    fun syncInspectorProfile() {
        viewModelScope.launch {
            authRepository.syncWithInspectorProfile(profileDao, forceOverwrite = true)
            _successMessage.value = "Inspector profile updated with verified OAuth identity."
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _successMessage.value = "Signed out. Encrypted session tokens destroyed."
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val profileDao: ProfileDao,
        private val billingRepository: BillingRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(authRepository, profileDao, billingRepository) as T
        }
    }
}
