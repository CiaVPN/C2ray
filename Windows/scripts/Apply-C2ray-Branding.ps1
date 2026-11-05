[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SourceRoot
)

$ErrorActionPreference = "Stop"
$SourceRoot = (Resolve-Path -LiteralPath $SourceRoot).Path

function Set-RequiredText {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Old,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$New
    )

    $content = Get-Content -LiteralPath $Path -Raw
    if (-not $content.Contains($Old)) {
        throw "Expected branding text was not found in $Path. Expected: $Old"
    }
    $content = $content.Replace($Old, $New)
    Set-Content -LiteralPath $Path -Value $content -NoNewline -Encoding utf8
}

function Write-C2rayIco {
    param(
        [Parameter(Mandatory = $true)][string]$ImagePath,
        [Parameter(Mandatory = $true)][string]$OutputPath
    )

    Add-Type -AssemblyName System.Drawing
    $source = [System.Drawing.Image]::FromFile($ImagePath)
    $images = [System.Collections.Generic.List[object]]::new()

    try {
        foreach ($size in @(16, 24, 32, 48, 64, 128, 256)) {
            $bitmap = [System.Drawing.Bitmap]::new(
                $size,
                $size,
                [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
            )
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            $memory = [System.IO.MemoryStream]::new()

            try {
                $graphics.Clear([System.Drawing.Color]::Transparent
                )
                $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

                # Fit the full source artwork into a square; do not crop the logo.
                $scale = [Math]::Min($size / [double]$source.Width, $size / [double]$source.Height)
                $width = [Math]::Max(1, [int][Math]::Round($source.Width * $scale))
                $height = [Math]::Max(1, [int][Math]::Round($source.Height * $scale))
                $x = [int](($size - $width) / 2)
                $y = [int](($size - $height) / 2)
                $destination = [System.Drawing.Rectangle]::new($x, $y, $width, $height)

                $graphics.DrawImage($source, $destination)
                $bitmap.Save($memory, [System.Drawing.Imaging.ImageFormat]::Png)
                $images.Add([pscustomobject]@{
                    Size = $size
                    Bytes = $memory.ToArray()
                })
            }
            finally {
                $memory.Dispose()
                $graphics.Dispose()
                $bitmap.Dispose()
            }
        }
    }
    finally {
        $source.Dispose()
    }

    $output = [System.IO.File]::Open(
        $OutputPath,
        [System.IO.FileMode]::Create,
        [System.IO.FileAccess]::Write,
        [System.IO.FileShare]::None
    )
    $writer = [System.IO.BinaryWriter]::new($output)

    try {
        # ICO directory header.
        $writer.Write([UInt16]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]$images.Count)

        $offset = 6 + (16 * $images.Count)
        foreach ($item in $images) {
            $dimension = if ($item.Size -eq 256) { 0 } else { [byte]$item.Size }
            $writer.Write([byte]$dimension)
            $writer.Write([byte]$dimension)
            $writer.Write([byte]0)
            $writer.Write([byte]0)
            $writer.Write([UInt16]1)
            $writer.Write([UInt16]32)
            $writer.Write([UInt32]$item.Bytes.Length)
            $writer.Write([UInt32]$offset)
            $offset += $item.Bytes.Length
        }

        foreach ($item in $images) {
            $writer.Write([byte[]]$item.Bytes)
        }
    }
    finally {
        $writer.Dispose()
        $output.Dispose()
    }
}

$assetsRoot = Join-Path (Split-Path -Parent $PSScriptRoot) "assets"
$logoSource = Join-Path $assetsRoot "C2ray-Main.png"
if (-not (Test-Path -LiteralPath $logoSource)) {
    throw "The official C2ray logo is missing from Windows/assets: $logoSource"
}

