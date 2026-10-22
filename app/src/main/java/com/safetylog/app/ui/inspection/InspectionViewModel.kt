package com.safetylog.app.ui.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.safetylog.app.data.BillingRepository
import com.safetylog.app.data.dao.EquipmentDao
import com.safetylog.app.data.dao.InspectionDao
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.data.model.InspectionRecord
import com.safetylog.app.data.reminder.InspectionReminderManager
import com.safetylog.app.data.sync.TeamSyncEngine
import com.safetylog.app.domain.EntitlementManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

data class InspectionUiState(
    val equipment: EquipmentItem? = null,
    val isPressureOk: Boolean = true,
    val isHoseIntact: Boolean = true,
    val isPinSealed: Boolean = true,
    val isSignagePresent: Boolean = true,
    val isAccessUnobstructed: Boolean = true,
    val notes: String = "",
    val photoUri: String? = null,
    val signaturePath: String? = null,
    val isSaving: Boolean = false,
    val showPaywall: Boolean = false,
    val errorMessage: String? = null,
    val isSavedSuccess: Boolean = false
) {
    val overallStatus: String
        get() = if (isPressureOk && isHoseIntact && isPinSealed && isSignagePresent && isAccessUnobstructed) {
            "PASSED"
        } else {
            "DEFICIENT"
        }

    val isAllChecksPassed: Boolean
        get() = overallStatus == "PASSED"
}

