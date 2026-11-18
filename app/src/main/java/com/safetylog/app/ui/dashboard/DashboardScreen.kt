package com.safetylog.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safetylog.app.data.auth.UserRole
import com.safetylog.app.data.dao.EquipmentWithLatestInspection
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.ui.components.PaywallBottomSheet
import com.safetylog.app.ui.components.SafetyBannerAd
import com.safetylog.app.ui.theme.*
import com.safetylog.app.util.TestControls
import kotlinx.coroutines.launch
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
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
    // Asset management is a role capability, never a side effect of the subscription
    // tier. The previous `|| tier != ENTERPRISE` granted viewers management access on
    // Free/Pro plans, contradicting the role model ("Inspection viewing only").
    val canManageAssets = uiState.userRole.canManageAssets

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
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
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
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
                                        if (TestControls.isEnabled || uiState.canPreviewLowerRoles) {
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
                                    text = "Audit Vault",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
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
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Signed out successfully")
                                        }
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
            if (isScrolledPastHero && canManageAssets) {
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
            contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Delinquency / Past-Due / Cancelled Alert Banner
            if (uiState.isSubscriptionDelinquentOrCancelled) {
                item {
                    SubscriptionDelinquencyBanner(
                        paymentStatus = uiState.paymentStatus,
                        subscriptionTier = uiState.subscriptionTier,
                        onReactivateSubscription = viewModel::reactivateSubscription,
                        onShowPaywall = { showPaywall = true }
                    )
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
                DashboardSearchAndFilterBar(
                    searchQuery = uiState.searchQuery,
                    filteredCount = uiState.filteredList.size,
                    totalCount = uiState.totalCount,
                    passedCount = uiState.passedCount,
                    deficientCount = uiState.deficientCount,
                    dueCount = uiState.dueCount,
                    activeFilter = uiState.activeFilter,
                    isDarkTheme = isDarkTheme,
                    onSearchQueryChanged = viewModel::onSearchQueryChanged,
                    onFilterSelected = viewModel::onFilterSelected
                )
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (canManageAssets) {
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
                            modifier = Modifier.heightIn(min = 48.dp)
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
                        canManageAssets = canManageAssets,
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

            // Bottom breathing clearance spacer ensuring content, words, and cards can always scroll completely clear of the bottom banner and FAB
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Selectable Asset Action Hub (Modal Bottom Sheet)
    selectedAssetForAction?.let { selected ->
        AssetActionBottomSheet(
            item = selected,
            userRole = uiState.userRole,
            canManageAssets = canManageAssets,
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
                        scope.launch {
                            snackbarHostState.showSnackbar("No calendar application installed")
                        }
                    }
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("Please set a valid inspection due date first")
                    }
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
        DecommissionEquipmentDialog(
            equipment = eq,
            onConfirm = {
                viewModel.deleteEquipment(eq)
                equipmentToDelete = null
            },
            onDismiss = { equipmentToDelete = null }
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
        NetworkStatusDialog(
            isOnline = uiState.isOnline,
            onDismiss = { showNetworkInfoDialog = false }
        )
    }

    // Interactive Role & Entitlements Switcher Dialog
    if (showRoleDialog) {
        RoleSwitcherDialog(
            userRole = uiState.userRole,
            authoritativeRole = uiState.authoritativeRole,
            isAuthenticated = uiState.authUser != null,
            authEmail = uiState.authUser?.email,
            onSwitchRole = { role -> viewModel.switchTestRole(role) },
            onNavigateToAuth = {
                showRoleDialog = false
                onNavigateToAuth()
            },
            onDismiss = { showRoleDialog = false }
        )
    }
}
