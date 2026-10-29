package com.safetylog.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safetylog.app.data.auth.PaymentStatus
import com.safetylog.app.data.auth.SubscriptionTier
import com.safetylog.app.data.auth.UserRole
import com.safetylog.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallBottomSheet(
    onDismissRequest: () -> Unit,
    currentTier: SubscriptionTier = SubscriptionTier.FREE,
    paymentStatus: PaymentStatus = PaymentStatus.ACTIVE,
    userRole: UserRole = UserRole.USER,
    initialEnterpriseSeats: Int = 5,
    onSelectTier: (SubscriptionTier, Int) -> Unit = { _, _ -> },
    onCancelSubscription: () -> Unit = {},
    onReactivateSubscription: () -> Unit = {},
    onSimulatePaymentFailed: () -> Unit = {},
    onSimulatePaymentSuccess: () -> Unit = {},
    onUpgradeClicked: () -> Unit = {},
    onLaunchBilling: ((android.app.Activity, SubscriptionTier, Int) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    var selectedTier by remember(currentTier) { mutableStateOf(if (currentTier != SubscriptionTier.FREE) currentTier else SubscriptionTier.INDIVIDUAL_PRO) }
    var enterpriseSeats by remember(initialEnterpriseSeats) { mutableIntStateOf(initialEnterpriseSeats) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFFFEF3C7), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = SafetyAmber,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "SafetyLog Self-Service Licensing",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Self-service subscriptions. Unlock Company OAuth, multi-seat team sync, or personal sharing. All paid options remove banner ads.",
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Billing restriction alert for subordinate team members in an active Enterprise organization
            val isCorporateMemberRestricted = currentTier == SubscriptionTier.ENTERPRISE && !userRole.canManageBilling
            val isBillingAllowed = !isCorporateMemberRestricted

            if (isCorporateMemberRestricted) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Corporate Billing Restricted",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "As a ${userRole.title}, your organization's Enterprise plan, seats, and billing are managed by your Account Admin or Owner.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Payment Past Due / Delinquent Alert Banner
            if (paymentStatus == PaymentStatus.PAYMENT_FAILED) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StatusDeficient.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, StatusDeficient)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CreditCardOff, contentDescription = null, tint = StatusDeficient)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Payment Failed / Past Due",
                                fontWeight = FontWeight.Bold,
                                color = StatusDeficient,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Text(
                            text = "Payment could not be delivered. Company OAuth, Team Sync, and unlimited audits are locked to Free use.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (userRole.canManageBilling) {
                            Button(
                                onClick = {
                                    onReactivateSubscription()
                                    onUpgradeClicked()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusDeficient),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry & Pay Outstanding Balance", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Subscription Cancelled Banner
            if (paymentStatus == PaymentStatus.CANCELLED) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SafetyAmber.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, SafetyAmber)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cancel, contentDescription = null, tint = SafetyAmber)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Subscription Cancelled",
                                fontWeight = FontWeight.Bold,
                                color = SafetyAmber,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Text(
                            text = "Your subscription was cancelled and features are locked to Free use. Resubscribe below to reactivate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Plan 1: Ad-Free Flat Rate ($2.99)
            SubscriptionTierCard(
                tier = SubscriptionTier.AD_FREE,
                isSelected = selectedTier == SubscriptionTier.AD_FREE,
                headline = "Ad-Free License",
                price = "$2.99",
                frequency = "One-Time Flat Rate",
                bullet1 = "Removes all banner ads across the entire app permanently",
                bullet2 = "Unlimited offline inspection logs for single technician",
                onClick = { selectedTier = SubscriptionTier.AD_FREE }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Plan 2: Individual Share & Sync ($0.99/mo)
            SubscriptionTierCard(
                tier = SubscriptionTier.INDIVIDUAL_PRO,
                isSelected = selectedTier == SubscriptionTier.INDIVIDUAL_PRO,
                isRecommended = true,
                headline = "Individual Share & Sync",
                price = "$0.99",
                frequency = "per month",
                bullet1 = "Share inspection logs & export vaults between your connected devices",
                bullet2 = "Personal OAuth identity linking (Google / Microsoft) & audit signatures",
                onClick = { selectedTier = SubscriptionTier.INDIVIDUAL_PRO }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Plan 3: Enterprise Organization ($0.50/person/mo)
            SubscriptionTierCard(
                tier = SubscriptionTier.ENTERPRISE,
                isSelected = selectedTier == SubscriptionTier.ENTERPRISE,
                headline = "Enterprise Organization",
                price = "$0.50",
                frequency = "per person / month",
                bullet1 = "🏢 Company-Specific OAuth: Configure corporate SAML 2.0, Microsoft Entra, Okta, or Google Workspace",
                bullet2 = "Enterprise Team Sync: Custom REST endpoints & encrypted deployment package export",
                onClick = { selectedTier = SubscriptionTier.ENTERPRISE }
            ) {
                // Interactive Seat Counter for Enterprise
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(Slate950, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Organization Seats:",
                            style = MaterialTheme.typography.labelMedium,
                            color = Slate300,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (userRole.canManageBilling && enterpriseSeats > 1) enterpriseSeats-- },
                                enabled = userRole.canManageBilling,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = if (userRole.canManageBilling) SafetyOrangeLight else Slate600)
                            }
                            Text(
                                text = "$enterpriseSeats persons",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { if (userRole.canManageBilling) enterpriseSeats++ },
                                enabled = userRole.canManageBilling,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", tint = if (userRole.canManageBilling) SafetyOrangeLight else Slate600)
                            }
                        }
                    }
                    val totalMonthly = enterpriseSeats * 0.50f
                    Text(
                        text = "Total: $enterpriseSeats users × $0.50 = \$${String.format(java.util.Locale.US, "%.2f", totalMonthly)}/month",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusPassedLight,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Confirm / Upgrade Button
            Button(
                onClick = {
                    if (isBillingAllowed) {
                        if (onLaunchBilling != null && activity != null) {
                            onLaunchBilling(activity, selectedTier, enterpriseSeats)
                        } else {
                            onSelectTier(selectedTier, enterpriseSeats)
                        }
                        onUpgradeClicked()
                    }
                },
                enabled = isBillingAllowed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .shadow(if (isBillingAllowed) 4.dp else 0.dp, RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SafetyOrange,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (isBillingAllowed) Icons.Default.CheckCircle else Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isBillingAllowed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                val btnText = if (isCorporateMemberRestricted) {
                    "Managed by Account Admin or Owner"
                } else when (selectedTier) {
                    SubscriptionTier.AD_FREE -> "Activate Ad-Free Pass • $2.99"
                    SubscriptionTier.INDIVIDUAL_PRO -> "Subscribe Individual Share • $0.99/mo"
                    SubscriptionTier.ENTERPRISE -> "Activate Enterprise (${enterpriseSeats} users) • \$${String.format(java.util.Locale.US, "%.2f", enterpriseSeats * 0.50f)}/mo"
                    SubscriptionTier.FREE -> "Confirm Standard Free"
                }
                Text(
                    text = btnText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isBillingAllowed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Self-Service Cancel Subscription Option (Only Account Admins & Owner)
            if (isBillingAllowed && currentTier != SubscriptionTier.FREE && currentTier != SubscriptionTier.AD_FREE && paymentStatus.isGoodStanding) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showCancelConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDeficient),
                    border = BorderStroke(1.dp, StatusDeficient.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel Subscription (Lock to Free)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Testing / Simulation Controls for Self-Service Lifecycle (Only Account Admins & Owner)
            if (isBillingAllowed) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Self-Service Lifecycle Simulation:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onSimulatePaymentFailed()
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Simulate Lapse", fontSize = 10.sp, maxLines = 1, color = StatusDeficient)
                            }
                            OutlinedButton(
                                onClick = {
                                    onCancelSubscription()
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Simulate Cancel", fontSize = 10.sp, maxLines = 1, color = SafetyAmber)
                            }
                            Button(
                                onClick = {
                                    onSimulatePaymentSuccess()
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusPassed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Simulate Success", fontSize = 10.sp, maxLines = 1, color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onDismissRequest) {
                Text(
                    text = "Close",
                    color = Slate500,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            title = { Text("Cancel Subscription?", fontWeight = FontWeight.Bold) },
            text = {
                Text("If you cancel your subscription, your account will immediately revert to Free tier. Company OAuth, Team Sync, and unlimited audits will be locked until payment is renewed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCancelSubscription()
                        showCancelConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDeficient)
                ) {
                    Text("Confirm Cancellation")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = false }) {
                    Text("Keep Active")
                }
            }
        )
    }
}

@Composable
private fun SubscriptionTierCard(
    tier: SubscriptionTier,
    isSelected: Boolean,
    isRecommended: Boolean = false,
    headline: String,
    price: String,
    frequency: String,
    bullet1: String,
    bullet2: String,
    onClick: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Slate900 else Slate850
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) SafetyOrange else Slate700
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isSelected,
                        onClick = onClick,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = SafetyOrange,
                            unselectedColor = Slate400
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (isRecommended) {
                    Surface(
                        color = SafetyOrange,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "POPULAR",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.padding(start = 42.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = price,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = SafetyAmberLight
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = frequency,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate300
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.padding(start = 42.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PaywallFeatureRow(text = bullet1)
                PaywallFeatureRow(text = bullet2)
            }

            extraContent?.invoke()
        }
    }
}

@Composable
private fun PaywallFeatureRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = StatusPassedLight,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Slate300,
            fontSize = 11.sp
        )
    }
}
