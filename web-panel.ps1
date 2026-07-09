. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path $Maven)) { throw "Maven bulunamadı: $Maven" }

Write-TestBaslik 'webpanelDerleme'
& $Maven -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Utf8Line ""
Write-Utf8Line "========================================"
Write-Utf8Line "YEREL WEB MVP KONTROL PANELİ"
Write-Utf8Line "========================================"
Write-Utf8Line "URL: http://127.0.0.1:8787"
Write-Utf8Line "Durdurmak için Ctrl+C"
Write-Utf8Line ""

& $Maven -q "-Dexec.mainClass=YerelWebSunucuApp" exec:java
exit $LASTEXITCODE
