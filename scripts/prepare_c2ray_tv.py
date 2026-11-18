#!/usr/bin/env python3
"""Apply the Android TV launcher, landscape and D-pad experience to C2ray."""
from pathlib import Path
import re
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = ROOT / "upstream" / "V2rayNG"
APP = UPSTREAM / "app"
RES = APP / "src/main/res"
EXPECTED_UPSTREAM = "57907cf354d9769200ed4717b4430d605abe0952"


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    content = path.read_text(encoding="utf-8")
    count = content.count(old)
    if count == 0 and new in content:
        return
    if count != 1:
        raise RuntimeError(f"{label}: expected one match in {path}, found {count}")
    path.write_text(content.replace(old, new, 1), encoding="utf-8")


def add_import(path: Path, anchor: str, imports: str, label: str) -> None:
    content = path.read_text(encoding="utf-8")
    missing = [line for line in imports.splitlines() if line not in content]
    if not missing:
        return
    if content.count(anchor) != 1:
        raise RuntimeError(f"{label}: import anchor not found exactly once in {path}")
    content = content.replace(anchor, anchor + "".join(line + "\n" for line in missing), 1)
    path.write_text(content, encoding="utf-8")


def create_tv_banner() -> None:
    source = ROOT / "branding" / "official_c2ray_logo.png"
    if not source.is_file():
        raise RuntimeError("Official C2ray logo is missing")
    logo = Image.open(source).convert("RGBA")

    # Android TV uses a 16:9 xhdpi banner. Keep the official artwork intact
    # and add the required product name in a high-contrast safe area.
    width, height = 320, 180
    canvas = Image.new("RGBA", (width, height), (8, 13, 25, 255))
    pixels = canvas.load()
    for x in range(width):
        blend = x / max(1, width - 1)
        color = (
            int(8 + 8 * blend),
            int(13 + 12 * blend),
            int(25 + 24 * blend),
            255,
        )
        for y in range(height):
            pixels[x, y] = color

    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle((12, 12, 308, 168), radius=20, outline=(53, 77, 115, 255), width=2)

    logo.thumbnail((112, 112), Image.Resampling.LANCZOS)
    logo_x = 28 + (112 - logo.width) // 2
    logo_y = (height - logo.height) // 2
    canvas.alpha_composite(logo, (logo_x, logo_y))

    font_candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf",
    ]
    font = None
    for candidate in font_candidates:
        try:
            font = ImageFont.truetype(candidate, 31)
            break
        except OSError:
            continue
    if font is None:
        font = ImageFont.load_default()

    label = "C2ray TV"
    draw.text((154, 65), label, font=font, fill=(255, 255, 255, 255), stroke_width=0)
    small_font = ImageFont.truetype(font_candidates[0], 12) if Path(font_candidates[0]).is_file() else ImageFont.load_default()
    draw.text((156, 105), "Xray Client  |  CiaConfig Sync", font=small_font, fill=(191, 207, 231, 255))

    banner_dir = RES / "drawable-xhdpi"
    banner_dir.mkdir(parents=True, exist_ok=True)
    banner_path = banner_dir / "c2ray_tv_banner.png"
    canvas.convert("RGB").save(banner_path, format="PNG", optimize=True)
    with Image.open(banner_path) as check:
        if check.size != (320, 180):
            raise RuntimeError(f"TV banner must be 320x180, got {check.size}")


