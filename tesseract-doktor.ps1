. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"

$Adaylar = @()
if (-not [string]::IsNullOrWhiteSpace($env:TESSERACT_PATH)) { $Adaylar += $env:TESSERACT_PATH }
$Adaylar += "C:\Program Files\Tesseract-OCR\tesseract.exe"
$Adaylar += "C:\Program Files (x86)\Tesseract-OCR\tesseract.exe"
$Adaylar += (Join-Path $env:LOCALAPPDATA "Programs\Tesseract-OCR\tesseract.exe")
$Adaylar += (Join-Path $env:LOCALAPPDATA "Tesseract-OCR\tesseract.exe")
$Komut = $null
foreach ($Aday in $Adaylar) {
    if (Test-Path -LiteralPath $Aday) { $Komut = $Aday; break }
}
if (-not $Komut) {
    $Bulunan = Get-Command tesseract -ErrorAction SilentlyContinue
    if ($Bulunan) { $Komut = $Bulunan.Source }
}

Write-Host "--- TESSERACT YEREL OCR DOKTORU ---"
if (-not $Komut) {
    Write-Host "Durum: YOK"
    Write-Host "Not: Archive.org EPUB/TXT metin yedeği varsa sistem yine çalışır."
    Write-Host "Kurulum: powershell -ExecutionPolicy Bypass -File .\tesseract-kurulum.ps1"
    exit 0
}

Write-Host "Komut: $Komut"
Write-Host ("TESSDATA_PREFIX: " + $(if ($env:TESSDATA_PREFIX) { $env:TESSDATA_PREFIX } else { "varsayılan" }))
$Surum = & $Komut --version 2>&1 | Select-Object -First 1
Write-Host "Sürüm: $Surum"
$Diller = @(& $Komut --list-langs 2>&1 | Select-Object -Skip 1)
$Turkce = $Diller -contains "tur"
Write-Host ("Türkçe modeli: " + $(if ($Turkce) { "HAZIR" } else { "YOK" }))
Write-Host ("Diller: " + ($Diller -join ", "))
Write-Host ("Durum: " + $(if ($Turkce) { "HAZIR" } else { "KISMİ — İngilizce OCR kullanılabilir" }))
