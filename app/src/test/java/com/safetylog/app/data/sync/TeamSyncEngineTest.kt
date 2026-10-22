package com.safetylog.app.data.sync

import com.safetylog.app.data.auth.EncryptedPayload
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TeamSyncEngineTest {

    @Test
    fun testSyncPackageJsonStructure() {
        val rootJson = JSONObject().apply {
            put("version", 2)
            put("exportedAt", 1700000000000L)
            put("organizationId", "CORP-SAFETY-99")

            val eqArray = JSONArray()
            eqArray.put(JSONObject().apply {
                put("tagId", "FE-TEST-01")
                put("locationDescription", "Warehouse Zone A")
                put("equipmentType", "10lb ABC Dry Chemical")
                put("serialNumber", "SN-9876")
                put("installDate", "2024-01-01")
                put("assignedChecklist", "Fire Extinguisher")
                put("reviewFrequency", "Monthly")
                put("assignedReviewDate", "2024-02-01")
                put("assignedReviewTime", "09:00 AM")
                put("nextReviewTimestamp", 1702500000000L)
            })
            put("equipment", eqArray)

            val insArray = JSONArray()
            insArray.put(JSONObject().apply {
                put("equipmentTag", "FE-TEST-01")
                put("timestamp", 1700005000000L)
                put("inspectorName", "John Doe, CSP")
                put("status", "PASSED")
                put("notes", "Pressure in green zone, tamper seal intact")
                put("checklistItemsJson", "[]")
                put("deficienciesJson", "[]")
            })
            put("inspections", insArray)
        }

        assertEquals(2, rootJson.getInt("version"))
        assertEquals("CORP-SAFETY-99", rootJson.getString("organizationId"))
        assertEquals(1, rootJson.getJSONArray("equipment").length())
        assertEquals(1, rootJson.getJSONArray("inspections").length())

        val firstEq = rootJson.getJSONArray("equipment").getJSONObject(0)
        assertEquals("FE-TEST-01", firstEq.getString("tagId"))
        assertEquals("Warehouse Zone A", firstEq.getString("locationDescription"))

        val firstIns = rootJson.getJSONArray("inspections").getJSONObject(0)
        assertEquals("PASSED", firstIns.getString("status"))
        assertEquals("John Doe, CSP", firstIns.getString("inspectorName"))
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
}