def apply_package_and_manifest() -> None:
    gradle = APP / "build.gradle.kts"
    content = gradle.read_text(encoding="utf-8")
    expected = [
        'applicationId = "com.C2ray.ang"',
        "versionCode = 803",
        'versionName = "2.3.16"',
    ]
    if any(item not in content for item in expected):
        raise RuntimeError("C2ray mobile identity changed; refusing to create a TV package from an unexpected base")
    content = content.replace('applicationId = "com.C2ray.ang"', 'applicationId = "com.c2ray.tv"', 1)
    content = content.replace("versionCode = 803", "versionCode = 804", 1)
    content = content.replace('versionName = "2.3.16"', 'versionName = "2.3.16-tv"', 1)
    old_output = "C2ray_" + "$" + "{variant.versionName}_" + "$" + "{abi}.apk"
    new_output = "C2ray-TV_" + "$" + "{variant.versionName}_" + "$" + "{abi}.apk"
    if old_output not in content:
        raise RuntimeError("Could not find the expected C2ray APK output-name pattern")
    content = content.replace(old_output, new_output, 1)
    gradle.write_text(content, encoding="utf-8")

    manifest = APP / "src/main/AndroidManifest.xml"
    content = manifest.read_text(encoding="utf-8")
    leanback_pattern = re.compile(
        r'(<uses-feature\s+android:name="android\.software\.leanback"\s+android:required=")false("\s*/>)',
        re.DOTALL,
    )
    content, count = leanback_pattern.subn(r'\1true\2', content, count=1)
    if count != 1:
        raise RuntimeError("Could not mark the Android TV leanback feature as required")
    content = content.replace(
        'android:banner="@mipmap/ic_banner"',
        'android:banner="@drawable/c2ray_tv_banner"',
        1,
    )
    if 'android:banner="@drawable/c2ray_tv_banner"' not in content:
        raise RuntimeError("Android TV banner manifest reference is missing")

    def landscape_activity(match: re.Match) -> str:
        tag = match.group(0)
        if "android:screenOrientation=" in tag:
            return tag
        if tag.rstrip().endswith("/>"):
            return tag.rstrip()[:-2].rstrip() + '\n            android:screenOrientation="landscape" />'
        return tag[:-1].rstrip() + '\n            android:screenOrientation="landscape">'

    content, activity_count = re.subn(r"<activity\b[^>]*>", landscape_activity, content)
    if activity_count < 1:
        raise RuntimeError("No application activities were found to configure for landscape")
    if 'android:hardware.touchscreen' not in content or 'android:required="false"' not in content:
        raise RuntimeError("Touchscreen must remain optional for Android TV")
    manifest.write_text(content, encoding="utf-8")


