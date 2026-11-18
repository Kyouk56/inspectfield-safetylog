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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.safetylog.app.ui.components.SafetyBannerAd
import com.safetylog.app.ui.dashboard.DashboardViewModel
import com.safetylog.app.ui.theme.*
import com.safetylog.app.util.CrashLogVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val uiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var showPaywall by remember { mutableStateOf(false) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var showEndpointDialog by remember { mutableStateOf(false) }
    // These originate from SharedPreferences / on-disk crash logs. Load them off the
    // composition thread so Keystore/prefs I/O never stalls a frame.
    var customEndpointInput by remember { mutableStateOf("") }
    var cloudTelemetryOptIn by remember { mutableStateOf(false) }
    var localCrashCount by remember { mutableIntStateOf(0) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(teamSyncEngine, context) {
        customEndpointInput = withContext(Dispatchers.IO) { teamSyncEngine.getCustomRestEndpoint() }
        cloudTelemetryOptIn = withContext(Dispatchers.IO) { CrashLogVault.isCloudTelemetryEnabled(context) }
        localCrashCount = withContext(Dispatchers.IO) { CrashLogVault.getCrashCount(context) }
    }

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
    // Drafts are held in plain `remember` (no key) so unrelated Settings state
    // updates cannot discard in-progress edits. Sync from the persisted config
    // only when it genuinely changes and the form is untouched.
    var companyNameInput by remember { mutableStateOf(uiState.companyOAuthConfig.companyName) }
    var corporateDomainInput by remember { mutableStateOf(uiState.companyOAuthConfig.corporateDomain) }
    var selectedProvider by remember { mutableStateOf(uiState.companyOAuthConfig.provider) }
    var tenantIdInput by remember { mutableStateOf(uiState.companyOAuthConfig.tenantIdOrDomain) }
    var clientIdInput by remember { mutableStateOf(uiState.companyOAuthConfig.clientId) }
    var enforceDomainInput by remember { mutableStateOf(uiState.companyOAuthConfig.enforceDomainRestriction) }
    var webhookUrlInput by remember { mutableStateOf(uiState.companyOAuthConfig.webhookUrl) }
    var webhookSecretInput by remember { mutableStateOf(uiState.companyOAuthConfig.webhookSecret) }
    var webhookAutoInput by remember { mutableStateOf(uiState.companyOAuthConfig.webhookAutoDispatch) }
    var cloudDriveUriInput by remember { mutableStateOf(uiState.companyOAuthConfig.cloudDriveFolderUri) }
    var cloudDriveNameInput by remember { mutableStateOf(uiState.companyOAuthConfig.cloudDriveFolderName) }
    var cloudDriveAutoInput by remember { mutableStateOf(uiState.companyOAuthConfig.cloudDriveAutoBackup) }
    var isTestingWebhook by remember { mutableStateOf(false) }
    var isBackingUpDrive by remember { mutableStateOf(false) }

    var lastSyncedOAuthConfig by remember { mutableStateOf(uiState.companyOAuthConfig) }
    LaunchedEffect(uiState.companyOAuthConfig) {
        val newConfig = uiState.companyOAuthConfig
        if (newConfig == lastSyncedOAuthConfig) return@LaunchedEffect
        val untouched = companyNameInput == lastSyncedOAuthConfig.companyName &&
            corporateDomainInput == lastSyncedOAuthConfig.corporateDomain &&
            selectedProvider == lastSyncedOAuthConfig.provider &&
            tenantIdInput == lastSyncedOAuthConfig.tenantIdOrDomain &&
            clientIdInput == lastSyncedOAuthConfig.clientId &&
            enforceDomainInput == lastSyncedOAuthConfig.enforceDomainRestriction &&
            webhookUrlInput == lastSyncedOAuthConfig.webhookUrl &&
            webhookSecretInput == lastSyncedOAuthConfig.webhookSecret &&
            webhookAutoInput == lastSyncedOAuthConfig.webhookAutoDispatch &&
            cloudDriveUriInput == lastSyncedOAuthConfig.cloudDriveFolderUri &&
            cloudDriveNameInput == lastSyncedOAuthConfig.cloudDriveFolderName &&
            cloudDriveAutoInput == lastSyncedOAuthConfig.cloudDriveAutoBackup
        if (untouched) {
            companyNameInput = newConfig.companyName
            corporateDomainInput = newConfig.corporateDomain
            selectedProvider = newConfig.provider
            tenantIdInput = newConfig.tenantIdOrDomain
            clientIdInput = newConfig.clientId
            enforceDomainInput = newConfig.enforceDomainRestriction
            webhookUrlInput = newConfig.webhookUrl
            webhookSecretInput = newConfig.webhookSecret
            webhookAutoInput = newConfig.webhookAutoDispatch
            cloudDriveUriInput = newConfig.cloudDriveFolderUri
            cloudDriveNameInput = newConfig.cloudDriveFolderName
            cloudDriveAutoInput = newConfig.cloudDriveAutoBackup
        }
        lastSyncedOAuthConfig = newConfig
    }

    // D-14: fields from the form draft are merged into any config saved from another
    // settings card, so toggling a webhook/drive switch cannot silently discard unsaved
    // OAuth edits. This draft is derived from the individual fields above.
    val oauthDraft = uiState.companyOAuthConfig.copy(
        companyName = companyNameInput,
        corporateDomain = corporateDomainInput,
        provider = selectedProvider,
        tenantIdOrDomain = tenantIdInput,
        clientId = clientIdInput,
        enforceDomainRestriction = enforceDomainInput,
        webhookUrl = webhookUrlInput,
        webhookSecret = webhookSecretInput,
        webhookAutoDispatch = webhookAutoInput,
        cloudDriveFolderUri = cloudDriveUriInput,
        cloudDriveFolderName = cloudDriveNameInput,
        cloudDriveAutoBackup = cloudDriveAutoInput
    )

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

            // Merge into the live form draft so unsaved OAuth/webhook edits are preserved.
            val updatedConfig = oauthDraft.copy(
                cloudDriveFolderUri = uri.toString(),
                cloudDriveFolderName = folderName
            )
            dashboardViewModel.saveCompanyOAuthConfig(updatedConfig)
            Toast.makeText(context, "Cloud drive folder linked: $folderName", Toast.LENGTH_SHORT).show()
        }
    }

    var packagePassphrase by remember { mutableStateOf("") }
    val importPackageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch(Dispatchers.IO) {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (content == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Could not read the selected package", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val result = teamSyncEngine.importEncryptedTeamPackage(content, packagePassphrase.ifBlank { null })
            withContext(Dispatchers.Main) {
                val message = when (result) {
                    is com.safetylog.app.data.sync.SyncResult.Success -> result.message
                    is com.safetylog.app.data.sync.SyncResult.Error -> result.error
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
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
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Display, Regulatory Vault & Developer Tools",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
        },
        bottomBar = {
            if (!uiState.isAdFree) {
                SafetyBannerAd(
                    isAdFree = uiState.isAdFree,
                    onRemoveAdsClicked = { showPaywall = true }
                )
            }
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

                        val isDelinquentRestricted = uiState.subscriptionTier == SubscriptionTier.ENTERPRISE && !uiState.userRole.canManageBilling
                        if (!isDelinquentRestricted) {
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
            SettingsAppearanceCard(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )

            // 2. Inspection Due Reminders & Notification Alarms
            SettingsInspectionRemindersCard(
                hasNotificationPermission = hasNotificationPermission,
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                dashboardViewModel = dashboardViewModel
            )

            // 3. Admin & Developer Sandbox
            SettingsDeveloperSandboxCard(
                uiState = uiState,
                themeMode = themeMode,
                dashboardViewModel = dashboardViewModel
            )

            // 3. Security, Hardware Keystore & Identity Protection
            SettingsIdentityVaultCard(
                uiState = uiState,
                dashboardViewModel = dashboardViewModel,
                onNavigateToAuth = onNavigateToAuth,
                onShowPrivacyPolicy = { showPrivacyPolicyDialog = true }
            )

            // 4. Company-Specific OAuth & Enterprise Identity Hub
            SettingsCompanyOAuthCard(
                uiState = uiState,
                dashboardViewModel = dashboardViewModel,
                companyNameInput = companyNameInput,
                onCompanyNameChange = { companyNameInput = it },
                corporateDomainInput = corporateDomainInput,
                onCorporateDomainChange = { corporateDomainInput = it },
                selectedProvider = selectedProvider,
                onProviderChange = { selectedProvider = it },
                tenantIdInput = tenantIdInput,
                onTenantIdChange = { tenantIdInput = it },
                clientIdInput = clientIdInput,
                onClientIdChange = { clientIdInput = it },
                enforceDomainInput = enforceDomainInput,
                onEnforceDomainChange = { enforceDomainInput = it },
                webhookUrlInput = webhookUrlInput,
                webhookSecretInput = webhookSecretInput,
                webhookAutoInput = webhookAutoInput,
                cloudDriveUriInput = cloudDriveUriInput,
                cloudDriveNameInput = cloudDriveNameInput,
                cloudDriveAutoInput = cloudDriveAutoInput,
                onNavigateToAuth = onNavigateToAuth,
                onShowPaywall = { showPaywall = true }
            )

            // 4B. Enterprise Team Members & Seat Roster (Admin Access)
            SettingsTeamRosterCard(
                uiState = uiState,
                teamSyncEngine = teamSyncEngine,
                onShowPaywall = { showPaywall = true },
                onOpenAddMemberDialog = {
                    newMemberEmail = ""
                    newMemberName = ""
                    newMemberRole = UserRole.USER
                    addMemberError = null
                    showAddMemberDialog = true
                },
                onEditMember = { member ->
                    editingMember = member
                    editEmailInput = member.email
                    editNameInput = member.name
                    editRoleInput = member.role
                    editIsActiveInput = member.isActive
                    editMemberError = null
                },
                onRemoveMember = { member ->
                    memberToRemove = member
                }
            )

            // 5. Team Synchronization & Cloud Interop
            SettingsTeamSyncCard(
                uiState = uiState,
                dashboardViewModel = dashboardViewModel,
                teamSyncEngine = teamSyncEngine,
                oauthDraft = oauthDraft,
                onSaveOAuthConfig = { dashboardViewModel.saveCompanyOAuthConfig(it) },
                cloudDriveUriInput = cloudDriveUriInput,
                cloudDriveNameInput = cloudDriveNameInput,
                cloudDriveAutoInput = cloudDriveAutoInput,
                onCloudDriveAutoChange = { cloudDriveAutoInput = it },
                isBackingUpDrive = isBackingUpDrive,
                onIsBackingUpDriveChange = { isBackingUpDrive = it },
                webhookUrlInput = webhookUrlInput,
                onWebhookUrlChange = { webhookUrlInput = it },
                webhookSecretInput = webhookSecretInput,
                onWebhookSecretChange = { webhookSecretInput = it },
                webhookAutoInput = webhookAutoInput,
                onWebhookAutoChange = { webhookAutoInput = it },
                isTestingWebhook = isTestingWebhook,
                onIsTestingWebhookChange = { isTestingWebhook = it },
                onLaunchDocumentTree = { openDocumentTreeLauncher.launch(null) },
                packagePassphrase = packagePassphrase,
                onPackagePassphraseChange = { packagePassphrase = it },
                onImportPackage = { importPackageLauncher.launch("*/*") },
                onShowEndpointDialog = { showEndpointDialog = true },
                onShowPaywall = { showPaywall = true }
            )

            // 6. Crash Log Vault & Local Diagnostics
            SettingsCrashLogVaultCard(
                localCrashCount = localCrashCount,
                onLocalCrashCountChange = { localCrashCount = it },
                cloudTelemetryOptIn = cloudTelemetryOptIn,
                onCloudTelemetryOptInChange = { cloudTelemetryOptIn = it },
                secureStorage = secureStorage
            )

            // 7. Subscriptions, Quotas & Self-Service Licensing
            SettingsSubscriptionQuotasCard(
                uiState = uiState,
                dashboardViewModel = dashboardViewModel,
                onShowPaywall = { showPaywall = true },
                onShowCancelConfirmDialog = { showCancelConfirmDialog = true }
            )

            // 8. Standards & Build Information
            SettingsComplianceAndAboutCard(
                onShowPrivacyPolicy = { showPrivacyPolicyDialog = true }
            )

            Spacer(modifier = Modifier.height(64.dp))
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

    // Settings Action & Team Member Dialogs
    SettingsActionDialogs(
        uiState = uiState,
        dashboardViewModel = dashboardViewModel,
        teamSyncEngine = teamSyncEngine,
        showCancelConfirmDialog = showCancelConfirmDialog,
        onDismissCancelConfirmDialog = { showCancelConfirmDialog = false },
        showEndpointDialog = showEndpointDialog,
        onDismissEndpointDialog = { showEndpointDialog = false },
        customEndpointInput = customEndpointInput,
        onCustomEndpointChange = { customEndpointInput = it },
        showAddMemberDialog = showAddMemberDialog,
        onDismissAddMemberDialog = { showAddMemberDialog = false },
        newMemberEmail = newMemberEmail,
        onNewMemberEmailChange = { newMemberEmail = it },
        newMemberName = newMemberName,
        onNewMemberNameChange = { newMemberName = it },
        newMemberRole = newMemberRole,
        onNewMemberRoleChange = { newMemberRole = it },
        addMemberError = addMemberError,
        onAddMemberErrorChange = { addMemberError = it },
        editingMember = editingMember,
        onDismissEditingMember = { editingMember = null },
        editEmailInput = editEmailInput,
        onEditEmailChange = { editEmailInput = it },
        editNameInput = editNameInput,
        onEditNameChange = { editNameInput = it },
        editRoleInput = editRoleInput,
        onEditRoleChange = { editRoleInput = it },
        editIsActiveInput = editIsActiveInput,
        onEditIsActiveChange = { editIsActiveInput = it },
        editMemberError = editMemberError,
        onEditMemberErrorChange = { editMemberError = it },
        memberToRemove = memberToRemove,
        onDismissMemberToRemove = { memberToRemove = null }
    )

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false }
        )
    }
}
