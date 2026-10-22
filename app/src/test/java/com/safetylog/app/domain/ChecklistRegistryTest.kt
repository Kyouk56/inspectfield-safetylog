package com.safetylog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistRegistryTest {

    @Test
    fun testAllProtocolsHaveFiveItems() {
        assertEquals(6, ChecklistRegistry.allProtocols.size)
        ChecklistRegistry.allProtocols.forEach { protocol ->
            assertEquals(
                "Protocol ${protocol.categoryName} must have exactly 5 verification items",
                5,
                protocol.items.size
            )
            assertTrue(
                "Protocol ${protocol.categoryName} must have non-empty common defect tags",
                protocol.commonDefectTags.isNotEmpty()
            )
        }
    }

    @Test
    fun testTypeMatchingForExtinguisher() {
        val checklist = ChecklistRegistry.getChecklistForType("10lb ABC Dry Chemical Extinguisher")
        assertEquals("Fire Extinguisher", checklist.categoryName)
        assertTrue(checklist.standardTitle.contains("NFPA 10"))
    }

    @Test
    fun testTypeMatchingForAed() {
        val checklist = ChecklistRegistry.getChecklistForType("ZOLL AED Defibrillator 3")
        assertEquals("Automated External Defibrillator", checklist.categoryName)
        assertTrue(checklist.standardTitle.contains("AHA & OSHA"))
    }

    @Test
    fun testTypeMatchingForEmergencyLight() {
        val checklist = ChecklistRegistry.getChecklistForType("Emergency Exit Light & Battery")
        assertEquals("Emergency & Exit Lighting", checklist.categoryName)
    }

    @Test
    fun testTypeMatchingForEyeWash() {
        val checklist = ChecklistRegistry.getChecklistForType("Chemical Shower and Eye Wash Bay")
        assertEquals("Emergency Eyewash & Shower", checklist.categoryName)
    }

    @Test
    fun testTypeMatchingForFireDoor() {
        val checklist = ChecklistRegistry.getChecklistForType("Main Corridor 2-Hour Fire Door")
        assertEquals("Fire Door Assembly", checklist.categoryName)
    }

    @Test
    fun testFallbackToGeneralSafety() {
        val checklist = ChecklistRegistry.getChecklistForType("Custom Industrial Machinery Crane")
        assertEquals("Commercial Safety Equipment", checklist.categoryName)
    }

    @Test
    fun testGetChecklistByNameExact() {
        val checklist = ChecklistRegistry.getChecklistByName("Fire Extinguisher")
        assertNotNull(checklist)
        assertEquals("Fire Extinguisher", checklist.categoryName)
    }
}
