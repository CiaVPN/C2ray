<h1 align="center">C2ray</h1>

<p align="center">
  <strong>A V2Ray-compatible client for Android and Windows</strong><br>
  Manage supported Xray/V2Ray connection profiles on your phone or desktop.
</p>

<p align="center">
  <a href="https://github.com/CiaVPN/C2ray"><img src="https://img.shields.io/badge/GitHub-C2ray-181717?style=for-the-badge&logo=github" alt="C2ray on GitHub"></a>
  <a href="https://github.com/CiaVPN/C2ray/releases"><img src="https://img.shields.io/badge/Download-Releases-2ea44f?style=for-the-badge&logo=github" alt="C2ray releases"></a>
  <a href="https://github.com/CiaVPN/C2ray/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge" alt="GPL-3.0 license"></a>
</p>

---

## About C2ray

**C2ray** is an independently maintained client project in the V2Ray ecosystem. It provides separate Android and Windows editions for working with supported Xray/V2Ray connection profiles.

- **Android edition:** based on the open-source [v2rayNG](https://github.com/2dust/v2rayNG) project.
- **Windows edition:** based on the Windows WPF client in [2dust/v2rayN](https://github.com/2dust/v2rayN), with C2ray branding and official CiaVPN logos.

C2ray is a **client application, not a VPN service**. It does not provide proxy servers, accounts, or connection subscriptions; you need a configuration or service of your own.

> C2ray is an independent project. It is not the official v2rayNG, v2rayN, Xray, or v2fly application.

## Project Links

| Resource | Link |
| --- | --- |
| **C2ray Repository** | [github.com/CiaVPN/C2ray](https://github.com/CiaVPN/C2ray) |
| **Download Releases** | [C2ray Releases](https://github.com/CiaVPN/C2ray/releases) |
| **Issues & Bug Reports** | [Report an issue](https://github.com/CiaVPN/C2ray/issues) |
| **CiaVPN Telegram** | [t.me/CiaConfig](https://t.me/CiaConfig) |
| **Official Logos** | [CiaVPN/C2ray-Logo](https://github.com/CiaVPN/C2ray-Logo) |
| **Project Owner** | [CiaVPN on GitHub](https://github.com/CiaVPN) |

## Features

- **Android and Windows clients:** use the edition built for your device.
- **Xray/V2Ray ecosystem:** protocol and configuration support depends on the core included in a particular build.
- **Connection profile management:** import and manage supported connection profiles and subscriptions.
- **Android VPN integration:** route selected device traffic through Android's VPN service when enabled and permitted.
- **Windows desktop integration:** system proxy, tray controls, and other desktop features provided by the upstream Windows client.
- **Open source:** source code and build workflows are available in this repository.

Features can vary between builds. Check the source code and release notes for details about a specific version.

## Download and Install

Open the [C2ray Releases page](https://github.com/CiaVPN/C2ray/releases) and choose the package for your platform.

### Android

1. Download the APK for your processor from the release assets.
2. Open the APK on your Android device and follow Android's installation prompts. You may need to allow installation from the app used to open the file.
3. Open C2ray, import a supported connection configuration, select a profile, and connect.
4. Approve Android's VPN permission prompt if it appears.

### Windows

Windows packages are portable ZIP archives:

| File | Processor |
| --- | --- |
| `C2ray-Windows-x64.zip` | Standard 64-bit Intel/AMD Windows PCs |
| `C2ray-Windows-arm64.zip` | Windows on ARM64 devices |
| `C2ray-Windows-source-7.25.6.zip` | Corresponding branded Windows source code |

1. Download the ZIP that matches your processor.
2. Extract it to a folder where you have write permission.
3. Run `C2ray.exe`.
4. Import your own supported connection link or subscription, select a profile, and connect.

The Windows packages are self-contained and include the .NET runtime. TUN mode or other system-level networking features may require administrator permission.

For your safety, download builds only from sources you trust. If a package is not listed in Releases, it has not been published there.

## Build from Source

### Android requirements

- Git
- JDK compatible with the Gradle configuration (the current GitHub Actions workflow uses **Java 21**)
- Android SDK and the SDK/NDK versions required by the current project configuration
- Network access to fetch Gradle and project dependencies

Clone with submodules:

```bash
git clone --recurse-submodules https://github.com/CiaVPN/C2ray.git
cd C2ray/V2rayNG
```

Build a debug APK:

**Linux / macOS / Kali Linux**
```bash
chmod +x gradlew
./gradlew assembleDebug
```

**Windows Command Prompt**
```bat
gradlew.bat assembleDebug
```

The debug APK is normally written under `V2rayNG/app/build/outputs/apk/debug/`.

For CI-specific SDK and NDK versions, see the [Android build workflow](https://github.com/CiaVPN/C2ray/blob/main/.github/workflows/build.yml).

### Windows requirements

- Windows 10/11
- Git with submodule support
- .NET 10 SDK
- PowerShell 7 or Windows PowerShell

The Windows workflow pins the upstream client to v2rayN **7.25.6**, applies the branding script, and publishes self-contained `win-x64` and `win-arm64` packages.

The automated build is defined in [build-windows.yml](.github/workflows/build-windows.yml). Branding and icon generation are handled by [Apply-C2ray-Branding.ps1](Windows/scripts/Apply-C2ray-Branding.ps1). Read [Windows/UPSTREAM.md](Windows/UPSTREAM.md) for upstream attribution and source details.

## GeoIP and GeoSite Data

The underlying Android project supports routing data such as GeoIP and GeoSite files. File formats and storage locations can vary by application version. Check the current source and relevant documentation before replacing or importing routing data.

Related resources:

- [Loyalsoldier/v2ray-rules-dat](https://github.com/Loyalsoldier/v2ray-rules-dat) — GeoIP/GeoSite routing data
- [Loyalsoldier/geoip](https://github.com/Loyalsoldier/geoip) — GeoIP data
- [v2rayNG Wiki](https://github.com/2dust/v2rayNG/wiki) — upstream Android project documentation
- [v2rayN Wiki](https://github.com/2dust/v2rayN/wiki) — upstream desktop project documentation

## Troubleshooting

- **The connection does not start:** Check that the configuration is valid, the server is reachable, and the required VPN/system permissions have been granted.
- **A configuration cannot be imported:** Confirm that its format is supported by the edition and core included in your build.
- **Routing behaves unexpectedly:** Review your routing rules and GeoIP/GeoSite data.
- **The app crashes or a feature is missing:** Check the release notes and open an issue with the app version, operating-system version, and relevant non-sensitive logs.

Never post passwords, private keys, subscription URLs, access tokens, or complete private connection configurations in a public issue.

## Privacy and Responsible Use

C2ray is a networking client. Your privacy and traffic handling depend on the configuration you use, the remote server, the bundled core, and your device settings. Use only servers and configurations you trust, and follow the laws and network policies applicable to you.

## Credits and Related Projects

C2ray builds on work from the open-source community. See the relevant source files and license notices for applicable attribution and licensing details.

- **[v2rayNG](https://github.com/2dust/v2rayNG)** — upstream Android client
- **[v2rayN](https://github.com/2dust/v2rayN)** — upstream Windows desktop client
- **[Xray-core](https://github.com/XTLS/Xray-core)** — Xray core
- **[v2fly/v2ray-core](https://github.com/v2fly/v2ray-core)** — V2Ray core
- **[AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)** — Android library used by the upstream Android workflow
- **[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)** — SOCKS5 tunnel component

C2ray is not affiliated with or endorsed by the maintainers of these upstream projects unless explicitly stated by those maintainers.

## License

This repository is distributed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file. The Windows release also provides its corresponding branded source archive; review [Windows/UPSTREAM.md](Windows/UPSTREAM.md) for details.

---

<p align="center">
  Made with care by <a href="https://github.com/CiaVPN">CiaVPN</a> · <a href="https://github.com/CiaVPN/C2ray">Source Code</a> · <a href="https://github.com/CiaVPN/C2ray/releases">Releases</a>
</p>
