#!/usr/bin/env python3
"""Apply the official CiaVPN/C2ray-Logo artwork and complete Android identity."""
from pathlib import Path
import re
import shutil
from PIL import Image, ImageChops, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "upstream" / "V2rayNG" / "app"
RES = APP / "src/main/res"
BRANDING = ROOT / "branding"


def replace_constant(path: Path, name: str, value: str) -> None:
    text = path.read_text(encoding="utf-8")
    pattern = rf'^(\s*const val {re.escape(name)}\s*=\s*).*$'
    updated, count = re.subn(pattern, lambda m: m.group(1) + value, text, count=1, flags=re.M)
    if count != 1:
        raise RuntimeError(f"Could not update AppConfig.{name}")
    path.write_text(updated, encoding="utf-8")


def install_icons() -> None:
    main_path = BRANDING / "official_c2ray_logo.png"
    secondary_path = BRANDING / "official_c2ray_notification.png"
    if not main_path.is_file() or not secondary_path.is_file():
        raise RuntimeError("Official C2ray logos were not downloaded")

    nodpi = RES / "drawable-nodpi"
    nodpi.mkdir(parents=True, exist_ok=True)

    # Primary C2ray-Logo artwork: launcher, adaptive icon and navigation drawer.
    logo = Image.open(main_path).convert("RGBA")
    if logo.width != logo.height:
        side = min(logo.size)
        left, top = (logo.width - side) // 2, (logo.height - side) // 2
        logo = logo.crop((left, top, left + side, top + side))
    logo.thumbnail((1024, 1024), Image.Resampling.LANCZOS)
    logo.save(nodpi / "c2ray_logo.png", format="PNG", optimize=True)

    # Secondary user artwork is converted into a transparent white glyph because
    # Android masks small status/notification and Quick Settings icons by alpha.
    source = Image.open(secondary_path).convert("RGBA")
    alpha = source.getchannel("A")
    low, high = alpha.getextrema()
    if low == 255 and high == 255:
        rgb = source.convert("RGB")
        w, h = rgb.size
        edges = [
            rgb.getpixel((0, 0)), rgb.getpixel((w - 1, 0)),
            rgb.getpixel((0, h - 1)), rgb.getpixel((w - 1, h - 1)),
            rgb.getpixel((w // 2, 0)), rgb.getpixel((w // 2, h - 1)),
            rgb.getpixel((0, h // 2)), rgb.getpixel((w - 1, h // 2)),
        ]
        background = tuple(round(sum(pixel[c] for pixel in edges) / len(edges)) for c in range(3))
        delta = ImageChops.difference(rgb, Image.new("RGB", rgb.size, background)).convert("L")
        delta = delta.filter(ImageFilter.MedianFilter(3))
        alpha = delta.point(lambda p: 0 if p < 24 else min(255, (p - 24) * 2))
    else:
        alpha = alpha.point(lambda p: 0 if p < 12 else p)

    bounds = alpha.getbbox()
    if not bounds:
        raise RuntimeError("The notification artwork has no visible foreground")
    alpha = alpha.crop(bounds)
    glyph = Image.new("RGBA", alpha.size, (255, 255, 255, 0))
    glyph.putalpha(alpha)
    glyph.thumbnail((192, 192), Image.Resampling.LANCZOS)
    notification = Image.new("RGBA", (192, 192), (255, 255, 255, 0))
    notification.alpha_composite(glyph, ((192 - glyph.width) // 2, (192 - glyph.height) // 2))
    notification.save(nodpi / "c2ray_notification.png", format="PNG", optimize=True)
    shutil.copy2(secondary_path, nodpi / "c2ray_notification_original.png")

    # Existing names used by the connection notification and Quick Settings tile.
    for pattern in ("ic_stat_*.png", "ic_stat_*.webp", "ic_stat_*.jpg", "ic_stat_*.xml"):
        for old in RES.rglob(pattern):
            old.unlink()
    for name in ("ic_stat_name", "ic_stat_name_black", "ic_stat_direct", "ic_stat_proxy"):
        shutil.copy2(nodpi / "c2ray_notification.png", nodpi / (name + ".png"))

    # Legacy launcher assets for API 24/25 and density-specific devices.
    density_sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in density_sizes.items():
        target = RES / folder
        target.mkdir(parents=True, exist_ok=True)
        scaled = logo.resize((size, size), Image.Resampling.LANCZOS)
        for name in (
            "ic_launcher.png", "ic_launcher_round.png", "ic_launcher_foreground.png",
            "ic_banner.png", "ic_banner_foreground.png",
        ):
            scaled.save(target / name, format="PNG", optimize=True)

    # The adaptive and round launcher resources for Android 8+ point to the same official art.
    values = RES / "values"
    values.mkdir(parents=True, exist_ok=True)
    (values / "c2ray_brand_colors.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<resources><color name="c2ray_icon_background">#080D19</color></resources>\n',
        encoding="utf-8",
    )
    adaptive = RES / "mipmap-anydpi-v26"
    adaptive.mkdir(parents=True, exist_ok=True)
    xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/c2ray_icon_background" />\n'
        '    <foreground android:drawable="@drawable/c2ray_logo" />\n'
        '    <monochrome android:drawable="@drawable/c2ray_notification" />\n'
        '</adaptive-icon>\n'
    )
    for name in ("ic_launcher.xml", "ic_launcher_round.xml", "ic_banner.xml"):
        (adaptive / name).write_text(xml, encoding="utf-8")


def main() -> None:
    gradle = APP / "build.gradle.kts"
    text = gradle.read_text(encoding="utf-8")
    if 'applicationId = "com.v2ray.ang"' not in text:
        raise RuntimeError("Unexpected package ID; the existing package name must be preserved")
    text = text.replace("versionCode = 755", "versionCode = 802")
    text = text.replace("v2rayNG_", "C2ray_")
    if "versionCode = 802" not in text or 'versionName = "2.3.15"' not in text:
        raise RuntimeError("Expected upstream 2.3.15 metadata was not preserved")
    gradle.write_text(text, encoding="utf-8")

    manifest = APP / "src/main/AndroidManifest.xml"
    text = manifest.read_text(encoding="utf-8")
    text = text.replace('android:icon="@drawable/c2ray_logo"', 'android:icon="@mipmap/ic_launcher"')
    text = text.replace('android:banner="@drawable/c2ray_logo"', 'android:banner="@drawable/c2ray_logo"')
    if 'android:icon="@mipmap/ic_launcher"' not in text:
        raise RuntimeError("Manifest launcher icon resource is missing")
    manifest.write_text(text, encoding="utf-8")

    drawer = APP / "src/main/java/com/v2ray/ang/ui/main/MainDrawer.kt"
    text = drawer.read_text(encoding="utf-8")
    text = text.replace("R.mipmap.ic_launcher_foreground", "R.drawable.c2ray_logo")
    for old_import in (
        "import androidx.compose.ui.graphics.BlendMode\n",
        "import androidx.compose.ui.graphics.Color\n",
        "import androidx.compose.ui.graphics.ColorFilter\n",
        "import com.v2ray.ang.ui.compose.LocalDarkTheme\n",
    ):
        text = text.replace(old_import, "")
    text = text.replace("                    val isDarkTheme = LocalDarkTheme.current\n", "")
    text = text.replace(
        "                        colorFilter = if (isDarkTheme) {\n"
        "                            ColorFilter.tint(Color.White, BlendMode.SrcIn)\n"
        "                        } else {\n"
        "                            null\n"
        "                        }\n",
        "",
    )
    drawer.write_text(text, encoding="utf-8")

    app_config = APP / "src/main/java/com/v2ray/ang/AppConfig.kt"
    replace_constant(app_config, "APP_URL", '"https://github.com/CiaVPN/C2ray"')
    replace_constant(app_config, "APP_PRIVACY_POLICY", '"$APP_URL/blob/main/CR.md"')
    replace_constant(app_config, "APP_PROMOTION_URL", '""')
    replace_constant(app_config, "TG_CHANNEL_URL", '"https://t.me/CiaConfig"')
    text = app_config.read_text(encoding="utf-8").replace('"backup_ng.zip"', '"c2ray_backup.zip"')
    text = text.replace('"CORE_M_CH_ID_V2"', '"C2RAY_CORE_M_CH_ID_V2"')
    app_config.write_text(text, encoding="utf-8")

    # Apply the brand name to every Android locale and flavor resource.
    for strings_file in (APP / "src").rglob("strings.xml"):
        text = strings_file.read_text(encoding="utf-8")
        text = re.sub(
            r'(<string\s+name="app_name"[^>]*>).*?(</string>)',
            r'\1C2ray\2',
            text,
            flags=re.DOTALL,
        )
        text = re.sub(
            r'(<item\s+name="app_name"[^>]*>).*?(</item>)',
            r'\1C2ray\2',
            text,
            flags=re.DOTALL,
        )
        text = re.sub("v2rayNG", "C2ray", text, flags=re.IGNORECASE)
        if strings_file == APP / "src/main/res/values/strings.xml" and "title_cia_config_sync" not in text:
            text = text.replace(
                "<resources>",
                '<resources>\n    <string name="title_cia_config_sync">CiaConfig - Channel Sync</string>',
                1,
            )
        strings_file.write_text(text, encoding="utf-8")

    http_util = APP / "src/main/java/com/v2ray/ang/util/HttpUtil.kt"
    text = http_util.read_text(encoding="utf-8").replace("v2rayNG/", "C2ray/")
    http_util.write_text(text, encoding="utf-8")
    logcat = APP / "src/main/java/com/v2ray/ang/ui/logcat/LogcatActivity.kt"
    text = logcat.read_text(encoding="utf-8").replace("v2rayNG_logcat_", "C2ray_logcat_")
    logcat.write_text(text, encoding="utf-8")

    install_icons()

    print("C2ray 2.3.15 official branding installed.")
    print("Package ID preserved: com.v2ray.ang")
    print("Main logo: CiaVPN/C2ray-Logo")
    print("Notification and Quick Settings logo: CiaVPN/C2ray-Logo")


if __name__ == "__main__":
    main()
