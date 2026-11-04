package com.v2ray.ang.ui.ciaconfig

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.PreferenceGroupHeader
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.verticalScrollbar
import java.text.DateFormat
import java.util.Date

class CiaConfigActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        val prefs = remember { getSharedPreferences(CiaConfigSync.PREFS, MODE_PRIVATE) }
        var enabled by rememberSaveable {
            mutableStateOf(prefs.getBoolean(CiaConfigSync.KEY_ENABLED, true))
        }
        var interval by rememberSaveable {
            mutableIntStateOf(prefs.getInt(CiaConfigSync.KEY_INTERVAL, 30).coerceAtLeast(15))
        }
        var syncing by rememberSaveable { mutableStateOf(false) }
        var lastSync by rememberSaveable {
            mutableLongStateOf(prefs.getLong(CiaConfigSync.KEY_LAST_SYNC, 0L))
        }
        var lastImported by rememberSaveable {
            mutableIntStateOf(prefs.getInt(CiaConfigSync.KEY_LAST_IMPORTED, 0))
        }
        var lastError by rememberSaveable {
            mutableStateOf(prefs.getString(CiaConfigSync.KEY_LAST_ERROR, "").orEmpty())
        }

        val scrollState = rememberScrollState()
        val intervalValues = listOf("15", "30", "60", "180", "360")
        val intervalEntries = listOf(
            "هر ۱۵ دقیقه",
            "هر ۳۰ دقیقه",
            "هر ۱ ساعت",
            "هر ۳ ساعت",
            "هر ۶ ساعت"
        )

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                AppTopBar(
                    title = "Channel Sync",
                    onBackClick = { finish() }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScrollbar(scrollState)
                    .verticalScroll(scrollState)
            ) {
                PreferenceGroupHeader(title = "همگام‌سازی کانال")

                SettingsSwitchItem(
                    title = "همگام‌سازی خودکار",
                    summary = "کانفیگ‌های تازهٔ کانال عمومی CiaConfig به‌صورت خودکار دریافت و به فهرست سرورها اضافه می‌شوند.",
                    checked = enabled,
                    onCheckedChange = { newValue ->
                        enabled = newValue
                        persistSettings(newValue, interval)
                    }
                )

                AppDivider()

                PreferenceGroupHeader(title = "زمان‌بندی بررسی")

                SettingsListItem(
                    title = "فاصلهٔ بررسی کانال",
                    entries = intervalEntries,
                    values = intervalValues,
                    selectedValue = interval.toString(),
                    onSelected = { value ->
                        interval = value.toIntOrNull() ?: 30
                        persistSettings(enabled, interval)
                    }
                )

                Text(
                    text = "همگام‌سازی پس‌زمینه فقط هنگام اتصال اینترنت اجرا می‌شود. اندروید حداقل بازهٔ ۱۵ دقیقه را برای کارهای دوره‌ای اعمال می‌کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                AppDivider()

                PreferenceGroupHeader(title = "وضعیت همگام‌سازی")

                Text(
                    text = "منبع کانفیگ‌ها: کانال رسمی @CiaConfig",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Text(
                    text = "سرویس دریافت CiaVPN به‌صورت ثابت در برنامه تنظیم شده و آدرس آن قابل تغییر نیست.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                val lastSyncLabel = if (lastSync > 0L) {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(lastSync))
                } else {
                    "هنوز همگام‌سازی انجام نشده است"
                }

                Text(
                    text = "آخرین بررسی: $lastSyncLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (lastSync > 0L) {
                    Text(
                        text = "کانفیگ اضافه‌شده در آخرین اجرا: $lastImported",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }

                if (lastError.isNotBlank()) {
                    Text(
                        text = "خطا: $lastError",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Button(
                    onClick = {
                        persistSettings(enabled, interval)
                        syncing = true
                        val request = CiaConfigSyncScheduler.runNow(this@CiaConfigActivity, true)
                        WorkManager.getInstance(this@CiaConfigActivity)
                            .getWorkInfoByIdLiveData(request.id)
                            .observe(this@CiaConfigActivity) { info ->
                                if (info == null) return@observe
                                if (!info.state.isFinished) {
                                    syncing = info.state == WorkInfo.State.RUNNING ||
                                        info.state == WorkInfo.State.ENQUEUED
                                    return@observe
                                }

                                syncing = false
                                if (info.state == WorkInfo.State.SUCCEEDED) {
                                    lastSync = prefs.getLong(
                                        CiaConfigSync.KEY_LAST_SYNC,
                                        System.currentTimeMillis()
                                    )
                                    lastImported = info.outputData.getInt("imported", 0)
                                    lastError = ""
                                    setResult(RESULT_OK)
                                    Toast.makeText(
                                        this@CiaConfigActivity,
                                        "همگام‌سازی انجام شد؛ \${lastImported} کانفیگ جدید اضافه شد",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    finish()
                                } else {
                                    lastError = info.outputData.getString("error")
                                        ?: prefs.getString(CiaConfigSync.KEY_LAST_ERROR, null)
                                        ?: "ارتباط با سرویس همگام‌سازی ناموفق بود"
                                    Toast.makeText(
                                        this@CiaConfigActivity,
                                        lastError,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                    },
                    enabled = !syncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Text(if (syncing) "در حال دریافت کانفیگ‌ها..." else "همگام‌سازی همین حالا")
                }

                Text(
                    text = "کانفیگ‌های تکراری دوباره وارد نمی‌شوند. پس از دریافت، می‌توانی سرورها را از فهرست اصلی بررسی و انتخاب کنی.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                NavigationBarsSpacer()
            }
        }
    }

    private fun persistSettings(enabled: Boolean, interval: Int) {
        getSharedPreferences(CiaConfigSync.PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(CiaConfigSync.KEY_ENABLED, enabled)
            .putInt(CiaConfigSync.KEY_INTERVAL, interval)
            .apply()

        CiaConfigSyncScheduler.configure(this, enabled, interval)
    }
}
