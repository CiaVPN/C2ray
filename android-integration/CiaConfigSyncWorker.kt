package com.v2ray.ang.ui.ciaconfig

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.work.BackoffPolicy
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
    private var foregroundHandler: Handler? = null
    private var foregroundTask: Runnable? = null

    fun configure(context: Context, enabled: Boolean, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(CiaConfigSync.PERIODIC_WORK)
            return
        }

        // WorkManager/JobScheduler cannot schedule periodic work more often than every 15 minutes.
        // Faster choices are handled by the foreground poller below while C2ray is open.
        val backgroundInterval = intervalMinutes
            .coerceAtLeast(CiaConfigSync.MIN_INTERVAL_MINUTES)
            .coerceAtMost(360)
        val request = PeriodicWorkRequestBuilder<CiaConfigSyncWorker>(
            backgroundInterval.toLong(),
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

    @Synchronized
    fun startForegroundPolling(context: Context) {
        stopForegroundPolling()
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(CiaConfigSync.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(CiaConfigSync.KEY_ENABLED, true)) return

        val intervalMinutes = prefs.getInt(CiaConfigSync.KEY_INTERVAL, 5).coerceIn(1, 360)
        val handler = Handler(Looper.getMainLooper())
        val task = object : Runnable {
            override fun run() {
                val currentPrefs = appContext.getSharedPreferences(
                    CiaConfigSync.PREFS,
                    Context.MODE_PRIVATE
                )
                if (!currentPrefs.getBoolean(CiaConfigSync.KEY_ENABLED, true)) {
                    stopForegroundPolling()
                    return
                }

                runNow(appContext, force = false)
                handler.postDelayed(this, intervalMinutes * 60_000L)
            }
        }

        foregroundHandler = handler
        foregroundTask = task
        handler.post(task)
    }

    @Synchronized
    fun stopForegroundPolling() {
        val handler = foregroundHandler
        val task = foregroundTask
        if (handler != null && task != null) handler.removeCallbacks(task)
        foregroundHandler = null
        foregroundTask = null
    }

    fun runNow(context: Context, force: Boolean = true): OneTimeWorkRequest {
        val request = OneTimeWorkRequestBuilder<CiaConfigSyncWorker>()
            .setInputData(workDataOf(CiaConfigSync.KEY_FORCE to force))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            CiaConfigSync.MANUAL_WORK,
            if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
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

        return try {
            val responseBody = withContext(Dispatchers.IO) {
                val connection = URL(CiaConfigSync.WORKER_ENDPOINT).openConnection() as? HttpsURLConnection
                    ?: throw IOException("Secure HTTPS connection could not be created")
                try {
                    connection.requestMethod = "GET"
                    connection.instanceFollowRedirects = true
                    connection.useCaches = false
                    connection.connectTimeout = 30_000
                    connection.readTimeout = 30_000
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("Cache-Control", "no-cache")
                    connection.setRequestProperty("Pragma", "no-cache")
                    connection.setRequestProperty("Connection", "close")
                    connection.setRequestProperty("User-Agent", "C2ray-Android/2.3.15")

                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (status !in 200..299) {
                        val detail = body.replace(Regex("\\s+"), " ").take(300)
                        throw IOException(
                            if (detail.isBlank()) "Channel Worker returned HTTP $status"
                            else "Channel Worker returned HTTP $status: $detail"
                        )
                    }
                    if (body.isBlank()) throw IOException("Channel Worker returned an empty response")
                    body
                } finally {
                    connection.disconnect()
                }
            }

            val root = try {
                JSONObject(responseBody)
            } catch (error: Exception) {
                throw IOException("Channel Worker returned invalid JSON: ${error.message ?: "parse error"}")
            }
            if (!root.optBoolean("ok", false)) {
                throw IOException(root.optString("error", "Channel Worker response is not ok"))
            }
            val configArray = root.optJSONArray("configs")
                ?: throw IOException("Channel Worker response has no configs array")

            val importedHashes = (prefs.getStringSet(CiaConfigSync.KEY_HASHES, emptySet()) ?: emptySet())
                .toMutableSet()
            val newUris = mutableListOf<String>()
            val newHashes = mutableListOf<String>()
            val hashesInResponse = mutableSetOf<String>()

            for (index in 0 until configArray.length()) {
                val item = configArray.optJSONObject(index) ?: continue
                val uri = item.optString("uri", "").trim()
                if (!isSupportedConfig(uri)) continue

                val hash = sha256(uri)
                if (hash !in importedHashes && hashesInResponse.add(hash)) {
                    newUris.add(uri)
                    newHashes.add(hash)
                }
            }

            var imported = 0
            if (newUris.isNotEmpty()) {
                val result = withContext(Dispatchers.IO) {
                    AngConfigManager.importBatchConfig(newUris.joinToString("\n"), "", true)
                }
                imported = result.first.coerceIn(0, newUris.size)
                // Only mark successfully imported links as seen; a rejected link can be retried later.
                importedHashes.addAll(newHashes.take(imported))
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
            val message = error.message ?: error.javaClass.simpleName.ifBlank { "Channel sync failed" }
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
