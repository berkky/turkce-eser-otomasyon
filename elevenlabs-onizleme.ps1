param(
    [Parameter(Mandatory=$false)]
    [ValidateRange(1,99999999)]
    [int]$EserId = 5,

    [Parameter(Mandatory=$false)]
    [switch]$RefreshPanel = $true
)

. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path $Maven)) { throw "Maven bulunamadı: $Maven" }

Write-Host "--- ELEVENLABS ÖNİZLEME (Adım 28) ---"
Write-Host "Eser ID: ESER-$("{0:D5}" -f $EserId)"
Write-Host "Mod: KISA_ONIZLEME — tam eser üretimi başlatılmaz."
Write-Host "Not: API anahtarı değeri loglara yazdırılmaz."
Write-Host ""

& $Maven -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$argsList = @("$EserId")
if ($RefreshPanel) {
    $argsList += "-RefreshPanel"
}
$argsJoined = $argsList -join " "
& $Maven -q "-Dexec.mainClass=ElevenLabsOnizlemeApp" "-Dexec.args=$argsJoined" exec:java
exit $LASTEXITCODE
