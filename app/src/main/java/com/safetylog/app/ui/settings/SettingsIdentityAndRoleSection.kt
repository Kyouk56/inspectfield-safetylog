package com.safetylog.app.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safetylog.app.data.auth.PaymentStatus
import com.safetylog.app.data.auth.SubscriptionTier
import com.safetylog.app.data.auth.UserRole
import com.safetylog.app.ui.dashboard.DashboardUiState
import com.safetylog.app.ui.dashboard.DashboardViewModel
import com.safetylog.app.ui.theme.*
import com.safetylog.app.util.TestControls

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsDeveloperSandboxCard(
    uiState: DashboardUiState,
    themeMode: AppThemeMode,
    dashboardViewModel: DashboardViewModel
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // 3. Admin & Developer Sandbox — test controls only, never exposed in the public release.
    // This is a local simulation tool (mock inventory, role/tier overrides), not a
    // production admin feature, so it is gated by build type instead of by a role that
    // the app itself can grant.
    if (TestControls.isEnabled) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(18.dp)),
            colors = CardDefaults.cardColors(
                containerColor = if (themeMode == AppThemeMode.DARK) Color(0xFF1E1B4B) else Color(0xFFEEF2FF)
            ),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.5.dp, Color(0xFF6366F1))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF6366F1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeveloperMode,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "DEVELOPER SANDBOX",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF6366F1),
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFF6366F1).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (uiState.userRole == UserRole.ACCOUNT_ADMIN) "ADMIN" else uiState.userRole.name,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF6366F1),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Isolated testing mode • Mock assets strictly isolated here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = uiState.isAdminTestViewActive,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            dashboardViewModel.toggleAdminTestView()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF6366F1)
                        )
                    )
                }

                AnimatedVisibility(visible = uiState.isAdminTestViewActive) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HorizontalDivider(color = Color(0xFF6366F1).copy(alpha = 0.3f))

                        Text(
                            text = "Simulate Role Permissions:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isOwnerAuthorized = com.safetylog.app.data.auth.AuthorizationConfig.isAuthorizedOwner(uiState.authUser?.email)

                            UserRole.entries.forEach { role ->
                                val isSelected = uiState.userRole == role
                                val isRestrictedOwner = role == UserRole.OWNER && !isOwnerAuthorized

                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isRestrictedOwner) {
                                            Toast.makeText(
                                                context,
                                                "Restricted: Owner authority is reserved exclusively for jdesrosiers88@gmail.com and hexenginelabs@gmail.com",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            dashboardViewModel.switchTestRole(role)
                                        }
                                    },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = role.title,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (isRestrictedOwner) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = "Restricted",
                                                    modifier = Modifier.size(11.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(role.badgeColorHex),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Text(
                            text = "Simulate Subscription Plan:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SubscriptionTier.entries.forEach { tier ->
                                val isSelected = uiState.subscriptionTier == tier
                                val label = when (tier) {
                                    SubscriptionTier.FREE -> "Free"
                                    SubscriptionTier.AD_FREE -> "Ad-Free $2.99"
                                    SubscriptionTier.INDIVIDUAL_PRO -> "Indiv Share $0.99"
                                    SubscriptionTier.ENTERPRISE -> "Enterprise $0.50/seat"
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { dashboardViewModel.setSubscriptionTier(tier) },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SafetyOrange,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    dashboardViewModel.loadTestSandboxAssets()
                                    Toast.makeText(context, "Loaded test sandbox inventory", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1.2f).height(40.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Load Sandbox Data", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    dashboardViewModel.purgeAllEquipmentForCleanTesting()
                                    Toast.makeText(context, "Purged test data. Vault is clean.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDeficient),
                                border = BorderStroke(1.dp, StatusDeficient.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Purge All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsIdentityVaultCard(
    uiState: DashboardUiState,
    dashboardViewModel: DashboardViewModel,
    onNavigateToAuth: () -> Unit,
    onShowPrivacyPolicy: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // 3. Security, Hardware Keystore & Identity Protection
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0D9488).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF0D9488),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Cryptographic Security Vault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Hardware-backed AES-256 session encryption",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "AES-256 ACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF10B981),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Session bearer keys and OAuth non-repudiation signatures are stored in hardware-isolated Android Keystore memory.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val authUser = uiState.authUser
            if (authUser != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(authUser.provider.brandColorHex)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = authUser.initials,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = authUser.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = authUser.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(authUser.provider.brandColorHex).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = authUser.provider.displayName,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(authUser.provider.brandColorHex),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onNavigateToAuth,
                        modifier = Modifier.weight(1.2f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("OAuth Vault", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            dashboardViewModel.signOut()
                            Toast.makeText(context, "Signed out of session", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDeficient)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            } else {
                Button(
                    onClick = onNavigateToAuth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                ) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign In with Google / OAuth Vault", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
            }

            OutlinedButton(
                onClick = onShowPrivacyPolicy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Privacy Policy & Data Safety Disclosures", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsSubscriptionQuotasCard(
    uiState: DashboardUiState,
    dashboardViewModel: DashboardViewModel,
    onShowPaywall: () -> Unit,
    onShowCancelConfirmDialog: () -> Unit
) {
    val context = LocalContext.current

    // 7. Subscriptions, Quotas & Self-Service Licensing
    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFFEF3C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Subscription & Quotas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(uiState.subscriptionTier.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (uiState.paymentStatus) {
                        PaymentStatus.ACTIVE -> if (uiState.isAdFree) Color(0xFF10B981).copy(alpha = 0.15f) else SafetyOrange.copy(alpha = 0.15f)
                        PaymentStatus.PAYMENT_FAILED -> StatusDeficient.copy(alpha = 0.15f)
                        PaymentStatus.CANCELLED -> SafetyAmber.copy(alpha = 0.15f)
                        PaymentStatus.GRACE_PERIOD -> SafetyAmber.copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = when (uiState.paymentStatus) {
                            PaymentStatus.ACTIVE -> if (uiState.isAdFree) "ACTIVE AD-FREE" else "FREE (ADS)"
                            PaymentStatus.PAYMENT_FAILED -> "PAST DUE"
                            PaymentStatus.CANCELLED -> "CANCELLED"
                            PaymentStatus.GRACE_PERIOD -> "GRACE PERIOD"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = when (uiState.paymentStatus) {
                            PaymentStatus.ACTIVE -> if (uiState.isAdFree) Color(0xFF10B981) else SafetyOrange
                            PaymentStatus.PAYMENT_FAILED -> StatusDeficient
                            PaymentStatus.CANCELLED -> SafetyAmber
                            PaymentStatus.GRACE_PERIOD -> SafetyAmber
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Payment Alert Box if Past Due or Cancelled
            if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) {
                Surface(shape = RoundedCornerShape(10.dp), color = StatusDeficient.copy(alpha = 0.12f), border = BorderStroke(1.dp, StatusDeficient)) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusDeficient, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Payment past due! Recurring features locked to Free tier until payment is delivered.",
                            style = MaterialTheme.typography.bodySmall, color = StatusDeficient, fontWeight = FontWeight.Bold, fontSize = 11.sp
                        )
                    }
                }
            } else if (uiState.paymentStatus == PaymentStatus.CANCELLED) {
                Surface(shape = RoundedCornerShape(10.dp), color = SafetyAmber.copy(alpha = 0.12f), border = BorderStroke(1.dp, SafetyAmber)) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Subscription cancelled. Account is running on Free usage (5 inspections/mo).",
                            style = MaterialTheme.typography.bodySmall, color = SafetyAmber, fontWeight = FontWeight.Bold, fontSize = 11.sp
                        )
                    }
                }
            }

            Text(
                text = "Monthly Audit Usage: ${uiState.inspectionsThisMonth} of ${if (uiState.isProUser) "Unlimited" else "${uiState.freeLimit} free"} completed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Self-Service Lifecycle Actions (Corporate lockdown applies only if Enterprise plan is active)
            val isCorporateMemberRestricted = uiState.subscriptionTier == SubscriptionTier.ENTERPRISE && !uiState.userRole.canManageBilling
            if (isCorporateMemberRestricted) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Corporate Billing Restricted", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = "As a ${uiState.userRole.title}, your organization's Enterprise subscription and seat expansion are managed by your Account Admin or Owner.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                // Quick Self-Service Tier Selector
                Text(
                    text = "Active Plan Tier:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SubscriptionTier.entries.forEach { tier ->
                        val isSelected = uiState.effectiveTier == tier
                        val label = when (tier) {
                            SubscriptionTier.FREE -> "Free (Ads)"
                            SubscriptionTier.AD_FREE -> "Ad-Free $2.99"
                            SubscriptionTier.INDIVIDUAL_PRO -> "Indiv $0.99/mo"
                            SubscriptionTier.ENTERPRISE -> "Enterprise $0.50/seat"
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                // Public release: real purchases only. Never grant a paid
                                // tier (or a privileged role) from a local chip press.
                                if (TestControls.isEnabled) {
                                    dashboardViewModel.setSubscriptionTier(tier, uiState.enterpriseSeats)
                                } else {
                                    onShowPaywall()
                                }
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SafetyOrange,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) {
                    Button(
                        onClick = {
                            dashboardViewModel.reactivateSubscription()
                            Toast.makeText(context, "Balance cleared. Features unlocked!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDeficient)
                    ) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pay Outstanding Balance & Restore Access", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else if (uiState.paymentStatus == PaymentStatus.CANCELLED) {
                    Button(
                        onClick = onShowPaywall,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reactivate Paid Subscription", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else if (uiState.subscriptionTier != SubscriptionTier.FREE && uiState.subscriptionTier != SubscriptionTier.AD_FREE) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onShowPaywall,
                            modifier = Modifier.weight(1.2f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manage Seats", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = onShowCancelConfirmDialog,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDeficient),
                            border = BorderStroke(1.dp, StatusDeficient.copy(alpha = 0.5f))
                        ) {
                            Text("Cancel Sub", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = onShowPaywall,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isAdFree) "Manage Plan & Team Seats" else "Upgrade / Unlock Functions",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White
                        )
                    }
                }

                // Self-Service Billing Test Controls — test builds only.
                if (TestControls.isEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text("Self-Service Lifecycle Simulation:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                dashboardViewModel.markPaymentFailed()
                                Toast.makeText(context, "Simulated payment failure: Features locked to free use", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(36.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)
                        ) { Text("Fail Pay", fontSize = 11.sp, maxLines = 1) }
                        OutlinedButton(
                            onClick = {
                                dashboardViewModel.cancelSubscription()
                                Toast.makeText(context, "Simulated cancel: Features locked to free use", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(36.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)
                        ) { Text("Cancel", fontSize = 11.sp, maxLines = 1) }
                        OutlinedButton(
                            onClick = {
                                dashboardViewModel.reactivateSubscription()
                                Toast.makeText(context, "Simulated success: Features restored", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(36.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)
                        ) { Text("Restore", fontSize = 11.sp, maxLines = 1) }
                    }
                }
            }
        }
    }
}
