. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path $Maven)) { throw "Maven bulunamadı: $Maven" }

Write-TestBaslik 'adim23derleme'
& $Maven -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Utf8Line ""
Write-TestBaslik 'adim21'
& $Maven -q "-Dexec.mainClass=Adim21Dogrulama" exec:java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Utf8Line ""
Write-TestBaslik 'adim22'
& $Maven -q "-Dexec.mainClass=Adim22Dogrulama" exec:java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Utf8Line ""
Write-TestBaslik 'adim23meta'
& $Maven -q "-Dexec.mainClass=Adim23Dogrulama" exec:java
exit $LASTEXITCODE
