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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE equipment_items ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE equipment_items ADD COLUMN reminderLeadDays INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getDatabase(context: Context): SafetyLogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SafetyLogDatabase::class.java,
                    "safetylog_db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
