package com.safetylog.app.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safetylog.app.data.auth.AuthorizationConfig
import com.safetylog.app.data.auth.UserRole
import com.safetylog.app.data.dao.EquipmentWithLatestInspection
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.domain.ChecklistRegistry
import com.safetylog.app.ui.theme.*
import com.safetylog.app.util.TestControls

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssetActionBottomSheet(
    item: EquipmentWithLatestInspection,
    userRole: UserRole,
    canManageAssets: Boolean,
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
            AssetOperationCard(
                title = "View Inspection Logs & Export",
                subtitle = "Export or sync certified PDF audit logs to Google Drive",
                icon = Icons.Default.History,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onViewLogs
            )

            // Option: Add Inspection Due Date to Device Calendar
            AssetOperationCard(
                title = "Add to Device Calendar",
                subtitle = "Schedule recurring NFPA/OSHA inspection event & alert",
                icon = Icons.Default.Event,
                iconTint = TechBlue,
                onClick = onAddToCalendar
            )

            // Option: Edit Asset & Review Schedule (Gated to canManageAssets)
            if (canManageAssets) {
                AssetOperationCard(
                    title = "Edit Asset & Review Schedule",
                    subtitle = "Update location, assigned checklist, or target review dates",
                    icon = Icons.Default.Edit,
                    iconTint = SafetyAmber,
                    onClick = onEdit
                )
            }

            // Option: Decommission Asset (Gated to canDeleteAssets)
            if (userRole.canDeleteAssets) {
                AssetOperationCard(
                    title = "Decommission Asset",
                    subtitle = "Permanently remove from active facility registry",
                    icon = Icons.Default.Delete,
                    iconTint = StatusDeficient,
                    iconContainerColor = StatusDeficientContainer,
                    titleColor = StatusDeficient,
                    borderColor = StatusDeficient.copy(alpha = 0.3f),
                    showTrailingArrow = false,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
internal fun AssetOperationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    titleColor: Color = Color.Unspecified,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    showTrailingArrow: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor)
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
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showTrailingArrow) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
internal fun DecommissionEquipmentDialog(
    equipment: EquipmentItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = StatusDeficient
            )
        },
        title = { Text("Decommission Equipment") },
        text = {
            Text("Are you sure you want to permanently remove ${equipment.tagId} (${equipment.equipmentType}) from the compliance registry? All associated inspection records will remain in historical audit logs.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = StatusDeficient)
            ) {
                Text("Decommission")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun NetworkStatusDialog(
    isOnline: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = null,
                tint = if (isOnline) StatusPassed else SafetyOrange
            )
        },
        title = {
            Text(
                text = if (isOnline) "Vault Status: Live Online" else "Vault Status: Air-Gapped Offline",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Text(
                text = if (isOnline)
                    "Device is securely connected to the internet. Real-time REST endpoints, webhooks, and Google Cloud Identity verification are operational."
                else
                    "Device is running in disconnected air-gapped mode. All inspection audits and hardware AES-256 vault writes remain strictly local.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
internal fun RoleSwitcherDialog(
    userRole: UserRole,
    authEmail: String?,
    onSwitchRole: (UserRole) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = Color(userRole.badgeColorHex),
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Active Role: ${userRole.title}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = authEmail?.let { "Linked: $it" } ?: "Local Standalone Session",
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
                    color = Color(userRole.badgeColorHex).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(userRole.badgeColorHex).copy(alpha = 0.35f))
                ) {
                    Text(
                        text = userRole.description,
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
                        isGranted = userRole.canManageAssets,
                        description = if (userRole.canManageAssets) "Add, edit, and organize equipment" else "Inspection viewing only"
                    )
                    RolePermissionCheckItem(
                        title = "Billing & Subscription Management",
                        isGranted = userRole.canManageBilling,
                        description = if (userRole.canManageBilling) "Manage tier, seats, and billing" else "Restricted to Account Admin/Owner"
                    )
                    RolePermissionCheckItem(
                        title = "Team Roster & Administration",
                        isGranted = userRole.canManageTeam,
                        description = if (userRole.canManageTeam) "Invite and manage team members" else "Restricted"
                    )
                    RolePermissionCheckItem(
                        title = "Integration & Security Settings",
                        isGranted = userRole.canManageSettings,
                        description = if (userRole.canManageSettings) "Configure OAuth, sync & webhooks" else "Restricted"
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
                    val isOwnerAuthorized = TestControls.isEnabled && AuthorizationConfig.isAuthorizedOwner(authEmail)

                    UserRole.entries.forEach { role ->
                        val isSelected = userRole == role
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
                                            "Restricted: Platform Owner authority is reserved exclusively for ${AuthorizationConfig.AUTHORIZED_OWNER_EMAILS.joinToString(" and ")}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSwitchRole(role)
                                        Toast.makeText(context, "Switched role to ${role.title}", Toast.LENGTH_SHORT).show()
                                        onDismiss()
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
                                                text = "Authorized owners only: ${AuthorizationConfig.AUTHORIZED_OWNER_EMAILS.joinToString(", ")}",
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
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
internal fun RolePermissionCheckItem(title: String, isGranted: Boolean, description: String) {
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
