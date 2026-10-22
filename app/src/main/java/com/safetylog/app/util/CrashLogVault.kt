package com.safetylog.app.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.safetylog.app.data.auth.SecureTokenStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Diagnostic and crash log entry stored in the local encrypted vault
 */
data class CrashVaultEntry(
    val timestamp: Long,
    val dateFormatted: String,
    val threadName: String,
    val exceptionType: String,
    val message: String,
    val encryptedStackTrace: String,
    val deviceModel: String,
    val osVersion: String
)

/**
 * Privacy-First Hybrid Crash Vault:
 * Stores crash and diagnostic traces encrypted in the local Android Keystore vault.
 * Provides export capability for field compliance audits and an opt-in toggle for cloud telemetry.
 */
object CrashLogVault {

    private const val PREFS_NAME = "safetylog_crash_vault"
    private const val KEY_CRASH_LOGS_JSON = "encrypted_crash_logs"
    private const val KEY_CLOUD_TELEMETRY_OPT_IN = "opt_in_cloud_telemetry"
    private const val MAX_LOGS = 50

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var isInstalled = false

    fun install(context: Context, secureStorage: SecureTokenStorage) {
        if (isInstalled) return
        isInstalled = true

        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(context, secureStorage, thread, throwable)
            } catch (_: Exception) {}

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun recordCrash(
        context: Context,
        secureStorage: SecureTokenStorage,
        thread: Thread,
        throwable: Throwable
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val rawTrace = sw.toString()

        val encryptedTrace = secureStorage.encryptString(rawTrace)

        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val entry = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("date", dateStr)
            put("thread", thread.name)
            put("exception", throwable.javaClass.simpleName)
            put("message", throwable.message ?: "No error message provided")
            put("encryptedTrace", encryptedTrace)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("androidVersion", "API ${Build.VERSION.SDK_INT}")
        }

        val existingStr = prefs.getString(KEY_CRASH_LOGS_JSON, "[]") ?: "[]"
        val array = try { JSONArray(existingStr) } catch (_: Exception) { JSONArray() }

        array.put(entry)

        // Keep at most MAX_LOGS
        val trimmed = JSONArray()
        val startIndex = (array.length() - MAX_LOGS).coerceAtLeast(0)
        for (i in startIndex until array.length()) {
            trimmed.put(array.get(i))
        }

        prefs.edit().putString(KEY_CRASH_LOGS_JSON, trimmed.toString()).commit()
    }

    fun isCloudTelemetryEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CLOUD_TELEMETRY_OPT_IN, false)
    }

    fun setCloudTelemetryEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CLOUD_TELEMETRY_OPT_IN, enabled).apply()
    }

    fun getCrashCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val str = prefs.getString(KEY_CRASH_LOGS_JSON, "[]") ?: "[]"
        return try { JSONArray(str).length() } catch (_: Exception) { 0 }
    }

    /**
     * Generates a plain-text/JSON diagnostic report for export to tech support or auditors
     */
    fun exportDiagnosticReport(context: Context, secureStorage: SecureTokenStorage): File {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val str = prefs.getString(KEY_CRASH_LOGS_JSON, "[]") ?: "[]"
        val array = try { JSONArray(str) } catch (_: Exception) { JSONArray() }

        val cacheDir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val reportFile = File(cacheDir, "SafetyLog_Diagnostic_Vault_${System.currentTimeMillis()}.txt")

        val sb = StringBuilder()
        sb.append("============================================================\n")
        sb.append("       SAFETYLOG COMPLIANCE & AUDIT VAULT DIAGNOSTICS       \n")
        sb.append("============================================================\n")
        sb.append("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("Total Stored Incidents: ${array.length()}\n")
        sb.append("Cloud Telemetry Opt-In: ${if (isCloudTelemetryEnabled(context)) "Enabled" else "Disabled (Privacy-First Local Vault)"}\n\n")

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            sb.append("------------------------------------------------------------\n")
            sb.append("Incident #${i + 1} | ${obj.optString("date")} | Thread: ${obj.optString("thread")}\n")
            sb.append("Exception: ${obj.optString("exception")}: ${obj.optString("message")}\n")
            val encTrace = obj.optString("encryptedTrace")
            val decrypted = secureStorage.decryptString(encTrace) ?: "[ENCRYPTED DATA CORRUPTED OR KEYS ROTATED]"
            sb.append("Stack Trace:\n$decrypted\n\n")
        }

        FileOutputStream(reportFile).use { fos ->
            fos.write(sb.toString().toByteArray(Charsets.UTF_8))
        }

        return reportFile
    }

    fun clearVault(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CRASH_LOGS_JSON).apply()
    }
}
