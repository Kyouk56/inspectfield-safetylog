package com.safetylog.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Industrial Navy / Slate Theme Palette
val Slate950 = Color(0xFF020617)
val Slate900 = Color(0xFF0F172A)
val Slate850 = Color(0xFF172033)
val Slate800 = Color(0xFF1E293B)
val Slate750 = Color(0xFF243047)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

// Material 3 Dark Surfaces (Avoid pure #000000 for OLED trail prevention)
val DarkSurfaceBase = Color(0xFF0B0F19)       // App background in Dark Mode
val DarkSurfaceElevated = Color(0xFF121B2B)   // Cards, top app bars
val DarkSurfaceCard = Color(0xFF19253B)       // Elevated cards & inputs
val DarkSurfaceBorder = Color(0xFF2B3A53)     // Subtle contrast border

// Safety Alert Accents (High Visibility)
val SafetyOrange = Color(0xFFEA580C)
val SafetyOrangeDark = Color(0xFFC2410C)
val SafetyOrangeLight = Color(0xFFFB923C)
val SafetyOrangeContainer = Color(0xFFFFEDD5)

val SafetyAmber = Color(0xFFD97706)
val SafetyAmberLight = Color(0xFFFBBF24)
val SafetyAmberContainer = Color(0xFFFEF3C7)

// Status Tokens - Light Mode
val StatusPassed = Color(0xFF16A34A)
val StatusPassedDark = Color(0xFF15803D)
val StatusPassedLight = Color(0xFF4ADE80)
val StatusPassedContainer = Color(0xFFDCFCE7)
val StatusPassedText = Color(0xFF14532D)

val StatusDeficient = Color(0xFFDC2626)
val StatusDeficientDark = Color(0xFFB91C1C)
val StatusDeficientLight = Color(0xFFF87171)
val StatusDeficientContainer = Color(0xFFFEE2E2)
val StatusDeficientText = Color(0xFF7F1D1D)

val StatusPending = Color(0xFFD97706)
val StatusPendingContainer = Color(0xFFFEF3C7)
val StatusPendingText = Color(0xFF78350F)

// Status Tokens - Dark Mode (M3 Container tones: dark background, soft bright text)
val StatusPassedContainerDark = Color(0xFF144726)
val StatusPassedTextDark = Color(0xFF86EFAC)
val StatusPassedBorderDark = Color(0xFF166534)

val StatusDeficientContainerDark = Color(0xFF6B1717)
val StatusDeficientTextDark = Color(0xFFFCA5A5)
val StatusDeficientBorderDark = Color(0xFF991B1B)

val StatusPendingContainerDark = Color(0xFF663B09)
val StatusPendingTextDark = Color(0xFFFDE68A)
val StatusPendingBorderDark = Color(0xFF92400E)

// Verification Tech Accents
val TechBlue = Color(0xFF0284C7)
val TechBlueDark = Color(0xFF0369A1)
val TechBlueLight = Color(0xFF38BDF8)
val TechBlueContainer = Color(0xFFE0F2FE)

val VerifiedEmerald = Color(0xFF059669)
val VerifiedEmeraldDark = Color(0xFF047857)

val CardBorderLight = Color(0xFFE2E8F0)
val CardBorderDark = Color(0xFF2B3A53)

data class StatusColorScheme(
    val accent: Color,
    val container: Color,
    val text: Color,
    val border: Color
)

@Composable
fun getComplianceStatusColors(status: String?, isDark: Boolean = MaterialTheme.colorScheme.background == DarkSurfaceBase): StatusColorScheme {
    val isPassed = status.equals("PASSED", ignoreCase = true)
    val isDeficient = status.equals("DEFICIENT", ignoreCase = true)

    return when {
        isPassed -> {
            if (isDark) {
                StatusColorScheme(
                    accent = StatusPassedLight,
                    container = StatusPassedContainerDark,
                    text = StatusPassedTextDark,
                    border = StatusPassedBorderDark
                )
            } else {
                StatusColorScheme(
                    accent = StatusPassed,
                    container = StatusPassedContainer,
                    text = StatusPassedText,
                    border = StatusPassedLight
                )
            }
        }
        isDeficient -> {
            if (isDark) {
                StatusColorScheme(
                    accent = StatusDeficientLight,
                    container = StatusDeficientContainerDark,
                    text = StatusDeficientTextDark,
                    border = StatusDeficientBorderDark
                )
            } else {
                StatusColorScheme(
                    accent = StatusDeficient,
                    container = StatusDeficientContainer,
                    text = StatusDeficientText,
                    border = StatusDeficientLight
                )
            }
        }
        else -> {
            // Due / Pending
            if (isDark) {
                StatusColorScheme(
                    accent = SafetyAmberLight,
                    container = StatusPendingContainerDark,
                    text = StatusPendingTextDark,
                    border = StatusPendingBorderDark
                )
            } else {
                StatusColorScheme(
                    accent = StatusPending,
                    container = StatusPendingContainer,
                    text = StatusPendingText,
                    border = SafetyAmberLight
                )
            }
        }
    }
}
