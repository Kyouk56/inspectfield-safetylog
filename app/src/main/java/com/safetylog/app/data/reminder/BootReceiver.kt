package com.safetylog.app.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safetylog.app.data.SafetyLogDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(9_000L) {
                    val db = SafetyLogDatabase.getDatabase(context)
                    val allEquipment = db.equipmentDao().getAllEquipment()
                    val reminderManager = InspectionReminderManager(context)

                    val activeReminders = allEquipment.filter {
                        it.reminderEnabled && it.nextReviewTimestamp > System.currentTimeMillis()
                    }
                    reminderManager.rescheduleAll(activeReminders) { isActive }
                }
            } catch (_: Exception) {
                // Non-fatal
            } finally {
                pendingResult.finish()
            }
        }
    }
}
