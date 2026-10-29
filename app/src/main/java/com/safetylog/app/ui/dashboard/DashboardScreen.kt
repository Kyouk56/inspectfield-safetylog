package com.safetylog.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.widget.Toast
import com.safetylog.app.data.auth.PaymentStatus
import com.safetylog.app.data.auth.SubscriptionTier
import com.safetylog.app.data.auth.UserRole
import com.safetylog.app.data.dao.EquipmentWithLatestInspection
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.domain.ChecklistRegistry
import com.safetylog.app.ui.components.PaywallBottomSheet
import com.safetylog.app.ui.components.SafetyBannerAd
import com.safetylog.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    onNavigateToAddEquipment: () -> Unit,
    onNavigateToEditEquipment: (Long) -> Unit,
    onNavigateToInspect: (Long) -> Unit,
    onNavigateToHistory: (Long) -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAuth: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var equipmentToDelete by remember { mutableStateOf<EquipmentItem?>(null) }
    var selectedAssetForAction by remember { mutableStateOf<EquipmentWithLatestInspection?>(null) }
    var showPaywall by remember { mutableStateOf(false) }
    val logDateFormat = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US) }
    val listState = rememberLazyListState()
    var showAccountMenu by remember { mutableStateOf(false) }
    var showRoleDialog by remember { mutableStateOf(false) }
    var showNetworkInfoDialog by remember { mutableStateOf(false) }
    val isScrolledPastHero by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2 ||
            (listState.firstVisibleItemIndex == 2 && listState.firstVisibleItemScrollOffset > 60)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(SafetyOrange, SafetyOrangeDark)
                                    )
                                )
                                .shadow(4.dp, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "SafetyLog",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp,
                                    maxLines = 1
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (uiState.isOnline) {
                                        if (isDarkTheme) Color(0xFF064E3B) else Color(0xFFD1FAE5)
                                    } else {
                                        if (isDarkTheme) Color(0xFF451A03) else Color(0xFFFEF3C7)
                                    },
                                    border = BorderStroke(0.5.dp, if (uiState.isOnline) StatusPassed else SafetyOrange),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showNetworkInfoDialog = true
                                        }
                                ) {
                                    Text(
                                        text = if (uiState.isOnline) "ONLINE" else "OFFLINE",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (uiState.isOnline) {
                                            if (isDarkTheme) Color(0xFF34D399) else Color(0xFF047857)
                                        } else {
                                            SafetyOrange
                                        },
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        letterSpacing = 0.5.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(uiState.userRole.badgeColorHex),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showRoleDialog = true
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        val badgeLabel = when (uiState.userRole) {
                                            UserRole.OWNER -> "OWNER"
                                            UserRole.ACCOUNT_ADMIN -> "ADMIN"
                                            UserRole.MANAGER -> "MANAGER"
                                            UserRole.USER -> "USER"
                                        }
                                        Text(
                                            text = badgeLabel,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            letterSpacing = 0.5.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Change Role",
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Compliance & Audit Vault",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToReports) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Reports & Export",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box {
                        IconButton(onClick = { showAccountMenu = true }) {
                            val authUser = uiState.authUser
                            if (authUser != null) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(authUser.provider.brandColorHex)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = authUser.initials,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Inspector Profile",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showAccountMenu,
                            onDismissRequest = { showAccountMenu = false }
                        ) {
                            val authUser = uiState.authUser
                            if (authUser != null) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = authUser.displayName,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = authUser.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        showAccountMenu = false
                                        onNavigateToProfile()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                                    }
                                )
                                HorizontalDivider()
                            }

                            DropdownMenuItem(
                                text = { Text("Inspector Profile") },
                                onClick = {
                                    showAccountMenu = false
                                    onNavigateToProfile()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Badge, contentDescription = null)
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Security & OAuth Vault") },
                                onClick = {
                                    showAccountMenu = false
                                    onNavigateToAuth()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.VpnKey, contentDescription = null)
                                }
                            )

                            if (authUser != null) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text("Sign Out", color = StatusDeficient, fontWeight = FontWeight.Bold)
                                    },
                                    onClick = {
                                        showAccountMenu = false
                                        viewModel.signOut()
                                        Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Logout,
                                            contentDescription = "Sign Out",
                                            tint = StatusDeficient
                                        )
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (!uiState.isAdFree) {
                SafetyBannerAd(
                    isAdFree = uiState.isAdFree,
                    onRemoveAdsClicked = { showPaywall = true }
                )
            }
        },
        floatingActionButton = {
            if (isScrolledPastHero && uiState.userRole.canManageAssets) {
                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigateToAddEquipment()
                    },
                    containerColor = SafetyOrange,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    shape = CircleShape
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Register Asset")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Delinquency / Past-Due / Cancelled Alert Banner
            if (uiState.isSubscriptionDelinquentOrCancelled) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(3.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED)
                                StatusDeficient.copy(alpha = 0.12f)
                            else
                                SafetyAmber.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.5.dp,
                            if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient else SafetyAmber
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED)
                                        Icons.Default.CreditCardOff
                                    else
                                        Icons.Default.CancelPresentation,
                                    contentDescription = null,
                                    tint = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient else SafetyAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED)
                                        "PAST DUE - FUNCTIONS LOCKED TO FREE USE"
                                    else
                                        "SUBSCRIPTION CANCELLED - FREE TIER ACTIVE",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient else SafetyAmber
                                )
                            }
                            Text(
                                text = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED)
                                    "Payment could not be delivered for your ${uiState.subscriptionTier.title}. Unlimited audits, cross-account sharing, and Company OAuth are locked down to Free use (5 audits/month with banner ads)."
                                else
                                    "Your recurring ${uiState.subscriptionTier.title} was cancelled. Features are running on Free use (5 inspections/month with banner ads).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Button(
                                onClick = {
                                    if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) {
                                        viewModel.reactivateSubscription()
                                    } else {
                                        showPaywall = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient else SafetyAmber
                                )
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) "Pay Outstanding Balance" else "Reactivate Subscription",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // 1. Executive Compliance Command Card
            item {
                ExecutiveComplianceHeroCard(
                    total = uiState.totalCount,
                    passed = uiState.passedCount,
                    deficient = uiState.deficientCount,
                    due = uiState.dueCount,
                    isDark = isDarkTheme,
                    onVaultClick = onNavigateToAuth
                )
            }

            // 2. Vault Security & License Quota Status
            item {
                LicenseQuotaCard(
                    isPro = uiState.isProUser,
                    subscriptionTier = uiState.subscriptionTier,
                    effectiveTier = uiState.effectiveTier,
                    paymentStatus = uiState.paymentStatus,
                    enterpriseSeats = uiState.enterpriseSeats,
                    inspectionsThisMonth = uiState.inspectionsThisMonth,
                    freeLimit = uiState.freeLimit,
                    progressFraction = uiState.monthlyProgressFraction,
                    onManageSubscription = { showPaywall = true }
                )
            }



            // 3. Search Bar and Category Filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by Tag ID, location, or model...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.onSearchQueryChanged("")
                                }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
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
                    if (uiState.searchQuery.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Found ${uiState.filteredList.size} of ${uiState.totalCount} assets matching \"${uiState.searchQuery}\"",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Reset Search",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SafetyOrange,
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.onSearchQueryChanged("")
                                }
                            )
                        }
                    }

                    // Modern Segmented Filter Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AuditFilterChip(
                            label = "All",
                            count = uiState.totalCount,
                            isSelected = uiState.activeFilter == FilterStatus.ALL,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onFilterSelected(FilterStatus.ALL)
                            },
                            activeColor = if (isDarkTheme) SafetyOrange else Slate900,
                            modifier = Modifier.weight(1f)
                        )
                        AuditFilterChip(
                            label = "Passed",
                            count = uiState.passedCount,
                            isSelected = uiState.activeFilter == FilterStatus.PASSED,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onFilterSelected(FilterStatus.PASSED)
                            },
                            activeColor = StatusPassed,
                            modifier = Modifier.weight(1f)
                        )
                        AuditFilterChip(
                            label = "Deficient",
                            count = uiState.deficientCount,
                            isSelected = uiState.activeFilter == FilterStatus.DEFICIENT,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onFilterSelected(FilterStatus.DEFICIENT)
                            },
                            activeColor = StatusDeficient,
                            modifier = Modifier.weight(1f)
                        )
                        AuditFilterChip(
                            label = "Due",
                            count = uiState.dueCount,
                            isSelected = uiState.activeFilter == FilterStatus.DUE,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onFilterSelected(FilterStatus.DUE)
                            },
                            activeColor = StatusPending,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. Assigned Facility Assets Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ASSIGNED FACILITY ASSETS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Assigned inventory • Tap 'Inspect' to record audit checks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    if (uiState.userRole.canManageAssets) {
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToAddEquipment()
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SafetyOrange.copy(alpha = 0.15f),
                                contentColor = SafetyOrange
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Register",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 5. Equipment Asset Cards or Empty State
            if (uiState.filteredList.isEmpty()) {
                item {
                    EmptyAssetsStateCard(
                        isSearching = uiState.searchQuery.isNotBlank() || uiState.activeFilter != FilterStatus.ALL,
                        userRole = uiState.userRole,
                        onAddClicked = onNavigateToAddEquipment
                    )
                }
            } else {
                items(uiState.filteredList, key = { it.equipment.id }) { item ->
                    EquipmentAssetCard(
                        item = item,
                        logDateFormat = logDateFormat,
                        isDark = isDarkTheme,
                        onClick = { selectedAssetForAction = item },
                        onDirectInspect = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigateToInspect(item.equipment.id)
                        }
                    )
                }
            }
        }
    }

    // Selectable Asset Action Hub (Modal Bottom Sheet)
    selectedAssetForAction?.let { selected ->
        AssetActionBottomSheet(
            item = selected,
            userRole = uiState.userRole,
            isDark = isDarkTheme,
            onDismiss = { selectedAssetForAction = null },
            onInspect = {
                val id = selected.equipment.id
                selectedAssetForAction = null
                onNavigateToInspect(id)
            },
            onViewLogs = {
                val id = selected.equipment.id
                selectedAssetForAction = null
                onNavigateToHistory(id)
            },
            onAddToCalendar = {
                val intent = viewModel.createCalendarIntent(selected.equipment)
                if (intent != null) {
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "No calendar application installed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Please set a valid inspection due date first", Toast.LENGTH_SHORT).show()
                }
            },
            onEdit = {
                val id = selected.equipment.id
                selectedAssetForAction = null
                onNavigateToEditEquipment(id)
            },
            onDelete = {
                val eq = selected.equipment
                selectedAssetForAction = null
                equipmentToDelete = eq
            }
        )
    }

    // Deletion Confirmation Dialog
    equipmentToDelete?.let { eq ->
        AlertDialog(
            onDismissRequest = { equipmentToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusDeficient
                )
            },
            title = { Text("Decommission Equipment") },
            text = {
                Text("Are you sure you want to permanently remove ${eq.tagId} (${eq.equipmentType}) from the compliance registry? All associated inspection records will remain in historical audit logs.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteEquipment(eq)
                        equipmentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDeficient)
                ) {
                    Text("Decommission")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { equipmentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Subscriptions and Licensing Paywall Sheet
    if (showPaywall) {
        PaywallBottomSheet(
            onDismissRequest = { showPaywall = false },
            currentTier = uiState.subscriptionTier,
            paymentStatus = uiState.paymentStatus,
            userRole = uiState.userRole,
            initialEnterpriseSeats = uiState.enterpriseSeats,
            onSelectTier = { tier, seats ->
                viewModel.setSubscriptionTier(tier, seats)
                showPaywall = false
            },
            onLaunchBilling = { act, tier, seats ->
                viewModel.launchBillingFlow(act, tier, seats)
                showPaywall = false
            },
            onCancelSubscription = {
                viewModel.cancelSubscription()
            },
            onReactivateSubscription = {
                viewModel.reactivateSubscription()
            },
            onSimulatePaymentFailed = {
                viewModel.markPaymentFailed()
            },
            onSimulatePaymentSuccess = {
                viewModel.reactivateSubscription()
            },
            onUpgradeClicked = {
                viewModel.reactivateSubscription()
                showPaywall = false
            }
        )
    }

    // Network Status Dialog
    if (showNetworkInfoDialog) {
        AlertDialog(
            onDismissRequest = { showNetworkInfoDialog = false },
            icon = {
                Icon(
                    imageVector = if (uiState.isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (uiState.isOnline) StatusPassed else SafetyOrange
                )
            },
            title = {
                Text(
                    text = if (uiState.isOnline) "Vault Status: Live Online" else "Vault Status: Air-Gapped Offline",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = if (uiState.isOnline)
                        "Device is securely connected to the internet. Real-time REST endpoints, webhooks, and Google Cloud Identity verification are operational."
                    else
                        "Device is running in disconnected air-gapped mode. All inspection audits and hardware AES-256 vault writes remain strictly local.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showNetworkInfoDialog = false }) {
                    Text("Dismiss", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Interactive Role & Entitlements Switcher Dialog
    if (showRoleDialog) {
        AlertDialog(
            onDismissRequest = { showRoleDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(uiState.userRole.badgeColorHex),
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Active Role: ${uiState.userRole.title}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = uiState.authUser?.email?.let { "Linked: $it" } ?: "Local Standalone Session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Role Description
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(uiState.userRole.badgeColorHex).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(uiState.userRole.badgeColorHex).copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = uiState.userRole.description,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Entitlements breakdown
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Role Entitlements & Permissions:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        RolePermissionCheckItem(
                            title = "Equipment Management",
                            isGranted = uiState.userRole.canManageAssets,
                            description = if (uiState.userRole.canManageAssets) "Add, edit, and organize equipment" else "Inspection viewing only"
                        )
                        RolePermissionCheckItem(
                            title = "Billing & Subscription Management",
                            isGranted = uiState.userRole.canManageBilling,
                            description = if (uiState.userRole.canManageBilling) "Manage tier, seats, and billing" else "Restricted to Account Admin/Owner"
                        )
                        RolePermissionCheckItem(
                            title = "Team Roster & Administration",
                            isGranted = uiState.userRole.canManageTeam,
                            description = if (uiState.userRole.canManageTeam) "Invite and manage team members" else "Restricted"
                        )
                        RolePermissionCheckItem(
                            title = "Integration & Security Settings",
                            isGranted = uiState.userRole.canManageSettings,
                            description = if (uiState.userRole.canManageSettings) "Configure OAuth, sync & webhooks" else "Restricted"
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Switch Role Selector
                    Text(
                        text = "Switch Active Role (Testing & Simulation):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val isOwnerAuthorized = com.safetylog.app.data.auth.AuthorizationConfig.isAuthorizedOwner(uiState.authUser?.email)

                        UserRole.entries.forEach { role ->
                            val isSelected = uiState.userRole == role
                            val isRestrictedOwner = role == UserRole.OWNER && !isOwnerAuthorized

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) {
                                    Color(role.badgeColorHex).copy(alpha = 0.18f)
                                } else if (isRestrictedOwner) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) Color(role.badgeColorHex) else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isRestrictedOwner) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            Toast.makeText(
                                                context,
                                                "Restricted: Platform Owner authority is reserved exclusively for j.desrosiers88@gmail.com and hexenginelabs@gmail.com",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.switchTestRole(role)
                                            Toast.makeText(context, "Switched role to ${role.title}", Toast.LENGTH_SHORT).show()
                                            showRoleDialog = false
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(role.badgeColorHex)
                                        ) {
                                            Text(
                                                text = when (role) {
                                                    UserRole.OWNER -> "OWNER"
                                                    UserRole.ACCOUNT_ADMIN -> "ADMIN"
                                                    UserRole.MANAGER -> "MANAGER"
                                                    UserRole.USER -> "USER"
                                                },
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = role.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                                if (isRestrictedOwner) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                                    ) {
                                                        Text(
                                                            text = "RESTRICTED",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            if (isRestrictedOwner) {
                                                Text(
                                                    text = "Authorized owners only: j.desrosiers88@gmail.com, hexenginelabs@gmail.com",
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Active",
                                            tint = Color(role.badgeColorHex),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else if (isRestrictedOwner) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Restricted",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRoleDialog = false }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun RolePermissionCheckItem(title: String, isGranted: Boolean, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isGranted) StatusPassed else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isGranted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExecutiveComplianceHeroCard(
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
                    letterSpacing = 0.8.sp
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
                    modifier = Modifier.size(76.dp),
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
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (complianceRate >= 90) "AUDIT READY" else "DEFICIENT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (complianceRate >= 90) StatusPassedLight else StatusDeficientLight,
                            fontSize = 7.sp
                        )
                    }
                }

                // Breakdown Counters
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    MetricCounterRow(
                        label = "Managed Assets",
                        value = total.toString(),
                        indicatorColor = Slate400,
                        textColor = Color.White
                    )
                    MetricCounterRow(
                        label = "Compliant (Passed)",
                        value = passed.toString(),
                        indicatorColor = StatusPassedLight,
                        textColor = if (passed > 0) StatusPassedLight else Color.White
                    )
                    MetricCounterRow(
                        label = "Deficiencies Flagged",
                        value = deficient.toString(),
                        indicatorColor = StatusDeficientLight,
                        textColor = if (deficient > 0) StatusDeficientLight else Color.White
                    )
                    MetricCounterRow(
                        label = "Inspection Due",
                        value = due.toString(),
                        indicatorColor = SafetyAmberLight,
                        textColor = if (due > 0) SafetyAmberLight else Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCounterRow(
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
            fontSize = 12.sp
        )
    }
}

@Composable
private fun LicenseQuotaCard(
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
                                maxLines = 1
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            maxLines = 1
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
private fun AuditFilterChip(
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

@Composable
private fun EquipmentAssetCard(
    item: EquipmentWithLatestInspection,
    logDateFormat: SimpleDateFormat,
    isDark: Boolean,
    onClick: () -> Unit,
    onDirectInspect: () -> Unit = {}
) {
    val eq = item.equipment
    val isPassed = item.lastInspectionStatus.equals("PASSED", ignoreCase = true)
    val isDeficient = item.lastInspectionStatus.equals("DEFICIENT", ignoreCase = true)
    val isDue = item.lastInspectionDate == null

    val statusColors = getComplianceStatusColors(item.lastInspectionStatus, isDark = isDark)
    val now = System.currentTimeMillis()
    val isOverdue = eq.nextReviewTimestamp in 1..<now

    val protocolName = eq.assignedChecklist.ifBlank {
        ChecklistRegistry.getChecklistForType(eq.equipmentType).categoryName
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left Status Accent Stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (isOverdue) StatusDeficient else statusColors.accent)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(10.dp)
            ) {
                // Top Row: Tag ID + Status Pill + Review Schedule Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Monospaced Tag Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isDark) DarkSurfaceCard else Slate900)
                                .border(
                                    1.dp,
                                    if (isDark) DarkSurfaceBorder else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = eq.tagId,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Status Badge Pill with M3 Container Tonal Contrast
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(statusColors.container)
                                .border(1.dp, statusColors.border, RoundedCornerShape(16.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(statusColors.accent)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when {
                                        isPassed -> "PASSED"
                                        isDeficient -> "DEFICIENT"
                                        else -> "DUE INSPECTION"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColors.text,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }

                    // Review Schedule Due Badge
                    if (isOverdue) {
                        Surface(
                            color = if (isDark) Color(0xFF3B1214) else Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, StatusDeficient.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "OVERDUE",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 8.5.sp,
                                color = StatusDeficient
                            )
                        }
                    } else if (eq.assignedReviewDate.isNotBlank()) {
                        Surface(
                            color = if (isDark) Color(0xFF132238) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "DUE: ${eq.assignedReviewDate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (eq.reminderEnabled) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = "Alarm Active",
                                        tint = SafetyOrange,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Equipment Type and Dedicated Icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = getEquipmentIcon(eq.equipmentType),
                        contentDescription = null,
                        tint = SafetyOrange,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = eq.equipmentType,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Location
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = eq.locationDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Assigned Checklist Protocol Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FactCheck,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Protocol: $protocolName • ${eq.reviewFrequency.ifBlank { "Monthly" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (eq.serialNumber.isNotBlank()) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = "Serial: ${eq.serialNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Action Bar: Last inspected time + Direct full-width actions
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LAST AUDIT LOG",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (item.lastInspectionDate != null) {
                                logDateFormat.format(Date(item.lastInspectionDate))
                            } else {
                                "Never Logged • Due Now"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDue) statusColors.text else MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onDirectInspect,
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Inspect",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            onClick = onClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Manage & Logs",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetActionBottomSheet(
    item: EquipmentWithLatestInspection,
    userRole: UserRole,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onInspect: () -> Unit,
    onViewLogs: () -> Unit,
    onAddToCalendar: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val eq = item.equipment
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val protocolName = eq.assignedChecklist.ifBlank {
        ChecklistRegistry.getChecklistForType(eq.equipmentType).categoryName
    }
    val protocol = ChecklistRegistry.getChecklistByName(protocolName)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Asset Info Header Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) DarkSurfaceCard else Slate900
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) DarkSurfaceBase else Slate800)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = eq.tagId,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        Surface(
                            color = if (isDark) Color(0xFF0369A1) else Color(0xFF0284C7),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "ASSIGNED ASSET",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = eq.equipmentType,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Slate400, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = eq.locationDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate300
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Protocol: ${protocol.standardTitle}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SafetyOrangeLight,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Review Cycle: ${eq.reviewFrequency.ifBlank { "Monthly" }} • Next Due: ${eq.assignedReviewDate.ifBlank { "Scheduled" }} at ${eq.assignedReviewTime.ifBlank { "09:00 AM" }}${if (eq.reminderEnabled) " • Alarm: ${eq.reminderLeadDays}d before" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate400
                    )
                }
            }

            Text(
                text = "ASSET OPERATIONS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            // Primary Option: Log New Inspection (Gated to roles that can conduct inspections)
            if (userRole.canConductInspections) {
                Button(
                    onClick = onInspect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .shadow(4.dp, RoundedCornerShape(14.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Log New Inspection Checklist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Slate400, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Audits restricted to Field Inspector or Manager role.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Option: View Inspection Logs (Available to all roles including Viewer)
            OutlinedCard(
                onClick = onViewLogs,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "View Inspection Logs & Export",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Export or sync certified PDF audit logs to Google Drive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }

            // Option: Add Inspection Due Date to Device Calendar
            OutlinedCard(
                onClick = onAddToCalendar,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, tint = TechBlue, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add to Device Calendar",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Schedule recurring NFPA/OSHA inspection event & alert",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }

            // Option: Edit Asset & Review Schedule (Gated to canManageAssets)
            if (userRole.canManageAssets) {
                OutlinedCard(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Edit Asset & Review Schedule",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Update location, assigned checklist, or target review dates",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Option: Decommission Asset (Gated to canDeleteAssets)
            if (userRole.canDeleteAssets) {
                OutlinedCard(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, StatusDeficient.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(StatusDeficientContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = StatusDeficient, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Decommission Asset",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusDeficient
                            )
                            Text(
                                text = "Permanently remove from active facility registry",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getEquipmentIcon(type: String): ImageVector {
    val lower = type.lowercase()
    return when {
        lower.contains("aed") || lower.contains("defibrillator") -> Icons.Default.MedicalServices
        lower.contains("eye") || lower.contains("wash") -> Icons.Default.WaterDrop
        lower.contains("blanket") -> Icons.Default.Bed
        else -> Icons.Default.FireExtinguisher
    }
}

@Composable
private fun EmptyAssetsStateCard(
    isSearching: Boolean,
    userRole: UserRole,
    onAddClicked: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(SafetyOrangeContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = SafetyOrange,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isSearching) "No Safety Assets Found" else "No Equipment Registered In Vault",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isSearching) {
                    "Try revising your search keyword or selected filter status."
                } else {
                    "Your compliance vault starts 100% clean with zero mock data. Register your facility's fire extinguishers, AEDs, and life-safety equipment."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!isSearching) {
                Spacer(modifier = Modifier.height(18.dp))
                if (userRole.canManageAssets) {
                    Button(
                        onClick = onAddClicked,
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Register First Asset", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
