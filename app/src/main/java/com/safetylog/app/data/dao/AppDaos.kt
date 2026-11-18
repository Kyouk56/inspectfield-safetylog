package com.safetylog.app.data.dao

import androidx.compose.runtime.Immutable
import androidx.room.*
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.data.model.InspectionRecord
import com.safetylog.app.data.model.InspectorProfile
import kotlinx.coroutines.flow.Flow

@Immutable
data class EquipmentWithLatestInspection(
    @Embedded val equipment: EquipmentItem,
    val lastInspectionDate: Long?,
    val lastInspectionStatus: String?
)

@Dao
interface EquipmentDao {
    @Query("""
        SELECT e.*,
               i.timestamp AS lastInspectionDate,
               i.overallStatus AS lastInspectionStatus
        FROM equipment_items e
        LEFT JOIN inspection_records i ON i.id = (
            SELECT i2.id FROM inspection_records i2
            WHERE i2.equipmentId = e.id
            ORDER BY i2.timestamp DESC, i2.id DESC
            LIMIT 1
        )
        ORDER BY e.tagId ASC
    """)
    fun getEquipmentSummaries(): Flow<List<EquipmentWithLatestInspection>>

    @Upsert
    suspend fun upsertEquipment(item: EquipmentItem): Long

    @Update
    suspend fun updateEquipment(item: EquipmentItem)

    @Query("SELECT * FROM equipment_items WHERE id = :id")
    suspend fun getEquipmentById(id: Long): EquipmentItem?

    @Query("SELECT * FROM equipment_items WHERE tagId = :tagId LIMIT 1")
    suspend fun getEquipmentByTag(tagId: String): EquipmentItem?

    @Query("SELECT * FROM equipment_items ORDER BY tagId ASC")
    suspend fun getAllEquipment(): List<EquipmentItem>

    @Delete
    suspend fun deleteEquipment(item: EquipmentItem)
}

@Dao
interface InspectionDao {
    @Insert
    suspend fun insertInspection(record: InspectionRecord): Long

    /** D-11: atomic quota check + insert. Room runs the default body in one transaction. */
    @Transaction
    suspend fun insertInspectionIfUnderQuota(record: InspectionRecord, startOfMonth: Long, limit: Int): Long? {
        if (countInspectionsSince(startOfMonth) >= limit) return null
        return insertInspection(record)
    }

    @Update
    suspend fun updateInspection(record: InspectionRecord)

    @Query("SELECT * FROM inspection_records WHERE syncId = :syncId LIMIT 1")
    suspend fun getInspectionBySyncId(syncId: String): InspectionRecord?

    @Query("SELECT * FROM inspection_records WHERE equipmentId = :equipmentId ORDER BY timestamp DESC")
    fun getInspectionsForEquipment(equipmentId: Long): Flow<List<InspectionRecord>>

    @Query("SELECT COUNT(*) FROM inspection_records WHERE timestamp >= :startOfMonth")
    suspend fun countInspectionsSince(startOfMonth: Long): Int

    @Query("SELECT COUNT(*) FROM inspection_records WHERE timestamp >= :startOfMonth")
    fun countInspectionsSinceFlow(startOfMonth: Long): Flow<Int>

    @Query("""
        SELECT * FROM inspection_records 
        WHERE timestamp BETWEEN :startDate AND :endDate 
        ORDER BY timestamp ASC
    """)
    suspend fun getInspectionsInRange(startDate: Long, endDate: Long): List<InspectionRecord>

    @Query("SELECT * FROM inspection_records ORDER BY timestamp DESC")
    fun getAllInspections(): Flow<List<InspectionRecord>>
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM inspector_profiles WHERE id = 1")
    fun getProfile(): Flow<InspectorProfile?>

    @Query("SELECT * FROM inspector_profiles WHERE id = 1")
    suspend fun getProfileOnce(): InspectorProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: InspectorProfile)
}
