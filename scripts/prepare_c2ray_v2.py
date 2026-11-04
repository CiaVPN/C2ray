#!/usr/bin/env python3
"""Apply the C2ray v2 branding and CiaConfig integration to the pinned upstream tree."""
from pathlib import Path
import re
import shutil
import sys

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = ROOT / "upstream" / "V2rayNG"
APP = UPSTREAM / "app"


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match in {path}, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_line(path: Path, pattern: str, replacement: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count == 0 and replacement not in text:
        raise RuntimeError(f"{label}: could not find target in {path}")
    if updated != text:
        path.write_text(updated, encoding="utf-8")


def main() -> None:
    required = [
        UPSTREAM / "settings.gradle.kts",
        UPSTREAM / "gradlew",
        APP / "build.gradle.kts",
        APP / "src/main/AndroidManifest.xml",
        APP / "src/main/java/com/v2ray/ang/AppConfig.kt",
        APP / "src/main/java/com/v2ray/ang/ui/main/MainActivity.kt",
        APP / "src/main/java/com/v2ray/ang/ui/main/MainDrawer.kt",
        APP / "src/main/res/values/strings.xml",
    ]
    missing = [str(p) for p in required if not p.is_file()]
    if missing:
        raise RuntimeError("Pinned upstream source is incomplete: " + ", ".join(missing))

    icon_source = ROOT / "fastlane/metadata/android/en-US/images/icon.png"
    if not icon_source.is_file():
        raise RuntimeError("C2ray source icon was not found at " + str(icon_source))

    # Give C2ray a unique install identity so it can coexist with v2rayNG.
    # Keep the upstream Kotlin namespace (com.v2ray.ang) unchanged; only the
    # Android applicationId controls the installed package name.
    gradle = APP / "build.gradle.kts"
    gradle_text = gradle.read_text(encoding="utf-8")
    if 'applicationId = "com.v2ray.ang"' not in gradle_text:
        raise RuntimeError("Unexpected upstream applicationId; refusing to apply the C2ray package identity")
    gradle_text = gradle_text.replace(
        'applicationId = "com.v2ray.ang"',
        'applicationId = "com.C2ray.ang"',
        1,
    )
    # Upstream is pinned to v2rayNG 2.3.15. Keep that release identity while
    # raising versionCode so this branded build installs as an update.
    if 'versionName = "2.3.15"' not in gradle_text or "versionCode = 755" not in gradle_text:
        raise RuntimeError("Unexpected upstream version; refusing to label a different source as C2ray 2.3.15")
    gradle_text = gradle_text.replace("versionCode = 755", "versionCode = 802")
    gradle_text = gradle_text.replace("v2rayNG_", "C2ray_")
    gradle.write_text(gradle_text, encoding="utf-8")

    settings = UPSTREAM / "settings.gradle.kts"
    replace_once(settings, 'rootProject.name = "v2rayNG"', 'rootProject.name = "C2ray"', "Gradle project name")

    drawer = APP / "src/main/java/com/v2ray/ang/ui/main/MainDrawer.kt"
    replace_once(
        drawer,
        '    Settings(R.drawable.ic_settings_24dp, R.string.title_settings),',
        '    Settings(R.drawable.ic_settings_24dp, R.string.title_settings),\n'
        '    ChannelSync(R.drawable.ic_settings_24dp, R.string.title_channel_sync),',
        "CiaConfig drawer destination",
    )
    replace_once(
        drawer,
        '    MainDestination.Settings\n)',
        '    MainDestination.Settings,\n    MainDestination.ChannelSync\n)',
        "CiaConfig primary drawer entry",
    )
    replace_once(
        drawer,
        '    Promotion(R.drawable.ic_promotion_24dp, R.string.title_pref_promotion),',
        '    CiaConfigChannel(R.drawable.ic_promotion_24dp, R.string.title_cia_config_channel),\n'
        '    Promotion(R.drawable.ic_promotion_24dp, R.string.title_pref_promotion),',
        "Add CiaConfig Telegram drawer destination",
    )
    replace_once(
        drawer,
        'private val drawerItems = primaryDrawerItems + listOf(\n'
        '    MainDestination.Promotion,\n'
        '    MainDestination.Logcat,',
        'private val drawerItems = primaryDrawerItems + listOf(\n'
        '    MainDestination.CiaConfigChannel,\n'
        '    MainDestination.Logcat,',
        "Keep CiaConfig Telegram shortcut in drawer",
    )
    replace_once(
        drawer,
        'painterResource(R.mipmap.ic_launcher_foreground)',
        'painterResource(R.drawable.c2ray_logo)',
        "C2ray drawer logo",
    )

    activity = APP / "src/main/java/com/v2ray/ang/ui/main/MainActivity.kt"
    replace_once(
        activity,
        'import com.v2ray.ang.ui.checkupdate.CheckUpdateActivity',
        'import com.v2ray.ang.ui.checkupdate.CheckUpdateActivity\n'
        'import com.v2ray.ang.ui.ciaconfig.CiaConfigActivity\n'
        'import com.v2ray.ang.ui.ciaconfig.CiaConfigSync\n'
        'import com.v2ray.ang.ui.ciaconfig.CiaConfigSyncScheduler',
        "CiaConfig activity import",
    )
    replace_once(
        activity,
        '        mainViewModel.onAction(MainAction.Initialize)\n\n        checkAndRequestPermission',
        '        mainViewModel.onAction(MainAction.Initialize)\n\n'
        '        val syncPrefs = getSharedPreferences(CiaConfigSync.PREFS, MODE_PRIVATE)\n'
        '        CiaConfigSyncScheduler.configure(\n'
        '            this,\n'
        '            syncPrefs.getBoolean(CiaConfigSync.KEY_ENABLED, true),\n'
        '            syncPrefs.getInt(CiaConfigSync.KEY_INTERVAL, 30)\n'
        '        )\n\n        checkAndRequestPermission',
        "Restore CiaConfig background schedule",
    )
    replace_once(
        activity,
        '            MainDestination.Settings -> Intent(this, SettingsActivity::class.java)',
        '            MainDestination.ChannelSync -> Intent(this, CiaConfigActivity::class.java)\n'
        '            MainDestination.CiaConfigChannel -> {\n'
        '                Utils.openUri(this, "https://t.me/CiaConfig")\n'
        '                return\n'
        '            }\n'
        '            MainDestination.Settings -> Intent(this, SettingsActivity::class.java)',
        "Channel Sync and CiaConfig channel navigation",
    )
    # Refresh the profile list after the CiaConfig screen returns from a completed import.
    activity_text = activity.read_text(encoding="utf-8")
    activity_text = activity_text.replace(
        'if (refreshGroups) mainViewModel.onAction(MainAction.RefreshGroups)',
        'mainViewModel.onAction(MainAction.RefreshGroups)',
    )
    activity.write_text(activity_text, encoding="utf-8")

    manifest = APP / "src/main/AndroidManifest.xml"
    manifest_text = manifest.read_text(encoding="utf-8")
    manifest_text = manifest_text.replace('android:banner="@mipmap/ic_banner"', 'android:banner="@drawable/c2ray_logo"')
    manifest_text = manifest_text.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/c2ray_logo"')
    settings_activity = (
        '        <activity\n'
        '            android:name=".ui.settings.SettingsActivity"\n'
        '            android:exported="false" />'
    )
    cia_activity = (
        '        <activity\n'
        '            android:name=".ui.ciaconfig.CiaConfigActivity"\n'
        '            android:exported="false" />\n'
        + settings_activity
    )
    if '.ui.ciaconfig.CiaConfigActivity' not in manifest_text:
        if settings_activity not in manifest_text:
            raise RuntimeError("Could not locate SettingsActivity manifest entry")
        manifest_text = manifest_text.replace(settings_activity, cia_activity, 1)
    manifest.write_text(manifest_text, encoding="utf-8")

    app_config = APP / "src/main/java/com/v2ray/ang/AppConfig.kt"
    replace_line(app_config, r'^\s*const val APP_URL\s*=.*$', '    const val APP_URL = "https://github.com/CiaVPN/C2ray"', "App repository URL")
    replace_line(app_config, r'^\s*const val TG_CHANNEL_URL\s*=.*$', '    const val TG_CHANNEL_URL = "https://t.me/CiaConfig"', "Telegram channel URL")
    replace_line(app_config, r'^\s*const val APP_PROMOTION_URL\s*=.*$', '    const val APP_PROMOTION_URL = ""', "Remove upstream promotion URL")
    replace_line(
        app_config,
        r'^\s*const val APP_PRIVACY_POLICY\s*=.*$',
        '    const val APP_PRIVACY_POLICY = "$APP_URL/blob/main/CR.md"',
        "C2ray privacy policy URL",
    )

    # Use C2ray's existing repository icon for both the application and in-app drawer.
    logo_dir = APP / "src/main/res/drawable-nodpi"
    logo_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(icon_source, logo_dir / "c2ray_logo.png")

    # Change visible brand strings in all Android locales and add the drawer label.
    for strings_file in (APP / "src").glob("*/res/values*/strings.xml"):
        text = strings_file.read_text(encoding="utf-8")
        text = re.sub(
            r'(<string\s+name="app_name"[^>]*>).*?(</string>)',
            r'\1C2ray\2',
            text,
            flags=re.DOTALL,
        )
        text = re.sub(r'v2rayNG', 'C2ray', text, flags=re.IGNORECASE)
        if strings_file == APP / "src/main/res/values/strings.xml" and "title_channel_sync" not in text:
            text = text.replace(
                "<resources>",
                '<resources>\n'
                '    <string name="title_channel_sync" translatable="false">Channel Sync</string>\n'
                '    <string name="title_cia_config_channel" translatable="false">CiaConfig</string>',
                1,
            )
        strings_file.write_text(text, encoding="utf-8")

    # Install the two Android integration classes.
    target = APP / "src/main/java/com/v2ray/ang/ui/ciaconfig"
    target.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / "android-integration/CiaConfigSyncWorker.kt", target / "CiaConfigSyncWorker.kt")
    shutil.copy2(ROOT / "android-integration/CiaConfigActivity.kt", target / "CiaConfigActivity.kt")

    print("C2ray v2 Android integration applied.")
    print("Application ID set to: com.C2ray.ang")
    print("Channel: https://t.me/CiaConfig")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print("C2ray preparation failed: " + str(exc), file=sys.stderr)
        raise
