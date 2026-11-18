package com.safetylog.app.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safetylog.app.data.auth.TeamMember
import com.safetylog.app.data.auth.UserRole
import com.safetylog.app.data.sync.TeamSyncEngine
import com.safetylog.app.ui.dashboard.DashboardUiState
import com.safetylog.app.ui.dashboard.DashboardViewModel
import com.safetylog.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
internal fun SettingsAppearanceCard(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val haptic = LocalHapticFeedback.current

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
}

@Composable
internal fun SettingsInspectionRemindersCard(
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    dashboardViewModel: DashboardViewModel
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // D-09: surface the special "exact alarm" permission. Without it the scheduler
    // silently falls back to inexact alarms, which is a compliance risk for due dates.
    var hasExactAlarmPermission by remember {
        mutableStateOf(com.safetylog.app.data.reminder.InspectionReminderManager.hasExactAlarmPermission(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasExactAlarmPermission =
                    com.safetylog.app.data.reminder.InspectionReminderManager.hasExactAlarmPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                                onClick = onRequestNotificationPermission,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasExactAlarmPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasExactAlarmPermission) StatusPassed else SafetyAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasExactAlarmPermission) "Exact Alarms: Allowed" else "Exact Alarms: Grant to keep due dates precise",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasExactAlarmPermission) StatusPassed else SafetyAmber
                            )
                        }
                        if (!hasExactAlarmPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            FilledTonalButton(
                                onClick = {
                                    try {
                                        context.startActivity(
                                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "No system screen available for exact alarms", Toast.LENGTH_SHORT).show()
                                    }
                                },
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
}

@Composable
internal fun SettingsComplianceAndAboutCard(
    onShowPrivacyPolicy: () -> Unit
) {
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
                onClick = onShowPrivacyPolicy,
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsActionDialogs(
    uiState: DashboardUiState,
    dashboardViewModel: DashboardViewModel,
    teamSyncEngine: TeamSyncEngine,
    showCancelConfirmDialog: Boolean,
    onDismissCancelConfirmDialog: () -> Unit,
    showEndpointDialog: Boolean,
    onDismissEndpointDialog: () -> Unit,
    customEndpointInput: String,
    onCustomEndpointChange: (String) -> Unit,
    showAddMemberDialog: Boolean,
    onDismissAddMemberDialog: () -> Unit,
    newMemberEmail: String,
    onNewMemberEmailChange: (String) -> Unit,
    newMemberName: String,
    onNewMemberNameChange: (String) -> Unit,
    newMemberRole: UserRole,
    onNewMemberRoleChange: (UserRole) -> Unit,
    addMemberError: String?,
    onAddMemberErrorChange: (String?) -> Unit,
    editingMember: TeamMember?,
    onDismissEditingMember: () -> Unit,
    editEmailInput: String,
    onEditEmailChange: (String) -> Unit,
    editNameInput: String,
    onEditNameChange: (String) -> Unit,
    editRoleInput: UserRole,
    onEditRoleChange: (UserRole) -> Unit,
    editIsActiveInput: Boolean,
    onEditIsActiveChange: (Boolean) -> Unit,
    editMemberError: String?,
    onEditMemberErrorChange: (String?) -> Unit,
    memberToRemove: TeamMember?,
    onDismissMemberToRemove: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Cancel Subscription Confirmation Dialog
    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = onDismissCancelConfirmDialog,
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = StatusDeficient) },
            title = { Text("Cancel Subscription?", fontWeight = FontWeight.Bold) },
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
                        onDismissCancelConfirmDialog()
                        Toast.makeText(context, "Subscription cancelled. Account reverted to Free.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDeficient)
                ) {
                    Text("Confirm Cancellation")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissCancelConfirmDialog) {
                    Text("Keep Subscription")
                }
            }
        )
    }

    // Custom Sync Endpoint Dialog
    if (showEndpointDialog) {
        AlertDialog(
            onDismissRequest = onDismissEndpointDialog,
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
                        onValueChange = onCustomEndpointChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    teamSyncEngine.setCustomRestEndpoint(customEndpointInput)
                    onDismissEndpointDialog()
                    Toast.makeText(context, "Sync endpoint updated", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissEndpointDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Team Member Dialog
    if (showAddMemberDialog) {
        AlertDialog(
            onDismissRequest = onDismissAddMemberDialog,
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
                            onNewMemberEmailChange(it)
                            onAddMemberErrorChange(null)
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
                        onValueChange = onNewMemberNameChange,
                        label = { Text("Full Name (Optional)") },
                        placeholder = { Text("e.g. Marcus Davis") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Text("Assign Role:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

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
                                onClick = { onNewMemberRoleChange(role) },
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
                        coroutineScope.launch {
                            val res = dashboardViewModel.addTeamMember(newMemberEmail, newMemberName, newMemberRole)
                            res.onSuccess { member ->
                                onDismissAddMemberDialog()
                                Toast.makeText(context, "Added ${member.displayTitle} to team roster", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                onAddMemberErrorChange(err.message ?: "Failed to add member")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TechBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Add Member", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissAddMemberDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Team Member Dialog
    editingMember?.let { member ->
        AlertDialog(
            onDismissRequest = onDismissEditingMember,
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
                            onEditEmailChange(it)
                            onEditMemberErrorChange(null)
                        },
                        label = { Text("Work Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        isError = editMemberError != null
                    )

                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = onEditNameChange,
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Text("Assigned Role:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

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
                                onClick = { onEditRoleChange(role) },
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
                            onCheckedChange = onEditIsActiveChange
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
                        coroutineScope.launch {
                            val res = dashboardViewModel.updateTeamMember(
                                member.id,
                                editEmailInput,
                                editNameInput,
                                editRoleInput,
                                editIsActiveInput
                            )
                            res.onSuccess {
                                onDismissEditingMember()
                                Toast.makeText(context, "Updated team member details", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                onEditMemberErrorChange(err.message ?: "Failed to update member")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TechBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissEditingMember) {
                    Text("Cancel")
                }
            }
        )
    }

    // Remove Confirmation Dialog
    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = onDismissMemberToRemove,
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
                        coroutineScope.launch {
                            val res = dashboardViewModel.removeTeamMember(member.id)
                            onDismissMemberToRemove()
                            res.onSuccess {
                                Toast.makeText(context, "Removed member and freed 1 seat", Toast.LENGTH_SHORT).show()
                            }
                            res.onFailure { err ->
                                Toast.makeText(context, err.message ?: "Failed to remove member", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDeficient),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Remove User", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissMemberToRemove) {
                    Text("Cancel")
                }
            }
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
