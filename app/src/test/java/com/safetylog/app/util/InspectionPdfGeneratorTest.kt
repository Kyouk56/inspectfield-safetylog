package com.safetylog.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InspectionPdfGeneratorTest {

    @Test
    fun tagIdsAreSanitizedForPdfFilenames() {
        assertEquals("FEEXT12", sanitizePdfFileNamePart("FE/EXT\\12"))
        assertEquals("FE-TEST-01", sanitizePdfFileNamePart("FE-TEST-01"))
        assertEquals("etcpasswd", sanitizePdfFileNamePart("../../etc/passwd"))
        assertFalse(sanitizePdfFileNamePart("..\\..\\").contains(".."))
        assertFalse(sanitizePdfFileNamePart("A:B*C?D\"E<F>G|H").contains(":"))
    }

    @Test
    fun blankTagIdFallsBackToSafeName() {
        assertEquals("asset", sanitizePdfFileNamePart(""))
        assertEquals("asset", sanitizePdfFileNamePart("   "))
        assertEquals("asset", sanitizePdfFileNamePart(".."))
    }
}
