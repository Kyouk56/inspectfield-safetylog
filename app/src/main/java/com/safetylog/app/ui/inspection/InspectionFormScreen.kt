package com.safetylog.app.ui.inspection

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safetylog.app.domain.ChecklistRegistry
import com.safetylog.app.ui.components.PaywallBottomSheet
import com.safetylog.app.ui.components.SafetyBannerAd
import com.safetylog.app.ui.components.SignaturePad
import com.safetylog.app.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionFormScreen(
    viewModel: InspectionViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDark = MaterialTheme.colorScheme.background == DarkSurfaceBase
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

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

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val photoDir = File(context.filesDir, "photos").apply { mkdirs() }
                    val photoFile = File(photoDir, "defect_${System.currentTimeMillis()}.jpg")
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        viewModel.onPhotoSelectionFailed()
                        return@launch
                    }
                    var bytesCopied = 0L
                    withContext(Dispatchers.IO) {
                        inputStream.use { input ->
                            FileOutputStream(photoFile).use { output ->
                                bytesCopied = input.copyTo(output)
                            }
                        }
                    }
                    if (bytesCopied > 0) {
                        viewModel.onPhotoSelected(photoFile.absolutePath)
                    } else {
                        viewModel.onPhotoSelectionFailed()
                    }
                } catch (_: Exception) {
                    viewModel.onPhotoSelectionFailed()
                }
            }
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
            ChecklistRegistry.getChecklistByName(eq.assignedChecklist)
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!uiState.isAdFree) {
                SafetyBannerAd(
                    isAdFree = uiState.isAdFree,
                    onRemoveAdsClicked = viewModel::openPaywall
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isPassed = uiState.isAllChecksPassed

            // Equipment Asset Details, Audit Timestamp, and Live Compliance Status Banner
            InspectionAssetHeaderSection(
                equipment = uiState.equipment,
                currentDateTimeStr = currentDateTimeStr,
                isPassed = isPassed,
                checklist = checklist,
                isDark = isDark
            )

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
                        .heightIn(min = 95.dp),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 3,
                    maxLines = 4,
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

            // Defect / Condition Photo Attachment Card
            InspectionPhotoEvidenceCard(
                photoUri = uiState.photoUri,
                onPhotoRemoved = { viewModel.onPhotoSelected(null) },
                onLaunchPhotoPicker = { photoPickerLauncher.launch("image/*") }
            )

            // Embedded Signature Pad
            SignaturePad(
                onSignatureCaptured = viewModel::onSignatureCaptured
            )

            // Save Inspection Button with Date Stamped Callout
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    // Never fabricate a signature. saveInspection() validates that a
                    // real technician signature was captured and surfaces an error.
                    viewModel.saveInspection()
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

            Spacer(modifier = Modifier.height(64.dp))
        }
    }

    // Paywall Dialog
    if (uiState.showPaywall) {
        PaywallBottomSheet(
            onDismissRequest = viewModel::dismissPaywall,
            currentTier = uiState.subscriptionTier,
            paymentStatus = uiState.paymentStatus,
            userRole = uiState.userRole,
            initialEnterpriseSeats = uiState.enterpriseSeats,
            onSelectTier = { tier, seats ->
                viewModel.setSubscriptionTier(tier, seats)
            },
            onCancelSubscription = viewModel::cancelSubscription,
            onReactivateSubscription = viewModel::reactivateSubscription,
            onSimulatePaymentFailed = viewModel::simulatePaymentFailed,
            onSimulatePaymentSuccess = viewModel::simulatePaymentSuccess,
            onUpgradeClicked = viewModel::upgradeToProAndSave,
            onLaunchBilling = { act, tier, seats ->
                viewModel.launchBillingFlow(act, tier, seats)
            }
        )
    }
}
