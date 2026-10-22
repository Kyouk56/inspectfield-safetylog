package com.safetylog.app.data.auth

import com.safetylog.app.data.BillingRepository
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
        val policyFile = File("../../PRIVACY_POLICY.md")
        val altFile = File("PRIVACY_POLICY.md")
        val activeFile = if (policyFile.exists()) policyFile else altFile
        if (activeFile.exists()) {
            val content = activeFile.readText()
            assertTrue("Policy must disclose Hex Engine Labs publisher", content.contains("Hex Engine Labs"))
            assertTrue("Policy must disclose hexenginelabs@gmail.com contact", content.contains("hexenginelabs@gmail.com"))
            assertTrue("Policy must disclose Camera access", content.contains("android.permission.CAMERA"))
            assertTrue("Policy must disclose Notifications", content.contains("android.permission.POST_NOTIFICATIONS"))
            assertTrue("Policy must disclose Exact Alarms", content.contains("android.permission.SCHEDULE_EXACT_ALARM"))
            assertTrue("Policy must disclose Hardware Keystore", content.contains("Android Keystore"))
            assertTrue("Policy must disclose AdMob / Advertising", content.contains("Google Mobile Ads (AdMob)"))
            assertTrue("Policy must disclose Google Play Billing", content.contains("Google Play In-App Billing"))
        }
    }
}
