package com.safetylog.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "inspector_profiles")
data class InspectorProfile(
    @PrimaryKey val id: Int = 1,
    val companyName: String,
    val inspectorName: String,
    val certificationNumber: String,
    val contactEmail: String,
    val contactPhone: String
)

@Entity(
    tableName = "equipment_items",
    indices = [Index(value = ["tagId"], unique = true)]
)
data class EquipmentItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tagId: String,               // e.g., "FE-042"
    val locationDescription: String, // e.g., "Main Warehouse, East Pillar 4"
    val equipmentType: String,       // e.g., "10lb ABC Dry Chemical"
    val serialNumber: String = "",
    val installDate: String? = null,
    val assignedChecklist: String = "Fire Extinguisher", // Protocol category name from ChecklistRegistry
    val reviewFrequency: String = "Monthly",             // Daily, Weekly, Bi-Weekly, Monthly, Quarterly, Semi-Annually, Annually
    val assignedReviewDate: String = "",                 // e.g., "Nov 20, 2026"
    val assignedReviewTime: String = "09:00 AM",
    val nextReviewTimestamp: Long = 0L,                  // Millis epoch of next scheduled review
    val reminderEnabled: Boolean = true,                 // System notification alarm enabled
    val reminderLeadDays: Int = 1                        // 0 = Day of review, 1 = 1 day before, 3 = 3 days before
)

@Entity(
    tableName = "inspection_records",
    foreignKeys = [
        ForeignKey(
            entity = EquipmentItem::class,
            parentColumns = ["id"],
            childColumns = ["equipmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["equipmentId"]), Index(value = ["timestamp"])]
)
data class InspectionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val equipmentId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val isPressureOk: Boolean,
    val isHoseIntact: Boolean,
    val isPinSealed: Boolean,
    val isSignagePresent: Boolean,
    val isAccessUnobstructed: Boolean,
    val overallStatus: String,      // "PASSED" or "DEFICIENT"
    val notes: String? = null,
    val photoUri: String? = null,
    val signaturePath: String,      // Local absolute path to saved signature PNG
    val checklistName: String = "", // e.g. "NFPA 10 5-Point Protocol"
    val checklistSummary: String = "" // Summary of evaluated verification points
)
