C2ray for Windows
=================

C2ray is a client application. It does not provide proxy servers, accounts,
subscriptions, or connection configurations.

INSTALLATION
------------
1. Download the ZIP matching your Windows processor:
   - C2ray-Windows-x64.zip: ordinary Intel/AMD 64-bit Windows PCs.
   - C2ray-Windows-arm64.zip: Windows devices with an ARM64 processor.
2. Extract the ZIP to a folder where you have write permission.
3. Run C2ray.exe.
4. Import your own supported connection link or subscription.
5. Select a profile and connect.

UPDATES
-------
Open Help > Check updates to check C2ray releases on GitHub.
Only releases containing the matching C2ray-Windows-x64.zip or
C2ray-Windows-arm64.zip package are considered by the Windows updater.
The Windows build version is stored in Windows/version.txt; future releases
use semantic tags such as v2.3.17.

The package is self-contained and includes the .NET runtime needed by C2ray.
No separate .NET installation is normally required.

TUN MODE
--------
Some TUN/network-driver features may require administrator permission and
additional Windows components. Run as administrator only when the feature
you choose requires it.

SUPPORT AND RELEASES
--------------------
Releases: https://github.com/CiaVPN/C2ray/releases
Telegram: https://t.me/CiaConfig
Issues:   https://github.com/CiaVPN/C2ray/issues

A CiaConfig Telegram shortcut is also available under Help.

WINDOWS SMARTSCREEN
-------------------
Portable ZIP builds are not automatically trusted by Windows. Without a
trusted Authenticode code-signing certificate, Microsoft Defender SmartScreen
may show "Windows protected your PC" when C2ray.exe is first launched.
Verify the download is from the official CiaVPN GitHub Releases page. If you
choose to run it, click More info > Run anyway only when you trust that build.

The GitHub Actions workflow can sign binaries when these repository secrets
are configured:
- C2RAY_SIGNING_CERTIFICATE_BASE64: base64-encoded PFX code-signing certificate
- C2RAY_SIGNING_CERTIFICATE_PASSWORD: the PFX password

The certificate must chain to a Windows-trusted public certificate authority.
A valid signature displays a verified publisher, but SmartScreen may still
warn until Microsoft builds reputation for the file or publisher.

C2ray for Windows is based on the open-source v2rayN project. See the included
GPL license and the corresponding source archive for attribution and source.
