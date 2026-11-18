package com.safetylog.app.ui.inspection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.domain.EquipmentChecklist
import com.safetylog.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal val COMMON_DEFECT_TAGS = listOf(
    "Low Gauge Pressure",
    "Broken Tamper Seal",
    "Blocked Physical Access",
    "Cracked Discharge Hose",
    "Missing Wall Signage",
    "Dented Cylinder Shell",
    "Hydrostatic Test Overdue"
)

@Composable
internal fun InspectionAssetHeaderSection(
    equipment: EquipmentItem?,
    currentDateTimeStr: String,
    isPassed: Boolean,
    checklist: EquipmentChecklist,
    isDark: Boolean
) {
    // Equipment Asset Details Card
    equipment?.let { eq ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isDark) DarkSurfaceCard else Slate900)
                                .border(
                                    1.dp,
                                    if (isDark) DarkSurfaceBorder else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = eq.tagId,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = eq.equipmentType,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = eq.locationDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (eq.serialNumber.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Serial: ${eq.serialNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp
                    )
                }
            }
        }
    }

    // Dedicated Audit Date & Time Stamp Card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isDark) DarkSurfaceCard else Slate100),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EventAvailable,
                    contentDescription = null,
                    tint = SafetyOrange,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AUDIT DATE & TIME STAMP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp,
                    fontSize = 8.5.sp
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = currentDateTimeStr,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Assigned Asset Audit • Stamped permanently in local vault",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Live Calculated Compliance Status Banner
    val statusColors = getComplianceStatusColors(
        status = if (isPassed) "PASSED" else "DEFICIENT",
        isDark = isDark
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(statusColors.container)
            .border(1.dp, statusColors.border, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(statusColors.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPassed) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (isPassed) "COMPLIANCE STATUS: PASSED" else "COMPLIANCE STATUS: DEFICIENT",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = statusColors.text
                )
                Text(
                    text = if (isPassed) {
                        "All 5 life-safety verifications satisfy ${checklist.categoryName} standards."
                    } else {
                        "Deficiencies detected. Corrective remarks required."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = statusColors.text
                )
            }
        }
    }
}

@Composable
internal fun InspectionPhotoEvidenceCard(
    photoUri: String?,
    onPhotoRemoved: () -> Unit,
    onLaunchPhotoPicker: () -> Unit
) {
    // File.exists() performs disk I/O; check it off the composition thread.
    val photoFileExists by produceState(initialValue = false, key1 = photoUri) {
        value = withContext(Dispatchers.IO) {
            photoUri != null && File(photoUri).exists()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = SafetyOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Defect & Condition Photo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (photoUri != null) {
                    TextButton(
                        onClick = onPhotoRemoved,
                        colors = ButtonDefaults.textButtonColors(contentColor = StatusDeficient)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (photoUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                ) {
                    AsyncImage(
                        model = if (photoFileExists) File(photoUri) else photoUri,
                        contentDescription = "Attached Defect Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onLaunchPhotoPicker,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Replace Photo Evidence")
                }
            } else {
                OutlinedButton(
                    onClick = onLaunchPhotoPicker,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp), tint = SafetyOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Attach Photo (Evidence / Condition)", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
internal fun ChecklistVerificationCard(
    standardCode: String,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    isDark: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val containerColor = if (isChecked) {
        MaterialTheme.colorScheme.surface
    } else {
        if (isDark) StatusDeficientContainerDark.copy(alpha = 0.5f) else Color(0xFFFFF1F2)
    }

    val badgeBg = if (isChecked) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        if (isDark) StatusDeficientContainerDark else Color(0xFFFEE2E2)
    }

    val badgeText = if (isChecked) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        if (isDark) StatusDeficientTextDark else StatusDeficientText
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (isChecked) MaterialTheme.colorScheme.outlineVariant else if (isDark) StatusDeficientBorderDark else Color(0xFFFCA5A5)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(badgeBg)
                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = standardCode,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = badgeText,
                        fontSize = 8.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isChecked) MaterialTheme.colorScheme.onSurface else if (isDark) StatusDeficientTextDark else StatusDeficientText
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Pass/Fail Pill Switch
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = StatusPassed,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = StatusDeficient
                )
            )
        }
    }
}
