package com.safetylog.app.ui.auth

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safetylog.app.data.auth.UserRole
import com.safetylog.app.ui.components.PaywallBottomSheet
import com.safetylog.app.ui.components.PrivacyPolicyDialog
import com.safetylog.app.ui.components.SafetyBannerAd
import com.safetylog.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showCorporateSsoInput by remember { mutableStateOf(false) }
    var corporateDomain by remember { mutableStateOf("") }
    var corporateOfficerName by remember { mutableStateOf("") }
    var showPaywall by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showClientIdDialog by remember { mutableStateOf(false) }
    var clientIdDraft by remember(uiState.googleClientId) { mutableStateOf(uiState.googleClientId ?: "") }
    var showEmailSignIn by remember { mutableStateOf(false) }
    var emailInputName by remember { mutableStateOf("") }
    var emailInputAddress by remember { mutableStateOf("") }
    var emailInputPassword by remember { mutableStateOf("") }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Security & OAuth Vault",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (uiState.isAuthenticated) StatusPassedLight.copy(alpha = 0.2f) else SafetyOrangeLight.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val statusText = if (uiState.isAuthenticated) {
                                            "Identity Vault is verified with ${uiState.currentUser?.provider?.displayName ?: "OAuth"} (${uiState.currentUser?.email})"
                                        } else {
                                            "Identity Vault is unlinked. Sign in below with Google or Corporate SSO to verify."
                                        }
                                        Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Text(
                                    text = if (uiState.isAuthenticated) "VERIFIED" else "UNLINKED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.isAuthenticated) StatusPassed else SafetyOrange,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Hardware-Backed AES-256-GCM Session Protection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. Hardware Keystore Cryptographic Telemetry Card
            KeystoreDiagnosticsCard(
                uiState = uiState,
                onRunDiagnostics = { viewModel.runKeystoreDiagnostics() }
            )

            // 2. Active Session Card (If Logged In)
            val currentUser = uiState.currentUser
            if (uiState.isAuthenticated && currentUser != null) {
                ActiveSessionCard(
                    uiState = uiState,
                    user = currentUser,
                    dateFormat = dateFormat,
                    onSyncProfile = { viewModel.syncInspectorProfile() },
                    onSignOut = { viewModel.signOut() },
                    onConfigureClientId = {
                        clientIdDraft = uiState.googleClientId ?: ""
                        showClientIdDialog = true
                    }
                )
            } else {
                // 3. Sign In Hero Card (When Not Authenticated)
                SignInHeroCard(
                    uiState = uiState,
                    context = context,
                    showEmailSignIn = showEmailSignIn,
                    onToggleEmailSignIn = { showEmailSignIn = !showEmailSignIn },
                    emailInputName = emailInputName,
                    onEmailNameChange = { emailInputName = it },
                    emailInputAddress = emailInputAddress,
                    onEmailAddressChange = { emailInputAddress = it },
                    emailInputPassword = emailInputPassword,
                    onEmailPasswordChange = { emailInputPassword = it },
                    showCorporateSsoInput = showCorporateSsoInput,
                    onToggleCorporateSsoInput = { showCorporateSsoInput = !showCorporateSsoInput },
                    corporateDomain = corporateDomain,
                    onCorporateDomainChange = { corporateDomain = it },
                    corporateOfficerName = corporateOfficerName,
                    onCorporateOfficerNameChange = { corporateOfficerName = it },
                    onConfigureClientId = {
                        clientIdDraft = uiState.googleClientId ?: ""
                        showClientIdDialog = true
                    },
                    onShowPaywall = { showPaywall = true },
                    viewModel = viewModel
                )
            }

            // 4. Compliance & Privacy Assurance Banner
            CompliancePrivacyBanner(
                onShowPrivacyPolicy = { showPrivacyPolicyDialog = true }
            )

            Spacer(modifier = Modifier.height(64.dp))
        }
    }

    if (showPaywall) {
        PaywallBottomSheet(
            onDismissRequest = { showPaywall = false },
            userRole = uiState.currentUser?.role ?: UserRole.USER,
            currentTier = uiState.subscriptionTier,
            paymentStatus = uiState.paymentStatus,
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

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false }
        )
    }

    if (showClientIdDialog) {
        GoogleOAuthClientIdDialog(
            uiState = uiState,
            context = context,
            clientIdDraft = clientIdDraft,
            onClientIdDraftChange = { clientIdDraft = it },
            onSetSandboxMode = { viewModel.setSandboxMode(it) },
            onSignInSandbox = { viewModel.signInSandbox() },
            onSaveClientId = { viewModel.saveCustomGoogleClientId(it) },
            onDismiss = { showClientIdDialog = false }
        )
    }
}

@Composable
internal fun CompliancePrivacyBanner(
    onShowPrivacyPolicy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = SafetyOrange,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Zero-Cloud Token Transmission",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "OAuth bearer keys are ciphered on-chip using Android Hardware Keystore. Tokens are never exposed in logs, crash dumps, or unencrypted backups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = onShowPrivacyPolicy,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        Icons.Default.Policy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "View Privacy Policy & Data Disclosures",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
