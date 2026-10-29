package com.safetylog.app.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safetylog.app.data.auth.AuthProvider
import com.safetylog.app.data.auth.AuthUser
import com.safetylog.app.data.auth.UserRole
import com.safetylog.app.ui.components.PaywallBottomSheet
import com.safetylog.app.ui.components.PrivacyPolicyDialog
import com.safetylog.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showCorporateSsoInput by remember { mutableStateOf(false) }
    var corporateDomain by remember { mutableStateOf("") }
    var corporateOfficerName by remember { mutableStateOf("") }
    var showPaywall by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showClientIdDialog by remember { mutableStateOf(false) }
    var clientIdDraft by remember(uiState.googleClientId) { mutableStateOf(uiState.googleClientId ?: "") }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
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
                                style = MaterialTheme.typography.titleMedium
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
                                        android.widget.Toast.makeText(context, statusText, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Text(
                                    text = if (uiState.isAuthenticated) "VERIFIED" else "UNLINKED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.isAuthenticated) StatusPassed else SafetyOrange,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Hardware-Backed AES-256-GCM Session Protection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(StatusPassedLight.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EnhancedEncryption,
                                    contentDescription = null,
                                    tint = StatusPassed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Android Keystore Security",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = uiState.keystoreDiagnostics?.securityLevel ?: "Hardware TEE / MasterKey Enclave",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            color = StatusPassed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, StatusPassed.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(StatusPassed)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.keystoreDiagnostics?.isFunctional == true) "AES-256 ACTIVE" else "INITIALIZING",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = StatusPassed
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Cryptographic specs grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SecurityMetricItem(
                            label = "Cipher Engine",
                            value = "AES-256-GCM",
                            subValue = "Hardware Authenticated"
                        )
                        SecurityMetricItem(
                            label = "Key Protection",
                            value = uiState.keystoreDiagnostics?.securityLevel?.split(" ")?.firstOrNull() ?: "TEE / Hardware",
                            subValue = "Non-Exportable"
                        )
                        SecurityMetricItem(
                            label = "Tamper Audit",
                            value = "SHA-256 Checked",
                            subValue = "Constant-Time"
                        )
                    }

                    // Diagnostic results box if diagnostics available
                    uiState.keystoreDiagnostics?.let { diag ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = StatusPassed,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (diag.isFunctional) "Hardware Self-Test: PASSED (100% Match)" else "Self-Test Alert",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (diag.isFunctional) StatusPassed else StatusDeficient
                                        )
                                    }
                                    Text(
                                        text = "${diag.latencyMs} ms",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Master: ${diag.masterKeyAlias.take(24)}... • Direct: ${diag.directCipherAlias}",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Diagnostic run button
                    OutlinedButton(
                        onClick = { viewModel.runKeystoreDiagnostics() },
                        enabled = !uiState.isRunningDiagnostics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (uiState.isRunningDiagnostics) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verifying Hardware Cryptography...", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Run Keystore Diagnostic Self-Test", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. Active Session Card (If Logged In)
            if (uiState.isAuthenticated && uiState.currentUser != null) {
                val user = uiState.currentUser!!
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, Color(user.provider.brandColorHex).copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(user.provider.brandColorHex),
                                                SafetyOrange
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.initials,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 20.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Verified",
                                        tint = StatusPassed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = user.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Surface(
                                    color = Color(user.provider.brandColorHex).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = user.provider.displayName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(user.provider.brandColorHex),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Session details
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SessionDetailRow(
                                icon = Icons.Default.AccessTime,
                                label = "Last Authentication",
                                value = dateFormat.format(Date(user.lastLoginEpochMs.takeIf { it > 0 } ?: System.currentTimeMillis()))
                            )
                            SessionDetailRow(
                                icon = Icons.Default.HourglassTop,
                                label = "Session Security Expiry",
                                value = dateFormat.format(Date(user.tokenExpiryEpochMs))
                            )
                            SessionDetailRow(
                                icon = Icons.Default.Fingerprint,
                                label = "Encrypted Token Fingerprint",
                                value = user.idToken?.take(18)?.let { "$it...[AES-256 Encrypted]" } ?: "Active Vault Token"
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Actions: Sync Profile & Sign Out
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.syncInspectorProfile() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Credentials", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.signOut() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusDeficient)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Secure Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        val serverClientId = uiState.googleClientId ?: androidx.compose.ui.res.stringResource(com.safetylog.app.R.string.google_web_client_id).takeIf { it.isNotBlank() }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (!serverClientId.isNullOrBlank()) "Google OAuth: Live Cloud Mode" else "Google OAuth: Local Identity Mode",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (!serverClientId.isNullOrBlank()) Color(0xFF059669) else SafetyOrange,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(
                                onClick = {
                                    clientIdDraft = uiState.googleClientId ?: ""
                                    showClientIdDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (!serverClientId.isNullOrBlank()) "Edit Client ID" else "Configure Client ID",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                // 3. Sign In Hero Card (When Not Authenticated)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "Sign In with Verified Account",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Authenticate with your Google, Microsoft, or corporate SSO account to verify your inspection audit logs with cryptographic non-repudiation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Primary Action: Sign in with Google
                        val serverClientId = uiState.googleClientId ?: androidx.compose.ui.res.stringResource(com.safetylog.app.R.string.google_web_client_id).takeIf { it.isNotBlank() }
                        GoogleSignInButton(
                            isLoading = uiState.isLoading,
                            onClick = {
                                viewModel.signInWithGoogle(context, serverClientId)
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (uiState.isSandboxMode) SafetyOrange else if (!serverClientId.isNullOrBlank()) Color(0xFF10B981) else SafetyOrange)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.isSandboxMode) "Offline Sandbox Active" else if (!serverClientId.isNullOrBlank()) "Live Google OAuth Ready" else "Standard Local Authentication",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.isSandboxMode) SafetyOrange else if (!serverClientId.isNullOrBlank()) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(
                                onClick = {
                                    clientIdDraft = uiState.googleClientId ?: ""
                                    showClientIdDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (!serverClientId.isNullOrBlank()) "Edit Client ID" else "Configure Client ID",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Sandbox quick sign-in button for instant test access
                        OutlinedButton(
                            onClick = { viewModel.signInSandbox() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Quick Demo Sign-In (Sandbox Mode)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Secondary Action: Sign in with Microsoft
                        MicrosoftSignInButton(
                            isLoading = uiState.isLoading,
                            onClick = { viewModel.signInWithMicrosoft() }
                        )

                        // Tertiary Action: Corporate SAML/OIDC SSO (Gated to Enterprise Plan)
                        if (!uiState.canUseCompanyOAuth) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = SafetyOrange,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Corporate SAML / OIDC Domain",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }

                                        Surface(
                                            color = SafetyOrange.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "ENTERPRISE",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SafetyOrange,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = if (uiState.paymentStatus == com.safetylog.app.data.auth.PaymentStatus.PAYMENT_FAILED) {
                                            "Payment Overdue: Enterprise subscription payment failed. Company OAuth is locked until payment is resolved."
                                        } else {
                                            "Company-specific OAuth identity integration and SAML 2.0 / Entra / Okta domain locking are exclusive to Enterprise subscribers ($0.50/seat/mo)."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Button(
                                        onClick = { showPaywall = true },
                                        modifier = Modifier.fillMaxWidth().height(40.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                                    ) {
                                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(16.dp), tint = SafetyAmber)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (uiState.paymentStatus == com.safetylog.app.data.auth.PaymentStatus.PAYMENT_FAILED) "Resolve Billing & Unlock SSO" else "Unlock Enterprise OAuth ($0.50/seat)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        } else {
                            // Active Enterprise Plan: Full Access to Company SSO
                            OutlinedButton(
                                onClick = { showCorporateSsoInput = !showCorporateSsoInput },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CorporateFare,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (showCorporateSsoInput) "Hide SSO Form" else "Sign In with Corporate SAML / OIDC Domain",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp
                                )
                            }

                            // Expandable Domain Input Form
                            AnimatedVisibility(visible = showCorporateSsoInput) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Corporate Single Sign-On (SSO)",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        if (uiState.companyOAuthConfig.isConfigured && corporateDomain.isBlank()) {
                                            LaunchedEffect(uiState.companyOAuthConfig) {
                                                corporateDomain = uiState.companyOAuthConfig.corporateDomain
                                            }
                                        }
                                        OutlinedTextField(
                                            value = corporateDomain,
                                            onValueChange = { corporateDomain = it },
                                            label = { Text("Corporate Domain") },
                                            placeholder = { Text(uiState.companyOAuthConfig.corporateDomain.ifBlank { "e.g. acmefire.com" }) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        OutlinedTextField(
                                            value = corporateOfficerName,
                                            onValueChange = { corporateOfficerName = it },
                                            label = { Text("Officer Full Name") },
                                            placeholder = { Text("e.g. John Doe") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        Button(
                                            onClick = {
                                                viewModel.signInWithCorporateSso(corporateDomain, corporateOfficerName)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                                        ) {
                                            Text("Connect Corporate SSO Vault", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Compliance & Privacy Assurance Banner
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
                            onClick = { showPrivacyPolicyDialog = true },
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

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showPaywall) {
        PaywallBottomSheet(
            onDismissRequest = { showPaywall = false },
            userRole = uiState.currentUser?.role ?: UserRole.USER,
            currentTier = uiState.subscriptionTier,
            paymentStatus = uiState.paymentStatus,
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
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showClientIdDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Google Cloud OAuth Setup", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "To enable official Google Sign-In with real Google accounts on your device, create an OAuth 2.0 Web Client ID in Google Cloud Console and paste it below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = clientIdDraft,
                        onValueChange = { clientIdDraft = it },
                        label = { Text("Web Client ID (.apps.googleusercontent.com)") },
                        placeholder = { Text("e.g. 123456789-abc.apps.googleusercontent.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Google Cloud Registration Parameters:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("Package: com.safetylog.inspection.app", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Release SHA-1: 62:FC:B5...EE:21", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("62:FC:B5:63:A9:32:68:10:95:77:C7:78:6D:01:8F:34:14:74:EE:21"))
                                        android.widget.Toast.makeText(context, "Copied Release SHA-1", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Copy", fontSize = 11.sp)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Debug SHA-1: 0E:58:7D...51:34", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("0E:58:7D:5D:00:F0:17:83:73:A2:72:C4:25:1D:29:AC:DD:CD:51:34"))
                                        android.widget.Toast.makeText(context, "Copied Debug SHA-1", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Copy", fontSize = 11.sp)
                                }
                            }
                            Text(
                                text = "Important for Play Store / Testers: If installed via Play Store track, copy the SHA-1 from Google Play Console > App Integrity > Play App Signing and add it to Google Cloud Console.",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Mode Switch: Live vs Sandbox
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.isSandboxMode) SafetyOrange.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (uiState.isSandboxMode) SafetyOrange.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Force Emulator Sandbox Mode",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Bypasses Google Play Services and simulates a verified local audit session for testing.",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.isSandboxMode,
                                onCheckedChange = { viewModel.setSandboxMode(it) }
                            )
                        }
                    }

                    // Direct sandbox login button inside dialog
                    Button(
                        onClick = {
                            viewModel.signInSandbox()
                            showClientIdDialog = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign In Now (Offline Sandbox)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "When in Sandbox mode or when offline, the app safely signs in using your active local Profile name and email.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCustomGoogleClientId(clientIdDraft.trim())
                        showClientIdDialog = false
                    }
                ) {
                    Text("Save Client ID")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClientIdDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SecurityMetricItem(
    label: String,
    value: String,
    subValue: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subValue,
            style = MaterialTheme.typography.labelSmall,
            color = StatusPassed,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SessionDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun GoogleSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFFFFF),
        border = BorderStroke(1.dp, Color(0xFFDADCE0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF4285F4)
                )
            } else {
                GoogleLogoIcon(modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Sign in with Google",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color(0xFF1F1F1F),
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}

@Composable
fun MicrosoftSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(1.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF2F2F2F),
        border = BorderStroke(1.dp, Color(0xFF4A4A4A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            MicrosoftLogoIcon(modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Sign in with Microsoft",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color.White,
                letterSpacing = 0.2.sp
            )
        }
    }
}

/**
 * Precision vector drawing of Google 4-color 'G' logo
 */
@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 4 Official Google brand colors
        val blue = Color(0xFF4285F4)
        val green = Color(0xFF34A853)
        val yellow = Color(0xFFFBBC05)
        val red = Color(0xFFEA4335)

        // Draw iconic 4-color G arcs & bar
        // Right blue bar
        drawRect(
            color = blue,
            topLeft = Offset(w * 0.48f, h * 0.40f),
            size = androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.20f)
        )

        // Draw stylized Google G segments
        val stroke = w * 0.20f
        val arcSize = androidx.compose.ui.geometry.Size(w - stroke, h - stroke)
        val arcTopLeft = Offset(stroke / 2, stroke / 2)

        // Blue Arc (top right & bottom right transition)
        drawArc(
            color = blue,
            startAngle = -45f,
            sweepAngle = 75f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )

        // Green Arc (bottom)
        drawArc(
            color = green,
            startAngle = 30f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )

        // Yellow Arc (bottom left)
        drawArc(
            color = yellow,
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )

        // Red Arc (top)
        drawArc(
            color = red,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
    }
}

/**
 * Precision drawing of 4-tile Microsoft logo
 */
@Composable
fun MicrosoftLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.width
        val tileSize = s * 0.44f
        val gap = s * 0.12f

        // Red tile
        drawRect(Color(0xFFF25022), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(tileSize, tileSize))
        // Green tile
        drawRect(Color(0xFF7FBA00), topLeft = Offset(tileSize + gap, 0f), size = androidx.compose.ui.geometry.Size(tileSize, tileSize))
        // Blue tile
        drawRect(Color(0xFF00A4EF), topLeft = Offset(0f, tileSize + gap), size = androidx.compose.ui.geometry.Size(tileSize, tileSize))
        // Yellow tile
        drawRect(Color(0xFFFFB900), topLeft = Offset(tileSize + gap, tileSize + gap), size = androidx.compose.ui.geometry.Size(tileSize, tileSize))
    }
}
