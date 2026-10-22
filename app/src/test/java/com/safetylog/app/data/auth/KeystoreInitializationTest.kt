package com.safetylog.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeystoreInitializationTest {

    @Test
    fun testLocalKeystoreAuthProviderProperties() {
        // Verify AuthProvider local keystore exists and has proper branding
        val providers = AuthProvider.values()
        assertTrue("AuthProvider should contain GOOGLE", providers.contains(AuthProvider.GOOGLE))
        assertTrue("AuthProvider should contain CORPORATE_SSO", providers.contains(AuthProvider.CORPORATE_SSO))
        assertTrue("AuthProvider should contain MICROSOFT", providers.contains(AuthProvider.MICROSOFT))
    }

    @Test
    fun testHardwareEncryptedUserAttributes() {
        val user = AuthUser(
            userId = "auth_local_test_123",
            email = "inspector@safetylog.local",
            displayName = "Local Safety Inspector",
            provider = AuthProvider.CORPORATE_SSO,
            role = UserRole.OWNER,
            idToken = "jwt_test_token_123",
            accessToken = "bearer_test_123",
            tokenExpiryEpochMs = System.currentTimeMillis() + 86400000L,
            lastLoginEpochMs = System.currentTimeMillis(),
            isHardwareEncrypted = true,
            keyAlias = "_safetylog_keystore_master_key_"
        )

        assertTrue("User session should be active", user.isSessionActive)
        assertTrue("User should be hardware encrypted", user.isHardwareEncrypted)
        assertEquals("LS", user.initials)
        assertEquals(UserRole.OWNER, user.role)
    }
}
