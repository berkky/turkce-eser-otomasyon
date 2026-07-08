. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"

Write-Host "========================================"
Write-Host "FFMPEG MP3 DONUSTURUCU KURULUMU"
Write-Host "========================================"
Write-Host ""

function Find-FFmpeg {
    $kayitli = [Environment]::GetEnvironmentVariable("FFMPEG_PATH", "User")
    if ($kayitli -and (Test-Path $kayitli -PathType Leaf)) {
        return (Resolve-Path $kayitli).Path
    }

    $komut = Get-Command ffmpeg -ErrorAction SilentlyContinue
    if ($komut -and $komut.Source -and (Test-Path $komut.Source -PathType Leaf)) {
        return $komut.Source
    }

    $sabit = "C:\ffmpeg\bin\ffmpeg.exe"
    if (Test-Path $sabit -PathType Leaf) {
        return $sabit
    }

    $wingetPaketleri = Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Packages"
    if (Test-Path $wingetPaketleri -PathType Container) {
        $bulunan = Get-ChildItem $wingetPaketleri -Filter ffmpeg.exe -File -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match "Gyan\.FFmpeg" } |
            Select-Object -First 1
        if ($bulunan) {
            return $bulunan.FullName
        }
    }

    return $null
}

$ffmpeg = Find-FFmpeg
if (-not $ffmpeg) {
    $winget = Get-Command winget -ErrorAction SilentlyContinue
    if (-not $winget) {
        throw "FFmpeg bulunamadi ve winget kullanilamiyor. Windows App Installer/winget kurulduktan sonra bu scripti yeniden calistir."
    }

    Write-Host "FFmpeg bulunamadi. WinGet ile Gyan.FFmpeg kuruluyor..."
    & winget install --id Gyan.FFmpeg --exact `
        --accept-package-agreements --accept-source-agreements `
        --disable-interactivity

    if ($LASTEXITCODE -ne 0) {
        throw "WinGet FFmpeg kurulumunu tamamlayamadi. Cikis kodu: $LASTEXITCODE"
    }

    Start-Sleep -Seconds 2
    $ffmpeg = Find-FFmpeg
}

if (-not $ffmpeg) {
    throw "FFmpeg kuruldu ancak ffmpeg.exe bulunamadi. Yeni bir PowerShell penceresi acip scripti tekrar calistir."
}

[Environment]::SetEnvironmentVariable("FFMPEG_PATH", $ffmpeg, "User")
$env:FFMPEG_PATH = $ffmpeg

Write-Host ""
Write-Host "FFmpeg kontrol ediliyor..."
$surum = & $ffmpeg -version 2>&1 | Select-Object -First 1
if ($LASTEXITCODE -ne 0) {
    throw "FFmpeg calistirilamadi: $ffmpeg"
}

$encoder = & $ffmpeg -hide_banner -encoders 2>&1 | Select-String "libmp3lame" | Select-Object -First 1
if (-not $encoder) {
    throw "FFmpeg bulundu ancak libmp3lame MP3 kodlayicisi bulunamadi."
}

if (-not [Environment]::GetEnvironmentVariable("PIPER_MP3_BITRATE", "User")) {
    [Environment]::SetEnvironmentVariable("PIPER_MP3_BITRATE", "64k", "User")
}
$env:PIPER_MP3_BITRATE = [Environment]::GetEnvironmentVariable("PIPER_MP3_BITRATE", "User")

Write-Host ""
Write-Host "FFMPEG KURULUMU TAMAMLANDI"
Write-Host "FFmpeg     : $ffmpeg"
Write-Host "Surum      : $surum"
Write-Host "MP3 encoder: libmp3lame"
Write-Host "Bitrate    : $env:PIPER_MP3_BITRATE mono"
Write-Host ""
Write-Host "Ust terminale donunce FFMPEG_PATH degiskenini yeniden yukle veya VS Code'u yeniden ac."
