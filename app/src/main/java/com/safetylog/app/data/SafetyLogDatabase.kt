package com.safetylog.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.safetylog.app.data.dao.EquipmentDao
import com.safetylog.app.data.dao.InspectionDao
import com.safetylog.app.data.dao.ProfileDao
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.data.model.InspectionRecord
import com.safetylog.app.data.model.InspectorProfile

@Database(
    entities = [InspectorProfile::class, EquipmentItem::class, InspectionRecord::class],
    version = 3,
    exportSchema = false
)
abstract class SafetyLogDatabase : RoomDatabase() {
    abstract fun equipmentDao(): EquipmentDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var INSTANCE: SafetyLogDatabase? = null

        private fun ensureReminderColumns(db: SupportSQLiteDatabase) {
            val columns = db.query("PRAGMA table_info(equipment_items)").use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    }
                }
            }
            if ("reminderEnabled" !in columns) {
                db.execSQL("ALTER TABLE equipment_items ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 1")
            }
            if ("reminderLeadDays" !in columns) {
                db.execSQL("ALTER TABLE equipment_items ADD COLUMN reminderLeadDays INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureReminderColumns(db)
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureReminderColumns(db)
        }

        fun getDatabase(context: Context): SafetyLogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SafetyLogDatabase::class.java,
                    "safetylog_db"
                )
                    // No destructive fallback: an unmapped version bump must fail loudly
                    // instead of silently deleting the entire inspection audit history.
                    .addMigrations(MIGRATION_1_3, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
