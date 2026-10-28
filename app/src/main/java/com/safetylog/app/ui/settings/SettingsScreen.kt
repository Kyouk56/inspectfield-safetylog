package com.safetylog.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safetylog.app.data.auth.CompanyOAuthConfig
import com.safetylog.app.data.auth.CompanyOAuthProvider
import com.safetylog.app.data.auth.PaymentStatus
import com.safetylog.app.data.auth.SecureTokenStorage
import com.safetylog.app.data.auth.SubscriptionTier
import com.safetylog.app.data.auth.TeamMember
import com.safetylog.app.data.auth.UserRole
import com.safetylog.app.data.sync.TeamSyncEngine
import com.safetylog.app.ui.components.PaywallBottomSheet
import com.safetylog.app.ui.components.PrivacyPolicyDialog
import com.safetylog.app.ui.dashboard.DashboardViewModel
import com.safetylog.app.ui.theme.*
import com.safetylog.app.util.CrashLogVault
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    dashboardViewModel: DashboardViewModel,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    teamSyncEngine: TeamSyncEngine,
    secureStorage: SecureTokenStorage,
    onNavigateToAuth: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var showPaywall by remember { mutableStateOf(false) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var showEndpointDialog by remember { mutableStateOf(false) }
    var customEndpointInput by remember { mutableStateOf(teamSyncEngine.getCustomRestEndpoint()) }
    var cloudTelemetryOptIn by remember { mutableStateOf(CrashLogVault.isCloudTelemetryEnabled(context)) }
    var localCrashCount by remember { mutableIntStateOf(CrashLogVault.getCrashCount(context)) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Inspection notifications enabled!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notification permission denied. Alarms may not display banners.", Toast.LENGTH_LONG).show()
        }
    }

    // Company OAuth self-service configuration form state
    var companyNameInput by remember(uiState.companyOAuthConfig) { mutableStateOf(uiState.companyOAuthConfig.companyName) }
    var corporateDomainInput by remember(uiState.companyOAuthConfig) { mutableStateOf(uiState.companyOAuthConfig.corporateDomain) }
    var selectedProvider by remember(uiState.companyOAuthConfig) { mutableStateOf(uiState.companyOAuthConfig.provider) }
    var tenantIdInput by remember(uiState.companyOAuthConfig) { mutableStateOf(uiState.companyOAuthConfig.tenantIdOrDomain) }
    var clientIdInput by remember(uiState.companyOAuthConfig) { mutableStateOf(uiState.companyOAuthConfig.clientId) }
    var enforceDomainInput by remember(uiState.companyOAuthConfig) { mutableStateOf(uiState.companyOAuthConfig.enforceDomainRestriction) }
    var webhookUrlInput by remember(uiState.companyOAuthConfig) { mutableStateOf(uiState.companyOAuthConfig.webhookUrl) }
    var webhookSecretInput by remember(uiState.companyOAuthConfig) { mutableStateOf(uiState.companyOAuthConfig.webhookSecret) }
    var webhookAutoInput by remember(uiState.companyOAuthConfig) { mutableStateOf(uiState.companyOAuthConfig.webhookAutoDispatch) }
    var cloudDriveUriInput by remember(uiState.companyOAuthConfig) { mutableStateOf(uiState.companyOAuthConfig.cloudDriveFolderUri) }
    var cloudDriveNameInput by remember(uiState.companyOAuthConfig) { mutableStateOf(uiState.companyOAuthConfig.cloudDriveFolderName) }
    var cloudDriveAutoInput by remember(uiState.companyOAuthConfig) { mutableStateOf(uiState.companyOAuthConfig.cloudDriveAutoBackup) }
    var isTestingWebhook by remember { mutableStateOf(false) }
    var isBackingUpDrive by remember { mutableStateOf(false) }

    // Team Members administration state
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<TeamMember?>(null) }
    var memberToRemove by remember { mutableStateOf<TeamMember?>(null) }
    var newMemberEmail by remember { mutableStateOf("") }
    var newMemberName by remember { mutableStateOf("") }
    var newMemberRole by remember { mutableStateOf(UserRole.USER) }
    var addMemberError by remember { mutableStateOf<String?>(null) }

    var editEmailInput by remember { mutableStateOf("") }
    var editNameInput by remember { mutableStateOf("") }
    var editRoleInput by remember { mutableStateOf(UserRole.USER) }
    var editIsActiveInput by remember { mutableStateOf(true) }
    var editMemberError by remember { mutableStateOf<String?>(null) }

    val openDocumentTreeLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {}
            val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            val folderName = docFile?.name ?: "Cloud Drive Folder"
            cloudDriveUriInput = uri.toString()
            cloudDriveNameInput = folderName

            val updatedConfig = uiState.companyOAuthConfig.copy(
                cloudDriveFolderUri = uri.toString(),
                cloudDriveFolderName = folderName
            )
            dashboardViewModel.saveCompanyOAuthConfig(updatedConfig)
            Toast.makeText(context, "Cloud drive folder linked: $folderName", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Settings & Preferences",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Display, Regulatory Vault & Developer Tools",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Delinquency / Past-Due / Cancelled Alert Banner
            if (uiState.isSubscriptionDelinquentOrCancelled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED)
                                    Icons.Default.CreditCardOff
                                else
                                    Icons.Default.CancelPresentation,
                                contentDescription = null,
                                tint = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient else SafetyAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED)
                                    "SUBSCRIPTION PAST DUE - FEATURES LOCKED"
                                else
                                    "SUBSCRIPTION CANCELLED - FREE TIER ACTIVE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient else SafetyAmber
                            )
                        }

                        Text(
                            text = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) {
                                "Payment could not be delivered for your ${uiState.subscriptionTier.title}. All recurring privileges (Company OAuth, Cross-Account Team Sync, and unlimited inspections) are immediately locked down to Free use (5 audits/month with banner ads)."
                            } else {
                                "Your recurring ${uiState.subscriptionTier.title} was cancelled. Functions have reverted to the Free tier (5 inspections/month with banner ads)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )

                        if (uiState.userRole.canManageBilling) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) {
                                            dashboardViewModel.reactivateSubscription()
                                            Toast.makeText(context, "Payment re-processed. Subscription restored!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            showPaywall = true
                                        }
                                    },
                                    modifier = Modifier.weight(1.3f).height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
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

                                OutlinedButton(
                                    onClick = { showPaywall = true },
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Review Plan", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Contact your Account Admin or Owner to resolve account billing and restore company privileges.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1. Appearance & Field Display Mode
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Field Display & Theme Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Switch contrast for dimly-lit utility rooms or bright sunlight",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsThemeChip(
                            label = "System Auto",
                            icon = Icons.Default.BrightnessAuto,
                            isSelected = themeMode == AppThemeMode.SYSTEM,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onThemeModeChange(AppThemeMode.SYSTEM)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SettingsThemeChip(
                            label = "Light",
                            icon = Icons.Default.LightMode,
                            isSelected = themeMode == AppThemeMode.LIGHT,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onThemeModeChange(AppThemeMode.LIGHT)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SettingsThemeChip(
                            label = "Dark Mode",
                            icon = Icons.Default.DarkMode,
                            isSelected = themeMode == AppThemeMode.DARK,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onThemeModeChange(AppThemeMode.DARK)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 2. Inspection Due Reminders & Notification Alarms
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SafetyOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = SafetyOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Inspection Alarms & Reminders",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "NFPA/OSHA schedules, background alarms & notifications",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Notification Permission & System Channel Status
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (hasNotificationPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (hasNotificationPermission) StatusPassed else SafetyAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (hasNotificationPermission) "System Notifications: Allowed" else "System Notifications: Permission Required",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (hasNotificationPermission) StatusPassed else SafetyAmber
                                    )
                                }
                                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    FilledTonalButton(
                                        onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text(
                                text = "Channel: Safety Equipment Reminders • High Importance, Sound & Vibration enabled. Alarms are preserved across device restarts via boot receiver.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Action Buttons Row: Test Notification & Reschedule Alarms
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val sent = dashboardViewModel.triggerTestNotification()
                                if (sent) {
                                    Toast.makeText(context, "Test inspection alert dispatched to status bar!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Notification permission needed or service unavailable.", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.NotificationAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Notification", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dashboardViewModel.rescheduleAllReminders()
                                Toast.makeText(context, "All upcoming inspection alarms synchronized!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                        ) {
                            Icon(Icons.Default.AlarmOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Alarms", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. Admin & Developer Sandbox (Strictly gated to Owner; stays accessible when active for testing)
            if (uiState.userRole.canAccessTestView || uiState.isAdminTestViewActive) {
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
                                    UserRole.entries.forEach { role ->
                                        val isSelected = uiState.userRole == role
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { dashboardViewModel.switchTestRole(role) },
                                            label = {
                                                Text(
                                                    text = role.title,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
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
                        onClick = { showPrivacyPolicyDialog = true },
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

            // 4. Company-Specific OAuth & Enterprise Identity Hub
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (uiState.canUseCompanyOAuth) Color(0xFF4F46E5).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                )
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
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.canUseCompanyOAuth) Color(0xFFEEF2FF) else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CorporateFare,
                                    contentDescription = null,
                                    tint = if (uiState.canUseCompanyOAuth) Color(0xFF4F46E5) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Company OAuth & SSO Hub",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Corporate SAML 2.0 / Entra ID / Okta SSO & Domain Lock",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (uiState.canUseCompanyOAuth)
                                Color(0xFF10B981).copy(alpha = 0.15f)
                            else if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED)
                                StatusDeficient.copy(alpha = 0.15f)
                            else
                                SafetyOrange.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (uiState.canUseCompanyOAuth)
                                    "ENTERPRISE ACTIVE"
                                else if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED)
                                    "LOCKED (PAST DUE)"
                                else
                                    "ENTERPRISE ONLY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (uiState.canUseCompanyOAuth)
                                    Color(0xFF10B981)
                                else if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED)
                                    StatusDeficient
                                else
                                    SafetyOrange,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (!uiState.canUseCompanyOAuth) {
                        // Gated / Locked State
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Corporate Single Sign-On is Gated",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                Text(
                                    text = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) {
                                        "Your company's Enterprise subscription payment failed. Custom SSO endpoints and company domain restrictions are locked to Free use until payment is delivered."
                                    } else if (uiState.paymentStatus == PaymentStatus.CANCELLED) {
                                        "Your Enterprise subscription was cancelled. Company OAuth configuration has been locked and reverted to Free tier usage."
                                    } else {
                                        "Company-specific OAuth configuration (SAML 2.0, Microsoft Entra ID / Azure AD, Okta OIDC, and Google Workspace Domain Lock) is exclusively available to active Enterprise subscribers ($0.50/seat/mo)."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )

                                Button(
                                    onClick = { showPaywall = true },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient else Slate900
                                    )
                                ) {
                                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(16.dp), tint = SafetyAmber)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED)
                                            "Resolve Past Due Balance"
                                        else if (uiState.paymentStatus == PaymentStatus.CANCELLED)
                                            "Reactivate Enterprise Plan"
                                        else
                                            "Unlock Enterprise OAuth ($0.50/seat)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        // Entitled Active State: Full Self-Service Form
                        val canManageSettings = uiState.userRole.canManageSettings

                        if (!canManageSettings) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Read-Only: Contact an Account Admin or Manager to modify company SSO credentials.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Self-service identity provider configuration. Enforce your company domain across all technicians and inspector signatures.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = companyNameInput,
                            onValueChange = { companyNameInput = it },
                            label = { Text("Organization / Company Name") },
                            placeholder = { Text("e.g. Apex Industrial Safety LLC") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = canManageSettings,
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = corporateDomainInput,
                            onValueChange = { corporateDomainInput = it },
                            label = { Text("Corporate Email Domain") },
                            placeholder = { Text("e.g. apexsafety.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = canManageSettings,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Text(
                            text = "Identity Protocol / Provider:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CompanyOAuthProvider.entries.forEach { provider ->
                                val isSelected = selectedProvider == provider
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedProvider = provider },
                                    enabled = canManageSettings,
                                    label = {
                                        Text(
                                            text = provider.displayName,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF4F46E5),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = tenantIdInput,
                            onValueChange = { tenantIdInput = it },
                            label = { Text("Issuer URL / Tenant ID") },
                            placeholder = { Text("e.g. https://login.microsoftonline.com/tenant-guid") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = canManageSettings,
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = clientIdInput,
                            onValueChange = { clientIdInput = it },
                            label = { Text("Client ID / Entity ID") },
                            placeholder = { Text("e.g. apex-inspectfield-client") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = canManageSettings,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Enforce Corporate Domain Only",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                val domainDisplay = if (corporateDomainInput.isNotBlank()) corporateDomainInput else "domain.com"
                                Text(
                                    text = "Strictly restrict field sign-ins to emails ending in @$domainDisplay",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = enforceDomainInput,
                                onCheckedChange = { enforceDomainInput = it },
                                enabled = canManageSettings
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!canManageSettings) return@Button
                                    val newConfig = CompanyOAuthConfig(
                                        companyName = companyNameInput,
                                        corporateDomain = corporateDomainInput,
                                        provider = selectedProvider,
                                        tenantIdOrDomain = tenantIdInput,
                                        clientId = clientIdInput,
                                        enforceDomainRestriction = enforceDomainInput,
                                        isConfigured = companyNameInput.isNotBlank() && corporateDomainInput.isNotBlank(),
                                        webhookUrl = webhookUrlInput,
                                        webhookSecret = webhookSecretInput,
                                        webhookAutoDispatch = webhookAutoInput,
                                        cloudDriveFolderUri = cloudDriveUriInput,
                                        cloudDriveFolderName = cloudDriveNameInput,
                                        cloudDriveAutoBackup = cloudDriveAutoInput
                                    )
                                    dashboardViewModel.saveCompanyOAuthConfig(newConfig)
                                    Toast.makeText(context, "Company OAuth configuration saved & activated!", Toast.LENGTH_SHORT).show()
                                },
                                enabled = canManageSettings,
                                modifier = Modifier.weight(1.3f).height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (canManageSettings) "Save & Activate OAuth" else "Save Restricted", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onNavigateToAuth,
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test SSO", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // 4B. Enterprise Team Members & Seat Roster (Admin Access)
            val usedSeats = uiState.teamMembers.count { it.isActive }
            val totalSeats = uiState.enterpriseSeats
            val isSeatsFull = usedSeats >= totalSeats

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (uiState.canUseCompanyOAuth) Color(0xFF0284C7).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.canUseCompanyOAuth) TechBlueContainer else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = if (uiState.canUseCompanyOAuth) TechBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Team Members & Seat Roster",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Assign roles, add/remove users by email ($0.50/seat/mo)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (!uiState.canUseCompanyOAuth) {
                                SafetyOrange.copy(alpha = 0.15f)
                            } else if (isSeatsFull) {
                                SafetyAmber.copy(alpha = 0.15f)
                            } else {
                                StatusPassed.copy(alpha = 0.15f)
                            }
                        ) {
                            Text(
                                text = if (!uiState.canUseCompanyOAuth) {
                                    "ENTERPRISE ONLY"
                                } else if (isSeatsFull) {
                                    "SEATS FULL ($usedSeats/$totalSeats)"
                                } else {
                                    "$usedSeats OF $totalSeats SEATS USED"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (!uiState.canUseCompanyOAuth) {
                                    SafetyOrange
                                } else if (isSeatsFull) {
                                    Color(0xFFB45309)
                                } else {
                                    StatusPassed
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (!uiState.canUseCompanyOAuth) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Team Member Administration is Gated",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                Text(
                                    text = "Adding, editing, and managing inspector email rosters is an Enterprise capability ($0.50/seat/mo). Upgrade to invite and manage team members.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                                Button(
                                    onClick = { showPaywall = true },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                                ) {
                                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(16.dp), tint = SafetyAmber)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Unlock Enterprise Team Seats", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    } else {
                        // Seat Meter
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Active Seat Utilization:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$usedSeats / $totalSeats Seats ($0.50/seat/mo)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val progressFraction = if (totalSeats > 0) (usedSeats.toFloat() / totalSeats.toFloat()).coerceIn(0f, 1f) else 0f
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (isSeatsFull) SafetyAmber else TechBlue,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            if (isSeatsFull) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "All seats allocated. Expand seats to add more.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            if (uiState.userRole.canManageBilling) {
                                                showPaywall = true
                                            } else {
                                                Toast.makeText(context, "Adding seats is restricted to Account Admins and the Owner.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("+ Add Seats", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TechBlue)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Team Member Cards List
                        if (uiState.teamMembers.isEmpty()) {
                            Text(
                                text = "No team members added yet. Tap '+ Add Team Member' below to register inspectors by email.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uiState.teamMembers.forEach { member ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Role-colored avatar circle
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(member.role.badgeColorHex).copy(alpha = 0.18f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = member.initials,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = Color(member.role.badgeColorHex)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        text = member.displayTitle,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (member.name.isNotBlank()) {
                                                        Text(
                                                            text = member.email,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Surface(
                                                            color = Color(member.role.badgeColorHex).copy(alpha = 0.15f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = member.role.title,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(member.role.badgeColorHex),
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }

                                                        if (!member.isActive) {
                                                            Surface(
                                                                color = MaterialTheme.colorScheme.outlineVariant,
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = "INACTIVE",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Action Buttons (Permission Enforced)
                                            val canEditThisMember = if (member.role == UserRole.OWNER) {
                                                uiState.userRole == UserRole.OWNER
                                            } else if (member.role == UserRole.ACCOUNT_ADMIN) {
                                                uiState.userRole.canChangeAdminPermissions
                                            } else {
                                                uiState.userRole.canManageTeam
                                            }

                                            val canRemoveThisMember = if (member.role == UserRole.OWNER) {
                                                false // Root Owner cannot be removed
                                            } else if (member.role == UserRole.ACCOUNT_ADMIN) {
                                                uiState.userRole.canChangeAdminPermissions
                                            } else {
                                                uiState.userRole.canManageTeam
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (canEditThisMember) {
                                                    IconButton(
                                                        onClick = {
                                                            editingMember = member
                                                            editEmailInput = member.email
                                                            editNameInput = member.name
                                                            editRoleInput = member.role
                                                            editIsActiveInput = member.isActive
                                                            editMemberError = null
                                                        },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "Edit Member",
                                                            tint = TechBlue,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }

                                                if (canRemoveThisMember) {
                                                    IconButton(
                                                        onClick = { memberToRemove = member },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.DeleteOutline,
                                                            contentDescription = "Remove Member",
                                                            tint = StatusDeficient,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Add Member and Cloud Drive Roster Sync Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!uiState.userRole.canManageTeam) return@Button
                                    if (isSeatsFull) {
                                        if (uiState.userRole.canManageBilling) {
                                            Toast.makeText(context, "All $totalSeats company seats are in use. Add seats to invite more members.", Toast.LENGTH_LONG).show()
                                            showPaywall = true
                                        } else {
                                            Toast.makeText(context, "All $totalSeats company seats are in use. Contact an Account Admin or Owner to expand seats.", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        newMemberEmail = ""
                                        newMemberName = ""
                                        newMemberRole = UserRole.USER
                                        addMemberError = null
                                        showAddMemberDialog = true
                                    }
                                },
                                enabled = uiState.userRole.canManageTeam,
                                modifier = Modifier.weight(1.3f).height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TechBlue)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (uiState.userRole.canManageTeam) "Add Team Member" else "Add Member (Restricted)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (uiState.companyOAuthConfig.cloudDriveFolderUri.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        if (!uiState.userRole.canManageTeam) return@OutlinedButton
                                        coroutineScope.launch {
                                            val res = teamSyncEngine.saveTeamRosterToCloudDrive(
                                                uiState.companyOAuthConfig.cloudDriveFolderUri,
                                                uiState.teamMembers
                                            )
                                            res.onSuccess { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }.onFailure { err ->
                                                Toast.makeText(context, err.message ?: "Cloud sync failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    enabled = uiState.userRole.canManageTeam,
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync Drive", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Team Synchronization & Cloud Interop
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
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(TechBlueContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = TechBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Enterprise Team Sync",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Cross-account sharing & encrypted team packages",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (uiState.canShareBetweenAccounts)
                                Color(0xFF10B981).copy(alpha = 0.15f)
                            else
                                SafetyOrange.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (uiState.canShareBetweenAccounts) "UNLOCKED" else "LOCKED (PRO)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (uiState.canShareBetweenAccounts) Color(0xFF10B981) else SafetyOrange,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (!uiState.canShareBetweenAccounts) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = SafetyOrange, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Cross-account sync is locked to Free use. Individual Pro ($0.99/mo) or Enterprise ($0.50/seat/mo) subscription required.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Sync REST Endpoint:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = teamSyncEngine.getCustomRestEndpoint(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        TextButton(onClick = {
                            if (uiState.canShareBetweenAccounts) {
                                showEndpointDialog = true
                            } else {
                                showPaywall = true
                            }
                        }) {
                            Text(if (uiState.canShareBetweenAccounts) "Edit" else "Unlock", fontWeight = FontWeight.Bold)
                        }
                    }

                    // 5A. Zero-Cost Company Cloud Drive Sync (Google Drive / OneDrive)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = TechBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Company Cloud Drive Sync ($0 Server Cost)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Save encrypted compliance backups directly into your company's Google Drive or OneDrive folder via Storage Access Framework.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (cloudDriveNameInput.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = if (cloudDriveNameInput.isNotBlank()) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (cloudDriveNameInput.isNotBlank()) "Folder: $cloudDriveNameInput" else "No folder linked (local storage only)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (cloudDriveNameInput.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        if (uiState.canShareBetweenAccounts) {
                                            openDocumentTreeLauncher.launch(null)
                                        } else {
                                            showPaywall = true
                                        }
                                    },
                                    enabled = uiState.canShareBetweenAccounts && uiState.userRole.canManageSettings,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(if (cloudDriveNameInput.isNotBlank()) "Change" else "Link Folder", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!uiState.canShareBetweenAccounts) {
                                        showPaywall = true
                                        return@Button
                                    }
                                    if (cloudDriveUriInput.isBlank()) {
                                        openDocumentTreeLauncher.launch(null)
                                        return@Button
                                    }
                                    isBackingUpDrive = true
                                    coroutineScope.launch {
                                        val res = teamSyncEngine.backupToCloudDriveFolder(cloudDriveUriInput)
                                        isBackingUpDrive = false
                                        res.onSuccess { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, err.message ?: "Backup failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TechBlue),
                                enabled = !isBackingUpDrive
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isBackingUpDrive) "Backing up..." else "Backup to Drive Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Auto-Backup on Every Inspection",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Automatically writes encrypted .safetylog.enc to linked folder",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = cloudDriveAutoInput,
                                onCheckedChange = { checked ->
                                    if (!uiState.canShareBetweenAccounts) {
                                        showPaywall = true
                                        return@Switch
                                    }
                                    if (!uiState.userRole.canManageSettings) {
                                        Toast.makeText(context, "Permission Denied: Only Admins and Managers can configure auto-backup", Toast.LENGTH_SHORT).show()
                                        return@Switch
                                    }
                                    if (checked && cloudDriveUriInput.isBlank()) {
                                        Toast.makeText(context, "Please link a Cloud Drive folder first", Toast.LENGTH_SHORT).show()
                                        openDocumentTreeLauncher.launch(null)
                                        return@Switch
                                    }
                                    cloudDriveAutoInput = checked
                                    val updated = uiState.companyOAuthConfig.copy(cloudDriveAutoBackup = checked)
                                    dashboardViewModel.saveCompanyOAuthConfig(updated)
                                },
                                enabled = uiState.canShareBetweenAccounts && uiState.userRole.canManageSettings
                            )
                        }
                    }

                    // 5B. Corporate Webhook Streaming
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Hub, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Corporate Webhook Streaming",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Stream real-time inspection events and defect logs directly into your company's internal REST endpoint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )

                        OutlinedTextField(
                            value = webhookUrlInput,
                            onValueChange = { webhookUrlInput = it },
                            label = { Text("Webhook Endpoint URL") },
                            placeholder = { Text("https://api.yourcompany.com/v1/inspections") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            enabled = uiState.canShareBetweenAccounts && uiState.userRole.canManageSettings
                        )

                        OutlinedTextField(
                            value = webhookSecretInput,
                            onValueChange = { webhookSecretInput = it },
                            label = { Text("Authorization Token / Secret (Optional)") },
                            placeholder = { Text("Bearer token or HMAC secret") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            enabled = uiState.canShareBetweenAccounts && uiState.userRole.canManageSettings
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!uiState.canShareBetweenAccounts) {
                                        showPaywall = true
                                        return@Button
                                    }
                                    if (webhookUrlInput.isBlank()) {
                                        Toast.makeText(context, "Enter a valid Webhook URL first", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isTestingWebhook = true
                                    coroutineScope.launch {
                                        val res = teamSyncEngine.testWebhookEndpoint(webhookUrlInput, webhookSecretInput)
                                        isTestingWebhook = false
                                        res.onSuccess { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, err.message ?: "Webhook test failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1.2f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                enabled = !isTestingWebhook
                            ) {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isTestingWebhook) "Testing..." else "Test Webhook Ping", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    if (!uiState.canShareBetweenAccounts) {
                                        showPaywall = true
                                        return@OutlinedButton
                                    }
                                    if (!uiState.userRole.canManageSettings) {
                                        Toast.makeText(context, "Permission Denied: Only Admins and Managers can edit webhook settings", Toast.LENGTH_SHORT).show()
                                        return@OutlinedButton
                                    }
                                    val updated = uiState.companyOAuthConfig.copy(
                                        webhookUrl = webhookUrlInput,
                                        webhookSecret = webhookSecretInput,
                                        webhookAutoDispatch = webhookAutoInput
                                    )
                                    dashboardViewModel.saveCompanyOAuthConfig(updated)
                                    Toast.makeText(context, "Webhook configuration saved!", Toast.LENGTH_SHORT).show()
                                },
                                enabled = uiState.canShareBetweenAccounts && uiState.userRole.canManageSettings,
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save Webhook", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Auto-Dispatch to Webhook on Inspection",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Dispatches JSON payload with item checks to endpoint",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = webhookAutoInput,
                                onCheckedChange = { checked ->
                                    if (!uiState.canShareBetweenAccounts) {
                                        showPaywall = true
                                        return@Switch
                                    }
                                    if (!uiState.userRole.canManageSettings) {
                                        Toast.makeText(context, "Permission Denied: Only Admins and Managers can edit webhook settings", Toast.LENGTH_SHORT).show()
                                        return@Switch
                                    }
                                    if (checked && webhookUrlInput.isBlank()) {
                                        Toast.makeText(context, "Enter a Webhook URL before enabling auto-dispatch", Toast.LENGTH_SHORT).show()
                                        return@Switch
                                    }
                                    webhookAutoInput = checked
                                    val updated = uiState.companyOAuthConfig.copy(webhookAutoDispatch = checked)
                                    dashboardViewModel.saveCompanyOAuthConfig(updated)
                                },
                                enabled = uiState.canShareBetweenAccounts && uiState.userRole.canManageSettings
                            )
                        }
                    }

                    // 5C. Encrypted Air-Gapped Package Export
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        text = "Last Synchronized: ${teamSyncEngine.getLastSyncDateFormatted()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = {
                            if (!uiState.canShareBetweenAccounts) {
                                showPaywall = true
                                return@OutlinedButton
                            }
                            coroutineScope.launch {
                                val result = teamSyncEngine.exportEncryptedTeamPackage()
                                result.onSuccess { file ->
                                    Toast.makeText(context, "Encrypted team package exported: ${file.name}", Toast.LENGTH_LONG).show()
                                }.onFailure { err ->
                                    Toast.makeText(context, "Export error: ${err.message ?: "Failed"}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.canShareBetweenAccounts) Icons.Default.Share else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.canShareBetweenAccounts) "Export Encrypted Team Package (.safetylog.enc)" else "Unlock Team Export (Pro / Enterprise)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 6. Crash Log Vault & Local Diagnostics
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Crash & Incident Vault",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Local hardware-encrypted crash logs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "$localCrashCount / 50 LOGS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Anonymous Telemetry & Crash Reporting",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Transmit unidentifiable crash stack traces to help resolve field bugs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = cloudTelemetryOptIn,
                            onCheckedChange = {
                                cloudTelemetryOptIn = it
                                CrashLogVault.setCloudTelemetryEnabled(context, it)
                                Toast.makeText(context, if (it) "Telemetry enabled" else "Telemetry disabled", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val report = CrashLogVault.exportDiagnosticReport(context, secureStorage)
                                Toast.makeText(context, "Audit report exported (${report.length()} bytes)", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Export Report", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                CrashLogVault.clearVault(context)
                                localCrashCount = CrashLogVault.getCrashCount(context)
                                Toast.makeText(context, "Incident vault cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDeficient),
                            border = BorderStroke(1.dp, StatusDeficient.copy(alpha = 0.5f))
                        ) {
                            Text("Clear Logs", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // 7. Subscriptions, Quotas & Self-Service Licensing
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFEF3C7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = SafetyAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Subscription & Quotas",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = uiState.subscriptionTier.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = StatusDeficient.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, StatusDeficient)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusDeficient, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Payment past due! Recurring features locked to Free tier until payment is delivered.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusDeficient,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else if (uiState.paymentStatus == PaymentStatus.CANCELLED) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SafetyAmber.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, SafetyAmber)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Subscription cancelled. Account is running on Free usage (5 inspections/mo).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SafetyAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = "Monthly Audit Usage: ${uiState.inspectionsThisMonth} of ${if (uiState.isProUser) "Unlimited" else "${uiState.freeLimit} free"} completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Self-Service Lifecycle Actions (Strictly gated to Owner & Account Admin)
                    if (!uiState.userRole.canManageBilling) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Billing & Financial Lockdown",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "As a ${uiState.userRole.title}, you cannot modify subscriptions, seats, or payments. Contact an Account Admin or Owner.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    } else {
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
                                onClick = { showPaywall = true },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reactivate Paid Subscription", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        } else if (uiState.subscriptionTier != SubscriptionTier.FREE && uiState.subscriptionTier != SubscriptionTier.AD_FREE) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showPaywall = true },
                                    modifier = Modifier.weight(1.2f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Manage Seats", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                }

                                OutlinedButton(
                                    onClick = { showCancelConfirmDialog = true },
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
                                onClick = { showPaywall = true },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (uiState.isAdFree) "Manage Plan & Team Seats" else "Upgrade / Unlock Functions",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }
                        }

                        // Self-Service Billing Test Controls
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                                    dashboardViewModel.markPaymentFailed()
                                    Toast.makeText(context, "Simulated payment failure: Features locked to free use", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("Fail Pay", fontSize = 11.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    dashboardViewModel.cancelSubscription()
                                    Toast.makeText(context, "Simulated cancel: Features locked to free use", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("Cancel", fontSize = 11.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    dashboardViewModel.reactivateSubscription()
                                    Toast.makeText(context, "Simulated success: Features restored", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("Restore", fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // 8. Standards & Build Information
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Regulatory Compliance Protocols",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• NFPA 10: Standard for Portable Fire Extinguishers (2022 Edition)\n" +
                               "• OSHA 29 CFR 1910.157: Portable Fire Suppression Equipment\n" +
                               "• AHA & ILCOR Guidelines: Automated External Defibrillators\n" +
                               "• Non-Repudiation: SHA-256 Digital Certificate Binding",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    TextButton(
                        onClick = { showPrivacyPolicyDialog = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "View In-App Privacy Policy & Data Disclosures",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "SafetyLog Enterprise • Version ${com.safetylog.app.BuildConfig.VERSION_NAME} (Build ${com.safetylog.app.BuildConfig.VERSION_CODE}) • Hardware Signed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Paywall Dialog
    if (showPaywall) {
        PaywallBottomSheet(
            onDismissRequest = { showPaywall = false },
            currentTier = uiState.subscriptionTier,
            paymentStatus = uiState.paymentStatus,
            userRole = uiState.userRole,
            initialEnterpriseSeats = uiState.enterpriseSeats,
            onSelectTier = { tier, seats ->
                dashboardViewModel.setSubscriptionTier(tier, seats)
                showPaywall = false
            },
            onLaunchBilling = { act, tier, seats ->
                dashboardViewModel.launchBillingFlow(act, tier, seats)
                showPaywall = false
            },
            onCancelSubscription = {
                dashboardViewModel.cancelSubscription()
            },
            onReactivateSubscription = {
                dashboardViewModel.reactivateSubscription()
            },
            onSimulatePaymentFailed = {
                dashboardViewModel.markPaymentFailed()
            },
            onSimulatePaymentSuccess = {
                dashboardViewModel.reactivateSubscription()
            },
            onUpgradeClicked = {
                dashboardViewModel.reactivateSubscription()
                showPaywall = false
            }
        )
    }

    // Cancel Subscription Confirmation Dialog
    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            icon = {
                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = StatusDeficient)
            },
            title = {
                Text("Cancel Subscription?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Are you sure you want to cancel your ${uiState.subscriptionTier.title}? Your account will immediately revert to the Free tier (5 inspections/month limit, banner ads active, cross-account sharing and company OAuth locked).",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        dashboardViewModel.cancelSubscription()
                        showCancelConfirmDialog = false
                        Toast.makeText(context, "Subscription cancelled. Account reverted to Free.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDeficient)
                ) {
                    Text("Confirm Cancellation")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCancelConfirmDialog = false }) {
                    Text("Keep Subscription")
                }
            }
        )
    }

    // Custom Sync Endpoint Dialog
    if (showEndpointDialog) {
        AlertDialog(
            onDismissRequest = { showEndpointDialog = false },
            title = { Text("Configure Sync Endpoint", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter your enterprise intranet or cloud REST sync URL:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customEndpointInput,
                        onValueChange = { customEndpointInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    teamSyncEngine.setCustomRestEndpoint(customEndpointInput)
                    showEndpointDialog = false
                    Toast.makeText(context, "Sync endpoint updated", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndpointDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Team Member Dialog
    if (showAddMemberDialog) {
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = TechBlue, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Team Member", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Add an inspector by their corporate email. They will automatically inherit your company's SSO and checklist sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = newMemberEmail,
                        onValueChange = {
                            newMemberEmail = it
                            addMemberError = null
                        },
                        label = { Text("Work Email *") },
                        placeholder = { Text("inspector@company.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        isError = addMemberError != null
                    )

                    OutlinedTextField(
                        value = newMemberName,
                        onValueChange = { newMemberName = it },
                        label = { Text("Full Name (Optional)") },
                        placeholder = { Text("e.g. Marcus Davis") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Text(
                        text = "Assign Role:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    val assignableRoles = if (uiState.userRole.canChangeAdminPermissions) {
                        listOf(UserRole.ACCOUNT_ADMIN, UserRole.MANAGER, UserRole.USER)
                    } else {
                        listOf(UserRole.MANAGER, UserRole.USER)
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        assignableRoles.forEach { role ->
                            val isSelected = newMemberRole == role
                            FilterChip(
                                selected = isSelected,
                                onClick = { newMemberRole = role },
                                label = {
                                    Text(
                                        text = role.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(role.badgeColorHex),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    addMemberError?.let { err ->
                        Text(
                            text = err,
                            color = StatusDeficient,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val res = dashboardViewModel.addTeamMember(newMemberEmail, newMemberName, newMemberRole)
                        res.onSuccess { member ->
                            showAddMemberDialog = false
                            Toast.makeText(context, "Added ${member.displayTitle} to team roster", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            addMemberError = err.message ?: "Failed to add member"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TechBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Add Member", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemberDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Team Member Dialog
    editingMember?.let { member ->
        AlertDialog(
            onDismissRequest = { editingMember = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = TechBlue, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Team Member", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editEmailInput,
                        onValueChange = {
                            editEmailInput = it
                            editMemberError = null
                        },
                        label = { Text("Work Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        isError = editMemberError != null
                    )

                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Text(
                        text = "Assigned Role:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    val editableRoles = if (uiState.userRole.canChangeAdminPermissions) {
                        listOf(UserRole.ACCOUNT_ADMIN, UserRole.MANAGER, UserRole.USER)
                    } else {
                        listOf(UserRole.MANAGER, UserRole.USER)
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        editableRoles.forEach { role ->
                            val isSelected = editRoleInput == role
                            FilterChip(
                                selected = isSelected,
                                onClick = { editRoleInput = role },
                                label = {
                                    Text(
                                        text = role.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(role.badgeColorHex),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Active Seat Status", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("Inactive users do not consume a paid seat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = editIsActiveInput,
                            onCheckedChange = { editIsActiveInput = it }
                        )
                    }

                    editMemberError?.let { err ->
                        Text(
                            text = err,
                            color = StatusDeficient,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val res = dashboardViewModel.updateTeamMember(
                            member.id,
                            editEmailInput,
                            editNameInput,
                            editRoleInput,
                            editIsActiveInput
                        )
                        res.onSuccess {
                            editingMember = null
                            Toast.makeText(context, "Updated team member details", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            editMemberError = err.message ?: "Failed to update member"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TechBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMember = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Remove Confirmation Dialog
    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = StatusDeficient, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove Team Member?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to remove ${member.displayTitle} (${member.email}) from your company team roster?\n\nThis will immediately free up 1 team seat and revoke their corporate SSO access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val res = dashboardViewModel.removeTeamMember(member.id)
                        memberToRemove = null
                        res.onSuccess {
                            Toast.makeText(context, "Removed member and freed 1 seat", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDeficient),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Remove User", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { memberToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false }
        )
    }
}

@Composable
fun SettingsThemeChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) SafetyOrange else MaterialTheme.colorScheme.outlineVariant
        ),
        color = if (isSelected) SafetyOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) SafetyOrange else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) SafetyOrange else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
