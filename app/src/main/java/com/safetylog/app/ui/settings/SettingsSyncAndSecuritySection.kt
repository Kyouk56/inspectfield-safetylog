package com.safetylog.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safetylog.app.data.auth.SecureTokenStorage
import com.safetylog.app.data.sync.TeamSyncEngine
import com.safetylog.app.ui.dashboard.DashboardUiState
import com.safetylog.app.ui.dashboard.DashboardViewModel
import com.safetylog.app.ui.theme.*
import com.safetylog.app.util.CrashLogVault
import kotlinx.coroutines.launch

@Composable
internal fun SettingsTeamSyncCard(
    uiState: DashboardUiState,
    dashboardViewModel: DashboardViewModel,
    teamSyncEngine: TeamSyncEngine,
    cloudDriveUriInput: String,
    cloudDriveNameInput: String,
    cloudDriveAutoInput: Boolean,
    onCloudDriveAutoChange: (Boolean) -> Unit,
    isBackingUpDrive: Boolean,
    onIsBackingUpDriveChange: (Boolean) -> Unit,
    webhookUrlInput: String,
    onWebhookUrlChange: (String) -> Unit,
    webhookSecretInput: String,
    onWebhookSecretChange: (String) -> Unit,
    webhookAutoInput: Boolean,
    onWebhookAutoChange: (Boolean) -> Unit,
    isTestingWebhook: Boolean,
    onIsTestingWebhookChange: (Boolean) -> Unit,
    onLaunchDocumentTree: () -> Unit,
    onShowEndpointDialog: () -> Unit,
    onShowPaywall: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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
                        onShowEndpointDialog()
                    } else {
                        onShowPaywall()
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
                                    onLaunchDocumentTree()
                                } else {
                                    onShowPaywall()
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
                                onShowPaywall()
                                return@Button
                            }
                            if (cloudDriveUriInput.isBlank()) {
                                onLaunchDocumentTree()
                                return@Button
                            }
                            onIsBackingUpDriveChange(true)
                            coroutineScope.launch {
                                val res = teamSyncEngine.backupToCloudDriveFolder(cloudDriveUriInput)
                                onIsBackingUpDriveChange(false)
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
                                onShowPaywall()
                                return@Switch
                            }
                            if (!uiState.userRole.canManageSettings) {
                                Toast.makeText(context, "Permission Denied: Only Admins and Managers can configure auto-backup", Toast.LENGTH_SHORT).show()
                                return@Switch
                            }
                            if (checked && cloudDriveUriInput.isBlank()) {
                                Toast.makeText(context, "Please link a Cloud Drive folder first", Toast.LENGTH_SHORT).show()
                                onLaunchDocumentTree()
                                return@Switch
                            }
                            onCloudDriveAutoChange(checked)
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
                    onValueChange = onWebhookUrlChange,
                    label = { Text("Webhook Endpoint URL") },
                    placeholder = { Text("https://api.yourcompany.com/v1/inspections") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    enabled = uiState.canShareBetweenAccounts && uiState.userRole.canManageSettings
                )

                OutlinedTextField(
                    value = webhookSecretInput,
                    onValueChange = onWebhookSecretChange,
                    label = { Text("Authorization Token / Secret (Optional)") },
                    placeholder = { Text("Bearer token or HMAC secret") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    // Never render a bearer/HMAC secret in plain text.
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = uiState.canShareBetweenAccounts && uiState.userRole.canManageSettings
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (!uiState.canShareBetweenAccounts) {
                                onShowPaywall()
                                return@Button
                            }
                            if (webhookUrlInput.isBlank()) {
                                Toast.makeText(context, "Enter a valid Webhook URL first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onIsTestingWebhookChange(true)
                            coroutineScope.launch {
                                val res = teamSyncEngine.testWebhookEndpoint(webhookUrlInput, webhookSecretInput)
                                onIsTestingWebhookChange(false)
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
                                onShowPaywall()
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
                                onShowPaywall()
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
                            onWebhookAutoChange(checked)
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
                        onShowPaywall()
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
}

@Composable
internal fun SettingsCrashLogVaultCard(
    localCrashCount: Int,
    onLocalCrashCountChange: (Int) -> Unit,
    cloudTelemetryOptIn: Boolean,
    onCloudTelemetryOptInChange: (Boolean) -> Unit,
    secureStorage: SecureTokenStorage
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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
                        onCloudTelemetryOptInChange(it)
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
                        coroutineScope.launch {
                            val report = CrashLogVault.exportDiagnosticReportIO(context, secureStorage)
                            Toast.makeText(context, "Audit report exported (${report.length()} bytes)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Export Report", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        CrashLogVault.clearVault(context)
                        onLocalCrashCountChange(CrashLogVault.getCrashCount(context))
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
}
