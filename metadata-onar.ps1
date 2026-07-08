param(
    [Parameter(Mandatory=$false)]
    [ValidateRange(1,99999999)]
    [int]$EserId = 6
)

. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path $Maven)) { throw "Maven bulunamadı: $Maven" }

& $Maven -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $Maven -q "-Dexec.mainClass=KaynakAlimOrkestratoruApp" "-Dexec.args=repair-metadata $EserId" exec:java
exit $LASTEXITCODE