class InspectionViewModel(
    private val equipmentId: Long,
    private val equipmentDao: EquipmentDao,
    private val inspectionDao: InspectionDao,
    private val entitlementManager: EntitlementManager,
    private val billingRepository: BillingRepository,
    private val teamSyncEngine: TeamSyncEngine? = null,
    private val reminderManager: InspectionReminderManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(InspectionUiState())
    val uiState: StateFlow<InspectionUiState> = _uiState.asStateFlow()

    init {
        loadEquipment()
    }

    private fun loadEquipment() {
        viewModelScope.launch {
            val item = equipmentDao.getEquipmentById(equipmentId)
            _uiState.update { it.copy(equipment = item) }
        }
    }

    fun onPressureChanged(ok: Boolean) {
        _uiState.update { it.copy(isPressureOk = ok) }
    }

    fun onHoseChanged(ok: Boolean) {
        _uiState.update { it.copy(isHoseIntact = ok) }
    }

    fun onPinChanged(ok: Boolean) {
        _uiState.update { it.copy(isPinSealed = ok) }
    }

    fun onSignageChanged(ok: Boolean) {
        _uiState.update { it.copy(isSignagePresent = ok) }
    }

    fun onAccessChanged(ok: Boolean) {
        _uiState.update { it.copy(isAccessUnobstructed = ok) }
    }

    fun setAllChecksPassed() {
        _uiState.update {
            it.copy(
                isPressureOk = true,
                isHoseIntact = true,
                isPinSealed = true,
                isSignagePresent = true,
                isAccessUnobstructed = true,
                errorMessage = null
            )
        }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes, errorMessage = null) }
    }

    fun onPhotoSelected(uri: String?) {
        _uiState.update { it.copy(photoUri = uri) }
    }

    fun onSignatureCaptured(path: String) {
        _uiState.update { it.copy(signaturePath = path, errorMessage = null) }
    }

    fun dismissPaywall() {
        _uiState.update { it.copy(showPaywall = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun calculateNextReviewDate(frequency: String): Pair<String, Long> {
        val cal = Calendar.getInstance()
        when (frequency) {
            "Daily" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "Weekly" -> cal.add(Calendar.DAY_OF_YEAR, 7)
            "Bi-Weekly" -> cal.add(Calendar.DAY_OF_YEAR, 14)
            "Monthly" -> cal.add(Calendar.DAY_OF_YEAR, 30)
            "Quarterly" -> cal.add(Calendar.DAY_OF_YEAR, 90)
            "Semi-Annually" -> cal.add(Calendar.DAY_OF_YEAR, 180)
            "Annually" -> cal.add(Calendar.DAY_OF_YEAR, 365)
            else -> cal.add(Calendar.DAY_OF_YEAR, 30)
        }
        val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US).format(cal.time)
        return Pair(dateStr, cal.timeInMillis)
    }

    fun saveInspection(forcedSignaturePath: String? = null) {
        val currentState = _uiState.value
        val sigPath = forcedSignaturePath ?: currentState.signaturePath

        if (!currentState.isAllChecksPassed && currentState.notes.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Deficiency detected: observations/remarks are required for audit compliance.") }
            return
        }

        if (sigPath.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Please capture and confirm technician signature before saving.") }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSaving = true) }
                val isPro = billingRepository.isProUser.value
                val canCreate = entitlementManager.canCreateInspection(isPro)

                if (!canCreate) {
                    _uiState.update { it.copy(isSaving = false, showPaywall = true) }
                    return@launch
                }

                val currentEq = currentState.equipment
                val protocol = if (!currentEq?.assignedChecklist.isNullOrBlank()) {
                    com.safetylog.app.domain.ChecklistRegistry.getChecklistByName(currentEq!!.assignedChecklist)
                } else {
                    com.safetylog.app.domain.ChecklistRegistry.getChecklistForType(currentEq?.equipmentType ?: "")
                }

                val record = InspectionRecord(
                    equipmentId = equipmentId,
                    timestamp = System.currentTimeMillis(),
                    isPressureOk = currentState.isPressureOk,
                    isHoseIntact = currentState.isHoseIntact,
                    isPinSealed = currentState.isPinSealed,
                    isSignagePresent = currentState.isSignagePresent,
                    isAccessUnobstructed = currentState.isAccessUnobstructed,
                    overallStatus = currentState.overallStatus,
                    notes = currentState.notes.takeIf { it.isNotBlank() },
                    photoUri = currentState.photoUri,
                    signaturePath = sigPath,
                    checklistName = protocol.categoryName,
                    checklistSummary = if (currentState.isAllChecksPassed) {
                        "5/5 ${protocol.categoryName} checks verified passed"
                    } else {
                        "Deficiencies recorded during ${protocol.categoryName} inspection"
                    }
                )

                val insertedId = inspectionDao.insertInspection(record)

                // Automatically advance asset's next review date according to assigned frequency
                if (currentEq != null) {
                    val (nextDate, nextTimestamp) = calculateNextReviewDate(currentEq.reviewFrequency)
                    val updatedEquipment = currentEq.copy(
                        assignedReviewDate = nextDate,
                        nextReviewTimestamp = nextTimestamp
                    )
                    equipmentDao.updateEquipment(updatedEquipment)
                    reminderManager?.scheduleReminder(updatedEquipment)
                }

                // Enterprise Zero-Cost Dispatch: Trigger corporate webhook and cloud drive backup if active and enabled
                if (billingRepository.canShareBetweenAccounts.value) {
                    val companyConfig = billingRepository.companyOAuthConfig.value
                    if (companyConfig.webhookAutoDispatch && companyConfig.webhookUrl.isNotBlank()) {
                        try {
                            teamSyncEngine?.dispatchInspectionToWebhook(
                                inspection = record.copy(id = insertedId),
                                equipment = currentEq,
                                config = companyConfig
                            )
                        } catch (_: Exception) {}
                    }
                    if (companyConfig.cloudDriveAutoBackup && companyConfig.cloudDriveFolderUri.isNotBlank()) {
                        try {
                            teamSyncEngine?.backupToCloudDriveFolder(companyConfig.cloudDriveFolderUri)
                        } catch (_: Exception) {}
                    }
                }

                _uiState.update { it.copy(isSaving = false, isSavedSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Error saving inspection: ${e.message}") }
            }
        }
    }

    fun upgradeToProAndSave() {
        billingRepository.setProStatus(true)
        _uiState.update { it.copy(showPaywall = false) }
        saveInspection()
    }

    fun launchBillingFlow(activity: android.app.Activity, tier: com.safetylog.app.data.auth.SubscriptionTier = com.safetylog.app.data.auth.SubscriptionTier.INDIVIDUAL_PRO, seats: Int = 1) {
        val success = billingRepository.launchBillingFlow(activity, tier, seats)
        if (success) {
            _uiState.update { it.copy(showPaywall = false) }
        } else {
            upgradeToProAndSave()
        }
    }

    class Factory(
        private val equipmentId: Long,
        private val equipmentDao: EquipmentDao,
        private val inspectionDao: InspectionDao,
        private val entitlementManager: EntitlementManager,
        private val billingRepository: BillingRepository,
        private val teamSyncEngine: TeamSyncEngine? = null,
        private val reminderManager: InspectionReminderManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InspectionViewModel(
                equipmentId = equipmentId,
                equipmentDao = equipmentDao,
                inspectionDao = inspectionDao,
                entitlementManager = entitlementManager,
                billingRepository = billingRepository,
                teamSyncEngine = teamSyncEngine,
                reminderManager = reminderManager
            ) as T
        }
    }
}
