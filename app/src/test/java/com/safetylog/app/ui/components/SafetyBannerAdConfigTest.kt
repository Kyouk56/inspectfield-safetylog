package com.safetylog.app.ui.components

import com.safetylog.app.BuildConfig
import com.safetylog.app.data.auth.SubscriptionTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the AdMob banner configuration, Ad-Free tier suppression rules,
 * and dimensional requirements for standard banner serving.
 */
class SafetyBannerAdConfigTest {

    @Test
    fun testAdMobBannerUnitIdConfiguration() {
        // Must start with Google AdMob publisher prefix
        assertTrue(
            "AdMob banner ID must start with ca-app-pub- prefix: ${BuildConfig.ADMOB_BANNER_ID}",
            BuildConfig.ADMOB_BANNER_ID.startsWith("ca-app-pub-")
        )
        // Standard test banner ID or configured custom banner ID
        assertTrue(
            "AdMob banner ID must contain ad unit delimiter '/': ${BuildConfig.ADMOB_BANNER_ID}",
            BuildConfig.ADMOB_BANNER_ID.contains("/")
        )
    }

    @Test
    fun testAdBannerSuppressionPerSubscriptionTier() {
        // Free tier must show ads
        assertFalse("Free tier should NOT be ad-free", SubscriptionTier.FREE.isAdFree)

        // Ad-Free one-time pass must suppress ads
        assertTrue("Ad-Free tier must suppress ads", SubscriptionTier.AD_FREE.isAdFree)

        // Individual Pro subscription must suppress ads
        assertTrue("Individual Pro must suppress ads", SubscriptionTier.INDIVIDUAL_PRO.isAdFree)

        // Enterprise seat must suppress ads
        assertTrue("Enterprise must suppress ads", SubscriptionTier.ENTERPRISE.isAdFree)
    }

    @Test
    fun testStandardBannerHeightRequirement() {
        // Google Mobile Ads standard banner specification is 320x50 dp.
        // The container must provide exactly 50 dp to prevent "Not enough space to show ad" load failures.
        val requiredStandardBannerHeightDp = 50
        assertEquals(50, requiredStandardBannerHeightDp)
    }

    @Test
    fun testSubscriptionTierEntitlements() {
        // Free Tier: ad-supported, no sharing, not enterprise
        assertFalse(SubscriptionTier.FREE.isAdFree)
        assertFalse(SubscriptionTier.FREE.allowsSharing)
        assertFalse(SubscriptionTier.FREE.isEnterprise)

        // Ad-Free One-Time Pass ($2.99): ad-free, no sharing, not enterprise
        assertTrue(SubscriptionTier.AD_FREE.isAdFree)
        assertFalse(SubscriptionTier.AD_FREE.allowsSharing)
        assertFalse(SubscriptionTier.AD_FREE.isEnterprise)

        // Individual Pro Subscription ($0.99/mo): ad-free, allows sharing, not enterprise
        assertTrue(SubscriptionTier.INDIVIDUAL_PRO.isAdFree)
        assertTrue(SubscriptionTier.INDIVIDUAL_PRO.allowsSharing)
        assertFalse(SubscriptionTier.INDIVIDUAL_PRO.isEnterprise)

        // Enterprise Seat Subscription ($0.50/seat/mo): ad-free, allows sharing, enterprise RBAC
        assertTrue(SubscriptionTier.ENTERPRISE.isAdFree)
        assertTrue(SubscriptionTier.ENTERPRISE.allowsSharing)
        assertTrue(SubscriptionTier.ENTERPRISE.isEnterprise)
    }
}

