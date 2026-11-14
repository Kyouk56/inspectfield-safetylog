package com.safetylog.app.ui.equipment

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.safetylog.app.data.auth.UserRole
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.data.model.InspectionRecord
import com.safetylog.app.domain.ChecklistRegistry
import com.safetylog.app.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

internal fun shareInspectionPdf(context: Context, file: File, tagId: String?) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Certified Life-Safety Audit Log - $tagId")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Sync / Send Log to Google Drive"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

internal fun viewInspectionPdf(context: Context, file: File, tagId: String?) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "View Inspection Log"))
    } catch (_: Exception) {
        shareInspectionPdf(context, file, tagId)
    }
}

@Composable
internal fun EquipmentOverviewCard(
    eq: EquipmentItem,
    inspectionsCount: Int,
    userRole: UserRole,
    isDark: Boolean,
    onNavigateToInspect: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) DarkSurfaceCard else Slate900
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isDark) DarkSurfaceBorder else Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = eq.equipmentType,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF131F33) else Slate800)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "$inspectionsCount Audits Logged",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Slate300
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = eq.locationDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate300
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            val protocolName = eq.assignedChecklist.ifBlank {
                ChecklistRegistry.getChecklistForType(eq.equipmentType).categoryName
            }
            val freq = eq.reviewFrequency.ifBlank { "Monthly" }
            val nextReview = eq.assignedReviewDate.ifBlank { "Scheduled" }
            val reviewTime = eq.assignedReviewTime.ifBlank { "09:00 AM" }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, tint = SafetyOrangeLight, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Protocol: $protocolName",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate300,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EventRepeat, contentDescription = null, tint = SafetyAmberLight, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Review Cycle: $freq • Next Due: $nextReview at $reviewTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate300
                )
            }

            if (eq.serialNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Serial: ${eq.serialNumber} • Installed: ${eq.installDate ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }

            if (userRole.canConductInspections) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigateToInspect()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Log New Inspection Checklist",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
internal fun AuditLogHistoryCard(
    record: InspectionRecord,
    protocolName: String,
    dateFormat: SimpleDateFormat,
    isDark: Boolean
) {
    val isPassed = record.overallStatus.equals("PASSED", ignoreCase = true)
    val statusColors = getComplianceStatusColors(record.overallStatus, isDark = isDark)
    val protocol = remember(protocolName) { ChecklistRegistry.getChecklistByName(protocolName) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Date & Time Stamp + Pass/Fail Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = dateFormat.format(Date(record.timestamp)),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "AUDIT DATE & TIME STAMP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColors.container)
                        .border(1.dp, statusColors.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isPassed) "PASSED" else "DEFICIENT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColors.text
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Protocol standard banner
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Rule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${protocol.categoryName} • ${protocol.standardTitle}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5 Dynamic Checks Grid from ChecklistRegistry
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                protocol.items.getOrNull(0)?.let { item ->
                    AuditCheckRow(label = "1. ${item.title}", isOk = record.isPressureOk, isDark = isDark)
                }
                protocol.items.getOrNull(1)?.let { item ->
                    AuditCheckRow(label = "2. ${item.title}", isOk = record.isHoseIntact, isDark = isDark)
                }
                protocol.items.getOrNull(2)?.let { item ->
                    AuditCheckRow(label = "3. ${item.title}", isOk = record.isPinSealed, isDark = isDark)
                }
                protocol.items.getOrNull(3)?.let { item ->
                    AuditCheckRow(label = "4. ${item.title}", isOk = record.isSignagePresent, isDark = isDark)
                }
                protocol.items.getOrNull(4)?.let { item ->
                    AuditCheckRow(label = "5. ${item.title}", isOk = record.isAccessUnobstructed, isDark = isDark)
                }
            }

            // Defect Notes
            if (!record.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "OBSERVATIONS / DEFECTS LOGGED:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = record.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Defect / Condition Photo Thumbnail
            if (!record.photoUri.isNullOrBlank()) {
                val photoFile = File(record.photoUri)
                val exists = photoFile.exists()
                if (exists || record.photoUri.startsWith("content://") || record.photoUri.startsWith("http")) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "DEFECT PHOTO EVIDENCE:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AsyncImage(
                        model = if (exists) photoFile else record.photoUri,
                        contentDescription = "Defect Photo Evidence",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Signature Thumbnail
            if (!record.signaturePath.isNullOrBlank() && File(record.signaturePath).exists()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val signatureBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = record.signaturePath) {
                        value = withContext(Dispatchers.IO) {
                            try {
                                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                                BitmapFactory.decodeFile(record.signaturePath, opts)?.asImageBitmap()
                            } catch (_: Exception) { null }
                        }
                    }
                    if (signatureBitmap != null) {
                        Image(
                            bitmap = signatureBitmap!!,
                            contentDescription = "Technician Signature",
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column {
                        Text(
                            text = "TECHNICIAN SIGNATURE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp
                        )
                        Text(
                            text = "Cryptographically archived",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AuditCheckRow(
    label: String,
    isOk: Boolean,
    isDark: Boolean
) {
    val checkColor = if (isOk) StatusPassed else StatusDeficient

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(checkColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isOk) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = checkColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
        Text(
            text = if (isOk) "PASS" else "FAIL",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = checkColor,
            fontSize = 10.sp
        )
    }
}
