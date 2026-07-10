param(
    [Parameter(Mandatory=$false)]
    [ValidateRange(1,99999999)]
    [int]$EserId = 5
)

. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path $Maven)) { throw "Maven bulunamadi: $Maven" }

Write-Host "--- TAM ESER ONAY TASLIGI (Adim 31) ---"
Write-Host "Eser ID: ESER-$("{0:D5}" -f $EserId)"
Write-Host ""

& $Maven -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $Maven -q "-Dexec.mainClass=TamEserOnayTaslagiApp" "-Dexec.args=$EserId" exec:java
exit $LASTEXITCODE
