package com.safetylog.app.ui.equipment

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.safetylog.app.data.dao.EquipmentDao
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.data.reminder.InspectionReminderManager
import com.safetylog.app.domain.ChecklistRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class EquipmentFormUiState(
    val id: Long = 0,
    val tagId: String = "",
    val locationDescription: String = "",
    val equipmentType: String = "10lb ABC Dry Chemical",
    val serialNumber: String = "",
    val installDate: String = "",
    val assignedChecklist: String = "Fire Extinguisher",
    val reviewFrequency: String = "Monthly",
    val assignedReviewDate: String = "",
    val assignedReviewTime: String = "09:00 AM",
    val reminderEnabled: Boolean = true,
    val reminderLeadDays: Int = 1, // 0 = day of, 1 = 1 day before, 3 = 3 days before
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSavedSuccess: Boolean = false,
    val errorMessage: String? = null
)

class EquipmentFormViewModel(
    private val equipmentId: Long?,
    private val equipmentDao: EquipmentDao,
    private val reminderManager: InspectionReminderManager? = null
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    private val _uiState = MutableStateFlow(
        EquipmentFormUiState(
            assignedReviewDate = calculateDefaultReviewDate("Monthly")
        )
    )
    val uiState: StateFlow<EquipmentFormUiState> = _uiState.asStateFlow()

    init {
        if (equipmentId != null && equipmentId > 0) {
            loadExistingEquipment(equipmentId)
        }
    }

    private fun calculateDefaultReviewDate(frequency: String): String {
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
        return dateFormat.format(cal.time)
    }

    private fun loadExistingEquipment(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val item = equipmentDao.getEquipmentById(id)
            if (item != null) {
                _uiState.update {
                    it.copy(
                        id = item.id,
                        tagId = item.tagId,
                        locationDescription = item.locationDescription,
                        equipmentType = item.equipmentType,
                        serialNumber = item.serialNumber,
                        installDate = item.installDate ?: "",
                        assignedChecklist = item.assignedChecklist.ifBlank {
                            ChecklistRegistry.getChecklistForType(item.equipmentType).categoryName
                        },
                        reviewFrequency = item.reviewFrequency.ifBlank { "Monthly" },
                        assignedReviewDate = item.assignedReviewDate.ifBlank { calculateDefaultReviewDate("Monthly") },
                        assignedReviewTime = item.assignedReviewTime.ifBlank { "09:00 AM" },
                        reminderEnabled = item.reminderEnabled,
                        reminderLeadDays = item.reminderLeadDays,
                        isEditing = true,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onTagIdChanged(tagId: String) {
        _uiState.update { it.copy(tagId = tagId, errorMessage = null) }
    }

    fun onLocationChanged(location: String) {
        _uiState.update { it.copy(locationDescription = location, errorMessage = null) }
    }

    fun onEquipmentTypeChanged(type: String) {
        val protocol = ChecklistRegistry.getChecklistForType(type).categoryName
        _uiState.update {
            it.copy(
                equipmentType = type,
                assignedChecklist = protocol
            )
        }
    }

    fun onAssignedChecklistChanged(checklistCategory: String) {
        _uiState.update { it.copy(assignedChecklist = checklistCategory) }
    }

    fun onReviewFrequencyChanged(frequency: String) {
        _uiState.update {
            it.copy(
                reviewFrequency = frequency,
                assignedReviewDate = calculateDefaultReviewDate(frequency)
            )
        }
    }

    fun onAssignedReviewDateChanged(date: String) {
        _uiState.update { it.copy(assignedReviewDate = date) }
    }

    fun setReviewDateOffsetDays(days: Int) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, days)
        _uiState.update { it.copy(assignedReviewDate = dateFormat.format(cal.time)) }
    }

    fun onAssignedReviewTimeChanged(time: String) {
        _uiState.update { it.copy(assignedReviewTime = time) }
    }

    fun onSerialNumberChanged(serial: String) {
        _uiState.update { it.copy(serialNumber = serial) }
    }

    fun onInstallDateChanged(date: String) {
        _uiState.update { it.copy(installDate = date) }
    }

    fun onReminderEnabledChanged(enabled: Boolean) {
        _uiState.update { it.copy(reminderEnabled = enabled) }
    }

    fun onReminderLeadDaysChanged(leadDays: Int) {
        _uiState.update { it.copy(reminderLeadDays = leadDays) }
    }

    fun getCalendarIntent(): Intent? {
        val state = _uiState.value
        val fullFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US)
        val timestamp = try {
            fullFormat.parse("${state.assignedReviewDate} ${state.assignedReviewTime}")?.time
                ?: (System.currentTimeMillis() + 30L * 24 * 3600 * 1000)
        } catch (_: Exception) {
            System.currentTimeMillis() + 30L * 24 * 3600 * 1000
        }
        val item = EquipmentItem(
            id = state.id,
            tagId = state.tagId.ifBlank { "ASSET" },
            locationDescription = state.locationDescription.ifBlank { "Field Location" },
            equipmentType = state.equipmentType,
            assignedChecklist = state.assignedChecklist,
            reviewFrequency = state.reviewFrequency,
            assignedReviewDate = state.assignedReviewDate,
            assignedReviewTime = state.assignedReviewTime,
            nextReviewTimestamp = timestamp,
            reminderEnabled = state.reminderEnabled,
            reminderLeadDays = state.reminderLeadDays
        )
        return reminderManager?.createCalendarIntent(item)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun saveEquipment() {
        val state = _uiState.value
        if (state.tagId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Tag ID / Asset ID is required (e.g. FE-101)") }
            return
        }
        if (state.locationDescription.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Location description is required (e.g. East Hallway)") }
            return
        }

        viewModelScope.launch {
            try {
                // Parse nextReviewTimestamp
                val fullFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US)
                val timestamp = try {
                    fullFormat.parse("${state.assignedReviewDate} ${state.assignedReviewTime}")?.time
                        ?: (System.currentTimeMillis() + 30L * 24 * 3600 * 1000)
                } catch (_: Exception) {
                    System.currentTimeMillis() + 30L * 24 * 3600 * 1000
                }

                val item = EquipmentItem(
                    id = state.id,
                    tagId = state.tagId.trim().uppercase(),
                    locationDescription = state.locationDescription.trim(),
                    equipmentType = state.equipmentType.trim(),
                    serialNumber = state.serialNumber.trim(),
                    installDate = state.installDate.trim().takeIf { it.isNotBlank() },
                    assignedChecklist = state.assignedChecklist.trim(),
                    reviewFrequency = state.reviewFrequency.trim(),
                    assignedReviewDate = state.assignedReviewDate.trim(),
                    assignedReviewTime = state.assignedReviewTime.trim(),
                    nextReviewTimestamp = timestamp,
                    reminderEnabled = state.reminderEnabled,
                    reminderLeadDays = state.reminderLeadDays
                )
                val insertedId = equipmentDao.upsertEquipment(item)
                val savedItem = if (item.id == 0L) item.copy(id = insertedId) else item
                reminderManager?.scheduleReminder(savedItem)

                _uiState.update { it.copy(isSavedSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error saving equipment: ${e.message}") }
            }
        }
    }

    class Factory(
        private val equipmentId: Long?,
        private val equipmentDao: EquipmentDao,
        private val reminderManager: InspectionReminderManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EquipmentFormViewModel(equipmentId, equipmentDao, reminderManager) as T
        }
    }
}
