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

        // All three PendingIntents share one base request code; their distinct Intent
        // actions keep them separate (D-10).
        val reqCode = AlarmCodes.base(equipmentId)
        val notificationToken = InspectionReminderManager.getOrCreateNotificationToken(context)

        fun buildActionPendingIntent(intentAction: String): PendingIntent {
            val tapIntent = Intent(context, MainActivity::class.java).apply {
                action = intentAction
                putExtra("equipmentId", equipmentId)
                putExtra(InspectionReminderManager.EXTRA_FROM_NOTIFICATION, true)
                putExtra(InspectionReminderManager.EXTRA_NOTIFICATION_TOKEN, notificationToken)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                reqCode,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val mainPendingIntent = buildActionPendingIntent("ACTION_INSPECT_EQUIPMENT")
        val inspectPendingIntent = buildActionPendingIntent("ACTION_INSPECT_NOW")
        val historyPendingIntent = buildActionPendingIntent("ACTION_VIEW_HISTORY")

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
            .addAction(R.drawable.ic_launcher_foreground, "Inspect Now", inspectPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "View History", historyPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(reqCode, notification)
        } catch (_: SecurityException) {
            // Permission revoked or not granted
        }
    }
}