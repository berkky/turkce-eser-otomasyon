param(
    [Parameter(Mandatory=$false)]
    [ValidateRange(1,99999999)]
    [int]$EserId = 6
)

. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"
$Arsiv = if ($env:ESER_ARSIVI) { $env:ESER_ARSIVI } else { Join-Path ([Environment]::GetFolderPath("Desktop")) "arsiv" }
$OnEk = "ESER-{0:D5}" -f $EserId
$Klasor = Get-ChildItem -LiteralPath $Arsiv -Directory -ErrorAction Stop |
    Where-Object { $_.Name.StartsWith($OnEk, [System.StringComparison]::OrdinalIgnoreCase) } |
    Select-Object -First 1
if (-not $Klasor) { throw "$OnEk arşiv klasörü bulunamadı: $Arsiv" }
$JsonYolu = Join-Path $Klasor.FullName "metadata\eser-metadata.json"
if (-not (Test-Path -LiteralPath $JsonYolu)) { throw "Metadata dosyası bulunamadı: $JsonYolu" }

$M = Get-Content -LiteralPath $JsonYolu -Raw -Encoding UTF8 | ConvertFrom-Json
$M | Format-List eserAdi,eserTuru,yazar,yayinevi,yayinYili,isbn,orijinalAdi,cevirmen,basimBilgisi,dil,lisans,bilgiKaynagi,kullanilanAiModeli,guvenPuani,metadataDurumu,kanit
Write-Host "Metadata dosyası: $JsonYolu"
