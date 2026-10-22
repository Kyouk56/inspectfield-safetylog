package com.safetylog.app.ui.equipment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.safetylog.app.domain.ChecklistRegistry
import com.safetylog.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val COMMON_EQUIPMENT_TYPES = listOf(
    "10lb ABC Dry Chemical",
    "5lb CO2 Extinguisher",
    "20lb ABC Dry Chemical",
    "Class K Wet Chemical",
    "2.5gal Water Mist",
    "Emergency Eye Wash",
    "AED Defibrillator",
    "Fire Blanket Station"
)

private val REVIEW_FREQUENCIES = listOf(
    "Daily",
    "Weekly",
    "Bi-Weekly",
    "Monthly",
    "Quarterly",
    "Semi-Annually",
    "Annually"
)

private val TIME_PRESETS = listOf(
    "09:00 AM",
    "11:00 AM",
    "01:00 PM",
    "03:00 PM",
    "05:00 PM"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentFormScreen(
    viewModel: EquipmentFormViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
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
    if (showReviewDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showReviewDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
                            val formatted = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(cal.time)
                            viewModel.onAssignedReviewDateChanged(formatted)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        showReviewDatePicker = false
                    }
                ) {
                    Text("Confirm Date", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReviewDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showInstallDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showInstallDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
                            val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                            viewModel.onInstallDateChanged(formatted)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        showInstallDatePicker = false
                    }
                ) {
                    Text("Confirm Date", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                                .background(if (isDark) DarkSurfaceCard else Color(0xFFE0F2FE))
                                .border(1.dp, if (isDark) DarkSurfaceBorder else Color.Transparent, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "3. Assigned Inspection Checklist",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mandated protocol executed when inspecting this asset",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Select Mandated Compliance Protocol",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Protocol Selection Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(ChecklistRegistry.allProtocols) { protocol ->
                            val isSelected = uiState.assignedChecklist.equals(protocol.categoryName, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onAssignedChecklistChanged(protocol.categoryName) },
                                label = {
                                    Text(
                                        text = protocol.categoryName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }

                    // Checklist Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) DarkSurfaceCard else Slate50)
                            .border(1.dp, if (isDark) DarkSurfaceBorder else Slate200, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedProtocol.standardTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Slate900
                                )
                                Surface(
                                    color = if (isDark) Color(0xFF0369A1) else Color(0xFFBAE6FD),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${selectedProtocol.items.size} CHECKS",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 9.sp,
                                        color = if (isDark) Color.White else Color(0xFF0369A1)
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            // Preview of 5 Verification items
                            selectedProtocol.items.forEach { item ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = StatusPassed,
                                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${item.standardCode} • ${item.description}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Assigned Review Schedule Card
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
                                .background(if (isDark) DarkSurfaceCard else Color(0xFFFEF3C7))
                                .border(1.dp, if (isDark) DarkSurfaceBorder else Color.Transparent, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.EventRepeat, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "4. Assigned Review Dates & Times",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Periodic review cycle and scheduled target deadline",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Mandatory Review Frequency",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Frequency selector chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(REVIEW_FREQUENCIES) { freq ->
                            val isSelected = uiState.reviewFrequency.equals(freq, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.onReviewFrequencyChanged(freq)
                                },
                                label = {
                                    Text(
                                        text = freq,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }

                    // Assigned Next Review Date
                    OutlinedTextField(
                        value = uiState.assignedReviewDate,
                        onValueChange = viewModel::onAssignedReviewDateChanged,
                        label = { Text("Scheduled Next Review Date *") },
                        placeholder = { Text("e.g. Nov 19, 2026") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showReviewDatePicker = true }) {
                                Icon(Icons.Default.Event, contentDescription = "Open Calendar", tint = SafetyOrange)
                            }
                        }
                    )

                    // Quick Date Presets Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setReviewDateOffsetDays(7)
                            },
                            label = { Text("+7 Days", fontSize = 10.sp) }
                        )
                        AssistChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setReviewDateOffsetDays(30)
                            },
                            label = { Text("+30 Days", fontSize = 10.sp) }
                        )
                        AssistChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setReviewDateOffsetDays(90)
                            },
                            label = { Text("+90 Days", fontSize = 10.sp) }
                        )
                        AssistChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setReviewDateOffsetDays(365)
                            },
                            label = { Text("+1 Year", fontSize = 10.sp) }
                        )
                    }

                    // NFPA & OSHA Standard Presets
                    Text(
                        text = "NFPA & OSHA Standard Interval Presets",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = uiState.reviewFrequency == "Monthly",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.onReviewFrequencyChanged("Monthly")
                                    viewModel.setReviewDateOffsetDays(30)
                                },
                                label = { Text("NFPA 10 Monthly (30d)", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = uiState.reviewFrequency == "Annually",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.onReviewFrequencyChanged("Annually")
                                    viewModel.setReviewDateOffsetDays(365)
                                },
                                label = { Text("NFPA 10 Annual (365d)", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = uiState.reviewFrequency == "Bi-Weekly",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.onReviewFrequencyChanged("Bi-Weekly")
                                    viewModel.setReviewDateOffsetDays(14)
                                },
                                label = { Text("OSHA 1910.151 (14d)", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = uiState.reviewFrequency == "Quarterly",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.onReviewFrequencyChanged("Quarterly")
                                    viewModel.setReviewDateOffsetDays(90)
                                },
                                label = { Text("NFPA 25 Sprinkler (90d)", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = uiState.reviewFrequency == "Semi-Annually",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.onReviewFrequencyChanged("Semi-Annually")
                                    viewModel.setReviewDateOffsetDays(180)
                                },
                                label = { Text("NFPA 72 Fire Alarm (180d)", fontSize = 11.sp) }
                            )
                        }
                    }

                    // Assigned Review Time
                    OutlinedTextField(
                        value = uiState.assignedReviewTime,
                        onValueChange = viewModel::onAssignedReviewTimeChanged,
                        label = { Text("Assigned Target Review Time *") },
                        placeholder = { Text("e.g. 09:00 AM") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )

                    // Quick Time Presets
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(TIME_PRESETS) { timePreset ->
                            val isSelected = uiState.assignedReviewTime == timePreset
                            SuggestionChip(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.onAssignedReviewTimeChanged(timePreset)
                                },
                                label = {
                                    Text(
                                        text = timePreset,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Inspection Due Reminder Settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.reminderEnabled) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (uiState.reminderEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = if (uiState.reminderEnabled) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Scheduled Alarm Reminder",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "System alert before inspection lapses",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.reminderEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.onReminderEnabledChanged(enabled)
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            }
                        )
                    }

                    AnimatedVisibility(visible = uiState.reminderEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Reminder Lead-Time:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = uiState.reminderLeadDays == 0,
                                    onClick = { viewModel.onReminderLeadDaysChanged(0) },
                                    label = { Text("Day of Audit", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = uiState.reminderLeadDays == 1,
                                    onClick = { viewModel.onReminderLeadDaysChanged(1) },
                                    label = { Text("1 Day Prior (Recommended)", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = uiState.reminderLeadDays == 3,
                                    onClick = { viewModel.onReminderLeadDaysChanged(3) },
                                    label = { Text("3 Days Prior", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Native Calendar Export Button
                    OutlinedButton(
                        onClick = {
                            val calIntent = viewModel.getCalendarIntent()
                            if (calIntent != null) {
                                try {
                                    context.startActivity(calIntent)
                                    Toast.makeText(context, "Opening Google / Device Calendar...", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    Toast.makeText(context, "No calendar application found on device", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.EventAvailable, contentDescription = null, modifier = Modifier.size(16.dp), tint = SafetyOrange)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to Device Calendar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
