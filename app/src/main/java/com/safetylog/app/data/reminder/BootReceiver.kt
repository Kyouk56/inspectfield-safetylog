package com.safetylog.app.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safetylog.app.data.SafetyLogDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = SafetyLogDatabase.getDatabase(context)
                val allEquipment = db.equipmentDao().getAllEquipment()
                val reminderManager = InspectionReminderManager(context)

                val activeReminders = allEquipment.filter {
                    it.reminderEnabled && it.nextReviewTimestamp > System.currentTimeMillis()
                }
                reminderManager.rescheduleAll(activeReminders)
            } catch (_: Exception) {
                // Non-fatal
            } finally {
                pendingResult.finish()
            }
        }
    }
}
