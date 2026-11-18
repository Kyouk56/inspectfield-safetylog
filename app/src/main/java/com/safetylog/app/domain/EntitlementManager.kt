package com.safetylog.app.domain

import com.safetylog.app.data.dao.InspectionDao
import com.safetylog.app.data.model.InspectionRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.util.Calendar

class EntitlementManager(private val inspectionDao: InspectionDao) {

    val FREE_MONTHLY_LIMIT = 5

    private fun getStartOfMonthTimestamp(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    suspend fun countInspectionsThisMonth(): Int {
        return inspectionDao.countInspectionsSince(getStartOfMonthTimestamp())
    }

    /**
     * D-11: recompute the month boundary on every emission and once a minute, so a
     * long-lived dashboard does not keep showing last month's quota after rollover.
     */
    fun countInspectionsThisMonthFlow(): Flow<Int> {
        val minuteTick: Flow<Unit> = flow {
            while (true) {
                emit(Unit)
                delay(60_000L)
            }
        }
        return combine(inspectionDao.getAllInspections(), minuteTick) { inspections, _ ->
            val start = getStartOfMonthTimestamp()
            inspections.count { it.timestamp >= start }
        }
    }

    /** D-11: check-and-insert in one serialized operation so concurrent saves cannot
     * both slip past the free-tier quota. Returns null when the quota is exhausted. */
    suspend fun tryCreateInspection(record: InspectionRecord, isProUser: Boolean): Long? {
        if (isProUser) return inspectionDao.insertInspection(record)
        return inspectionDao.insertInspectionIfUnderQuota(record, getStartOfMonthTimestamp(), FREE_MONTHLY_LIMIT)
    }

    suspend fun canCreateInspection(isProUser: Boolean): Boolean {
        if (isProUser) return true
        val countThisMonth = countInspectionsThisMonth()
        return countThisMonth < FREE_MONTHLY_LIMIT
    }

    suspend fun getRemainingFreeInspections(isProUser: Boolean): Int {
        if (isProUser) return Int.MAX_VALUE
        val countThisMonth = countInspectionsThisMonth()
        return (FREE_MONTHLY_LIMIT - countThisMonth).coerceAtLeast(0)
    }
}
