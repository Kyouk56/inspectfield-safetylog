package com.safetylog.app.util

import com.safetylog.app.BuildConfig

/**
 * Single gate for developer/test-only controls: sandbox auth, role simulation,
 * paid-tier simulation and billing lifecycle simulation.
 *
 * Enabled in every debug build and in a release build compiled with
 * `-PenableTestControls=true`. Always off in the public Play release, so an
 * end user can never grant themselves a privileged role or paid entitlement.
 */
object TestControls {
    val isEnabled: Boolean = BuildConfig.ENABLE_TEST_CONTROLS
}
