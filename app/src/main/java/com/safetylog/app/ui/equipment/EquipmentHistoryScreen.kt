package com.safetylog.app.ui.equipment

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safetylog.app.data.BillingRepository
import com.safetylog.app.data.auth.AuthRepository
import com.safetylog.app.data.auth.SubscriptionTier
import com.safetylog.app.data.auth.UserRole
import com.safetylog.app.data.dao.EquipmentDao
import com.safetylog.app.data.dao.InspectionDao
import com.safetylog.app.data.dao.ProfileDao
import com.safetylog.app.data.model.EquipmentItem
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
    val profile by remember(profileDao) { profileDao.getProfile() }.collectAsStateWithLifecycle(initialValue = null)
    val inspectionsFlow = remember(equipmentId) { inspectionDao.getInspectionsForEquipment(equipmentId) }
    val inspections by inspectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val isDark = MaterialTheme.colorScheme.background == DarkSurfaceBase

    val canShare by billingRepository.canShareBetweenAccounts.collectAsStateWithLifecycle()
    val isAdFree by billingRepository.isAdFree.collectAsStateWithLifecycle()
    val currentTier by billingRepository.currentTier.collectAsStateWithLifecycle()
    val enterpriseSeats by billingRepository.enterpriseSeats.collectAsStateWithLifecycle()
    val paymentStatus by billingRepository.paymentStatus.collectAsStateWithLifecycle()
    val currentUser by authRepository.currentUser.collectAsStateWithLifecycle()
    val userRole = currentUser?.role ?: UserRole.USER
    var showPaywall by remember { mutableStateOf(false) }

    var isGeneratingPdf by remember { mutableStateOf(false) }
    var generatedPdfFile by remember { mutableStateOf<File?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(equipmentId, inspections.size) {
        equipment = equipmentDao.getEquipmentById(equipmentId)
    }

    LaunchedEffect(equipmentId, inspections) {
        generatedPdfFile = null
    }

    // Android Storage Access Framework for syncing directly to Google Drive, OneDrive, or local drives
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri: Uri? ->
        if (destinationUri != null && generatedPdfFile != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val pdf = generatedPdfFile ?: return@launch
                    context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                        pdf.inputStream().use { input ->
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
        val cachedPdf = generatedPdfFile
        if (cachedPdf != null && cachedPdf.exists()) {
            onReady(cachedPdf)
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
        producePdf { _ ->
            val suggestedName = "SafetyLog_${safeFileLabel(eq.tagId)}_AuditRecord.pdf"
            createDocLauncher.launch(suggestedName)
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
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        equipment?.let {
                            Text(
                                text = "${it.equipmentType} • ${it.locationDescription}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                    EquipmentOverviewCard(
                        eq = eq,
                        inspectionsCount = inspections.size,
                        userRole = userRole,
                        isDark = isDark,
                        onNavigateToInspect = { onNavigateToInspect(equipmentId) }
                    )
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
                                        Text(
                                            text = "Sync & Save to Google Drive",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
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
                                                producePdf { shareInspectionPdf(context, it, equipment?.tagId) }
                                            }
                                        },
                                        enabled = !isGeneratingPdf,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
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
                                        onClick = { producePdf { viewInspectionPdf(context, it, equipment?.tagId) } },
                                        enabled = !isGeneratingPdf,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
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
                            letterSpacing = 1.sp,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${inspections.size} recorded",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
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

            // Bottom clearance spacer ensuring logs and buttons can always scroll clear of the ad banner
            item {
                Spacer(modifier = Modifier.height(72.dp))
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

/** Keeps tag IDs from injecting path separators / unsafe characters into SAF filenames. */
private fun safeFileLabel(value: String): String {
    val cleaned = value
        .replace(Regex("[\\\\/:*?\"<>|]"), "")
        .replace("..", "")
        .trim()
    return cleaned.ifBlank { "Asset" }
}
