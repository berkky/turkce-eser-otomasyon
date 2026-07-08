. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"

$Winget = Get-Command winget -ErrorAction SilentlyContinue
if (-not $Winget) { throw "winget bulunamadı. Tesseract'ı elle kurup TESSERACT_PATH ortam değişkenini ayarla." }

Write-Host "Tesseract OCR kuruluyor..."
& winget install --id UB-Mannheim.TesseractOCR -e --accept-package-agreements --accept-source-agreements
if ($LASTEXITCODE -ne 0) { throw "Tesseract winget kurulumu başarısız oldu. Hata kodu: $LASTEXITCODE" }

$Adaylar = @(
    "C:\Program Files\Tesseract-OCR\tesseract.exe",
    "C:\Program Files (x86)\Tesseract-OCR\tesseract.exe",
    (Join-Path $env:LOCALAPPDATA "Programs\Tesseract-OCR\tesseract.exe"),
    (Join-Path $env:LOCALAPPDATA "Tesseract-OCR\tesseract.exe")
)
$Tesseract = $Adaylar | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $Tesseract) {
    $Bulunan = Get-Command tesseract -ErrorAction SilentlyContinue
    if ($Bulunan) { $Tesseract = $Bulunan.Source }
}
if (-not $Tesseract) { throw "Kurulum tamamlandı ancak tesseract.exe bulunamadı." }

# Dil modelleri kullanıcı alanında tutulur; Program Files yazma izni gerekmez.
$Tessdata = Join-Path $env:LOCALAPPDATA "EserOtomasyon\tessdata"
New-Item -ItemType Directory -Force -Path $Tessdata | Out-Null
$Taban = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main"
foreach ($Dil in @("tur", "eng", "osd")) {
    $Hedef = Join-Path $Tessdata ($Dil + ".traineddata")
    if (-not (Test-Path -LiteralPath $Hedef)) {
        Write-Host "$Dil dil modeli indiriliyor..."
        Invoke-WebRequest -Uri "$Taban/$Dil.traineddata" -OutFile $Hedef -UseBasicParsing
    }
}

[Environment]::SetEnvironmentVariable("TESSERACT_PATH", $Tesseract, "User")
[Environment]::SetEnvironmentVariable("TESSDATA_PREFIX", $Tessdata, "User")
$env:TESSERACT_PATH = $Tesseract
$env:TESSDATA_PREFIX = $Tessdata
Write-Host "TESSERACT_PATH kaydedildi: $Tesseract"
Write-Host "TESSDATA_PREFIX kaydedildi: $Tessdata"
& (Join-Path $PSScriptRoot "tesseract-doktor.ps1")
