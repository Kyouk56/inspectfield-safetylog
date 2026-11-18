package com.safetylog.app.data.auth

/**
 * Pure policy for privileged actions. Kept free of Android/BuildConfig dependencies so
 * the fail-closed rule can be unit tested directly.
 */
object AuthorizationPolicy {

    /**
     * A privileged mutation is allowed only when:
     *  - this is a test/debug build with no server configured (local fallback), or
     *  - a server is configured, the device is online, and a valid signed snapshot exists.
     *
     * Read-only UI does not use this gate; it may render from the cached snapshot offline.
     */
    fun privilegedActionAllowed(
        serverConfigured: Boolean,
        hasSignedSnapshot: Boolean,
        snapshotExpired: Boolean,
        online: Boolean,
        localFallbackAllowed: Boolean
    ): Boolean {
        if (localFallbackAllowed) return true
        if (!serverConfigured) return false
        if (!online) return false
        if (!hasSignedSnapshot) return false
        return !snapshotExpired
    }
}
