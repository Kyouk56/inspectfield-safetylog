package com.safetylog.app.ui.equipment

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safetylog.app.data.BillingRepository
import com.safetylog.app.data.auth.PaymentStatus
import com.safetylog.app.data.auth.SubscriptionTier
import com.safetylog.app.data.auth.UserRole
import com.safetylog.app.domain.ChecklistRegistry
import com.safetylog.app.ui.components.PaywallBottomSheet
import com.safetylog.app.ui.components.SafetyBannerAd
import com.safetylog.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentFormScreen(
    viewModel: EquipmentFormViewModel,
    billingRepository: BillingRepository? = null,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAdFree by remember(billingRepository) { billingRepository?.isAdFree ?: kotlinx.coroutines.flow.MutableStateFlow(false) }.collectAsStateWithLifecycle()
    val currentTier by remember(billingRepository) { billingRepository?.currentTier ?: kotlinx.coroutines.flow.MutableStateFlow(SubscriptionTier.FREE) }.collectAsStateWithLifecycle()
    val paymentStatus by remember(billingRepository) { billingRepository?.paymentStatus ?: kotlinx.coroutines.flow.MutableStateFlow(PaymentStatus.ACTIVE) }.collectAsStateWithLifecycle()
    val enterpriseSeats by remember(billingRepository) { billingRepository?.enterpriseSeats ?: kotlinx.coroutines.flow.MutableStateFlow(5) }.collectAsStateWithLifecycle()
    var showPaywall by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background == DarkSurfaceBase
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Inspection reminder notifications enabled!", Toast.LENGTH_SHORT).show()
        }
    }

    var attemptedSave by remember { mutableStateOf(false) }
    var showReviewDatePicker by remember { mutableStateOf(false) }
    var showInstallDatePicker by remember { mutableStateOf(false) }

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

    // Material 3 Date Picker Dialogs
    EquipmentDatePickerDialogs(
        showReviewDatePicker = showReviewDatePicker,
        onDismissReviewDatePicker = { showReviewDatePicker = false },
        onReviewDateSelected = viewModel::onAssignedReviewDateChanged,
        showInstallDatePicker = showInstallDatePicker,
        onDismissInstallDatePicker = { showInstallDatePicker = false },
        onInstallDateSelected = viewModel::onInstallDateChanged
    )

    val selectedProtocol = remember(uiState.assignedChecklist) {
        ChecklistRegistry.getChecklistByName(uiState.assignedChecklist)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (uiState.isEditing) "Edit Asset & Schedule" else "Register Safety Asset",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Configure asset details, checklist & review dates",
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!isAdFree) {
                SafetyBannerAd(
                    isAdFree = isAdFree,
                    onRemoveAdsClicked = { showPaywall = true }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Facility Asset Identification Card
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDark) DarkSurfaceCard else Slate900)
                                .border(1.dp, if (isDark) DarkSurfaceBorder else Color.Transparent, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "1. Asset Identification & Location",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Assign unique physical barcode tag & physical placement",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Tag ID
                    OutlinedTextField(
                        value = uiState.tagId,
                        onValueChange = viewModel::onTagIdChanged,
                        label = { Text("Tag ID / Asset Barcode *") },
                        placeholder = { Text("e.g. FE-042 or EXT-109") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = attemptedSave && uiState.tagId.isBlank(),
                        supportingText = if (attemptedSave && uiState.tagId.isBlank()) {
                            { Text("Asset Barcode / Tag ID is mandatory.", color = StatusDeficient) }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    val prefix = when {
                                        uiState.equipmentType.contains("AED", ignoreCase = true) -> "AED"
                                        uiState.equipmentType.contains("Hose", ignoreCase = true) -> "FH"
                                        uiState.equipmentType.contains("Eye", ignoreCase = true) -> "EW"
                                        uiState.equipmentType.contains("Alarm", ignoreCase = true) -> "FA"
                                        else -> "FE"
                                    }
                                    val randomSuffix = (100..999).random()
                                    viewModel.onTagIdChanged("$prefix-$randomSuffix")
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Generate", modifier = Modifier.size(14.dp), tint = SafetyOrange)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Generate", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SafetyOrange)
                            }
                        }
                    )

                    // Location
                    OutlinedTextField(
                        value = uiState.locationDescription,
                        onValueChange = viewModel::onLocationChanged,
                        label = { Text("Physical Location Description *") },
                        placeholder = { Text("e.g. Main Warehouse, East Pillar 4") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = attemptedSave && uiState.locationDescription.isBlank(),
                        supportingText = if (attemptedSave && uiState.locationDescription.isBlank()) {
                            { Text("Physical location is required to locate equipment.", color = StatusDeficient) }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                }
            }

            // 2. Equipment Specifications Card
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDark) DarkSurfaceCard else SafetyOrangeContainer)
                                .border(1.dp, if (isDark) DarkSurfaceBorder else Color.Transparent, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Engineering, contentDescription = null, tint = SafetyOrange, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "2. Equipment Model & Specifications",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Equipment category, model type, serial & install dates",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Preset chips
                    Text(
                        text = "Standard Equipment Categories",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(COMMON_EQUIPMENT_TYPES) { type ->
                            val isSelected = uiState.equipmentType == type
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.onEquipmentTypeChanged(type)
                                },
                                label = { Text(type, fontSize = 11.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }

                    // Custom equipment type
                    OutlinedTextField(
                        value = uiState.equipmentType,
                        onValueChange = viewModel::onEquipmentTypeChanged,
                        label = { Text("Equipment Type / Rating *") },
                        placeholder = { Text("e.g. 10lb ABC Dry Chemical") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Serial Number
                    OutlinedTextField(
                        value = uiState.serialNumber,
                        onValueChange = viewModel::onSerialNumberChanged,
                        label = { Text("Manufacturer Serial Number (Optional)") },
                        placeholder = { Text("e.g. SN-8921-X9") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Install Date
                    OutlinedTextField(
                        value = uiState.installDate,
                        onValueChange = viewModel::onInstallDateChanged,
                        label = { Text("Installation / Commissioning Date (Optional)") },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showInstallDatePicker = true }) {
                                Icon(Icons.Default.Event, contentDescription = "Pick Calendar Date", tint = SafetyOrange)
                            }
                        }
                    )
                }
            }

            // 3. Assigned Checklist Protocol Card
            AssignedChecklistProtocolCard(
                uiState = uiState,
                selectedProtocol = selectedProtocol,
                isDark = isDark,
                onAssignedChecklistChanged = viewModel::onAssignedChecklistChanged
            )

            // 4. Assigned Review Schedule Card
            AssignedReviewScheduleCard(
                uiState = uiState,
                viewModel = viewModel,
                isDark = isDark,
                onShowReviewDatePicker = { showReviewDatePicker = true },
                onRequestNotificationPermission = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Button(
                onClick = {
                    attemptedSave = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.saveEquipment()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(4.dp, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isEditing) "Save Asset Specifications & Schedule" else "Register Safety Asset",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }

    if (showPaywall && billingRepository != null) {
        PaywallBottomSheet(
            onDismissRequest = { showPaywall = false },
            userRole = UserRole.ACCOUNT_ADMIN,
            currentTier = currentTier,
            paymentStatus = paymentStatus,
            initialEnterpriseSeats = enterpriseSeats,
            onSelectTier = { tier, seats ->
                billingRepository.setTier(tier)
                if (tier == SubscriptionTier.ENTERPRISE) {
                    billingRepository.setEnterpriseSeats(seats)
                }
                showPaywall = false
            },
            onLaunchBilling = { act, tier, seats ->
                billingRepository.launchBillingFlow(act, tier, seats)
                showPaywall = false
            },
            onCancelSubscription = { billingRepository.cancelSubscription() },
            onReactivateSubscription = { billingRepository.reactivateSubscription() },
            onSimulatePaymentFailed = { billingRepository.markPaymentFailed() },
            onSimulatePaymentSuccess = { billingRepository.reactivateSubscription() }
        )
    }
}
