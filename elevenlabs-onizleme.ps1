param(
    [Parameter(Mandatory=$false)]
    [ValidateRange(1,99999999)]
    [int]$EserId = 5
)

. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path $Maven)) { throw "Maven bulunamadı: $Maven" }

Write-Host "--- ELEVENLABS ÖNİZLEME ---"
Write-Host "Eser ID: ESER-$("{0:D5}" -f $EserId)"
Write-Host "Not: API anahtarı değeri loglara yazdırılmaz."
Write-Host ""

& $Maven -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $Maven -q "-Dexec.mainClass=ElevenLabsOnizlemeApp" "-Dexec.args=$EserId" exec:java
exit $LASTEXITCODE
