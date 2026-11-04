package com.v2ray.ang.ui.ciaconfig

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.text.DateFormat
import java.util.Date

class CiaConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(CiaConfigSync.PREFS, MODE_PRIVATE)
        val enabled = prefs.getBoolean(CiaConfigSync.KEY_ENABLED, false)
        val endpoint = prefs.getString(CiaConfigSync.KEY_ENDPOINT, "").orEmpty()
        val interval = prefs.getInt(CiaConfigSync.KEY_INTERVAL, 30)
        val lastSync = prefs.getLong(CiaConfigSync.KEY_LAST_SYNC, 0L)
        val lastError = prefs.getString(CiaConfigSync.KEY_LAST_ERROR, "").orEmpty()
        val lastSyncText = if (lastSync > 0L) {
            DateFormat.getDateTimeInstance().format(Date(lastSync))
        } else {
            "هنوز همگام‌سازی نشده"
        }

        setContent {
            MaterialTheme {
                CiaConfigSettingsScreen(
                    initialEnabled = enabled,
                    initialEndpoint = endpoint,
                    initialInterval = interval,
                    lastSyncText = lastSyncText,
                    lastError = lastError,
                    onBack = { finish() },
                    onOpenChannel = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/CiaConfig")))
                    },
                    onSave = { isEnabled, apiUrl, minutes ->
                        persistSettings(isEnabled, apiUrl, minutes)
                        Toast.makeText(this, "تنظیمات CiaConfig ذخیره شد", Toast.LENGTH_SHORT).show()
                    },
                    onSyncNow = { isEnabled, apiUrl, minutes ->
                        persistSettings(isEnabled, apiUrl, minutes)
                        val request = CiaConfigSyncScheduler.runNow(this, true)
                        WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.id)
                            .observe(this) { info ->
                                if (info == null || !info.state.isFinished) return@observe
                                if (info.state == WorkInfo.State.SUCCEEDED) {
                                    val count = info.outputData.getInt("imported", 0)
                                    setResult(RESULT_OK)
                                    Toast.makeText(
                                        this,
                                        "همگام‌سازی تمام شد؛ " + count + " کانفیگ جدید اضافه شد",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    finish()
                                } else {
                                    val error = info.outputData.getString("error") ?: "اتصال به Worker ناموفق بود"
                                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                                }
                            }
                    }
                )
            }
        }
    }

    private fun persistSettings(enabled: Boolean, endpoint: String, interval: Int) {
        getSharedPreferences(CiaConfigSync.PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(CiaConfigSync.KEY_ENABLED, enabled)
            .putString(CiaConfigSync.KEY_ENDPOINT, endpoint.trim())
            .putInt(CiaConfigSync.KEY_INTERVAL, interval)
            .apply()

        CiaConfigSyncScheduler.configure(this, enabled, interval)
    }
}

@Composable
private fun CiaConfigSettingsScreen(
    initialEnabled: Boolean,
    initialEndpoint: String,
    initialInterval: Int,
    lastSyncText: String,
    lastError: String,
    onBack: () -> Unit,
    onOpenChannel: () -> Unit,
    onSave: (Boolean, String, Int) -> Unit,
    onSyncNow: (Boolean, String, Int) -> Unit
) {
    var enabled by rememberSaveable { mutableStateOf(initialEnabled) }
    var endpoint by rememberSaveable { mutableStateOf(initialEndpoint) }
    var interval by rememberSaveable { mutableIntStateOf(initialInterval.coerceAtLeast(15)) }
    var intervalMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CiaConfig", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text("بازگشت") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("همگام‌سازی خودکار کانفیگ‌ها", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (enabled) "فعال است" else "خاموش است")
                        Text(
                            "لینک‌های جدید کانال عمومی @CiaConfig دریافت و به فهرست سرورها اضافه می‌شوند.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        }

        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("آدرس API کلادفلر Worker") },
            placeholder = { Text("https://YOUR-WORKER.workers.dev/api/configs") },
            supportingText = { Text("ابتدا Worker داخل پوشه cloudflare-worker را Deploy کن و URL آن را اینجا قرار بده.") },
            singleLine = true
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("فاصله بررسی کانال", style = MaterialTheme.typography.titleSmall)
            Box {
                OutlinedButton(onClick = { intervalMenuExpanded = true }) {
                    Text(interval.toString() + " دقیقه")
                }
                DropdownMenu(
                    expanded = intervalMenuExpanded,
                    onDismissRequest = { intervalMenuExpanded = false }
                ) {
                    listOf(15, 30, 60, 180, 360).forEach { value ->
                        DropdownMenuItem(
                            text = { Text(value.toString() + " دقیقه") },
                            onClick = {
                                interval = value
                                intervalMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Text(
                "حداقل بازه زمان‌بندی پس‌زمینه اندروید ۱۵ دقیقه است؛ اجرای فوری هم از دکمه پایین ممکن است.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("وضعیت آخرین بررسی", style = MaterialTheme.typography.titleSmall)
                Text(lastSyncText, style = MaterialTheme.typography.bodyMedium)
                if (lastError.isNotBlank()) {
                    Text("خطا: " + lastError, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Button(
            onClick = { onSave(enabled, endpoint, interval) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ذخیره تنظیمات")
        }

        Button(
            onClick = { onSyncNow(enabled, endpoint, interval) },
            enabled = endpoint.startsWith("https://", ignoreCase = true),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("همگام‌سازی همین حالا")
        }

        OutlinedButton(
            onClick = onOpenChannel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("مشاهده کانال CiaConfig در تلگرام")
        }

        Text(
            "کانفیگ‌های تکراری دوباره وارد نمی‌شوند. این قابلیت فقط لینک‌ها را دریافت می‌کند و سلامت یا امنیت سرورها را تضمین نمی‌کند.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
