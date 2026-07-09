param(
    [Parameter(Mandatory=$false)]
    [ValidateRange(1,99999999)]
    [int]$EserId = 5,

    [Parameter(Mandatory=$false)]
    [switch]$Mock = $true,

    [Parameter(Mandatory=$false)]
    [switch]$GercekApiOnayli,

    [Parameter(Mandatory=$false)]
    [switch]$DemoFixture
)

. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path $Maven)) { throw "Maven bulunamadi: $Maven" }

Write-Host "--- ELEVENLABS FORCED ALIGNMENT (Adim 29) ---"
Write-Host "Eser ID: ESER-$("{0:D5}" -f $EserId)"
if ($DemoFixture) {
    Write-Host "Mod: DEMO FIXTURE (mock, gercek eser onizlemesine dokunmaz)"
} elseif ($Mock) {
    Write-Host "Mod: MOCK (API cagrisi yapilmaz)"
} elseif ($GercekApiOnayli) {
    Write-Host "Mod: GERCEK API (acik onayli)"
} else {
    Write-Host "Mod: GUVENLI - mock veya -GercekApiOnayli gerekir"
}
Write-Host "Not: API anahtari degeri loglara yazdirilmaz."
Write-Host ""

& $Maven -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$argsList = @("$EserId")
if ($Mock) { $argsList += "-Mock" }
if ($GercekApiOnayli) { $argsList += "-GercekApiOnayli" }
if ($DemoFixture) { $argsList += "-DemoFixture" }
$argsJoined = $argsList -join " "
& $Maven -q "-Dexec.mainClass=ElevenLabsAlignmentApp" "-Dexec.args=$argsJoined" exec:java
exit $LASTEXITCODE
