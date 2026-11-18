package com.safetylog.app.data.auth

import com.safetylog.app.data.BillingRepository
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PlayBillingAndPolicyTest {

    @Test
    fun testBillingSkuConstants() {
        assertEquals("safetylog_adfree_299", BillingRepository.SKU_AD_FREE)
        assertEquals("safetylog_pro_monthly_99", BillingRepository.SKU_INDIVIDUAL_PRO)
        assertEquals("safetylog_enterprise_monthly_seat", BillingRepository.SKU_ENTERPRISE_SEAT)
    }

    @Test
    fun testEffectiveTierCalculation() {
        // Active Pro
        val proEffective = BillingRepository.calculateEffectiveTier(
            tier = SubscriptionTier.INDIVIDUAL_PRO,
            status = PaymentStatus.ACTIVE,
            oneTimeAdFree = false
        )
        assertEquals(SubscriptionTier.INDIVIDUAL_PRO, proEffective)
        assertTrue(proEffective.isAdFree)
        assertTrue(proEffective.allowsSharing)
        assertFalse(proEffective.isEnterprise)

        // Cancelled Pro without lifetime ad-free reverts to FREE
        val cancelledPro = BillingRepository.calculateEffectiveTier(
            tier = SubscriptionTier.INDIVIDUAL_PRO,
            status = PaymentStatus.CANCELLED,
            oneTimeAdFree = false
        )
        assertEquals(SubscriptionTier.FREE, cancelledPro)
        assertFalse(cancelledPro.isAdFree)

        // Cancelled Pro WITH lifetime ad-free reverts to AD_FREE
        val cancelledProWithLifetime = BillingRepository.calculateEffectiveTier(
            tier = SubscriptionTier.INDIVIDUAL_PRO,
            status = PaymentStatus.CANCELLED,
            oneTimeAdFree = true
        )
        assertEquals(SubscriptionTier.AD_FREE, cancelledProWithLifetime)
        assertTrue(cancelledProWithLifetime.isAdFree)
        assertFalse(cancelledProWithLifetime.allowsSharing)

        // Active Enterprise
        val entEffective = BillingRepository.calculateEffectiveTier(
            tier = SubscriptionTier.ENTERPRISE,
            status = PaymentStatus.ACTIVE,
            oneTimeAdFree = false
        )
        assertEquals(SubscriptionTier.ENTERPRISE, entEffective)
        assertTrue(entEffective.isEnterprise)
        assertTrue(entEffective.allowsSharing)
    }

    @Test
    fun testPrivacyPolicyDocumentDisclosures() {
        // Project root contains PRIVACY_POLICY.md
        val candidates = listOf(
            File("../PRIVACY_POLICY.md"),
            File("PRIVACY_POLICY.md"),
            File("../../PRIVACY_POLICY.md")
        )
        val activeFile = candidates.firstOrNull { it.exists() }
        assertNotNull("PRIVACY_POLICY.md must exist in one of the candidate locations", activeFile)
        val content = activeFile!!.readText()

        assertTrue("Policy must disclose Hex Engine Labs publisher", content.contains("Hex Engine Labs"))
        assertTrue("Policy must disclose hexenginelabs@gmail.com contact", content.contains("hexenginelabs@gmail.com"))
        assertTrue("Policy must disclose Camera access", content.contains("android.permission.CAMERA"))
        assertTrue("Policy must disclose Notifications", content.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue("Policy must disclose Exact Alarms", content.contains("android.permission.SCHEDULE_EXACT_ALARM"))
        assertTrue("Policy must disclose Hardware Keystore", content.contains("Android Keystore"))
        assertTrue("Policy must disclose AdMob / Advertising", content.contains("Google Mobile Ads (AdMob)"))
        assertTrue("Policy must disclose Google Play Billing", content.contains("Google Play In-App Billing"))
        assertTrue("Policy must disclose Account and Data Deletion", content.contains("Account and Data Deletion"))
        assertTrue("Policy must disclose Clear All Local Data option", content.contains("Clear All Local Data"))
    }

    @Test
    fun testHostedHtmlPrivacyPolicyDisclosures() {
        val htmlCandidates = listOf(
            File("../docs/index.html"),
            File("docs/index.html"),
            File("../../docs/index.html")
        )
        val activeHtml = htmlCandidates.firstOrNull { it.exists() }
        assertNotNull("docs/index.html must exist in one of the candidate locations", activeHtml)
        val htmlContent = activeHtml!!.readText()

        assertTrue("Hosted HTML must disclose Hex Engine Labs", htmlContent.contains("Hex Engine Labs"))
        assertTrue("Hosted HTML must disclose hexenginelabs@gmail.com", htmlContent.contains("hexenginelabs@gmail.com"))
        assertTrue("Hosted HTML must have account-deletion section anchor", htmlContent.contains("id=\"account-deletion\""))
        assertTrue("Hosted HTML must disclose Account and Data Deletion", htmlContent.contains("Account and Data Deletion"))
        assertTrue("Hosted HTML must disclose Camera permission", htmlContent.contains("android.permission.CAMERA"))
        assertTrue("Hosted HTML must disclose Exact Alarms", htmlContent.contains("android.permission.SCHEDULE_EXACT_ALARM"))
    }

    class FakePrefs : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = data[key] as? Set<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    class FakeEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor { if (key != null) data[key] = value; return this }
        override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor { if (key != null) data[key] = values; return this }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { if (key != null) data[key] = value; return this }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor { if (key != null) data[key] = value; return this }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { if (key != null) data[key] = value; return this }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { if (key != null) data[key] = value; return this }
        override fun remove(key: String?): SharedPreferences.Editor { if (key != null) data.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { data.clear(); return this }
        override fun commit(): Boolean = true
        override fun apply() {}
    }

    @Test
    fun releaseModeSetTierIndividualProIsNoOp() {
        val repo = BillingRepository(FakePrefs(), debugMode = false)
        assertEquals(SubscriptionTier.FREE, repo.currentTier.value)
        repo.setTier(SubscriptionTier.INDIVIDUAL_PRO)
        assertEquals(SubscriptionTier.FREE, repo.currentTier.value)
        assertFalse(repo.isProUser.value)
    }

    @Test
    fun adFreeDoesNotGrantIsProUser() {
        val repo = BillingRepository(FakePrefs(), debugMode = true)
        repo.setTier(SubscriptionTier.AD_FREE)
        assertEquals(SubscriptionTier.AD_FREE, repo.currentTier.value)
        assertEquals(SubscriptionTier.AD_FREE, repo.effectiveTier.value)
        assertFalse("AD_FREE must not grant isProUser", repo.isProUser.value)
        assertTrue("AD_FREE must be ad-free", repo.isAdFree.value)
    }

    @Test
    fun lifetimeAdFreeSurvivesSubscriptionLapse() {
        val repo = BillingRepository(FakePrefs(), debugMode = true)
        repo.setTier(SubscriptionTier.AD_FREE)
        repo.setTier(SubscriptionTier.INDIVIDUAL_PRO)
        assertTrue(repo.hasOneTimeAdFree.value)
        repo.cancelSubscription()
        assertEquals(SubscriptionTier.AD_FREE, repo.effectiveTier.value)
        assertFalse(repo.isProUser.value)
        assertTrue(repo.isAdFree.value)
    }

    @Test
    fun lifetimeAdFreeSurvivesPaymentFailure() {
        val repo = BillingRepository(FakePrefs(), debugMode = true)
        repo.setTier(SubscriptionTier.AD_FREE)
        repo.setTier(SubscriptionTier.INDIVIDUAL_PRO)
        repo.markPaymentFailed()
        assertEquals(SubscriptionTier.AD_FREE, repo.effectiveTier.value)
        assertFalse(repo.isProUser.value)
        assertTrue(repo.isAdFree.value)
    }

    @Test
    fun undecryptableCiphertextIsRejectedWhileLegacyPlaintextJsonIsPreserved() {
        val prefs = FakePrefs()
        val editor = prefs.edit()
        editor.putString(
            "safetylog_company_webhook_secret",
            "dGVzdEl2MTIzNDU2:c2VjdXJlQ2lwaGVyVGV4dDEyMw=="
        )
        editor.putString(
            "safetylog_team_members",
            """[{"id":"m-1","email":"legacy@example.com","name":"Legacy Inspector","role":"MANAGER","addedEpochMs":1700000000000,"isActive":true}]"""
        )
        editor.apply()

        val repo = BillingRepository(prefs, debugMode = false)
        assertEquals("", repo.companyOAuthConfig.value.webhookSecret)
        assertEquals(1, repo.teamMembers.value.size)
        assertEquals("legacy@example.com", repo.teamMembers.value.first().email)
    }

    @Test
    fun undecryptableTeamCiphertextFallsBackToDefaultRosterSafely() {
        val prefs = FakePrefs()
        prefs.edit()
            .putString("safetylog_team_members", "dGVzdEl2MTIzNDU2:c2VjdXJlQ2lwaGVyVGV4dDEyMw==")
            .apply()

        val repo = BillingRepository(prefs, debugMode = false)
        assertTrue(repo.teamMembers.value.none { it.email.contains(":") })
        assertTrue(repo.teamMembers.value.isEmpty())
    }

    @Test
    fun releaseBuildWithoutSavedRosterSeedsNoDemoTeamMembers() {
        val releaseRepo = BillingRepository(FakePrefs(), debugMode = false)
        assertTrue(releaseRepo.teamMembers.value.isEmpty())

        val debugRepo = BillingRepository(FakePrefs(), debugMode = true)
        assertEquals(2, debugRepo.teamMembers.value.size)
    }
}
