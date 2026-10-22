package com.safetylog.app.data.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.safetylog.app.MainActivity
import com.safetylog.app.R

class InspectionAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        if (intent.action != InspectionReminderManager.ACTION_INSPECTION_REMINDER) return

        val equipmentId = intent.getLongExtra(InspectionReminderManager.EXTRA_EQUIPMENT_ID, -1L)
        if (equipmentId <= 0L) return

        val tagId = intent.getStringExtra(InspectionReminderManager.EXTRA_TAG_ID) ?: "Asset"
        val equipmentType = intent.getStringExtra(InspectionReminderManager.EXTRA_EQUIPMENT_TYPE) ?: "Equipment"
        val location = intent.getStringExtra(InspectionReminderManager.EXTRA_LOCATION) ?: "Site"
        val dueDate = intent.getStringExtra(InspectionReminderManager.EXTRA_DUE_DATE) ?: "Today"
        val dueTime = intent.getStringExtra(InspectionReminderManager.EXTRA_DUE_TIME) ?: "09:00 AM"

        InspectionReminderManager.initNotificationChannel(context)

        // Main Tap Intent: opens inspection form for this equipment
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            action = "ACTION_INSPECT_EQUIPMENT"
            putExtra("equipmentId", equipmentId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            equipmentId.toInt(),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action Button: View History
        val historyIntent = Intent(context, MainActivity::class.java).apply {
            action = "ACTION_VIEW_HISTORY"
            putExtra("equipmentId", equipmentId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val historyPendingIntent = PendingIntent.getActivity(
            context,
            (equipmentId + 100000).toInt(),
            historyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Inspection Due: $tagId ($equipmentType)"
        val summary = "Scheduled review due at $location on $dueDate at $dueTime."
        val bigText = "Mandatory safety compliance check required for $tagId ($equipmentType).\n" +
                "Location: $location\n" +
                "Scheduled Due: $dueDate • $dueTime\n" +
                "Tap to complete checklist audit and sign."

        val notification = NotificationCompat.Builder(context, InspectionReminderManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Inspect Now", mainPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "View History", historyPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(equipmentId.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission revoked or not granted
        }
    }
}
