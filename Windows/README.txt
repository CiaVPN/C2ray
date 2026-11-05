C2ray for Windows
=================

CiaConfig automatic sync
------------------------
Uses the same public Worker as the Android app:
https://c2ray-channel-sync.webserviceamir.workers.dev/api/configs

Automatic sync runs at startup and checks no more than once every 15 minutes.
Use the CiaConfig menu in the top bar > Sync CiaConfig now for a manual check. Existing profiles are not
imported twice; the standard C2ray profile parser is used.

The Windows package includes Xray-core v26.6.27 under bin/xray, plus geoip.dat and geosite.dat under bin/. This core version keeps compatibility with legacy public VLESS links using security=none. Such links do not have transport encryption; prefer TLS or REALITY profiles whenever available.

GitHub releases: https://github.com/CiaVPN/C2ray/releases
Telegram: https://t.me/CiaConfig

Unsigned builds may display the Microsoft SmartScreen warning. This cannot
legitimately be disabled without a trusted signing certificate.
