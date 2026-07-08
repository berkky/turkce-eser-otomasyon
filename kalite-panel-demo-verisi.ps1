. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path $Maven)) { throw "Maven bulunamadı: $Maven" }

$DemoSesArsiv = Join-Path $env:TEMP "eser-kalite-panel-demo-ses-arsivi"
if (Test-Path $DemoSesArsiv) {
    Remove-Item -Recurse -Force $DemoSesArsiv
}
New-Item -ItemType Directory -Path $DemoSesArsiv -Force | Out-Null

Write-TestBaslik 'demo'
Write-Utf8Line "Uyarı: MOCK dosyalar gerçek TTS değildir; yalnızca test amaçlıdır."
Write-Utf8Line "Demo ses arşivi: $DemoSesArsiv"

& $Maven -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $Maven -q "-Dexec.mainClass=SesKalitePanelDemoApp" "-Dexec.args=$DemoSesArsiv" exec:java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$env:ESER_SES_ARSIVI = $DemoSesArsiv
& $Maven -q "-Dexec.mainClass=SesKalitePanelApp" exec:java
exit $LASTEXITCODE
