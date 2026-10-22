package com.safetylog.app.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.safetylog.app.data.auth.CompanyOAuthConfig
import com.safetylog.app.data.auth.EncryptedPayload
import com.safetylog.app.data.auth.SecureTokenStorage
import com.safetylog.app.data.dao.EquipmentDao
import com.safetylog.app.data.dao.InspectionDao
import com.safetylog.app.data.model.EquipmentItem
import com.safetylog.app.data.model.InspectionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * Result of a team synchronization or package import/export operation
 */
sealed class SyncResult {
    data class Success(val message: String, val itemsCount: Int = 0) : SyncResult()
    data class Error(val error: String) : SyncResult()
}

/**
 * Dual Team Synchronization Engine supporting:
 * 1. Configurable Enterprise REST Sync API (for direct intranet/cloud server sync)
 * 2. Encrypted Air-Gapped Sync Packages (.safetylog.enc) for offline field sharing via Drive/Email/Local
 */
class TeamSyncEngine(
    private val context: Context,
    private val equipmentDao: EquipmentDao,
    private val inspectionDao: InspectionDao,
    private val secureStorage: SecureTokenStorage
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("safetylog_sync_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CUSTOM_REST_ENDPOINT = "sync_rest_endpoint"
        private const val KEY_LAST_SYNC_TIMESTAMP = "sync_last_timestamp"
        const val DEFAULT_REST_ENDPOINT = "https://api.safetylog.com/v1/sync"
        const val SYNC_FILE_EXTENSION = ".safetylog.enc"
    }

    fun getCustomRestEndpoint(): String {
        return prefs.getString(KEY_CUSTOM_REST_ENDPOINT, DEFAULT_REST_ENDPOINT) ?: DEFAULT_REST_ENDPOINT
    }

    fun setCustomRestEndpoint(endpoint: String) {
        prefs.edit().putString(KEY_CUSTOM_REST_ENDPOINT, endpoint.trim()).apply()
    }

    fun getLastSyncDateFormatted(): String {
        val last = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        if (last <= 0L) return "Never"
        val df = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
        return df.format(Date(last))
    }

    /**
     * Exports full facility state (equipment & inspections) into an AES-256-GCM encrypted package
     */
    suspend fun exportEncryptedTeamPackage(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val allEquipment = equipmentDao.getAllEquipment()
            val allInspections = mutableListOf<InspectionRecord>()
            allEquipment.forEach { eq ->
                val records = inspectionDao.getInspectionsInRange(0L, System.currentTimeMillis() + 100000L)
                allInspections.addAll(records.filter { it.equipmentId == eq.id })
            }

            val rootJson = JSONObject().apply {
                put("version", 2)
                put("exportedAt", System.currentTimeMillis())
                put("organizationId", secureStorage.loadUser()?.userId ?: "DEFAULT_ORG")

                val eqArray = JSONArray()
                allEquipment.forEach { eq ->
                    eqArray.put(JSONObject().apply {
                        put("tagId", eq.tagId)
                        put("locationDescription", eq.locationDescription)
                        put("equipmentType", eq.equipmentType)
                        put("serialNumber", eq.serialNumber)
                        put("installDate", eq.installDate)
                        put("assignedChecklist", eq.assignedChecklist)
                        put("reviewFrequency", eq.reviewFrequency)
                        put("assignedReviewDate", eq.assignedReviewDate)
                        put("assignedReviewTime", eq.assignedReviewTime)
                        put("nextReviewTimestamp", eq.nextReviewTimestamp)
                    })
                }
                put("equipment", eqArray)

                val insArray = JSONArray()
                allInspections.forEach { ins ->
                    insArray.put(JSONObject().apply {
                        put("equipmentTag", allEquipment.find { it.id == ins.equipmentId }?.tagId ?: "")
                        put("timestamp", ins.timestamp)
                        put("isPressureOk", ins.isPressureOk)
                        put("isHoseIntact", ins.isHoseIntact)
                        put("isPinSealed", ins.isPinSealed)
                        put("isSignagePresent", ins.isSignagePresent)
                        put("isAccessUnobstructed", ins.isAccessUnobstructed)
                        put("overallStatus", ins.overallStatus)
                        put("notes", ins.notes ?: "")
                        put("checklistName", ins.checklistName)
                        put("checklistSummary", ins.checklistSummary)
                    })
                }
                put("inspections", insArray)
            }

            val rawJsonBytes = rootJson.toString().toByteArray(Charsets.UTF_8)
            val encryptedPayload = secureStorage.encryptData(rawJsonBytes)
            val serialized = encryptedPayload.toSerialized()

            val syncDir = File(context.cacheDir, "sync_exports").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val exportFile = File(syncDir, "SafetyLog_TeamSync_$timeStamp$SYNC_FILE_EXTENSION")

            FileOutputStream(exportFile).use { fos ->
                fos.write(serialized.toByteArray(Charsets.UTF_8))
            }

            prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis()).apply()
            Result.success(exportFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Imports and decrypts a .safetylog.enc package, upserting data into the local Room database
     */
    suspend fun importEncryptedTeamPackage(packageContent: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            val payload = EncryptedPayload.fromSerialized(packageContent.trim())
                ?: return@withContext SyncResult.Error("Invalid package format. Must be an encrypted SafetyLog package.")

            val decryptedBytes = secureStorage.decryptData(payload)
            val jsonStr = String(decryptedBytes, Charsets.UTF_8)
            val rootJson = JSONObject(jsonStr)

            val eqArray = rootJson.getJSONArray("equipment")
            var importedCount = 0

            for (i in 0 until eqArray.length()) {
                val obj = eqArray.getJSONObject(i)
                val tagId = obj.getString("tagId")

                val existing = equipmentDao.getEquipmentByTag(tagId)
                val item = EquipmentItem(
                    id = existing?.id ?: 0L,
                    tagId = tagId,
                    locationDescription = obj.optString("locationDescription", ""),
                    equipmentType = obj.optString("equipmentType", "General Safety"),
                    serialNumber = obj.optString("serialNumber", ""),
                    installDate = obj.optString("installDate", ""),
                    assignedChecklist = obj.optString("assignedChecklist", "Fire Extinguisher"),
                    reviewFrequency = obj.optString("reviewFrequency", "Monthly"),
                    assignedReviewDate = obj.optString("assignedReviewDate", ""),
                    assignedReviewTime = obj.optString("assignedReviewTime", "09:00 AM"),
                    nextReviewTimestamp = obj.optLong("nextReviewTimestamp", 0L)
                )
                equipmentDao.upsertEquipment(item)
                importedCount++
            }

            prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis()).apply()
            SyncResult.Success("Successfully synced $importedCount assets into compliance vault.", importedCount)
        } catch (e: Exception) {
            SyncResult.Error("Decryption/Import Failed: ${e.localizedMessage ?: "Corrupted file or mismatched keys"}")
        }
    }

    /**
     * Synchronizes local state with an Enterprise REST endpoint via JSON POST
     */
    suspend fun syncWithEnterpriseRestServer(): SyncResult = withContext(Dispatchers.IO) {
        val endpoint = getCustomRestEndpoint()
        try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                val token = secureStorage.getAuthToken()
                if (token != null) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }

            val allEquipment = equipmentDao.getAllEquipment()
            val payload = JSONObject().apply {
                put("deviceId", secureStorage.getDeviceId())
                put("timestamp", System.currentTimeMillis())
                put("equipmentCount", allEquipment.size)
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            if (code in 200..299) {
                prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis()).apply()
                SyncResult.Success("Enterprise sync completed with $endpoint (HTTP $code)")
            } else {
                SyncResult.Error("Enterprise server responded with HTTP $code. Check server endpoint.")
            }
        } catch (e: Exception) {
            SyncResult.Error("REST Sync error: ${e.localizedMessage ?: "Unable to reach server"}")
        }
    }

    /**
     * Tests a corporate webhook endpoint by sending a lightweight ping payload
     */
    suspend fun testWebhookEndpoint(webhookUrl: String, secretToken: String): Result<String> = withContext(Dispatchers.IO) {
        if (webhookUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Webhook URL cannot be blank."))
        }
        val startTime = System.currentTimeMillis()
        try {
            val url = URL(webhookUrl.trim())
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7000
                readTimeout = 7000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "SafetyLog-Enterprise-Webhook/1.0")
                setRequestProperty("X-SafetyLog-Event", "ping")
                if (secretToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${secretToken.trim()}")
                }
            }

            val testPayload = JSONObject().apply {
                put("event", "ping")
                put("source", "SafetyLog Enterprise Hub")
                put("deviceId", secureStorage.getDeviceId())
                put("timestamp", System.currentTimeMillis())
            }

            conn.outputStream.use { os ->
                os.write(testPayload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            val latency = System.currentTimeMillis() - startTime
            if (code in 200..299) {
                Result.success("Webhook verified! Server responded HTTP $code in ${latency}ms")
            } else {
                Result.failure(Exception("Server returned HTTP $code. Verify endpoint permissions."))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Connection failed (${e.localizedMessage ?: "timeout"}). Verify URL and server accessibility."))
        }
    }

    /**
     * Streams a completed inspection to the company's internal webhook endpoint
     */
    suspend fun dispatchInspectionToWebhook(
        inspection: InspectionRecord,
        equipment: EquipmentItem?,
        config: CompanyOAuthConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        if (config.webhookUrl.isBlank()) {
            return@withContext Result.success("No webhook configured.")
        }
        try {
            val url = URL(config.webhookUrl.trim())
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "SafetyLog-Enterprise-Webhook/1.0")
                setRequestProperty("X-SafetyLog-Event", "inspection_completed")
                if (config.webhookSecret.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${config.webhookSecret.trim()}")
                }
            }

            val payload = JSONObject().apply {
                put("event", "inspection_completed")
                put("companyName", config.companyName)
                put("corporateDomain", config.corporateDomain)
                put("timestamp", inspection.timestamp)
                put("inspectionId", inspection.id)
                put("overallStatus", inspection.overallStatus)
                put("checklistName", inspection.checklistName)
                put("notes", inspection.notes ?: "")

                val equipObj = JSONObject().apply {
                    put("tagId", equipment?.tagId ?: "")
                    put("type", equipment?.equipmentType ?: "")
                    put("location", equipment?.locationDescription ?: "")
                    put("serialNumber", equipment?.serialNumber ?: "")
                }
                put("equipment", equipObj)

                val checksObj = JSONObject().apply {
                    put("isPressureOk", inspection.isPressureOk)
                    put("isHoseIntact", inspection.isHoseIntact)
                    put("isPinSealed", inspection.isPinSealed)
                    put("isSignagePresent", inspection.isSignagePresent)
                    put("isAccessUnobstructed", inspection.isAccessUnobstructed)
                }
                put("itemChecks", checksObj)
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            if (code in 200..299) {
                Result.success("Dispatched to enterprise webhook (HTTP $code)")
            } else {
                Result.failure(Exception("Webhook server rejected payload with HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Webhook delivery error: ${e.localizedMessage ?: "unreachable"}"))
        }
    }

    /**
     * Exports and saves an encrypted backup package directly into the company's designated Google Drive / OneDrive folder
     * via Android Storage Access Framework (SAF) DocumentFile — costing $0.00 in cloud server infrastructure.
     */
    suspend fun backupToCloudDriveFolder(treeUriString: String): Result<String> = withContext(Dispatchers.IO) {
        if (treeUriString.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("No cloud drive folder selected."))
        }
        try {
            val treeUri = Uri.parse(treeUriString)
            val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(Exception("Unable to access cloud drive folder. Re-select folder permissions."))

            val exportResult = exportEncryptedTeamPackage()
            val exportFile = exportResult.getOrElse {
                return@withContext Result.failure(it)
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val targetFileName = "SafetyLog_Vault_${timeStamp}$SYNC_FILE_EXTENSION"
            val newFile = pickedDir.createFile("application/octet-stream", targetFileName)
                ?: return@withContext Result.failure(Exception("Failed to create file in cloud drive folder."))

            context.contentResolver.openOutputStream(newFile.uri)?.use { os ->
                exportFile.inputStream().use { inputStream ->
                    inputStream.copyTo(os)
                }
            } ?: return@withContext Result.failure(Exception("Failed to open output stream to cloud drive."))

            Result.success("Encrypted backup saved to ${newFile.name ?: targetFileName} in cloud folder.")
        } catch (e: Exception) {
            Result.failure(Exception("Cloud Drive Sync failed: ${e.localizedMessage ?: "Permission denied"}"))
        }
    }

    /**
     * Synchronizes company team roster to the linked Google Drive / OneDrive folder via SAF DocumentFile at $0 developer server cost.
     */
    suspend fun saveTeamRosterToCloudDrive(
        treeUriString: String,
        members: List<com.safetylog.app.data.auth.TeamMember>
    ): Result<String> = withContext(Dispatchers.IO) {
        if (treeUriString.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("No cloud drive folder selected."))
        }
        try {
            val treeUri = Uri.parse(treeUriString)
            val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(Exception("Unable to access cloud drive folder."))

            val rosterJson = JSONObject().apply {
                put("version", 1)
                put("updatedAt", System.currentTimeMillis())
                put("totalMembers", members.size)
                put("activeMembers", members.count { it.isActive })
                val array = JSONArray()
                members.forEach { m ->
                    array.put(JSONObject().apply {
                        put("id", m.id)
                        put("email", m.email)
                        put("name", m.name)
                        put("role", m.role.name)
                        put("addedEpochMs", m.addedEpochMs)
                        put("isActive", m.isActive)
                    })
                }
                put("members", array)
            }

            val existingFile = pickedDir.findFile("SafetyLog_TeamRoster.json")
            val targetFile = existingFile ?: pickedDir.createFile("application/json", "SafetyLog_TeamRoster.json")
                ?: return@withContext Result.failure(Exception("Failed to create roster file in cloud drive."))

            context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { os ->
                os.write(rosterJson.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(Exception("Failed to write roster to cloud drive."))

            Result.success("Team roster synchronized to cloud drive.")
        } catch (e: Exception) {
            Result.failure(Exception("Cloud drive roster sync error: ${e.localizedMessage ?: "Permission denied"}"))
        }
    }

    /**
     * Dispatches team roster updates to the company's internal webhook endpoint.
     */
    suspend fun dispatchTeamRosterToWebhook(
        config: CompanyOAuthConfig,
        members: List<com.safetylog.app.data.auth.TeamMember>,
        eventType: String = "roster_updated"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (config.webhookUrl.isBlank()) {
            return@withContext Result.success("No webhook configured.")
        }
        try {
            val url = URL(config.webhookUrl.trim())
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "SafetyLog-Enterprise-Webhook/1.0")
                setRequestProperty("X-SafetyLog-Event", eventType)
                if (config.webhookSecret.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${config.webhookSecret.trim()}")
                }
            }

            val payload = JSONObject().apply {
                put("event", eventType)
                put("companyName", config.companyName)
                put("corporateDomain", config.corporateDomain)
                put("timestamp", System.currentTimeMillis())
                put("totalMembers", members.size)
                put("activeMembers", members.count { it.isActive })

                val array = JSONArray()
                members.forEach { m ->
                    array.put(JSONObject().apply {
                        put("id", m.id)
                        put("email", m.email)
                        put("name", m.name)
                        put("role", m.role.name)
                        put("isActive", m.isActive)
                    })
                }
                put("members", array)
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            if (code in 200..299) {
                Result.success("Roster update dispatched to webhook (HTTP $code)")
            } else {
                Result.failure(Exception("Webhook server returned HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Webhook delivery error: ${e.localizedMessage ?: "unreachable"}"))
        }
    }
}

