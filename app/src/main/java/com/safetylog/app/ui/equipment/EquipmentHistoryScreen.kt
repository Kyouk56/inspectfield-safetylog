package com.safetylog.app.ui.equipment

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.safetylog.app.data.BillingRepository
import com.safetylog.app.data.auth.AuthRepository
import com.safetylog.app.data.auth.PaymentStatus
import com.safetylog.app.data.auth.SubscriptionTier
import com.safetylog.app.data.auth.UserRole
import com.safetylog.app.data.dao.EquipmentDao
import com.safetylog.app.data.dao.InspectionDao
import com.safetylog.app.data.dao.ProfileDao
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.data.model.InspectionRecord
import com.safetylog.app.data.model.InspectorProfile
import com.safetylog.app.domain.ChecklistRegistry
import com.safetylog.app.ui.components.PaywallBottomSheet
import com.safetylog.app.ui.components.SafetyBannerAd
import com.safetylog.app.ui.theme.*
import com.safetylog.app.util.InspectionPdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentHistoryScreen(
    equipmentId: Long,
    equipmentDao: EquipmentDao,
    inspectionDao: InspectionDao,
    profileDao: ProfileDao,
    billingRepository: BillingRepository,
    authRepository: AuthRepository,
    pdfGenerator: InspectionPdfGenerator,
    onNavigateBack: () -> Unit,
    onNavigateToInspect: (Long) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var equipment by remember { mutableStateOf<EquipmentItem?>(null) }
    var profile by remember { mutableStateOf<InspectorProfile?>(null) }
    val inspectionsFlow = remember(equipmentId) { inspectionDao.getInspectionsForEquipment(equipmentId) }
    val inspections by inspectionsFlow.collectAsState(initial = emptyList())
    val isDark = MaterialTheme.colorScheme.background == DarkSurfaceBase

    val canShare by billingRepository.canShareBetweenAccounts.collectAsState()
    val isAdFree by billingRepository.isAdFree.collectAsState()
    val currentTier by billingRepository.currentTier.collectAsState()
    val enterpriseSeats by billingRepository.enterpriseSeats.collectAsState()
    val paymentStatus by billingRepository.paymentStatus.collectAsState()
    val currentUser by authRepository.currentUser.collectAsState()
    val userRole = currentUser?.role ?: UserRole.USER
    var showPaywall by remember { mutableStateOf(false) }

    var isGeneratingPdf by remember { mutableStateOf(false) }
    var generatedPdfFile by remember { mutableStateOf<File?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(equipmentId) {
        equipment = equipmentDao.getEquipmentById(equipmentId)
        profile = profileDao.getProfileOnce()
    }

    // Android Storage Access Framework for syncing directly to Google Drive, OneDrive, or local drives
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri: Uri? ->
        if (destinationUri != null && generatedPdfFile != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                        generatedPdfFile!!.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Log synced and saved to Google Drive / Cloud successfully!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun producePdf(onReady: (File) -> Unit) {
        val eq = equipment ?: return
        if (generatedPdfFile != null && generatedPdfFile!!.exists()) {
            onReady(generatedPdfFile!!)
            return
        }
        isGeneratingPdf = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val pdf = pdfGenerator.generateSingleAssetReport(profile, eq, inspections)
                withContext(Dispatchers.Main) {
                    generatedPdfFile = pdf
                    isGeneratingPdf = false
                    onReady(pdf)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isGeneratingPdf = false
                    Toast.makeText(context, "Error producing PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun syncToGoogleDrive() {
        if (!canShare) {
            showPaywall = true
            return
        }
        val eq = equipment ?: return
        producePdf { pdf ->
            val suggestedName = "SafetyLog_${eq.tagId}_AuditRecord.pdf"
            createDocLauncher.launch(suggestedName)
        }
    }

    fun sharePdf(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Certified Life-Safety Audit Log - ${equipment?.tagId}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Sync / Send Log to Google Drive"))
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun viewPdf(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "View Inspection Log"))
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open PDF viewer: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val dateFormat = remember { SimpleDateFormat("MMMM dd, yyyy • hh:mm a", Locale.US) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = equipment?.tagId ?: "Asset History",
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "ASSIGNED ASSET",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        equipment?.let {
                            Text(
                                text = "${it.equipmentType} • ${it.locationDescription}",
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
                actions = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        syncToGoogleDrive()
                    }) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Sync to Google Drive",
                            tint = SafetyOrange
                        )
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
        },
        floatingActionButton = {
            if (userRole.canConductInspections && inspections.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigateToInspect(equipmentId)
                    },
                    containerColor = SafetyOrange,
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                ) {
                    Icon(Icons.Default.Checklist, contentDescription = "Log New Inspection")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Equipment Overview Card
            equipment?.let { eq ->
                item {
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
                                        text = "${inspections.size} Audits Logged",
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
                                        onNavigateToInspect(equipmentId)
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

                // Cloud Sync & PDF Production Hub Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) DarkSurfaceCard else Color(0xFFEFF6FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Cloud Sync & Audit Export",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Produce certified signed logs • Save or sync to Google Drive anytime",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Primary Action: Sync to Google Drive / Cloud Drive
                                Button(
                                    onClick = { syncToGoogleDrive() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isGeneratingPdf
                                ) {
                                    if (isGeneratingPdf) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                    } else {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sync & Save to Google Drive", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }

                                // Secondary Actions Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Share PDF (Requires individual or enterprise sharing plan)
                                    OutlinedButton(
                                        onClick = {
                                            if (!canShare) {
                                                showPaywall = true
                                            } else {
                                                producePdf { sharePdf(it) }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share PDF", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    }

                                    // View PDF
                                    OutlinedButton(
                                        onClick = { producePdf { viewPdf(it) } },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("View PDF", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Timeline Header
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ASSIGNED ASSET AUDIT LOGS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${inspections.size} recorded",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Permanent facility record • Each check creates a date-stamped log",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            // Inspection Records or Empty State
            if (inspections.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.HistoryToggleOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No Audit Logs on Record",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "This assigned asset is registered in facility inventory. Tap 'Log New Inspection' below to record its first date-stamped checklist.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(inspections, key = { it.id }) { record ->
                    val protocolName = record.checklistName.ifBlank {
                        equipment?.assignedChecklist?.ifBlank { equipment?.equipmentType ?: "" } ?: ""
                    }
                    AuditLogHistoryCard(
                        record = record,
                        protocolName = protocolName,
                        dateFormat = dateFormat,
                        isDark = isDark
                    )
                }
            }
        }
    }

    if (showPaywall) {
        PaywallBottomSheet(
            onDismissRequest = { showPaywall = false },
            userRole = userRole,
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
            onCancelSubscription = {
                billingRepository.cancelSubscription()
            },
            onReactivateSubscription = {
                billingRepository.reactivateSubscription()
            },
            onSimulatePaymentFailed = {
                billingRepository.markPaymentFailed()
            },
            onSimulatePaymentSuccess = {
                billingRepository.reactivateSubscription()
            }
        )
    }
}

@Composable
private fun AuditLogHistoryCard(
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
                    val bitmap = remember(record.signaturePath) {
                        try {
                            BitmapFactory.decodeFile(record.signaturePath)
                        } catch (_: Exception) { null }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
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
private fun AuditCheckRow(
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
