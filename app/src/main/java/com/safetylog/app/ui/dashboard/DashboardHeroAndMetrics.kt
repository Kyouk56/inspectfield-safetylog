package com.safetylog.app.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.safetylog.app.data.auth.PaymentStatus
import com.safetylog.app.data.auth.SubscriptionTier
import com.safetylog.app.ui.theme.*

@Composable
internal fun SubscriptionDelinquencyBanner(
    paymentStatus: PaymentStatus,
    subscriptionTier: SubscriptionTier,
    onReactivateSubscription: () -> Unit,
    onShowPaywall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (paymentStatus == PaymentStatus.PAYMENT_FAILED)
                StatusDeficient.copy(alpha = 0.12f)
            else
                SafetyAmber.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.5.dp,
            if (paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient else SafetyAmber
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (paymentStatus == PaymentStatus.PAYMENT_FAILED)
                        Icons.Default.CreditCardOff
                    else
                        Icons.Default.CancelPresentation,
                    contentDescription = null,
                    tint = if (paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient else SafetyAmber,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (paymentStatus == PaymentStatus.PAYMENT_FAILED)
                        "PAST DUE - FUNCTIONS LOCKED TO FREE USE"
                    else
                        "SUBSCRIPTION CANCELLED - FREE TIER ACTIVE",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient else SafetyAmber
                )
            }
            Text(
                text = if (paymentStatus == PaymentStatus.PAYMENT_FAILED)
                    "Payment could not be delivered for your ${subscriptionTier.title}. Unlimited audits, cross-account sharing, and Company OAuth are locked down to Free use (5 audits/month with banner ads)."
                else
                    "Your recurring ${subscriptionTier.title} was cancelled. Features are running on Free use (5 inspections/month with banner ads).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(
                onClick = {
                    if (paymentStatus == PaymentStatus.PAYMENT_FAILED) {
                        onReactivateSubscription()
                    } else {
                        onShowPaywall()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(38.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient else SafetyAmber
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (paymentStatus == PaymentStatus.PAYMENT_FAILED) "Pay Outstanding Balance" else "Reactivate Subscription",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
internal fun ExecutiveComplianceHeroCard(
    total: Int,
    passed: Int,
    deficient: Int,
    due: Int,
    isDark: Boolean,
    onVaultClick: () -> Unit = {}
) {
    val complianceRate = if (total > 0) ((passed.toFloat() / total.toFloat()) * 100).toInt() else 100
    val animatedProgress by animateFloatAsState(
        targetValue = if (total > 0) passed.toFloat() / total.toFloat() else 1f,
        animationSpec = tween(durationMillis = 1000),
        label = "gaugeProgress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) DarkSurfaceCard else Slate900
        ),
        shape = RoundedCornerShape(14.dp),
        border = if (isDark) BorderStroke(1.dp, DarkSurfaceBorder) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row with live vault status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FACILITY COMPLIANCE SCORE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Slate400,
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF0D2538) else Color(0xFF0F2942))
                        .border(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onVaultClick)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "VAULT SECURED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        fontSize = 8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Metrics Section: Compact Radial Gauge + Metrics Column
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Radial Arc Gauge
                Box(
                    modifier = Modifier.size(76.dp).weight(1f).padding(end = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val gaugeColor = when {
                        complianceRate >= 90 -> StatusPassed
                        complianceRate >= 60 -> SafetyAmber
                        else -> StatusDeficient
                    }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 7.dp.toPx()
                        val arcSize = size.width - strokeWidth
                        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                        // Track Background Arc
                        drawArc(
                            color = if (isDark) Color(0xFF131F33) else Slate800,
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Progress Arc
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(gaugeColor.copy(alpha = 0.7f), gaugeColor)
                            ),
                            startAngle = 135f,
                            sweepAngle = 270f * animatedProgress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$complianceRate%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (complianceRate >= 90) "AUDIT READY" else "DEFICIENT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (complianceRate >= 90) StatusPassedLight else StatusDeficientLight,
                            fontSize = 7.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Breakdown Counters
                Column(
                    modifier = Modifier.weight(1f).padding(start = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    MetricCounterRow("Managed Assets", total.toString(), Slate400, Color.White)
                    MetricCounterRow("Compliant (Passed)", passed.toString(), StatusPassedLight, if (passed > 0) StatusPassedLight else Color.White)
                    MetricCounterRow("Deficiencies Flagged", deficient.toString(), StatusDeficientLight, if (deficient > 0) StatusDeficientLight else Color.White)
                    MetricCounterRow("Inspection Due", due.toString(), SafetyAmberLight, if (due > 0) SafetyAmberLight else Color.White)
                }
            }
        }
    }
}

@Composable
internal fun MetricCounterRow(
    label: String,
    value: String,
    indicatorColor: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Slate300,
                fontSize = 11.sp
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
internal fun LicenseQuotaCard(
    isPro: Boolean,
    subscriptionTier: SubscriptionTier,
    effectiveTier: SubscriptionTier,
    paymentStatus: PaymentStatus,
    enterpriseSeats: Int,
    inspectionsThisMonth: Int,
    freeLimit: Int,
    progressFraction: Float,
    onManageSubscription: () -> Unit
) {
    val isDelinquent = !paymentStatus.isGoodStanding
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onManageSubscription() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isDelinquent) StatusDeficient.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDelinquent) {
                                    StatusDeficient.copy(alpha = 0.15f)
                                } else {
                                    when (subscriptionTier) {
                                        SubscriptionTier.FREE -> SafetyOrangeContainer
                                        SubscriptionTier.AD_FREE -> Color(0xFFD1FAE5)
                                        SubscriptionTier.INDIVIDUAL_PRO -> SafetyAmberContainer
                                        SubscriptionTier.ENTERPRISE -> Color(0xFFE0E7FF)
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDelinquent) {
                                if (paymentStatus == PaymentStatus.PAYMENT_FAILED) Icons.Default.CreditCardOff else Icons.Default.CancelPresentation
                            } else {
                                when (subscriptionTier) {
                                    SubscriptionTier.FREE -> Icons.Default.CalendarMonth
                                    SubscriptionTier.AD_FREE -> Icons.Default.CheckCircle
                                    SubscriptionTier.INDIVIDUAL_PRO -> Icons.Default.WorkspacePremium
                                    SubscriptionTier.ENTERPRISE -> Icons.Default.CorporateFare
                                }
                            },
                            contentDescription = null,
                            tint = if (isDelinquent) {
                                if (paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient else SafetyAmber
                            } else {
                                when (subscriptionTier) {
                                    SubscriptionTier.FREE -> SafetyOrange
                                    SubscriptionTier.AD_FREE -> Color(0xFF059669)
                                    SubscriptionTier.INDIVIDUAL_PRO -> SafetyAmber
                                    SubscriptionTier.ENTERPRISE -> Color(0xFF4F46E5)
                                }
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = subscriptionTier.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isDelinquent) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (paymentStatus == PaymentStatus.PAYMENT_FAILED)
                                        StatusDeficient.copy(alpha = 0.15f)
                                    else
                                        SafetyAmber.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (paymentStatus == PaymentStatus.PAYMENT_FAILED) "PAST DUE" else "CANCELLED",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient else SafetyAmber,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isDelinquent) {
                                "Functions locked ($inspectionsThisMonth of $freeLimit used)"
                            } else {
                                when (subscriptionTier) {
                                    SubscriptionTier.FREE -> "$inspectionsThisMonth of $freeLimit free used"
                                    SubscriptionTier.AD_FREE -> "Ad-Free License • Unlimited"
                                    SubscriptionTier.INDIVIDUAL_PRO -> "Individual Pro • Unlimited"
                                    SubscriptionTier.ENTERPRISE -> "Enterprise • $enterpriseSeats seats"
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                AssistChip(
                    onClick = onManageSubscription,
                    modifier = Modifier.height(28.dp),
                    label = {
                        Text(
                            text = if (isDelinquent) {
                                if (paymentStatus == PaymentStatus.PAYMENT_FAILED) "Resolve" else "Reactivate"
                            } else if (effectiveTier == SubscriptionTier.FREE) {
                                "Upgrade"
                            } else {
                                "Manage"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                )
            }

            if (effectiveTier == SubscriptionTier.FREE) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (inspectionsThisMonth >= freeLimit) StatusDeficient else SafetyOrange,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun DashboardSearchAndFilterBar(
    searchQuery: String,
    filteredCount: Int,
    totalCount: Int,
    passedCount: Int,
    deficientCount: Int,
    dueCount: Int,
    activeFilter: FilterStatus,
    isDarkTheme: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onFilterSelected: (FilterStatus) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            placeholder = { Text("Search by Tag ID, location, or model...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSearchQueryChanged("")
                    }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { keyboardController?.hide() }
            ),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = SafetyOrange
            )
        )

        // Active Search Query Result Feedback
        if (searchQuery.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Found $filteredCount of $totalCount assets matching \"$searchQuery\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSearchQueryChanged("")
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "Reset Search",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SafetyOrange,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Modern Segmented Filter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                Triple("All", totalCount, FilterStatus.ALL to (if (isDarkTheme) SafetyOrange else Slate900)),
                Triple("Passed", passedCount, FilterStatus.PASSED to StatusPassed),
                Triple("Deficient", deficientCount, FilterStatus.DEFICIENT to StatusDeficient),
                Triple("Due", dueCount, FilterStatus.DUE to StatusPending)
            )
            filters.forEach { (label, count, statusAndColor) ->
                val (status, activeColor) = statusAndColor
                AuditFilterChip(
                    label = label,
                    count = count,
                    isSelected = activeFilter == status,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFilterSelected(status)
                    },
                    activeColor = activeColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun AuditFilterChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) activeColor else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isSelected) activeColor else MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
            }
        }
    }
}
