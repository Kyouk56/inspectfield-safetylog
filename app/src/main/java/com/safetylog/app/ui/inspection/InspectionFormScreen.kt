package com.safetylog.app.ui.inspection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.safetylog.app.domain.ChecklistRegistry
import com.safetylog.app.ui.components.PaywallBottomSheet
import com.safetylog.app.ui.components.SignaturePad
import com.safetylog.app.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private val COMMON_DEFECT_TAGS = listOf(
    "Low Gauge Pressure",
    "Broken Tamper Seal",
    "Blocked Physical Access",
    "Cracked Discharge Hose",
    "Missing Wall Signage",
    "Dented Cylinder Shell",
    "Hydrostatic Test Overdue"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionFormScreen(
    viewModel: InspectionViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDark = MaterialTheme.colorScheme.background == DarkSurfaceBase
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(uiState.isSavedSuccess) {
        if (uiState.isSavedSuccess) {
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val currentDateTimeStr = remember {
        val d = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.US).format(Date())
        val t = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
        "$d • $t"
    }

    val checklist = remember(uiState.equipment) {
        val eq = uiState.equipment
        if (!eq?.assignedChecklist.isNullOrBlank()) {
            ChecklistRegistry.getChecklistByName(eq!!.assignedChecklist)
        } else {
            ChecklistRegistry.getChecklistForType(eq?.equipmentType ?: "")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Assigned Asset Audit",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        uiState.equipment?.let {
                            Text(
                                text = "${it.tagId} • ${it.equipmentType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Equipment Asset Details Card
            uiState.equipment?.let { eq ->
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
            val isPassed = uiState.isAllChecksPassed
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

            // 5 Verification Check Cards dynamically loaded from ChecklistRegistry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = checklist.standardTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Protocol: ${checklist.categoryName} • 5 Verification Checks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 1-Tap "Pass All" / "All Compliant" Speed Action
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setAllChecksPassed()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isPassed) StatusPassedContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = null,
                        tint = if (isPassed) StatusPassed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Pass All",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isPassed) StatusPassed else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            ChecklistVerificationCard(
                standardCode = checklist.items[0].standardCode,
                title = "1. ${checklist.items[0].title}",
                subtitle = checklist.items[0].description,
                isChecked = uiState.isPressureOk,
                isDark = isDark,
                onCheckedChange = { ok ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.onPressureChanged(ok)
                }
            )

            ChecklistVerificationCard(
                standardCode = checklist.items[1].standardCode,
                title = "2. ${checklist.items[1].title}",
                subtitle = checklist.items[1].description,
                isChecked = uiState.isHoseIntact,
                isDark = isDark,
                onCheckedChange = { ok ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.onHoseChanged(ok)
                }
            )

            ChecklistVerificationCard(
                standardCode = checklist.items[2].standardCode,
                title = "3. ${checklist.items[2].title}",
                subtitle = checklist.items[2].description,
                isChecked = uiState.isPinSealed,
                isDark = isDark,
                onCheckedChange = { ok ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.onPinChanged(ok)
                }
            )

            ChecklistVerificationCard(
                standardCode = checklist.items[3].standardCode,
                title = "4. ${checklist.items[3].title}",
                subtitle = checklist.items[3].description,
                isChecked = uiState.isSignagePresent,
                isDark = isDark,
                onCheckedChange = { ok ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.onSignageChanged(ok)
                }
            )

            ChecklistVerificationCard(
                standardCode = checklist.items[4].standardCode,
                title = "5. ${checklist.items[4].title}",
                subtitle = checklist.items[4].description,
                isChecked = uiState.isAccessUnobstructed,
                isDark = isDark,
                onCheckedChange = { ok ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.onAccessChanged(ok)
                }
            )

            // Defect Notes & Quick Observation Chips
            val notesRequireAttention = !isPassed && uiState.notes.isBlank()

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (!isPassed) "Defect Documentation (Required) *" else "Observations & Inspection Notes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (notesRequireAttention) StatusDeficient else MaterialTheme.colorScheme.onSurface
                    )
                    if (!isPassed) {
                        Surface(
                            color = if (isDark) StatusDeficientContainerDark else Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "MANDATORY REMARKS",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp,
                                color = if (isDark) StatusDeficientTextDark else StatusDeficient
                            )
                        }
                    }
                }

                // Quick 1-tap defect observation chips tailored to asset category
                Text(
                    text = "Tap to Append Common Findings",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(checklist.commonDefectTags) { tag ->
                        AssistChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val current = uiState.notes
                                val updated = if (current.isBlank()) tag else "$current; $tag"
                                viewModel.onNotesChanged(updated)
                            },
                            label = { Text(tag, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = SafetyOrange
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = viewModel::onNotesChanged,
                    label = { Text(if (isPassed) "Observations & Notes (Optional)" else "Defect Observations (Required) *") },
                    placeholder = { Text("e.g. Regular monthly verification completed, all indicators confirmed...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 3,
                    isError = notesRequireAttention,
                    supportingText = if (notesRequireAttention) {
                        { Text("Safety audit regulations require documenting defect reason.", color = StatusDeficient) }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = if (notesRequireAttention) StatusDeficient else MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = if (notesRequireAttention) StatusDeficient else SafetyOrange
                    )
                )
            }

            // Embedded Signature Pad
            SignaturePad(
                onSignatureCaptured = viewModel::onSignatureCaptured
            )

            // Save Inspection Button with Date Stamped Callout
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    var currentSig = uiState.signaturePath
                    if (currentSig.isNullOrBlank()) {
                        val bitmap = android.graphics.Bitmap.createBitmap(800, 300, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)

                        val titlePaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 34f
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD_ITALIC)
                        }
                        canvas.drawText("Electronically Certified & Signed", 40f, 130f, titlePaint)

                        val subPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.DKGRAY
                            textSize = 24f
                            isAntiAlias = true
                        }
                        val stampDate = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US).format(Date())
                        canvas.drawText("Audit Timestamp: $stampDate", 40f, 190f, subPaint)

                        val file = File(context.filesDir, "sig_auto_${System.currentTimeMillis()}.png")
                        FileOutputStream(file).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        }
                        currentSig = file.absolutePath
                        viewModel.onSignatureCaptured(currentSig)
                    }
                    viewModel.saveInspection(forcedSignaturePath = currentSig)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .shadow(3.dp, RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPassed) StatusPassed else SafetyOrange
                ),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPassed) "Save & Sign Inspection Log" else "Log Deficient Inspection",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Paywall Dialog
    if (uiState.showPaywall) {
        PaywallBottomSheet(
            onDismissRequest = viewModel::dismissPaywall,
            onUpgradeClicked = viewModel::upgradeToProAndSave,
            onLaunchBilling = { act, tier, seats ->
                viewModel.launchBillingFlow(act, tier, seats)
            }
        )
    }
}

@Composable
private fun ChecklistVerificationCard(
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