def apply_tv_navigation_and_focus() -> None:
    main_screen = APP / "src/main/java/com/v2ray/ang/ui/main/MainScreen.kt"
    add_import(
        main_screen,
        "import androidx.compose.foundation.pager.rememberPagerState\n",
        "import android.content.pm.PackageManager\nimport androidx.compose.ui.platform.LocalContext",
        "Android TV detection imports",
    )
    replace_once(
        main_screen,
        "    val doubleColumnDisplay = uiState.doubleColumnDisplay",
        "    val context = LocalContext.current\n"
        "    val isAndroidTv = remember(context) {\n"
        "        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)\n"
        "    }\n"
        "    val doubleColumnDisplay = uiState.doubleColumnDisplay || isAndroidTv",
        "Use the TV two-column server layout",
    )

    pager = APP / "src/main/java/com/v2ray/ang/ui/main/MainServerPager.kt"
    add_import(
        pager,
        "import androidx.compose.foundation.background\n",
        "import android.content.pm.PackageManager\n"
        "import androidx.compose.foundation.border\n"
        "import androidx.compose.foundation.layout.heightIn\n"
        "import androidx.compose.foundation.shape.RoundedCornerShape\n"
        "import androidx.compose.runtime.mutableStateOf\n"
        "import androidx.compose.ui.draw.clip\n"
        "import androidx.compose.ui.focus.focusGroup\n"
        "import androidx.compose.ui.focus.onFocusChanged\n"
        "import androidx.compose.ui.graphics.Color\n"
        "import androidx.compose.ui.platform.LocalContext",
        "D-pad focus imports",
    )

    replace_once(
        pager,
        "            modifier = Modifier\n"
        "                .fillMaxSize()\n"
        "                .verticalScrollbar(gridState),",
        "            modifier = Modifier\n"
        "                .fillMaxSize()\n"
        "                .focusGroup()\n"
        "                .verticalScrollbar(gridState),",
        "Group server grid focus navigation",
    )
    replace_once(
        pager,
        "            modifier = Modifier\n"
        "                .fillMaxSize()\n"
        "                .verticalScrollbar(listState),",
        "            modifier = Modifier\n"
        "                .fillMaxSize()\n"
        "                .focusGroup()\n"
        "                .verticalScrollbar(listState),",
        "Group server list focus navigation",
    )

    replace_once(
        pager,
        "    val testResult = if (row.testDelayMillis == 0L) {",
        "    val context = LocalContext.current\n"
        "    val isAndroidTv = remember(context) {\n"
        "        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)\n"
        "    }\n"
        "    var hasFocus by remember(row.guid) { mutableStateOf(false) }\n"
        "    val focusShape = RoundedCornerShape(14.dp)\n"
        "    val testResult = if (row.testDelayMillis == 0L) {",
        "TV server-row focus state",
    )
    replace_once(
        pager,
        "        modifier = Modifier\n"
        "            .fillMaxWidth()\n"
        "            .height(IntrinsicSize.Min)\n"
        "            .semantics {",
        "        modifier = Modifier\n"
        "            .fillMaxWidth()\n"
        "            .height(IntrinsicSize.Min)\n"
        "            .heightIn(min = if (isAndroidTv) 104.dp else 0.dp)\n"
        "            .then(\n"
        "                if (isAndroidTv) Modifier\n"
        "                    .onFocusChanged { hasFocus = it.isFocused }\n"
        "                    .clip(focusShape)\n"
        "                    .background(\n"
        "                        if (hasFocus) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)\n"
        "                        else MaterialTheme.colorScheme.surface\n"
        "                    )\n"
        "                    .border(\n"
        "                        width = if (hasFocus) 2.dp else 0.dp,\n"
        "                        color = if (hasFocus) MaterialTheme.colorScheme.primary else Color.Transparent,\n"
        "                        shape = focusShape\n"
        "                    )\n"
        "                    .padding(horizontal = 8.dp, vertical = 6.dp)\n"
        "                else Modifier\n"
        "            )\n"
        "            .semantics {",
        "TV server-row focus ring and touch target",
    )
    replace_once(
        pager,
        "                Text(row.remarks, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph), maxLines = 2, overflow = TextOverflow.Ellipsis)",
        "                Text(\n"
        "                    row.remarks,\n"
        "                    Modifier.weight(1f),\n"
        "                    style = (if (isAndroidTv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)\n"
        "                        .copy(lineBreak = LineBreak.Paragraph),\n"
        "                    maxLines = 2,\n"
        "                    overflow = TextOverflow.Ellipsis\n"
        "                )",
        "TV server-row readable title",
    )
    replace_once(
        pager,
        "                if (doubleColumnDisplay) {",
        "                if (doubleColumnDisplay || isAndroidTv) {",
        "TV server-row compact action menu",
    )


def main() -> None:
    upstream_repo = ROOT / "upstream"
    # The git checkout is at upstream/; V2rayNG/ is a nested Gradle project.
    # Git worktrees may expose .git as either a directory or a file.
    if not (upstream_repo / ".git").exists() and not (upstream_repo / ".git").is_file():
        raise RuntimeError("Pinned upstream checkout is not initialized")
    apply_package_and_manifest()
    apply_tv_navigation_and_focus()
    create_tv_banner()

    gradle = APP / "build.gradle.kts"
    manifest = APP / "src/main/AndroidManifest.xml"
    screen = APP / "src/main/java/com/v2ray/ang/ui/main/MainScreen.kt"
    pager = APP / "src/main/java/com/v2ray/ang/ui/main/MainServerPager.kt"
    for path in (gradle, manifest, screen, pager, RES / "drawable-xhdpi/c2ray_tv_banner.png"):
        if not path.is_file() or path.stat().st_size == 0:
            raise RuntimeError(f"Expected Android TV output is missing: {path}")

    print("C2ray Android TV preparation completed.")
    print("Application ID: com.c2ray.tv")
    print("Version: 2.3.16-tv (versionCode 804)")
    print("Leanback launcher, landscape activities, TV banner and D-pad focus are configured.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print("C2ray Android TV preparation failed: " + str(exc), file=sys.stderr)
        raise
