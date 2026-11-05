# Windows edition: upstream source and notices

The C2ray Windows edition is built from the Windows WPF client in
[2dust/v2rayN](https://github.com/2dust/v2rayN), pinned to the upstream
[7.25.6 release](https://github.com/2dust/v2rayN/releases/tag/7.25.6).

The build workflow checks out that exact tag (including its submodules), then
runs [Apply-C2ray-Branding.ps1](scripts/Apply-C2ray-Branding.ps1) before
compiling. The branding step changes the application identity, app-data and
startup identifiers, release repository metadata, visible product labels,
and the application/tray icon resources. The original upstream notices and
source headers are retained.

The Windows client and its modifications are distributed under the GNU
General Public License, version 3 (GPL-3.0). The corresponding source archive
is published as `C2ray-Windows-source-7.25.6.zip` with Windows release
packages. It contains the checked-out source tree after branding, the exact
branding/build script, this notice, and a copy of the GPL license.

- Upstream project: https://github.com/2dust/v2rayN
- Pinned upstream tag: https://github.com/2dust/v2rayN/tree/7.25.6
- C2ray source and build workflow: https://github.com/CiaVPN/C2ray
- Official C2ray logos: https://github.com/CiaVPN/C2ray-Logo
- License: [GPL-3.0](../LICENSE)

C2ray is an independent downstream project and is not the official v2rayN
application. The upstream project's authors and copyright notices remain
applicable to their respective portions of the code.
