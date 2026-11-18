package com.safetylog.app.domain

import com.safetylog.app.data.dao.InspectionDao
import com.safetylog.app.data.model.InspectionRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EntitlementManagerTest {

    private lateinit var fakeInspectionDao: FakeInspectionDao
    private lateinit var entitlementManager: EntitlementManager

    class FakeInspectionDao : InspectionDao {
        var countToReturn = 0

        override suspend fun insertInspection(record: InspectionRecord): Long = 1L
        override suspend fun insertInspectionIfUnderQuota(record: InspectionRecord, startOfMonth: Long, limit: Int): Long? {
            if (countToReturn >= limit) return null
            countToReturn++
            return 1L
        }
        override suspend fun updateInspection(record: InspectionRecord) = Unit
        override suspend fun getInspectionBySyncId(syncId: String): InspectionRecord? = null
        override fun getInspectionsForEquipment(equipmentId: Long): Flow<List<InspectionRecord>> = flowOf(emptyList())
        override suspend fun countInspectionsSince(startOfMonth: Long): Int = countToReturn
        override fun countInspectionsSinceFlow(startOfMonth: Long): Flow<Int> = flowOf(countToReturn)
        override suspend fun getInspectionsInRange(startDate: Long, endDate: Long): List<InspectionRecord> = emptyList()
        override fun getAllInspections(): Flow<List<InspectionRecord>> = flowOf(emptyList())
    }

    @Before
    fun setUp() {
        fakeInspectionDao = FakeInspectionDao()
        entitlementManager = EntitlementManager(fakeInspectionDao)
    }

    @Test
    fun atomicInsertEnforcesFreeQuota() = runBlocking {
        val record = InspectionRecord(
            equipmentId = 1L,
            isPressureOk = true,
            isHoseIntact = true,
            isPinSealed = true,
            isSignagePresent = true,
            isAccessUnobstructed = true,
            overallStatus = "PASSED",
            signaturePath = "/tmp/sig.png"
        )
        fakeInspectionDao.countToReturn = 5
        assertEquals(null, entitlementManager.tryCreateInspection(record, isProUser = false))

        fakeInspectionDao.countToReturn = 4
        assertEquals(1L, entitlementManager.tryCreateInspection(record, isProUser = false))
        assertEquals(5, fakeInspectionDao.countToReturn)

        // Pro users bypass the quota entirely.
        assertEquals(1L, entitlementManager.tryCreateInspection(record, isProUser = true))
    }

    @Test
    fun testFreeUserUnderLimitCanCreateInspection() = runBlocking {
        fakeInspectionDao.countToReturn = 2
        val canCreate = entitlementManager.canCreateInspection(isProUser = false)
        assertTrue(canCreate)

        val remaining = entitlementManager.getRemainingFreeInspections(isProUser = false)
        assertEquals(3, remaining)
    }

    @Test
    fun testFreeUserAtLimitCannotCreateInspection() = runBlocking {
        fakeInspectionDao.countToReturn = 5
        val canCreate = entitlementManager.canCreateInspection(isProUser = false)
        assertFalse(canCreate)

        val remaining = entitlementManager.getRemainingFreeInspections(isProUser = false)
        assertEquals(0, remaining)
    }

    @Test
    fun testFreeUserOverLimitCoercesToZero() = runBlocking {
        fakeInspectionDao.countToReturn = 8
        val canCreate = entitlementManager.canCreateInspection(isProUser = false)
        assertFalse(canCreate)

        val remaining = entitlementManager.getRemainingFreeInspections(isProUser = false)
        assertEquals(0, remaining)
    }

    @Test
    fun testProUserHasUnlimitedInspections() = runBlocking {
        fakeInspectionDao.countToReturn = 100
        val canCreate = entitlementManager.canCreateInspection(isProUser = true)
        assertTrue(canCreate)

        val remaining = entitlementManager.getRemainingFreeInspections(isProUser = true)
        assertEquals(Int.MAX_VALUE, remaining)
    }
}
