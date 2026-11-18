package com.safetylog.app.util

import com.safetylog.app.BuildConfig

/** Compile-time configuration for the server-authoritative authorization layer. */
object ServerAuthorization {
    const val BASE_URL: String = BuildConfig.AUTH_API_BASE_URL
    const val PUBLIC_KEY_SPKI_BASE64: String = BuildConfig.AUTH_PUBLIC_KEY_SPKI_BASE64

    /** True only when the app can verify signed snapshots from the authorization server. */
    val isConfigured: Boolean
        get() = BASE_URL.isNotBlank() && PUBLIC_KEY_SPKI_BASE64.isNotBlank()
}
