package com.safetylog.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safetylog.app.data.auth.CompanyOAuthConfig
import com.safetylog.app.data.auth.CompanyOAuthProvider
import com.safetylog.app.data.auth.PaymentStatus
import com.safetylog.app.data.auth.SubscriptionTier
import com.safetylog.app.data.auth.TeamMember
import com.safetylog.app.data.auth.UserRole
import com.safetylog.app.data.sync.TeamSyncEngine
import com.safetylog.app.ui.dashboard.DashboardUiState
import com.safetylog.app.ui.dashboard.DashboardViewModel
import com.safetylog.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsCompanyOAuthCard(
    uiState: DashboardUiState,
    dashboardViewModel: DashboardViewModel,
    companyNameInput: String,
    onCompanyNameChange: (String) -> Unit,
    corporateDomainInput: String,
    onCorporateDomainChange: (String) -> Unit,
    selectedProvider: CompanyOAuthProvider,
    onProviderChange: (CompanyOAuthProvider) -> Unit,
    tenantIdInput: String,
    onTenantIdChange: (String) -> Unit,
    clientIdInput: String,
    onClientIdChange: (String) -> Unit,
    enforceDomainInput: Boolean,
    onEnforceDomainChange: (Boolean) -> Unit,
    webhookUrlInput: String,
    webhookSecretInput: String,
    webhookAutoInput: Boolean,
    cloudDriveUriInput: String,
    cloudDriveNameInput: String,
    cloudDriveAutoInput: Boolean,
    onNavigateToAuth: () -> Unit,
    onShowPaywall: () -> Unit
) {
    val context = LocalContext.current

    // 4. Company-Specific OAuth & Enterprise Identity Hub
    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (uiState.canUseCompanyOAuth) Color(0xFF4F46E5).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape)
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
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Corporate SAML 2.0 / Entra ID / Okta SSO & Domain Lock",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (uiState.canUseCompanyOAuth) Color(0xFF10B981).copy(alpha = 0.15f)
                    else if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient.copy(alpha = 0.15f)
                    else SafetyOrange.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (uiState.canUseCompanyOAuth) "ENTERPRISE ACTIVE"
                        else if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) "LOCKED (PAST DUE)"
                        else "ENTERPRISE ONLY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (uiState.canUseCompanyOAuth) Color(0xFF10B981)
                        else if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient
                        else SafetyOrange,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
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
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Corporate Single Sign-On is Gated", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
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
                            onClick = onShowPaywall,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) StatusDeficient else Slate900
                            )
                        ) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(16.dp), tint = SafetyAmber)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.paymentStatus == PaymentStatus.PAYMENT_FAILED) "Resolve Past Due Balance"
                                else if (uiState.paymentStatus == PaymentStatus.CANCELLED) "Reactivate Enterprise Plan"
                                else "Unlock Enterprise OAuth ($0.50/seat)",
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
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    value = companyNameInput, onValueChange = onCompanyNameChange,
                    label = { Text("Organization / Company Name") }, placeholder = { Text("e.g. Apex Industrial Safety LLC") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = canManageSettings, shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = corporateDomainInput, onValueChange = onCorporateDomainChange,
                    label = { Text("Corporate Email Domain") }, placeholder = { Text("e.g. apexsafety.com") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = canManageSettings, shape = RoundedCornerShape(10.dp)
                )

                Text("Identity Protocol / Provider:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompanyOAuthProvider.entries.forEach { provider ->
                        val isSelected = selectedProvider == provider
                        FilterChip(
                            selected = isSelected,
                            onClick = { onProviderChange(provider) },
                            enabled = canManageSettings,
                            label = { Text(provider.displayName, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF4F46E5), selectedLabelColor = Color.White)
                        )
                    }
                }

                OutlinedTextField(
                    value = tenantIdInput, onValueChange = onTenantIdChange,
                    label = { Text("Issuer URL / Tenant ID") }, placeholder = { Text("e.g. https://login.microsoftonline.com/tenant-guid") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = canManageSettings, shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = clientIdInput, onValueChange = onClientIdChange,
                    label = { Text("Client ID / Entity ID") }, placeholder = { Text("e.g. apex-inspectfield-client") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = canManageSettings, shape = RoundedCornerShape(10.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Enforce Corporate Domain Only",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        val domainDisplay = if (corporateDomainInput.isNotBlank()) corporateDomainInput else "domain.com"
                        Text(
                            text = "Strictly restrict field sign-ins to emails ending in @$domainDisplay",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(checked = enforceDomainInput, onCheckedChange = onEnforceDomainChange, enabled = canManageSettings)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        modifier = Modifier.weight(1.3f).heightIn(min = 42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (canManageSettings) "Save & Activate OAuth" else "Save Restricted",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(onClick = onNavigateToAuth, modifier = Modifier.weight(1f).heightIn(min = 42.dp), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test SSO", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsTeamRosterCard(
    uiState: DashboardUiState,
    teamSyncEngine: TeamSyncEngine,
    onShowPaywall: () -> Unit,
    onOpenAddMemberDialog: () -> Unit,
    onEditMember: (TeamMember) -> Unit,
    onRemoveMember: (TeamMember) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 4B. Enterprise Team Members & Seat Roster (Admin Access)
    val usedSeats = uiState.teamMembers.count { it.isActive }
    val totalSeats = uiState.enterpriseSeats
    val isSeatsFull = usedSeats >= totalSeats

    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (uiState.canUseCompanyOAuth) Color(0xFF0284C7).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape)
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
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Assign roles, add/remove users by email ($0.50/seat/mo)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (!uiState.canUseCompanyOAuth) SafetyOrange.copy(alpha = 0.15f)
                    else if (isSeatsFull) SafetyAmber.copy(alpha = 0.15f)
                    else StatusPassed.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (!uiState.canUseCompanyOAuth) "ENTERPRISE ONLY"
                        else if (isSeatsFull) "SEATS FULL ($usedSeats/$totalSeats)"
                        else "$usedSeats OF $totalSeats SEATS USED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (!uiState.canUseCompanyOAuth) SafetyOrange
                        else if (isSeatsFull) Color(0xFFB45309)
                        else StatusPassed,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
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
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Team Member Administration is Gated", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            text = "Adding, editing, and managing inspector email rosters is an Enterprise capability ($0.50/seat/mo). Upgrade to invite and manage team members.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                        Button(
                            onClick = onShowPaywall,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Active Seat Utilization:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$usedSeats / $totalSeats Seats ($0.50/seat/mo)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val progressFraction = if (totalSeats > 0) (usedSeats.toFloat() / totalSeats.toFloat()).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (isSeatsFull) SafetyAmber else TechBlue,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    if (isSeatsFull) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "All seats allocated. Expand seats to add more.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB45309),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = {
                                    val isCorporateRestricted = uiState.subscriptionTier == SubscriptionTier.ENTERPRISE && !uiState.userRole.canManageBilling
                                    if (!isCorporateRestricted) {
                                        onShowPaywall()
                                    } else {
                                        Toast.makeText(context, "Adding seats is restricted to Account Admins and the Owner.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "+ Add Seats",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = TechBlue,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // Role-colored avatar circle
                                        Box(
                                            modifier = Modifier.size(40.dp).clip(CircleShape)
                                                .background(Color(member.role.badgeColorHex).copy(alpha = 0.18f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(member.initials, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(member.role.badgeColorHex))
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = member.displayTitle, style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                                            )
                                            if (member.name.isNotBlank()) {
                                                Text(
                                                    text = member.email, style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Surface(color = Color(member.role.badgeColorHex).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                                    Text(
                                                        text = member.role.title, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                                        color = Color(member.role.badgeColorHex), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }

                                                if (!member.isActive) {
                                                    Surface(color = MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(4.dp)) {
                                                        Text(
                                                            text = "INACTIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                                            IconButton(onClick = { onEditMember(member) }, modifier = Modifier.size(36.dp)) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Member", tint = TechBlue, modifier = Modifier.size(18.dp))
                                            }
                                        }

                                        if (canRemoveThisMember) {
                                            IconButton(onClick = { onRemoveMember(member) }, modifier = Modifier.size(36.dp)) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Member", tint = StatusDeficient, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Add Member and Cloud Drive Roster Sync Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (!uiState.userRole.canManageTeam) return@Button
                            if (isSeatsFull) {
                                val isCorporateRestricted = uiState.subscriptionTier == SubscriptionTier.ENTERPRISE && !uiState.userRole.canManageBilling
                                if (!isCorporateRestricted) {
                                    Toast.makeText(context, "All $totalSeats company seats are in use. Add seats to invite more members.", Toast.LENGTH_LONG).show()
                                    onShowPaywall()
                                } else {
                                    Toast.makeText(context, "All $totalSeats company seats are in use. Contact an Account Admin or Owner to expand seats.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                onOpenAddMemberDialog()
                            }
                        },
                        enabled = uiState.userRole.canManageTeam,
                        modifier = Modifier.weight(1.3f).heightIn(min = 42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TechBlue)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.userRole.canManageTeam) "Add Team Member" else "Add Member (Restricted)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
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
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sync Drive",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

