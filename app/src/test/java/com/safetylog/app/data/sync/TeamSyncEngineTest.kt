package com.safetylog.app.data.sync

import com.safetylog.app.data.auth.EncryptedPayload
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.data.model.InspectionRecord
import org.junit.Assert.*
import org.junit.Test

class TeamSyncEngineTest {

    @Test
    fun exportIncludesReminderSettingsAndStableInspectionIds() {
        val equipment = EquipmentItem(
            id = 7L,
            tagId = "FE-TEST-01",
            locationDescription = "Warehouse Zone A",
            equipmentType = "10lb ABC Dry Chemical",
            reminderEnabled = false,
            reminderLeadDays = 3
        )
        val inspection = InspectionRecord(
            syncId = "stable-sync-id-123",
            equipmentId = 7L,
            isPressureOk = true,
            isHoseIntact = true,
            isPinSealed = true,
            isSignagePresent = true,
            isAccessUnobstructed = true,
            overallStatus = "PASSED",
            signaturePath = "/local/signature.png"
        )
        val root = TeamSyncEngine.buildPackageJson(
            allEquipment = listOf(equipment),
            equipmentById = mapOf(7L to equipment),
            allInspections = listOf(inspection),
            organizationId = "CORP-SAFETY-99",
            cryptoMode = "passphrase"
        )

        assertEquals(PackageCrypto.ENVELOPE_VERSION, root.getInt("version"))
        assertEquals("passphrase", root.getString("cryptoMode"))
        assertEquals("CORP-SAFETY-99", root.getString("organizationId"))

        val eq = root.getJSONArray("equipment").getJSONObject(0)
        assertEquals("FE-TEST-01", eq.getString("tagId"))
        assertFalse(eq.getBoolean("reminderEnabled"))
        assertEquals(3, eq.getInt("reminderLeadDays"))

        val ins = root.getJSONArray("inspections").getJSONObject(0)
        assertEquals("stable-sync-id-123", ins.getString("syncId"))
        assertEquals("FE-TEST-01", ins.getString("equipmentTag"))
        assertEquals("PASSED", ins.getString("overallStatus"))
    }

    @Test
    fun testEncryptedPayloadSerializationRoundtrip() {
        val originalJson = "{\"version\":2,\"organizationId\":\"TEST_ORG\"}"
        val fakeIv = "aXYxMjM0NTY3ODkwMTI="
        val fakeCipher = "Y2lwaGVydGV4dERhdGExMjM0NTY3OA=="

        val payload = EncryptedPayload(ivBase64 = fakeIv, cipherTextBase64 = fakeCipher)
        val serialized = payload.toSerialized()

        val parsed = EncryptedPayload.fromSerialized(serialized)
        assertNotNull(parsed)
        assertEquals(fakeIv, parsed?.ivBase64)
        assertEquals(fakeCipher, parsed?.cipherTextBase64)
    }

    @Test
    fun testDefaultRestEndpoint() {
        assertEquals("https://api.safetylog.com/v1/sync", TeamSyncEngine.DEFAULT_REST_ENDPOINT)
        assertEquals(".safetylog.enc", TeamSyncEngine.SYNC_FILE_EXTENSION)
    }

    @Test
    fun enterpriseEndpointsMustBeHttps() {
        assertTrue(TeamSyncEngine.isHttpsUrl("https://sync.example.com/v1"))
        assertTrue(TeamSyncEngine.isHttpsUrl("  HTTPS://sync.example.com/v1  "))
        assertFalse(TeamSyncEngine.isHttpsUrl("http://sync.example.com/v1"))
        assertFalse(TeamSyncEngine.isHttpsUrl("ftp://sync.example.com/v1"))
        assertFalse(TeamSyncEngine.isHttpsUrl("sync.example.com/v1"))
        assertFalse(TeamSyncEngine.isHttpsUrl(""))
    }
}
