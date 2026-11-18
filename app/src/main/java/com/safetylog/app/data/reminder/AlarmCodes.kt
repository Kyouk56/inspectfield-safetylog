package com.safetylog.app.data.reminder

/**
 * Stable request code for an equipment's alarm and notification actions (D-10).
 *
 * PendingIntent identity is (requestCode + Intent filter). The alarm, primary action and
 * secondary action therefore share one base code but use distinct Intent actions (see
 * InspectionAlarmReceiver), so no XOR-derived code can collide with another asset.
 * The 29-bit mask leaves room for future per-asset codes and is unique for all realistic
 * auto-increment SQLite ids (up to ~537 million assets).
 */
object AlarmCodes {
    private const val BASE_MASK = 0x1FFFFFFF

    fun base(equipmentId: Long): Int = (equipmentId and BASE_MASK.toLong()).toInt()
}
