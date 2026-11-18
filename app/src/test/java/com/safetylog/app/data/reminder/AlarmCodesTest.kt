package com.safetylog.app.data.reminder

import org.junit.Assert.*
import org.junit.Test

class AlarmCodesTest {

    @Test
    fun baseCodesAreUniqueForRealisticAssetIds() {
        val ids = listOf(1L, 2L, 3L, 999L, 100_000L, 1_000_000L)
        val codes = ids.map { AlarmCodes.base(it) }
        assertEquals(ids.size, codes.toSet().size)
        assertTrue(codes.all { it >= 0 && it <= 0x1FFFFFFF })
    }

    @Test
    fun sameIdAlwaysDerivesTheSameCode() {
        assertEquals(AlarmCodes.base(42L), AlarmCodes.base(42L))
    }
}
