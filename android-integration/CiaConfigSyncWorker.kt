package com.v2ray.ang.ui.ciaconfig

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.v2ray.ang.handler.AngConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

internal object CiaConfigSync {
    const val PREFS = "c2ray_cia_config_sync"
    const val KEY_ENABLED = "enabled"
    const val WORKER_ENDPOINT = "https://c2ray-channel-sync.webserviceamir.workers.dev/api/configs"
    const val KEY_INTERVAL = "interval_minutes"
    const val KEY_HASHES = "imported_hashes"
    const val KEY_LAST_SYNC = "last_sync"
    const val KEY_LAST_IMPORTED = "last_imported"
    const val KEY_LAST_ERROR = "last_error"
    const val KEY_FORCE = "force"
    const val PERIODIC_WORK = "c2ray_cia_config_periodic"
    const val MANUAL_WORK = "c2ray_cia_config_manual"
    const val MIN_INTERVAL_MINUTES = 15
}

object CiaConfigSyncScheduler {
    fun configure(context: Context, enabled: Boolean, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(CiaConfigSync.PERIODIC_WORK)
            return
        }

        val request = PeriodicWorkRequestBuilder<CiaConfigSyncWorker>(
            intervalMinutes.coerceAtLeast(CiaConfigSync.MIN_INTERVAL_MINUTES).toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            CiaConfigSync.PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runNow(context: Context, force: Boolean = true): OneTimeWorkRequest {
        val request = OneTimeWorkRequestBuilder<CiaConfigSyncWorker>()
            .setInputData(workDataOf(CiaConfigSync.KEY_FORCE to force))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            CiaConfigSync.MANUAL_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
        return request
    }
}

class CiaConfigSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(CiaConfigSync.PREFS, Context.MODE_PRIVATE)
        val force = inputData.getBoolean(CiaConfigSync.KEY_FORCE, false)

        if (!force && !prefs.getBoolean(CiaConfigSync.KEY_ENABLED, true)) {
            return Result.success()
        }

        val endpoint = CiaConfigSync.WORKER_ENDPOINT
        if (!endpoint.startsWith("https://", ignoreCase = true)) {
            prefs.edit().putString(CiaConfigSync.KEY_LAST_ERROR, "Set a valid HTTPS Worker URL").apply()
            return Result.failure(workDataOf("error" to "Set a valid HTTPS Worker URL"))
        }

        return try {
            val responseBody = withContext(Dispatchers.IO) {
                val connection = URL(endpoint).openConnection() as? HttpsURLConnection
                    ?: throw IOException("HTTPS endpoint is required")
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 20_000
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("User-Agent", "C2ray-Android/2.0")
                    val status = connection.responseCode
                    if (status !in 200..299) throw IOException("Worker returned HTTP " + status)
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } finally {
                    connection.disconnect()
                }
            }

            val root = JSONObject(responseBody)
            if (!root.optBoolean("ok", false)) throw IOException(root.optString("error", "Worker response is not ok"))
            val configArray = root.optJSONArray("configs") ?: throw IOException("No configs array in Worker response")

            val importedHashes = (prefs.getStringSet(CiaConfigSync.KEY_HASHES, emptySet()) ?: emptySet()).toMutableSet()
            val newUris = mutableListOf<String>()
            val hashesInResponse = mutableSetOf<String>()

            for (index in 0 until configArray.length()) {
                val item = configArray.optJSONObject(index) ?: continue
                val uri = item.optString("uri", "").trim()
                if (!isSupportedConfig(uri)) continue

                val hash = sha256(uri)
                if (hash !in importedHashes && hashesInResponse.add(hash)) {
                    newUris.add(uri)
                }
            }

            var imported = 0
            if (newUris.isNotEmpty()) {
                val result = withContext(Dispatchers.IO) {
                    AngConfigManager.importBatchConfig(newUris.joinToString("\n"), "", true)
                }
                imported = result.first
                if (imported > 0) {
                    importedHashes.addAll(hashesInResponse)
                }
            }

            val boundedHashes = importedHashes.toList().takeLast(2500).toSet()
            prefs.edit()
                .putStringSet(CiaConfigSync.KEY_HASHES, boundedHashes)
                .putLong(CiaConfigSync.KEY_LAST_SYNC, System.currentTimeMillis())
                .putInt(CiaConfigSync.KEY_LAST_IMPORTED, imported)
                .remove(CiaConfigSync.KEY_LAST_ERROR)
                .apply()

            Result.success(
                workDataOf(
                    "found" to configArray.length(),
                    "new" to newUris.size,
                    "imported" to imported
                )
            )
        } catch (error: Exception) {
            val message = error.message ?: "Channel sync failed"
            prefs.edit().putString(CiaConfigSync.KEY_LAST_ERROR, message).apply()
            if (runAttemptCount < 3) Result.retry()
            else Result.failure(workDataOf("error" to message))
        }
    }

    private fun isSupportedConfig(uri: String): Boolean {
        val allowed = listOf(
            "vless://", "vmess://", "ss://", "trojan://",
            "socks://", "socks5://", "hysteria2://", "hy2://", "wireguard://"
        )
        return allowed.any { uri.startsWith(it, ignoreCase = true) } && uri.length <= 16_384
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }
}
