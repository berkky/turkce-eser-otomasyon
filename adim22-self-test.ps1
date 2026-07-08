. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path $Maven)) { throw "Maven bulunamadı: $Maven" }

Write-Host "--- ADIM 22 MAVEN DERLEME ---"
& $Maven -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "`n--- ADIM 21 GERİYE DÖNÜK DOĞRULAMA ---"
& $Maven -q "-Dexec.mainClass=Adim21Dogrulama" exec:java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "`n--- ADIM 22 YEREL KÜNYE DOĞRULAMA ---"
& $Maven -q "-Dexec.mainClass=Adim22Dogrulama" exec:java
exit $LASTEXITCODE
