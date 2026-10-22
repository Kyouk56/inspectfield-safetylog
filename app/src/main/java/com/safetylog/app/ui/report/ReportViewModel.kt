package com.safetylog.app.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.safetylog.app.data.dao.EquipmentDao
import com.safetylog.app.data.dao.InspectionDao
import com.safetylog.app.data.dao.ProfileDao
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.data.model.InspectionRecord
import com.safetylog.app.data.model.InspectorProfile
import com.safetylog.app.util.InspectionPdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

data class ReportUiState(
    val startDateMillis: Long = 0,
    val endDateMillis: Long = 0,
    val selectedPreset: ReportDatePreset = ReportDatePreset.THIS_MONTH,
    val recordsWithEquipment: List<Pair<EquipmentItem, InspectionRecord>> = emptyList(),
    val profile: InspectorProfile? = null,
    val isGeneratingPdf: Boolean = false,
    val generatedPdfFile: File? = null,
    val errorMessage: String? = null,
    val isLoadingData: Boolean = false
) {
    val totalInspections: Int get() = recordsWithEquipment.size
    val passedCount: Int get() = recordsWithEquipment.count { it.second.overallStatus.equals("PASSED", ignoreCase = true) }
    val deficientCount: Int get() = recordsWithEquipment.count { it.second.overallStatus.equals("DEFICIENT", ignoreCase = true) }
}

enum class ReportDatePreset(val displayName: String) {
    THIS_MONTH("This Month"),
    LAST_30_DAYS("Last 30 Days"),
    LAST_90_DAYS("Last 90 Days"),
    ALL_TIME("All Time")
}

class ReportViewModel(
    private val inspectionDao: InspectionDao,
    private val equipmentDao: EquipmentDao,
    private val profileDao: ProfileDao,
    private val pdfGenerator: InspectionPdfGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        applyPreset(ReportDatePreset.THIS_MONTH)
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val prof = profileDao.getProfileOnce()
            _uiState.update { it.copy(profile = prof) }
        }
    }

    fun applyPreset(preset: ReportDatePreset) {
        val calendar = Calendar.getInstance()
        val end = calendar.timeInMillis
        val start = when (preset) {
            ReportDatePreset.THIS_MONTH -> {
                calendar.apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            ReportDatePreset.LAST_30_DAYS -> {
                calendar.apply {
                    add(Calendar.DAY_OF_YEAR, -30)
                }.timeInMillis
            }
            ReportDatePreset.LAST_90_DAYS -> {
                calendar.apply {
                    add(Calendar.DAY_OF_YEAR, -90)
                }.timeInMillis
            }
            ReportDatePreset.ALL_TIME -> 0L
        }

        _uiState.update {
            it.copy(
                startDateMillis = start,
                endDateMillis = end,
                selectedPreset = preset,
                generatedPdfFile = null
            )
        }
        loadRecords(start, end)
    }

    fun setCustomDateRange(start: Long, end: Long) {
        _uiState.update {
            it.copy(
                startDateMillis = start,
                endDateMillis = end,
                generatedPdfFile = null
            )
        }
        loadRecords(start, end)
    }

    private fun loadRecords(start: Long, end: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingData = true) }
            val records = inspectionDao.getInspectionsInRange(start, end)
            val equipmentMap = equipmentDao.getAllEquipment().associateBy { it.id }

            val pairs = records.mapNotNull { record ->
                val eq = equipmentMap[record.equipmentId]
                if (eq != null) Pair(eq, record) else null
            }

            _uiState.update {
                it.copy(
                    recordsWithEquipment = pairs,
                    isLoadingData = false
                )
            }
        }
    }

    fun generatePdf() {
        val state = _uiState.value
        if (state.recordsWithEquipment.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No inspection records found in the selected date range.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingPdf = true, errorMessage = null) }
            try {
                val pdfFile = withContext(Dispatchers.IO) {
                    pdfGenerator.generateReport(state.profile, state.recordsWithEquipment)
                }
                _uiState.update { it.copy(isGeneratingPdf = false, generatedPdfFile = pdfFile) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isGeneratingPdf = false, errorMessage = "Error generating PDF: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    class Factory(
        private val inspectionDao: InspectionDao,
        private val equipmentDao: EquipmentDao,
        private val profileDao: ProfileDao,
        private val pdfGenerator: InspectionPdfGenerator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReportViewModel(inspectionDao, equipmentDao, profileDao, pdfGenerator) as T
        }
    }
}
