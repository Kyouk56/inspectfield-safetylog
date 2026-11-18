package com.safetylog.app.ui.auth

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safetylog.app.R
import com.safetylog.app.data.auth.AuthUser
import com.safetylog.app.data.auth.PaymentStatus
import com.safetylog.app.ui.theme.*
import com.safetylog.app.util.TestControls
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun ActiveSessionCard(
    uiState: AuthUiState,
    user: AuthUser,
    dateFormat: SimpleDateFormat,
    onSyncProfile: () -> Unit,
    onSignOut: () -> Unit,
    onConfigureClientId: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
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
                        .background(Brush.linearGradient(listOf(Color(user.provider.brandColorHex), SafetyOrange))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = user.initials, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = user.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Verified", tint = StatusPassed, modifier = Modifier.size(16.dp))
                    }
                    Text(text = user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(color = Color(user.provider.brandColorHex).copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
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
                SessionDetailRow(Icons.Default.AccessTime, "Last Authentication", dateFormat.format(Date(user.lastLoginEpochMs.takeIf { it > 0 } ?: System.currentTimeMillis())))
                SessionDetailRow(Icons.Default.HourglassTop, "Session Security Expiry", dateFormat.format(Date(user.tokenExpiryEpochMs)))
                // Never display raw ID-token material: it is a bearer credential and is
                // trivially capturable via screenshot/screen recording.
                SessionDetailRow(Icons.Default.Fingerprint, "Encrypted Token Status", "Vault Token Active (AES-256)")
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Actions: Sync Profile & Sign Out
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onSyncProfile, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync Credentials", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onSignOut,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDeficient)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Secure Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            val serverClientId = uiState.googleClientId ?: stringResource(R.string.google_web_client_id).takeIf { it.isNotBlank() }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (!serverClientId.isNullOrBlank()) "Google OAuth: Live Cloud Mode" else "Google OAuth: Local Identity Mode",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (!serverClientId.isNullOrBlank()) Color(0xFF059669) else SafetyOrange,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                TextButton(onClick = onConfigureClientId, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (!serverClientId.isNullOrBlank()) "Edit Client ID" else "Configure Client ID",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
internal fun SignInHeroCard(
    uiState: AuthUiState,
    context: Context,
    showEmailSignIn: Boolean,
    onToggleEmailSignIn: () -> Unit,
    emailInputName: String,
    onEmailNameChange: (String) -> Unit,
    emailInputAddress: String,
    onEmailAddressChange: (String) -> Unit,
    emailInputPassword: String,
    onEmailPasswordChange: (String) -> Unit,
    showCorporateSsoInput: Boolean,
    onToggleCorporateSsoInput: () -> Unit,
    corporateDomain: String,
    onCorporateDomainChange: (String) -> Unit,
    corporateOfficerName: String,
    onCorporateOfficerNameChange: (String) -> Unit,
    onConfigureClientId: () -> Unit,
    onShowPaywall: () -> Unit,
    viewModel: AuthViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(16.dp)),
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
            val serverClientId = uiState.googleClientId ?: stringResource(R.string.google_web_client_id).takeIf { it.isNotBlank() }
            GoogleSignInButton(
                isLoading = uiState.isLoading,
                onClick = { viewModel.signInWithGoogle(context, serverClientId) }
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
                        text = if (uiState.isSandboxMode) "Offline Sandbox Active" else if (!serverClientId.isNullOrBlank()) "Live Google OAuth Ready" else "Google Sign-In not configured",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.isSandboxMode) SafetyOrange else if (!serverClientId.isNullOrBlank()) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                }
                TextButton(
                    onClick = onConfigureClientId,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (!serverClientId.isNullOrBlank()) "Edit Client ID" else "Configure Client ID",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Work Email / Inspector Account Sign In Option
            OutlinedButton(
                onClick = onToggleEmailSignIn,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp), tint = SafetyOrange)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (showEmailSignIn) "Hide Email Form" else "Sign In with Inspector Work Email",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp
                )
            }

            AnimatedVisibility(visible = showEmailSignIn) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Inspector Work Credentials",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                        OutlinedTextField(
                            value = emailInputName,
                            onValueChange = onEmailNameChange,
                            label = { Text("Full Name") },
                            placeholder = { Text("e.g. John Desrosiers") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = emailInputAddress,
                            onValueChange = onEmailAddressChange,
                            label = { Text("Work Email Address") },
                            placeholder = { Text("e.g. jdesrosiers88@gmail.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = emailInputPassword,
                            onValueChange = onEmailPasswordChange,
                            label = { Text("Inspector Password") },
                            placeholder = { Text("Required with server authorization") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Text(
                            text = "Passwords are verified by the authorization server; a name and email alone cannot grant privileges.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        Button(
                            onClick = {
                                viewModel.signInWithEmail(
                                    name = emailInputName.ifBlank { "Certified Safety Inspector" },
                                    email = emailInputAddress.ifBlank { "inspector@safetylog.app" },
                                    password = emailInputPassword
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign In & Authenticate Vault", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Sandbox quick sign-in button for instant test access (test builds only).
            if (TestControls.isEnabled) {
                OutlinedButton(
                    onClick = { viewModel.signInSandbox() },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Quick Demo Sign-In (Sandbox Mode)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Secondary Action: Sign in with Microsoft
            MicrosoftSignInButton(
                isLoading = uiState.isLoading,
                onClick = { viewModel.signInWithMicrosoft() }
            )

            // Tertiary Action: Corporate SAML/OIDC SSO (Gated to Enterprise Plan)
            CorporateSsoSection(
                uiState = uiState,
                showCorporateSsoInput = showCorporateSsoInput,
                onToggleCorporateSsoInput = onToggleCorporateSsoInput,
                corporateDomain = corporateDomain,
                onCorporateDomainChange = onCorporateDomainChange,
                corporateOfficerName = corporateOfficerName,
                onCorporateOfficerNameChange = onCorporateOfficerNameChange,
                onShowPaywall = onShowPaywall,
                onConnectCorporateSso = { viewModel.signInWithCorporateSso(corporateDomain, corporateOfficerName) }
            )
        }
    }
}

@Composable
internal fun CorporateSsoSection(
    uiState: AuthUiState,
    showCorporateSsoInput: Boolean,
    onToggleCorporateSsoInput: () -> Unit,
    corporateDomain: String,
    onCorporateDomainChange: (String) -> Unit,
    corporateOfficerName: String,
    onCorporateOfficerNameChange: (String) -> Unit,
    onShowPaywall: () -> Unit,
    onConnectCorporateSso: () -> Unit
) {
    // Autofill at most once. A conditional LaunchedEffect re-entered composition every
    // time the user cleared the field, making the domain impossible to edit.
    var domainAutoFilled by remember { mutableStateOf(false) }
    if (!uiState.canUseCompanyOAuth) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = SafetyOrange, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Corporate SAML / OIDC Domain",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(color = SafetyOrange.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = "ENTERPRISE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = SafetyOrange,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) {
                        "Payment Overdue: Enterprise subscription payment failed. Company OAuth is locked until payment is resolved."
                    } else {
                        "Company-specific OAuth identity integration and SAML 2.0 / Entra / Okta domain locking are exclusive to Enterprise subscribers ($0.50/seat/mo)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Button(
                    onClick = onShowPaywall,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                ) {
                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(16.dp), tint = SafetyAmber)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) "Resolve Billing & Unlock SSO" else "Unlock Enterprise OAuth ($0.50/seat)",
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
            onClick = onToggleCorporateSsoInput,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Icon(Icons.Default.CorporateFare, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Corporate Single Sign-On (SSO)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    LaunchedEffect(uiState.companyOAuthConfig) {
                        if (!domainAutoFilled && uiState.companyOAuthConfig.isConfigured && corporateDomain.isBlank()) {
                            domainAutoFilled = true
                            onCorporateDomainChange(uiState.companyOAuthConfig.corporateDomain)
                        }
                    }
                    OutlinedTextField(
                        value = corporateDomain,
                        onValueChange = onCorporateDomainChange,
                        label = { Text("Corporate Domain") },
                        placeholder = { Text(uiState.companyOAuthConfig.corporateDomain.ifBlank { "e.g. acmefire.com" }) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = corporateOfficerName,
                        onValueChange = onCorporateOfficerNameChange,
                        label = { Text("Officer Full Name") },
                        placeholder = { Text("e.g. John Doe") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Button(
                        onClick = onConnectCorporateSso,
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
