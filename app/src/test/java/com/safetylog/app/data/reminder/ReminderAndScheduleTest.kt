package com.safetylog.app.data.reminder

import com.safetylog.app.data.model.EquipmentItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderAndScheduleTest {

    @Test
    fun testCalendarRRuleFrequencies() {
        assertEquals("FREQ=DAILY", InspectionReminderManager.getCalendarRRule("Daily"))
        assertEquals("FREQ=WEEKLY", InspectionReminderManager.getCalendarRRule("Weekly"))
        assertEquals("FREQ=WEEKLY;INTERVAL=2", InspectionReminderManager.getCalendarRRule("Bi-Weekly"))
        assertEquals("FREQ=MONTHLY", InspectionReminderManager.getCalendarRRule("Monthly"))
        assertEquals("FREQ=MONTHLY;INTERVAL=3", InspectionReminderManager.getCalendarRRule("Quarterly"))
        assertEquals("FREQ=MONTHLY;INTERVAL=6", InspectionReminderManager.getCalendarRRule("Semi-Annually"))
        assertEquals("FREQ=YEARLY", InspectionReminderManager.getCalendarRRule("Annually"))
        assertEquals("", InspectionReminderManager.getCalendarRRule("Custom Unknown"))
    }

    @Test
    fun testEquipmentItemReminderDefaults() {
        val item = EquipmentItem(
            tagId = "FE-TEST-001",
            equipmentType = "Fire Extinguisher",
            locationDescription = "Warehouse Sector A"
        )
        assertTrue("Reminders should be enabled by default for new equipment", item.reminderEnabled)
        assertEquals("Default lead time should be 1 day prior", 1, item.reminderLeadDays)
    }

    @Test
    fun testLeadTimeCalculations() {
        val oneDayMillis = 24L * 60L * 60L * 1000L
        val threeDaysMillis = 3L * 24L * 60L * 60L * 1000L

        val targetTimestamp = 1775000000000L

        val item1DayLead = EquipmentItem(
            tagId = "FE-01",
            equipmentType = "Fire Extinguisher",
            locationDescription = "Main Hall",
            reminderEnabled = true,
            reminderLeadDays = 1,
            nextReviewTimestamp = targetTimestamp
        )

        val trigger1Day = item1DayLead.nextReviewTimestamp - (item1DayLead.reminderLeadDays.toLong() * oneDayMillis)
        assertEquals(targetTimestamp - oneDayMillis, trigger1Day)

        val item3DayLead = item1DayLead.copy(reminderLeadDays = 3)
        val trigger3Day = item3DayLead.nextReviewTimestamp - (item3DayLead.reminderLeadDays.toLong() * oneDayMillis)
        assertEquals(targetTimestamp - threeDaysMillis, trigger3Day)

        val itemSameDay = item1DayLead.copy(reminderLeadDays = 0)
        val triggerSameDay = itemSameDay.nextReviewTimestamp - (itemSameDay.reminderLeadDays.toLong() * oneDayMillis)
        assertEquals(targetTimestamp, triggerSameDay)
    }

    @Test
    fun testReminderChannelAndActionConstants() {
        assertEquals("safety_inspection_reminders", InspectionReminderManager.CHANNEL_ID)
        assertEquals("Inspection Due Date Reminders", InspectionReminderManager.CHANNEL_NAME)
        assertEquals("com.safetylog.app.ACTION_INSPECTION_REMINDER", InspectionReminderManager.ACTION_INSPECTION_REMINDER)
        assertEquals("extra_equipment_id", InspectionReminderManager.EXTRA_EQUIPMENT_ID)
        assertEquals("extra_tag_id", InspectionReminderManager.EXTRA_TAG_ID)
    }
}