$temp = Join-Path ([System.IO.Path]::GetTempPath()) ("c2ray-brand-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $temp -Force | Out-Null

try {
    $mainPng = Join-Path $temp "c2ray-main.png"
    $trayPng = Join-Path $temp "c2ray-tray.png"

    # Use the exact same official C2ray artwork for the app and every tray state.
    Copy-Item -LiteralPath $logoSource -Destination $mainPng
    Copy-Item -LiteralPath $logoSource -Destination $trayPng

    foreach ($image in @($mainPng, $trayPng)) {
        if (-not (Test-Path -LiteralPath $image) -or (Get-Item -LiteralPath $image).Length -lt 1000) {
            throw "The official C2ray logo could not be downloaded: $image"
        }
    }

    $appRoot = Join-Path $SourceRoot "v2rayN\v2rayN"
    $resourceRoot = Join-Path $appRoot "Resources"
    $serviceRoot = Join-Path $SourceRoot "v2rayN\ServiceLib"

    if (-not (Test-Path -LiteralPath $resourceRoot)) {
        throw "The pinned upstream Windows project layout was not found under $SourceRoot"
    }

    # Main application icon: executable, window title bar, and taskbar.
    Write-C2rayIco -ImagePath $mainPng -OutputPath (Join-Path $resourceRoot "v2rayN.ico")

    # Secondary CiaVPN mark: every tray / notification state uses the matching project artwork.
    foreach ($name in @("NotifyIcon1.ico", "NotifyIcon2.ico", "NotifyIcon3.ico", "NotifyIcon4.ico")) {
        Write-C2rayIco -ImagePath $trayPng -OutputPath (Join-Path $resourceRoot $name)
    }

    # Product identity and Windows startup registration.
    $globalPath = Join-Path $serviceRoot "Global.cs"
    Set-RequiredText $globalPath 'public const string AppName = "v2rayN";' 'public const string AppName = "C2ray";'
    Set-RequiredText $globalPath 'public const string AutoRunName = "v2rayNAutoRun";' 'public const string AutoRunName = "C2rayAutoRun";'
    Set-RequiredText $globalPath 'public const string LocalAppData = "V2RAYN_LOCAL_APPLICATION_DATA_V2";' 'public const string LocalAppData = "C2RAY_LOCAL_APPLICATION_DATA_V2";'
    Set-RequiredText $globalPath '{ ECoreType.v2rayN, "2dust/v2rayN" },' '{ ECoreType.v2rayN, "CiaVPN/C2ray" },'

    # Use a C2ray-owned data directory instead of sharing v2rayN's local data.
    $utilsPath = Join-Path $serviceRoot "Common\Utils.cs"
    Set-RequiredText $utilsPath 'Environment.SpecialFolder.LocalApplicationData), "v2rayN");' 'Environment.SpecialFolder.LocalApplicationData), "C2ray");'

    # The existing Android release tag is not a semantic Windows application version.
    # Keep the upstream self-updater out of the C2ray UI until C2ray publishes versioned Windows tags.
    $coreInfoPath = Join-Path $serviceRoot "Manager\CoreInfoManager.cs"
    Set-RequiredText $coreInfoPath 'DownloadUrlWin64 = urlN + "/download/{0}/v2rayN-windows-64.zip",' 'DownloadUrlWin64 = urlN + "/download/{0}/C2ray-Windows-x64.zip",'
    Set-RequiredText $coreInfoPath 'DownloadUrlWinArm64 = urlN + "/download/{0}/v2rayN-windows-arm64.zip",' 'DownloadUrlWinArm64 = urlN + "/download/{0}/C2ray-Windows-arm64.zip",'
    Set-RequiredText $coreInfoPath @'
            if (IsCheckUpdateSupported(ECoreType.v2rayN))
            {
                lst.Add(ECoreType.v2rayN);
            }

'@ ''

    # WPF application display name and tray tooltip.
    $mainWindowPath = Join-Path $appRoot "Views\MainWindow.xaml"
    Set-RequiredText $mainWindowPath 'Title="v2rayN"' 'Title="C2ray"'

    $statusBarPath = Join-Path $appRoot "Views\StatusBarView.xaml"
    Set-RequiredText $statusBarPath 'ToolTipText="v2rayN"' 'ToolTipText="C2ray"'

    # Version and Windows file metadata follow the Android release line.
    $propsPath = Join-Path $SourceRoot "v2rayN\Directory.Build.props"
    Set-RequiredText $propsPath '<Version>7.25.6</Version>' '<Version>2.3.16</Version>'
    Set-RequiredText $propsPath '<Authors>2dust</Authors>' '<Authors>CiaVPN</Authors>'

    $projectPath = Join-Path $appRoot "v2rayN.csproj"
    $project = Get-Content -LiteralPath $projectPath -Raw
    if ($project -notmatch '<AssemblyName>') {
        $project = $project.Replace(
            '<OutputType>WinExe</OutputType>',
            @'
<OutputType>WinExe</OutputType>
        <AssemblyName>C2ray</AssemblyName>
        <Product>C2ray</Product>
        <Company>CiaVPN</Company>
'@
        )
        Set-Content -LiteralPath $projectPath -Value $project -NoNewline -Encoding utf8
    }

    # Replace branded application labels while retaining translations and format placeholders.
    Get-ChildItem -LiteralPath (Join-Path $serviceRoot "Resx") -Filter "ResUI*.resx" -File |
        ForEach-Object {
            $content = Get-Content -LiteralPath $_.FullName -Raw
            if ($content.Contains("v2rayN")) {
                $content = $content.Replace("v2rayN", "C2ray")
                Set-Content -LiteralPath $_.FullName -Value $content -NoNewline -Encoding utf8
            }
        }

    Write-Host "C2ray branding applied successfully."
    Write-Host "Main icon: C2ray primary logo"
    Write-Host "Tray icons: CiaVPN secondary logo"
}
finally {
    Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
}
