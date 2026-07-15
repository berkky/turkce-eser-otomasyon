param(
    [switch]$Status,
    [switch]$Mock,
    [switch]$LocalSource,
    [switch]$Live,
    [ValidateSet("lumen", "ursa", "sal")]
    [string]$Voice = "sal",
    [switch]$GercekApiOnayli,
    [long]$Seed = 0,
    [decimal]$BudgetUsd = 1.00
)

. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path -LiteralPath $Maven)) { throw "Maven bulunamadı: $Maven" }

$arguments = @()
if ($Status -or (-not $Mock -and -not $Live)) { $arguments += "--status" }
if ($Mock) { $arguments += "--mock" }
if ($LocalSource) { $arguments += "--local-source" }
if ($Live) { $arguments += @("--live", "--voice", $Voice, "--budget-usd", $BudgetUsd.ToString([Globalization.CultureInfo]::InvariantCulture)) }
if ($GercekApiOnayli) { $arguments += "--gercek-api-onayli" }
if ($Seed -ne 0) { $arguments += @("--seed", $Seed.ToString()) }

& $Maven -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { throw "Derleme başarısız." }

$execArgs = @("-q", "-Dexec.mainClass=TtsAbApp")
if ($arguments.Count -gt 0) {
    $quoted = ($arguments | ForEach-Object { '"' + ($_ -replace '"', '\"') + '"' }) -join " "
    $execArgs += "-Dexec.args=$quoted"
}
$execArgs += "exec:java"
& $Maven @execArgs
exit $LASTEXITCODE
