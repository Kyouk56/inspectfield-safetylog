package com.safetylog.app.domain

import com.safetylog.app.data.dao.InspectionDao
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

    fun countInspectionsThisMonthFlow(): kotlinx.coroutines.flow.Flow<Int> {
        return inspectionDao.countInspectionsSinceFlow(getStartOfMonthTimestamp())
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
