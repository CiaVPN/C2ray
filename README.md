<h1 align="center">C2ray</h1>

<p align="center">
  <strong>A V2Ray-compatible client for Android</strong><br>
  A practical way to manage and use supported Xray/V2Ray connection profiles on your Android device.
</p>

<p align="center">
  <a href="https://github.com/CiaVPN/C2ray"><img src="https://img.shields.io/badge/GitHub-C2ray-181717?style=for-the-badge&logo=github" alt="C2ray on GitHub"></a>
  <a href="https://github.com/CiaVPN/C2ray/releases"><img src="https://img.shields.io/badge/Download-Releases-2ea44f?style=for-the-badge&logo=android" alt="C2ray releases"></a>
  <a href="https://github.com/CiaVPN/C2ray/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge" alt="GPL-3.0 license"></a>
</p>

---

## About C2ray

**C2ray** is an Android client project in the V2Ray ecosystem. It is based on the open-source **v2rayNG** project and is intended to provide a convenient Android interface for working with supported Xray/V2Ray connections.

C2ray is a **client application, not a VPN service**. It does not provide proxy servers or connection subscriptions; you need a configuration or service of your own.

> C2ray is an independently maintained project. It is not the official v2rayNG, Xray, or v2fly application.

## Project Links

| Resource | Link |
| --- | --- |
| **C2ray Repository** | [github.com/CiaVPN/C2ray](https://github.com/CiaVPN/C2ray) |
| **Download Releases** | [C2ray Releases](https://github.com/CiaVPN/C2ray/releases) |
| **Issues & Bug Reports** | [Report an issue](https://github.com/CiaVPN/C2ray/issues) |
| **Project Owner** | [CiaVPN on GitHub](https://github.com/CiaVPN) |

## Features

- **Android client:** Use supported proxy configurations from an Android device.
- **Xray/V2Ray ecosystem:** Built around the core ecosystem used by V2Ray-compatible clients. Exact protocol and configuration support depends on the core included in a particular build.
- **Connection profile management:** Import and manage supported connection profiles using the options available in the app.
- **Android VPN integration:** Route selected device traffic through an Android VPN connection when enabled and permitted by the operating system.
- **Routing data:** Supports the GeoIP/GeoSite data workflow used by the underlying project; routing behavior depends on your configuration and data files.
- **Open source:** The code and build instructions are available in this repository.

Features can vary between builds. Check the source code and release notes for details about a specific version.

## Download and Install

1. Open the [C2ray Releases page](https://github.com/CiaVPN/C2ray/releases).
2. Download an APK asset when a release is available.
3. Open the APK on your Android device and follow Android's installation prompts. You may need to allow installation from the app you used to open the file.
4. Open C2ray and import a supported connection configuration using the import options available in that build.
5. Select the profile you want to use, start the connection, and approve Android's VPN permission prompt if it appears.

For your safety, download APKs only from sources you trust. If the Releases page has no APK assets, a packaged release has not been published there yet.

## Build from Source

### Requirements

- Git
- JDK compatible with the Gradle configuration (the current GitHub Actions workflow uses **Java 21**)
- Android SDK and the SDK/NDK versions required by the current project configuration
- Network access to fetch Gradle and project dependencies

### Clone the repository

Clone with submodules so the required source dependencies are checked out:

```bash
git clone --recurse-submodules https://github.com/CiaVPN/C2ray.git
cd C2ray/V2rayNG
```

### Build a debug APK

**Linux / macOS / Kali Linux**

```bash
chmod +x gradlew
./gradlew assembleDebug
```

**Windows Command Prompt**

```bat
gradlew.bat assembleDebug
```

If the build completes successfully, the debug APK is normally written under:

```text
V2rayNG/app/build/outputs/apk/debug/
```

Build requirements can change as the project evolves. For CI-specific SDK and NDK versions, see the [GitHub Actions build workflow](https://github.com/CiaVPN/C2ray/blob/main/.github/workflows/build.yml).

## GeoIP and GeoSite Data

The underlying project supports routing data such as GeoIP and GeoSite files. These files and their storage locations can vary by application version and Android release. Before replacing or importing routing data, check the current source code and the relevant documentation.

Related resources:

- [Loyalsoldier/v2ray-rules-dat](https://github.com/Loyalsoldier/v2ray-rules-dat) — GeoIP/GeoSite routing data
- [Loyalsoldier/geoip](https://github.com/Loyalsoldier/geoip) — GeoIP data
- [v2rayNG Wiki](https://github.com/2dust/v2rayNG/wiki) — upstream project documentation

## Troubleshooting

- **The connection does not start:** Check that the configuration is valid, the server is reachable, and Android VPN permission has been granted.
- **A configuration cannot be imported:** Confirm that its format is supported by the version and core included in your build.
- **Routing behaves unexpectedly:** Review your routing rules and GeoIP/GeoSite data.
- **The app crashes or a feature is missing:** Check the release notes and open an issue with the app version, Android version, and relevant non-sensitive logs.

Never post passwords, private keys, subscription URLs, access tokens, or complete private connection configurations in a public issue.

## Privacy and Responsible Use

C2ray is a networking client. Your privacy and traffic handling depend on the configuration you use, the remote server, the bundled core, and your device settings. Use only servers and configurations you trust, and follow the laws and network policies applicable to you.

## Credits and Related Projects

C2ray builds on work from the open-source community. See the repository's source files and license notices for the applicable attribution and licensing details.

- **[v2rayNG](https://github.com/2dust/v2rayNG)** — upstream Android client project
- **[Xray-core](https://github.com/XTLS/Xray-core)** — Xray core
- **[v2fly/v2ray-core](https://github.com/v2fly/v2ray-core)** — V2Ray core
- **[AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)** — Android library used by the upstream build workflow
- **[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)** — SOCKS5 tunnel component

C2ray is not affiliated with or endorsed by the maintainers of these upstream projects unless explicitly stated by those maintainers.

## License

This repository includes a **GNU General Public License v3.0 (GPL-3.0)** license. See the [`LICENSE`](https://github.com/CiaVPN/C2ray/blob/main/LICENSE) file for the license text and review upstream notices before redistributing modified builds.

---

<p align="center">
  Made with care by <a href="https://github.com/CiaVPN">CiaVPN</a> · <a href="https://github.com/CiaVPN/C2ray">Source Code</a> · <a href="https://github.com/CiaVPN/C2ray/releases">Releases</a>
</p>
