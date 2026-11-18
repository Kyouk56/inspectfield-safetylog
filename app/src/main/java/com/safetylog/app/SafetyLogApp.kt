package com.safetylog.app

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.safetylog.app.data.BillingRepository
import com.safetylog.app.data.SafetyLogDatabase
import com.safetylog.app.domain.EntitlementManager
import com.safetylog.app.util.InspectionPdfGenerator

class SafetyLogApp : Application() {

    val database by lazy { SafetyLogDatabase.getDatabase(this) }
    val entitlementManager by lazy { EntitlementManager(database.inspectionDao()) }
    val billingRepository by lazy { BillingRepository(this, secureTokenStorage) }
    val pdfGenerator by lazy { InspectionPdfGenerator(this) }
    val secureTokenStorage by lazy { com.safetylog.app.data.auth.SecureTokenStorage(this) }
    val authorizationRepository by lazy {
        com.safetylog.app.data.auth.AuthorizationRepository(
            api = com.safetylog.app.data.auth.AuthorizationApi(
                com.safetylog.app.data.auth.HttpAuthorizationTransport(
                    com.safetylog.app.util.ServerAuthorization.BASE_URL
                )
            ),
            secureStorage = secureTokenStorage
        )
    }
    val authRepository by lazy {
        com.safetylog.app.data.auth.AuthRepository(this, secureTokenStorage, authorizationRepository)
    }
    val teamSyncEngine by lazy {
        com.safetylog.app.data.sync.TeamSyncEngine(
            context = this,
            equipmentDao = database.equipmentDao(),
            inspectionDao = database.inspectionDao(),
            secureStorage = secureTokenStorage
        )
    }
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.e("SafetyLogApp", "Uncaught coroutine exception", throwable)
        }
    )

    val reminderManager by lazy { com.safetylog.app.data.reminder.InspectionReminderManager(this) }
    val networkMonitor by lazy { com.safetylog.app.util.NetworkMonitor(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Notification Channels for Inspection Reminders
        com.safetylog.app.data.reminder.InspectionReminderManager.initNotificationChannel(this)

        // Initialize On-Device Hardware Keystore Vault & AES-256 Cipher by default for every individual user
        secureTokenStorage.initVault()

        // Restore (and verify) any cached server-signed authorization decision.
        authorizationRepository.restoreCachedSnapshot()
        authorizationRepository.startAutoRefresh(appScope)

        // Every acknowledged Play purchase is verified server-side before the
        // authoritative snapshot is updated with the paid entitlement.
        billingRepository.onPurchaseVerified = { productId, purchaseToken ->
            appScope.launch {
                try {
                    authorizationRepository.verifyPurchase(purchaseToken, productId)
                } catch (e: Exception) {
                    Log.e("SafetyLogApp", "Purchase verification failed", e)
                }
            }
        }

        // Install Privacy-First Encrypted Crash & Diagnostic Vault
        com.safetylog.app.util.CrashLogVault.install(this, secureTokenStorage)
        com.safetylog.app.util.CrashLogVault.cleanupDiagnosticExports(this)

        // Initialize Theme Manager for Dark / Light mode persistence
        com.safetylog.app.ui.theme.ThemeManager.init(this)

        // Asynchronously initialize Google Mobile Ads SDK for Hybrid Banner
        try {
            com.google.android.gms.ads.MobileAds.initialize(this) {}
        } catch (_: Exception) {}

        // NOTE: No mock or seed data is automatically created by default.
        // Mock data is only generated when an Owner-Admin explicitly enables Test View.
    }

    companion object {
        lateinit var instance: SafetyLogApp
            private set
    }
}
