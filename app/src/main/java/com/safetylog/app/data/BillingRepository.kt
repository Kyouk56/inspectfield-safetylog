package com.safetylog.app.data

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.billingclient.api.*
import com.safetylog.app.util.TestControls
import com.safetylog.app.data.auth.CompanyOAuthProvider
import com.safetylog.app.data.auth.CompanyOAuthConfig
import com.safetylog.app.data.auth.SecureTokenStorage
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

class BillingRepository private constructor(
    private val context: Context?,
    private val prefs: SharedPreferences,
    private val debugMode: Boolean = TestControls.isEnabled,
    private val secureStorage: SecureTokenStorage? = null
) : PurchasesUpdatedListener {

    constructor(context: Context) : this(context, context.getSharedPreferences("safetylog_prefs", Context.MODE_PRIVATE), TestControls.isEnabled, SecureTokenStorage(context))

    // Test-friendly constructor: allows exercising tier/entitlement logic without an Android Context.
    constructor(prefs: SharedPreferences, debugMode: Boolean = TestControls.isEnabled) : this(null, prefs, debugMode, null)

    // Optional constructor accepting an explicit SecureTokenStorage (used when callers
    // share a vault instance instead of letting BillingRepository create its own).
    constructor(context: Context, secureStorage: SecureTokenStorage) : this(context, context.getSharedPreferences("safetylog_prefs", Context.MODE_PRIVATE), TestControls.isEnabled, secureStorage)

    constructor(prefs: SharedPreferences, secureStorage: SecureTokenStorage?, debugMode: Boolean = TestControls.isEnabled) : this(null, prefs, debugMode, secureStorage)

    constructor(prefs: SharedPreferences, debugMode: Boolean, secureStorage: SecureTokenStorage?) : this(null, prefs, debugMode, secureStorage)

    constructor(secureStorage: SecureTokenStorage, prefs: SharedPreferences, debugMode: Boolean = TestControls.isEnabled) : this(null, prefs, debugMode, secureStorage)

    /**
     * Optional hook invoked after a Play purchase is acknowledged and applied locally.
     * The app uses it to have the authorization server verify the token and reissue a
     * signed snapshot, so a verified purchase is reflected in the authoritative tier.
     */
    var onPurchaseVerified: ((productId: String, purchaseToken: String) -> Unit)? = null

    private val _currentTier = MutableStateFlow(loadInitialTier())
    val currentTier: StateFlow<SubscriptionTier> = _currentTier.asStateFlow()

    private val _paymentStatus = MutableStateFlow(loadInitialPaymentStatus())
    val paymentStatus: StateFlow<PaymentStatus> = _paymentStatus.asStateFlow()

    private val _enterpriseSeats = MutableStateFlow(prefs.getInt(KEY_ENTERPRISE_SEATS, 5).coerceAtLeast(1))
    val enterpriseSeats: StateFlow<Int> = _enterpriseSeats.asStateFlow()

    private val _hasOneTimeAdFree = MutableStateFlow(
        prefs.getBoolean(KEY_ONE_TIME_AD_FREE, false)
    )
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

    // Backward-compatible flow: true only for a paid *subscription* in good standing.
    // AD_FREE is the lifetime ad-removal license and must not unlock Pro-only quotas.
    private val _isProUser = MutableStateFlow(
        _effectiveTier.value == SubscriptionTier.INDIVIDUAL_PRO ||
            _effectiveTier.value == SubscriptionTier.ENTERPRISE
    )
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

    private val billingClient: BillingClient? = context?.let {
        BillingClient.newBuilder(it)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
    }

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

    private fun isEncryptedEnvelope(value: String): Boolean {
        val payload = com.safetylog.app.data.auth.EncryptedPayload.fromSerialized(value) ?: return false
        val base64Pattern = Regex("^[A-Za-z0-9+/=_-]+$")
        return payload.ivBase64.matches(base64Pattern) && payload.cipherTextBase64.matches(base64Pattern)
    }

    private fun decryptPrefString(stored: String?): String? {
        if (stored.isNullOrBlank()) return null
        val decrypted = secureStorage?.decryptString(stored)
        if (decrypted != null) return decrypted
        return if (isEncryptedEnvelope(stored)) null else stored
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
        val webhookSecret = decryptPrefString(prefs.getString(KEY_COMPANY_WEBHOOK_SECRET, "")) ?: ""
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
        val rawJson = decryptPrefString(prefs.getString(KEY_TEAM_MEMBERS, null))
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
        if (!debugMode) return emptyList()
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
        _isProUser.value = effective == SubscriptionTier.INDIVIDUAL_PRO ||
            effective == SubscriptionTier.ENTERPRISE
        _isAdFree.value = effective.isAdFree
        _canShareBetweenAccounts.value = effective.allowsSharing
        _canUseCompanyOAuth.value = (effective.isEnterprise && _paymentStatus.value.isGoodStanding)
    }

    private var reconnectAttempts = 0

    private fun startBillingConnection() {
        val client = billingClient ?: return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    reconnectAttempts = 0
                    queryPurchases()
                    queryProductDetails()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Bounded reconnect/retry so Play Billing reconnects cleanly instead of
                // staying disconnected. Back off exponentially, capped at 16s, max 5 tries.
                if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
                val attempt = reconnectAttempts
                reconnectAttempts++
                val delayMs = (1000L shl attempt).coerceAtMost(16000L)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (billingClient.isReady) {
                        reconnectAttempts = 0
                        return@postDelayed
                    }
                    startBillingConnection()
                }, delayMs)
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

        billingClient?.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetailsMap.value = result.productDetailsList.associateBy { it.productId }
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
        if (details == null || billingClient?.isReady != true) {
            // Simulation/offline fallback is DEBUG-only; in release builds we never
            // grant paid tiers for free. Return false so the UI can surface an
            // unavailable/error message instead of unlocking Pro/Enterprise.
            if (debugMode) {
                setTier(tier)
                if (tier == SubscriptionTier.ENTERPRISE) {
                    setEnterpriseSeats(enterpriseSeats)
                }
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

        val client = billingClient
        val result = client.launchBillingFlow(activity, billingFlowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun queryPurchases() {
        val client = billingClient ?: return
        // 1. Query active subscriptions (Individual Pro $0.99/mo & Enterprise $0.50/seat/mo)
        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        client.queryPurchasesAsync(subsParams) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var foundActiveSubscription = false
                var foundPendingPurchase = false
                for (purchase in purchases) {
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> {
                            foundActiveSubscription = true
                            handleValidPurchase(purchase)
                        }
                        Purchase.PurchaseState.PENDING -> {
                            // Ignore/mark pending cleanly; do not revoke entitlements.
                            foundPendingPurchase = true
                        }
                        else -> { /* ignore */ }
                    }
                }
                // Reconcile with Play: if a recurring tier is persisted locally but Play reports
                // no active subscription (cancelled at period end, refunded, revoked), the paid
                // entitlement must be revoked immediately instead of lingering forever.
                if (!foundActiveSubscription && !foundPendingPurchase &&
                    (_currentTier.value == SubscriptionTier.INDIVIDUAL_PRO ||
                        _currentTier.value == SubscriptionTier.ENTERPRISE)
                ) {
                    Log.w(TAG, "No active Play subscription found; revoking recurring entitlement")
                    markPaymentFailed()
                }
            }
        }

        // 2. Query active one-time in-app purchases (Ad-Free $2.99 flat)
        val inAppParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(inAppParams) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasAdFreePurchase = purchases.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.contains(SKU_AD_FREE)
                }
                for (purchase in purchases) {
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> handleValidPurchase(purchase)
                        Purchase.PurchaseState.PENDING -> { /* ignore pending cleanly */ }
                        else -> { /* ignore */ }
                    }
                }
                // A refunded/revoked lifetime license must no longer grant ad-free access.
                if (!hasAdFreePurchase && _hasOneTimeAdFree.value) {
                    Log.w(TAG, "Ad-free license no longer reported by Play; revoking")
                    _hasOneTimeAdFree.value = false
                    prefs.edit().remove(KEY_ONE_TIME_AD_FREE).apply()
                    if (_currentTier.value == SubscriptionTier.AD_FREE) {
                        applyTier(SubscriptionTier.FREE, preserveOneTimeAdFree = false)
                    } else {
                        recalculateEntitlements()
                    }
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    // Acknowledgement is centralised in handleValidPurchase so purchases
                    // discovered via queryPurchases are acknowledged too.
                    handleValidPurchase(purchase)
                }
            }
        }
    }

    private fun handleValidPurchase(purchase: Purchase) {
        val prods = purchase.products

        // Acknowledge here so both onPurchasesUpdated and queryPurchases acknowledge.
        // Unacknowledged purchases are refunded/revoked by Play after three days.
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient?.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Failed to acknowledge purchase: ${result.debugMessage}")
                }
            }
        }

        val isAdFreePurchase = prods.contains(SKU_AD_FREE)
        if (isAdFreePurchase) {
            _hasOneTimeAdFree.value = true
            prefs.edit().putBoolean(KEY_ONE_TIME_AD_FREE, true).apply()
        }
        // Do not downgrade an active Pro/Enterprise subscription to AD_FREE.
        val hasHigherActiveTier = (_currentTier.value == SubscriptionTier.INDIVIDUAL_PRO ||
            _currentTier.value == SubscriptionTier.ENTERPRISE) && _paymentStatus.value.isGoodStanding

        // Only explicitly recognised SKUs grant a paid entitlement. An unknown SKU
        // must never silently upgrade the user to Pro.
        when {
            prods.contains(SKU_ENTERPRISE_SEAT) -> applyTier(SubscriptionTier.ENTERPRISE, true)
            prods.contains(SKU_INDIVIDUAL_PRO) -> applyTier(SubscriptionTier.INDIVIDUAL_PRO, true)
            isAdFreePurchase && !hasHigherActiveTier -> applyTier(SubscriptionTier.AD_FREE, true)
            isAdFreePurchase && hasHigherActiveTier -> { /* keep active higher tier; do not downgrade */ }
            else -> Log.w(TAG, "Ignoring purchase with unrecognised product IDs: $prods")
        }

        try {
            val token = purchase.purchaseToken
            prefs.edit().putString(KEY_PURCHASE_TOKEN, token).apply()
            val purchasedSku = prods.firstOrNull {
                it == SKU_ENTERPRISE_SEAT || it == SKU_INDIVIDUAL_PRO || it == SKU_AD_FREE
            }
            // Do not invoke onPurchaseVerified to overwrite an active Pro/Enterprise
            // server tier with AD_FREE.
            val shouldVerify = when {
                purchasedSku == SKU_AD_FREE && hasHigherActiveTier -> false
                purchasedSku != null -> true
                else -> false
            }
            if (shouldVerify && purchasedSku != null) onPurchaseVerified?.invoke(purchasedSku, token)
        } catch (_: Exception) {}
    }

    /**
     * Applies a tier once the caller has a legitimate reason to do so: a purchase
     * reported by Play, or an explicitly DEBUG-only simulation. Private so that UI
     * code cannot hand out paid entitlements directly.
     */
    private fun applyTier(tier: SubscriptionTier, preserveOneTimeAdFree: Boolean) {
        val isOneTimeAdFree = when {
            tier == SubscriptionTier.AD_FREE -> true
            preserveOneTimeAdFree -> _hasOneTimeAdFree.value
            else -> false
        }
        _hasOneTimeAdFree.value = isOneTimeAdFree

        prefs.edit()
            .putString(KEY_SUBSCRIPTION_TIER, tier.name)
            .putString(KEY_PAYMENT_STATUS, PaymentStatus.ACTIVE.name)
            .putBoolean(KEY_ONE_TIME_AD_FREE, isOneTimeAdFree)
            .putBoolean(KEY_IS_PRO, tier == SubscriptionTier.INDIVIDUAL_PRO || tier == SubscriptionTier.ENTERPRISE)
            .apply()

        _currentTier.value = tier
        _paymentStatus.value = PaymentStatus.ACTIVE
        recalculateEntitlements()
    }

    /**
     * Host-facing tier change. In release builds this may only reset to FREE (cancel /
     * refund flows); paid tiers are granted exclusively through [handleValidPurchase].
     * DEBUG keeps the old simulation behaviour so local testing still works.
     */
    fun setTier(tier: SubscriptionTier, preserveOneTimeAdFree: Boolean = true) {
        if (!debugMode && tier != SubscriptionTier.FREE) {
            Log.w(TAG, "Ignoring release-build request to set paid tier directly: $tier")
            return
        }
        applyTier(tier, preserveOneTimeAdFree)
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
        if (!debugMode) return
        setTier(if (isPro) SubscriptionTier.INDIVIDUAL_PRO else SubscriptionTier.FREE)
    }

    // --- Self-Service Lifecycle Methods ---

    fun cancelSubscription() {
        if (_currentTier.value == SubscriptionTier.AD_FREE || _currentTier.value == SubscriptionTier.FREE) {
            setTier(SubscriptionTier.FREE)
            return
        }
        prefs.edit()
            .putString(KEY_PAYMENT_STATUS, PaymentStatus.CANCELLED.name)
            .apply()
        _paymentStatus.value = PaymentStatus.CANCELLED
        recalculateEntitlements()
    }

    fun markPaymentFailed() {
        if (_currentTier.value == SubscriptionTier.AD_FREE || _currentTier.value == SubscriptionTier.FREE) {
            setTier(SubscriptionTier.FREE)
            return
        }
        prefs.edit()
            .putString(KEY_PAYMENT_STATUS, PaymentStatus.PAYMENT_FAILED.name)
            .apply()
        _paymentStatus.value = PaymentStatus.PAYMENT_FAILED
        recalculateEntitlements()
    }

    /**
     * "Retry / pay outstanding balance". In release this must re-check Play rather than
     * restoring good standing locally, otherwise a cancelled subscription regains all
     * paid entitlements with a single button press. DEBUG keeps the simulation path.
     */
    fun reactivateSubscription() {
        if (!debugMode) {
            queryPurchases()
            return
        }
        prefs.edit().putString(KEY_PAYMENT_STATUS, PaymentStatus.ACTIVE.name).apply()
        _paymentStatus.value = PaymentStatus.ACTIVE
        recalculateEntitlements()
    }

    fun saveCompanyOAuthConfig(config: CompanyOAuthConfig) {
        val secretToStore = if (config.webhookSecret.isBlank()) {
            ""
        } else {
            secureStorage?.encryptString(config.webhookSecret) ?: config.webhookSecret
        }
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
            .putString(KEY_COMPANY_WEBHOOK_SECRET, secretToStore)
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
        val rosterJson = array.toString()
        val rosterToStore = if (rosterJson.isBlank()) rosterJson else (secureStorage?.encryptString(rosterJson) ?: rosterJson)
        prefs.edit().putString(KEY_TEAM_MEMBERS, rosterToStore).apply()
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
        if (!debugMode) return
        val next = when (_currentTier.value) {
            SubscriptionTier.FREE -> SubscriptionTier.AD_FREE
            SubscriptionTier.AD_FREE -> SubscriptionTier.INDIVIDUAL_PRO
            SubscriptionTier.INDIVIDUAL_PRO -> SubscriptionTier.ENTERPRISE
            SubscriptionTier.ENTERPRISE -> SubscriptionTier.FREE
        }
        setTier(next)
    }

    companion object {
        private const val TAG = "BillingRepository"
        private const val MAX_RECONNECT_ATTEMPTS = 5

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
