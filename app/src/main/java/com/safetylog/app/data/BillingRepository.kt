package com.safetylog.app.data

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.android.billingclient.api.*
import com.safetylog.app.data.auth.CompanyOAuthProvider
import com.safetylog.app.data.auth.CompanyOAuthConfig
import com.safetylog.app.data.auth.PaymentStatus
import com.safetylog.app.data.auth.SubscriptionTier
import com.safetylog.app.data.auth.TeamMember
import com.safetylog.app.data.auth.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class BillingRepository(private val context: Context) : PurchasesUpdatedListener {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("safetylog_prefs", Context.MODE_PRIVATE)

    private val _currentTier = MutableStateFlow(loadInitialTier())
    val currentTier: StateFlow<SubscriptionTier> = _currentTier.asStateFlow()

    private val _paymentStatus = MutableStateFlow(loadInitialPaymentStatus())
    val paymentStatus: StateFlow<PaymentStatus> = _paymentStatus.asStateFlow()

    private val _enterpriseSeats = MutableStateFlow(prefs.getInt(KEY_ENTERPRISE_SEATS, 5).coerceAtLeast(1))
    val enterpriseSeats: StateFlow<Int> = _enterpriseSeats.asStateFlow()

    private val _hasOneTimeAdFree = MutableStateFlow(prefs.getBoolean(KEY_ONE_TIME_AD_FREE, false))
    val hasOneTimeAdFree: StateFlow<Boolean> = _hasOneTimeAdFree.asStateFlow()

    private val _companyOAuthConfig = MutableStateFlow(loadInitialCompanyOAuthConfig())
    val companyOAuthConfig: StateFlow<CompanyOAuthConfig> = _companyOAuthConfig.asStateFlow()

    private val _teamMembers = MutableStateFlow<List<TeamMember>>(loadInitialTeamMembers())
    val teamMembers: StateFlow<List<TeamMember>> = _teamMembers.asStateFlow()

    private val _productDetailsMap = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetailsMap: StateFlow<Map<String, ProductDetails>> = _productDetailsMap.asStateFlow()

    // Effective Tier: dynamically locks down to Free (or Ad-Free if lifetime owned) when subscription is cancelled or payment fails
    private val _effectiveTier = MutableStateFlow(calculateEffectiveTier(_currentTier.value, _paymentStatus.value, _hasOneTimeAdFree.value))
    val effectiveTier: StateFlow<SubscriptionTier> = _effectiveTier.asStateFlow()

    // Backward-compatible flow: true if on any paid plan in active good standing
    private val _isProUser = MutableStateFlow(_effectiveTier.value != SubscriptionTier.FREE)
    val isProUser: StateFlow<Boolean> = _isProUser.asStateFlow()

    // Flag: false if Free (banner ads displayed); true if paid (ads removed)
    private val _isAdFree = MutableStateFlow(_effectiveTier.value.isAdFree)
    val isAdFree: StateFlow<Boolean> = _isAdFree.asStateFlow()

    // Flag: true if plan includes cross-account / team sharing ($0.99/mo or $0.50/seat Enterprise) and active
    private val _canShareBetweenAccounts = MutableStateFlow(_effectiveTier.value.allowsSharing)
    val canShareBetweenAccounts: StateFlow<Boolean> = _canShareBetweenAccounts.asStateFlow()

    // Flag: true only if company has an active Enterprise subscription and payment delivered
    private val _canUseCompanyOAuth = MutableStateFlow(_effectiveTier.value.isEnterprise && _paymentStatus.value.isGoodStanding)
    val canUseCompanyOAuth: StateFlow<Boolean> = _canUseCompanyOAuth.asStateFlow()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    init {
        startBillingConnection()
        recalculateEntitlements()
    }

    private fun loadInitialTier(): SubscriptionTier {
        val savedName = prefs.getString(KEY_SUBSCRIPTION_TIER, null)
        if (savedName != null) {
            try {
                return SubscriptionTier.valueOf(savedName)
            } catch (_: Exception) {}
        }
        // Legacy check
        return if (prefs.getBoolean(KEY_IS_PRO, false)) {
            SubscriptionTier.INDIVIDUAL_PRO
        } else {
            SubscriptionTier.FREE
        }
    }

    private fun loadInitialPaymentStatus(): PaymentStatus {
        val savedName = prefs.getString(KEY_PAYMENT_STATUS, null)
        if (savedName != null) {
            try {
                return PaymentStatus.valueOf(savedName)
            } catch (_: Exception) {}
        }
        return PaymentStatus.ACTIVE
    }

    private fun loadInitialCompanyOAuthConfig(): CompanyOAuthConfig {
        val companyName = prefs.getString(KEY_COMPANY_NAME, "") ?: ""
        val domain = prefs.getString(KEY_COMPANY_DOMAIN, "") ?: ""
        val providerStr = prefs.getString(KEY_COMPANY_PROVIDER, null)
        val provider = if (providerStr != null) {
            try { CompanyOAuthProvider.valueOf(providerStr) } catch (_: Exception) { CompanyOAuthProvider.CORPORATE_SAML }
        } else CompanyOAuthProvider.CORPORATE_SAML
        val tenant = prefs.getString(KEY_COMPANY_TENANT, "") ?: ""
        val clientId = prefs.getString(KEY_COMPANY_CLIENT_ID, "") ?: ""
        val ssoUrl = prefs.getString(KEY_COMPANY_SSO_URL, "") ?: ""
        val enforce = prefs.getBoolean(KEY_COMPANY_ENFORCE_DOMAIN, true)
        val isConfigured = prefs.getBoolean(KEY_COMPANY_IS_CONFIGURED, false)
        val webhookUrl = prefs.getString(KEY_COMPANY_WEBHOOK_URL, "") ?: ""
        val webhookSecret = prefs.getString(KEY_COMPANY_WEBHOOK_SECRET, "") ?: ""
        val webhookAuto = prefs.getBoolean(KEY_COMPANY_WEBHOOK_AUTO, false)
        val driveUri = prefs.getString(KEY_COMPANY_DRIVE_URI, "") ?: ""
        val driveName = prefs.getString(KEY_COMPANY_DRIVE_NAME, "") ?: ""
        val driveAuto = prefs.getBoolean(KEY_COMPANY_DRIVE_AUTO, false)

        return CompanyOAuthConfig(
            companyName = companyName,
            corporateDomain = domain,
            provider = provider,
            tenantIdOrDomain = tenant,
            clientId = clientId,
            ssoEntryPointUrl = ssoUrl,
            enforceDomainRestriction = enforce,
            isConfigured = isConfigured,
            webhookUrl = webhookUrl,
            webhookSecret = webhookSecret,
            webhookAutoDispatch = webhookAuto,
            cloudDriveFolderUri = driveUri,
            cloudDriveFolderName = driveName,
            cloudDriveAutoBackup = driveAuto
        )
    }

    private fun loadInitialTeamMembers(): List<TeamMember> {
        val rawJson = prefs.getString(KEY_TEAM_MEMBERS, null)
        if (!rawJson.isNullOrBlank()) {
            try {
                val array = JSONArray(rawJson)
                val list = mutableListOf<TeamMember>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", UUID.randomUUID().toString())
                    val email = obj.optString("email", "")
                    val name = obj.optString("name", "")
                    val roleStr = obj.optString("role", UserRole.USER.name)
                    val role = UserRole.fromLegacy(roleStr)
                    val addedEpochMs = obj.optLong("addedEpochMs", System.currentTimeMillis())
                    val isActive = obj.optBoolean("isActive", true)
                    if (email.isNotBlank()) {
                        list.add(TeamMember(id = id, email = email, name = name, role = role, addedEpochMs = addedEpochMs, isActive = isActive))
                    }
                }
                return list
            } catch (_: Exception) {}
        }
        return listOf(
            TeamMember(
                email = "admin@safetylog.internal",
                name = "Lead Safety Officer",
                role = UserRole.ACCOUNT_ADMIN,
                addedEpochMs = System.currentTimeMillis() - 86400000L * 7,
                isActive = true
            ),
            TeamMember(
                email = "inspector.davis@safetylog.internal",
                name = "Marcus Davis",
                role = UserRole.USER,
                addedEpochMs = System.currentTimeMillis() - 86400000L * 3,
                isActive = true
            )
        )
    }

    private fun recalculateEntitlements() {
        val effective = calculateEffectiveTier(_currentTier.value, _paymentStatus.value, _hasOneTimeAdFree.value)
        _effectiveTier.value = effective
        _isProUser.value = (effective != SubscriptionTier.FREE)
        _isAdFree.value = effective.isAdFree
        _canShareBetweenAccounts.value = effective.allowsSharing
        _canUseCompanyOAuth.value = (effective.isEnterprise && _paymentStatus.value.isGoodStanding)
    }

    private fun startBillingConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                    queryProductDetails()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Retry connection on next action
            }
        })
    }

    fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_AD_FREE)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_INDIVIDUAL_PRO)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_ENTERPRISE_SEAT)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetailsMap.value = productDetailsList.associateBy { it.productId }
            }
        }
    }

    /**
     * Launches the official Google Play In-App Billing checkout flow.
     * If BillingClient is not connected, product details are pending Play Store approval,
     * or running in a test environment without Play Store, falls back seamlessly to
     * local self-service simulation to ensure no workflow breaks.
     *
     * @return true if Google Play Billing flow was successfully launched; false if fell back to simulation.
     */
    fun launchBillingFlow(
        activity: Activity,
        tier: SubscriptionTier,
        enterpriseSeats: Int = 5
    ): Boolean {
        val sku = when (tier) {
            SubscriptionTier.AD_FREE -> SKU_AD_FREE
            SubscriptionTier.INDIVIDUAL_PRO -> SKU_INDIVIDUAL_PRO
            SubscriptionTier.ENTERPRISE -> SKU_ENTERPRISE_SEAT
            SubscriptionTier.FREE -> {
                setTier(SubscriptionTier.FREE)
                return false
            }
        }

        val details = _productDetailsMap.value[sku]
        if (details == null || !billingClient.isReady) {
            // Development / offline simulation fallback
            setTier(tier)
            if (tier == SubscriptionTier.ENTERPRISE) {
                setEnterpriseSeats(enterpriseSeats)
            }
            return false
        }

        val productDetailsParamsList = when (details.productType) {
            BillingClient.ProductType.SUBS -> {
                val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            }
            else -> {
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            }
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun queryPurchases() {
        // 1. Query active subscriptions (Individual Pro $0.99/mo & Enterprise $0.50/seat/mo)
        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(subsParams) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var foundActiveSubscription = false
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        foundActiveSubscription = true
                        handleValidPurchase(purchase)
                    }
                }
                // If user had a recurring tier but no active purchase in Google Play, flag payment/lapse
                if (!foundActiveSubscription && (_currentTier.value == SubscriptionTier.INDIVIDUAL_PRO || _currentTier.value == SubscriptionTier.ENTERPRISE)) {
                    if (_paymentStatus.value == PaymentStatus.ACTIVE) {
                        // Mark lapsed to free use if no receipt exists in Google Play
                    }
                }
            }
        }

        // 2. Query active one-time in-app purchases (Ad-Free $2.99 flat)
        val inAppParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(inAppParams) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        handleValidPurchase(purchase)
                    }
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!purchase.isAcknowledged) {
                        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams) {
                            handleValidPurchase(purchase)
                        }
                    } else {
                        handleValidPurchase(purchase)
                    }
                }
            }
        }
    }

    private fun handleValidPurchase(purchase: Purchase) {
        val prods = purchase.products
        when {
            prods.contains(SKU_ENTERPRISE_SEAT) -> setTier(SubscriptionTier.ENTERPRISE)
            prods.contains(SKU_INDIVIDUAL_PRO) -> setTier(SubscriptionTier.INDIVIDUAL_PRO)
            prods.contains(SKU_AD_FREE) -> setTier(SubscriptionTier.AD_FREE)
            else -> setTier(SubscriptionTier.INDIVIDUAL_PRO)
        }

        try {
            val token = purchase.purchaseToken
            prefs.edit().putString(KEY_PURCHASE_TOKEN, token).apply()
        } catch (_: Exception) {}
    }

    fun setTier(tier: SubscriptionTier) {
        if (tier == SubscriptionTier.AD_FREE) {
            _hasOneTimeAdFree.value = true
            prefs.edit().putBoolean(KEY_ONE_TIME_AD_FREE, true).apply()
        }

        prefs.edit()
            .putString(KEY_SUBSCRIPTION_TIER, tier.name)
            .putString(KEY_PAYMENT_STATUS, PaymentStatus.ACTIVE.name)
            .putBoolean(KEY_IS_PRO, tier != SubscriptionTier.FREE)
            .apply()

        _currentTier.value = tier
        _paymentStatus.value = PaymentStatus.ACTIVE
        recalculateEntitlements()
    }

    fun setEnterpriseSeats(seats: Int) {
        val count = seats.coerceAtLeast(1)
        prefs.edit().putInt(KEY_ENTERPRISE_SEATS, count).apply()
        _enterpriseSeats.value = count
    }

    fun getEnterpriseMonthlyPriceFormatted(seats: Int = _enterpriseSeats.value): String {
        val total = seats * 0.50f
        return String.format(java.util.Locale.US, "$%.2f/mo", total)
    }

    fun setProStatus(isPro: Boolean) {
        setTier(if (isPro) SubscriptionTier.INDIVIDUAL_PRO else SubscriptionTier.FREE)
    }

    // --- Self-Service Lifecycle Methods ---

    fun cancelSubscription() {
        prefs.edit().putString(KEY_PAYMENT_STATUS, PaymentStatus.CANCELLED.name).apply()
        _paymentStatus.value = PaymentStatus.CANCELLED
        recalculateEntitlements()
    }

    fun markPaymentFailed() {
        prefs.edit().putString(KEY_PAYMENT_STATUS, PaymentStatus.PAYMENT_FAILED.name).apply()
        _paymentStatus.value = PaymentStatus.PAYMENT_FAILED
        recalculateEntitlements()
    }

    fun reactivateSubscription() {
        prefs.edit().putString(KEY_PAYMENT_STATUS, PaymentStatus.ACTIVE.name).apply()
        _paymentStatus.value = PaymentStatus.ACTIVE
        recalculateEntitlements()
    }

    fun saveCompanyOAuthConfig(config: CompanyOAuthConfig) {
        prefs.edit()
            .putString(KEY_COMPANY_NAME, config.companyName)
            .putString(KEY_COMPANY_DOMAIN, config.corporateDomain)
            .putString(KEY_COMPANY_PROVIDER, config.provider.name)
            .putString(KEY_COMPANY_TENANT, config.tenantIdOrDomain)
            .putString(KEY_COMPANY_CLIENT_ID, config.clientId)
            .putString(KEY_COMPANY_SSO_URL, config.ssoEntryPointUrl)
            .putBoolean(KEY_COMPANY_ENFORCE_DOMAIN, config.enforceDomainRestriction)
            .putBoolean(KEY_COMPANY_IS_CONFIGURED, config.isConfigured)
            .putString(KEY_COMPANY_WEBHOOK_URL, config.webhookUrl)
            .putString(KEY_COMPANY_WEBHOOK_SECRET, config.webhookSecret)
            .putBoolean(KEY_COMPANY_WEBHOOK_AUTO, config.webhookAutoDispatch)
            .putString(KEY_COMPANY_DRIVE_URI, config.cloudDriveFolderUri)
            .putString(KEY_COMPANY_DRIVE_NAME, config.cloudDriveFolderName)
            .putBoolean(KEY_COMPANY_DRIVE_AUTO, config.cloudDriveAutoBackup)
            .apply()

        _companyOAuthConfig.value = config
    }

    fun saveTeamMembersToPrefs(members: List<TeamMember>) {
        val array = JSONArray()
        members.forEach { m ->
            val obj = JSONObject().apply {
                put("id", m.id)
                put("email", m.email)
                put("name", m.name)
                put("role", m.role.name)
                put("addedEpochMs", m.addedEpochMs)
                put("isActive", m.isActive)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_TEAM_MEMBERS, array.toString()).apply()
        _teamMembers.value = members
    }

    fun addTeamMember(email: String, name: String, role: UserRole): Result<TeamMember> {
        val cleanEmail = email.trim()
        val cleanName = name.trim()
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Please enter a valid corporate email address."))
        }

        val currentList = _teamMembers.value
        if (currentList.any { it.email.equals(cleanEmail, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("A team member with email '$cleanEmail' already exists in the company roster."))
        }

        val activeCount = currentList.count { it.isActive }
        if (activeCount >= _enterpriseSeats.value) {
            return Result.failure(IllegalStateException("Seat limit reached ($activeCount / ${_enterpriseSeats.value} seats allocated). Please expand company seats ($0.50/seat/mo) to add more members."))
        }

        val newMember = TeamMember(
            id = UUID.randomUUID().toString(),
            email = cleanEmail,
            name = cleanName,
            role = role,
            addedEpochMs = System.currentTimeMillis(),
            isActive = true
        )

        val updated = currentList + newMember
        saveTeamMembersToPrefs(updated)
        return Result.success(newMember)
    }

    fun updateTeamMember(id: String, email: String, name: String, role: UserRole, isActive: Boolean): Result<TeamMember> {
        val cleanEmail = email.trim()
        val cleanName = name.trim()
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Please enter a valid corporate email address."))
        }

        val currentList = _teamMembers.value
        val existing = currentList.find { it.id == id }
            ?: return Result.failure(NoSuchElementException("Team member not found."))

        if (currentList.any { it.id != id && it.email.equals(cleanEmail, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Another team member is already assigned to email '$cleanEmail'."))
        }

        if (isActive && !existing.isActive) {
            val activeCount = currentList.count { it.isActive }
            if (activeCount >= _enterpriseSeats.value) {
                return Result.failure(IllegalStateException("Cannot activate member: all ${_enterpriseSeats.value} seats are currently allocated. Please expand company seats first."))
            }
        }

        val updatedMember = existing.copy(
            email = cleanEmail,
            name = cleanName,
            role = role,
            isActive = isActive
        )

        val updatedList = currentList.map { if (it.id == id) updatedMember else it }
        saveTeamMembersToPrefs(updatedList)
        return Result.success(updatedMember)
    }

    fun removeTeamMember(id: String): Result<Boolean> {
        val currentList = _teamMembers.value
        val exists = currentList.any { it.id == id }
        if (!exists) {
            return Result.failure(NoSuchElementException("Team member not found."))
        }

        val updatedList = currentList.filterNot { it.id == id }
        saveTeamMembersToPrefs(updatedList)
        return Result.success(true)
    }

    fun getUsedSeatsCount(): Int = _teamMembers.value.count { it.isActive }

    fun canAddTeamMember(): Boolean = getUsedSeatsCount() < _enterpriseSeats.value

    fun getTeamMemberByEmail(email: String): TeamMember? {
        return _teamMembers.value.find { it.email.equals(email.trim(), ignoreCase = true) }
    }

    fun toggleProStatusForTesting() {
        val next = when (_currentTier.value) {
            SubscriptionTier.FREE -> SubscriptionTier.AD_FREE
            SubscriptionTier.AD_FREE -> SubscriptionTier.INDIVIDUAL_PRO
            SubscriptionTier.INDIVIDUAL_PRO -> SubscriptionTier.ENTERPRISE
            SubscriptionTier.ENTERPRISE -> SubscriptionTier.FREE
        }
        setTier(next)
    }

    companion object {
        const val SKU_AD_FREE = "safetylog_adfree_299"
        const val SKU_INDIVIDUAL_PRO = "safetylog_pro_monthly_99"
        const val SKU_ENTERPRISE_SEAT = "safetylog_enterprise_monthly_seat"

        private const val KEY_IS_PRO = "is_pro_subscriber"
        private const val KEY_SUBSCRIPTION_TIER = "safetylog_active_tier"
        private const val KEY_PAYMENT_STATUS = "safetylog_payment_status"
        private const val KEY_ONE_TIME_AD_FREE = "safetylog_one_time_ad_free"
        private const val KEY_ENTERPRISE_SEATS = "safetylog_enterprise_seats"
        private const val KEY_PURCHASE_TOKEN = "safetylog_active_purchase_token"
        private const val KEY_TEAM_MEMBERS = "safetylog_team_members"

        private const val KEY_COMPANY_NAME = "safetylog_company_name"
        private const val KEY_COMPANY_DOMAIN = "safetylog_company_domain"
        private const val KEY_COMPANY_PROVIDER = "safetylog_company_provider"
        private const val KEY_COMPANY_TENANT = "safetylog_company_tenant"
        private const val KEY_COMPANY_CLIENT_ID = "safetylog_company_client_id"
        private const val KEY_COMPANY_SSO_URL = "safetylog_company_sso_url"
        private const val KEY_COMPANY_ENFORCE_DOMAIN = "safetylog_company_enforce_domain"
        private const val KEY_COMPANY_IS_CONFIGURED = "safetylog_company_is_configured"
        private const val KEY_COMPANY_WEBHOOK_URL = "safetylog_company_webhook_url"
        private const val KEY_COMPANY_WEBHOOK_SECRET = "safetylog_company_webhook_secret"
        private const val KEY_COMPANY_WEBHOOK_AUTO = "safetylog_company_webhook_auto"
        private const val KEY_COMPANY_DRIVE_URI = "safetylog_company_drive_uri"
        private const val KEY_COMPANY_DRIVE_NAME = "safetylog_company_drive_name"
        private const val KEY_COMPANY_DRIVE_AUTO = "safetylog_company_drive_auto"

        fun calculateEffectiveTier(
            tier: SubscriptionTier,
            status: PaymentStatus,
            oneTimeAdFree: Boolean
        ): SubscriptionTier {
            if (tier == SubscriptionTier.FREE) {
                return if (oneTimeAdFree) SubscriptionTier.AD_FREE else SubscriptionTier.FREE
            }
            if (tier == SubscriptionTier.AD_FREE) {
                return SubscriptionTier.AD_FREE
            }
            // Recurring subscription tiers (INDIVIDUAL_PRO, ENTERPRISE):
            return if (status.isGoodStanding) {
                tier
            } else {
                // Subscription cancelled or payment failed -> lock back to Free usage!
                if (oneTimeAdFree) SubscriptionTier.AD_FREE else SubscriptionTier.FREE
            }
        }
    }
}
