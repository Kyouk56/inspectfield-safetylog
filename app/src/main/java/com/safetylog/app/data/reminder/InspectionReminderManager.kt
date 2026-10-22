package com.safetylog.app.data.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.safetylog.app.MainActivity
import com.safetylog.app.R
import com.safetylog.app.data.model.EquipmentItem

class InspectionReminderManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "safety_inspection_reminders"
        const val CHANNEL_NAME = "Inspection Due Date Reminders"
        const val CHANNEL_DESC = "Compliance alarms and scheduled inspection reminders for fire safety equipment"

        const val ACTION_INSPECTION_REMINDER = "com.safetylog.app.ACTION_INSPECTION_REMINDER"
        const val EXTRA_EQUIPMENT_ID = "extra_equipment_id"
        const val EXTRA_TAG_ID = "extra_tag_id"
        const val EXTRA_EQUIPMENT_TYPE = "extra_equipment_type"
        const val EXTRA_LOCATION = "extra_location"
        const val EXTRA_DUE_DATE = "extra_due_date"
        const val EXTRA_DUE_TIME = "extra_due_time"
        const val EXTRA_DUE_TIMESTAMP = "extra_due_timestamp"

        const val TEST_NOTIFICATION_ID = 99999

        fun initNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 150, 350)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }

        fun getCalendarRRule(frequency: String): String = when (frequency) {
            "Daily" -> "FREQ=DAILY"
            "Weekly" -> "FREQ=WEEKLY"
            "Bi-Weekly" -> "FREQ=WEEKLY;INTERVAL=2"
            "Monthly" -> "FREQ=MONTHLY"
            "Quarterly" -> "FREQ=MONTHLY;INTERVAL=3"
            "Semi-Annually" -> "FREQ=MONTHLY;INTERVAL=6"
            "Annually" -> "FREQ=YEARLY"
            else -> ""
        }
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * Schedules a proactive Android alarm for an equipment item's next scheduled inspection.
     */
    fun scheduleReminder(equipment: EquipmentItem) {
        if (alarmManager == null) return
        if (!equipment.reminderEnabled || equipment.nextReviewTimestamp <= 0L) {
            cancelReminder(equipment.id)
            return
        }

        val leadMillis = equipment.reminderLeadDays.toLong().coerceAtLeast(0L) * 24L * 60L * 60L * 1000L
        var triggerAtMillis = equipment.nextReviewTimestamp - leadMillis
        val now = System.currentTimeMillis()

        if (triggerAtMillis < now) {
            if (equipment.nextReviewTimestamp > now) {
                // Lead time passed, but inspection is still in the future -> trigger prompt shortly
                triggerAtMillis = now + 60_000L
            } else {
                // Already overdue: don't schedule stale alarm
                return
            }
        }

        val intent = Intent(context, InspectionAlarmReceiver::class.java).apply {
            action = ACTION_INSPECTION_REMINDER
            putExtra(EXTRA_EQUIPMENT_ID, equipment.id)
            putExtra(EXTRA_TAG_ID, equipment.tagId)
            putExtra(EXTRA_EQUIPMENT_TYPE, equipment.equipmentType)
            putExtra(EXTRA_LOCATION, equipment.locationDescription)
            putExtra(EXTRA_DUE_DATE, equipment.assignedReviewDate)
            putExtra(EXTRA_DUE_TIME, equipment.assignedReviewTime)
            putExtra(EXTRA_DUE_TIMESTAMP, equipment.nextReviewTimestamp)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            equipment.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            // Graceful fallback if exact alarms permission restricted by OEM
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    /**
     * Cancels any pending alarm for the given equipment ID.
     */
    fun cancelReminder(equipmentId: Long) {
        if (alarmManager == null) return
        val intent = Intent(context, InspectionAlarmReceiver::class.java).apply {
            action = ACTION_INSPECTION_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            equipmentId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Reschedules alarms for all active equipment.
     */
    fun rescheduleAll(equipmentList: List<EquipmentItem>) {
        equipmentList.forEach { equipment ->
            scheduleReminder(equipment)
        }
    }

    /**
     * Builds a native Android Calendar Contract intent to insert an inspection reminder into Google Calendar / Outlook.
     */
    fun createCalendarIntent(equipment: EquipmentItem): Intent {
        val startMillis = if (equipment.nextReviewTimestamp > 0) equipment.nextReviewTimestamp else System.currentTimeMillis()
        val endMillis = startMillis + (30 * 60 * 1000) // 30 minute standard inspection duration
        val rrule = getCalendarRRule(equipment.reviewFrequency)

        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            putExtra(CalendarContract.Events.TITLE, "Safety Inspection: ${equipment.tagId} (${equipment.equipmentType})")
            putExtra(
                CalendarContract.Events.DESCRIPTION,
                "Mandatory safety compliance inspection for ${equipment.tagId} (${equipment.equipmentType}) at ${equipment.locationDescription}.\n" +
                "Protocol: ${equipment.assignedChecklist}\n" +
                "Recurrence: ${equipment.reviewFrequency}\n" +
                "Logged with SafetyLog Fire & Safety Audit."
            )
            putExtra(CalendarContract.Events.EVENT_LOCATION, equipment.locationDescription)
            if (rrule.isNotBlank()) {
                putExtra(CalendarContract.Events.RRULE, rrule)
            }
            putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        }
    }

    /**
     * Sends an immediate test notification to verify channel audio, vibration, and banner formatting.
     */
    fun sendTestNotification(): Boolean {
        initNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("SafetyLog: Inspection Reminder Test")
            .setContentText("Notification channel verified active. Scheduled compliance alarms will alert you here.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Notification channel verified active!\n" +
                    "Scheduled NFPA and OSHA recurring compliance alarms will alert you before assets become overdue."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
