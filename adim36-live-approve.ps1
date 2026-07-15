param(
    [Parameter(Mandatory = $true)][ValidateSet('xai', 'openai')][string]$Provider,
    [Parameter(Mandatory = $true)][string]$SelectionId,
    [Parameter(Mandatory = $true)][string]$Voice,
    [Parameter(Mandatory = $true)][decimal]$MaxBudgetUsd,
    [Parameter(Mandatory = $true)][string]$Confirmation,
    [int]$MaxRequestCount = 1
)

. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
. (Join-Path $PSScriptRoot "canonical-paths.ps1")
. (Join-Path $PSScriptRoot "maven-resolve.ps1")
$ErrorActionPreference = "Stop"

# Offline only — no TTS / key required
$env:ELEVENLABS_OFFLINE = 'true'
$env:ELEVENLABS_LIVE_ENABLED = 'false'
$env:XAI_TTS_LIVE_ENABLED = 'false'
$env:OPENAI_TTS_LIVE_ENABLED = 'false'
$env:GOOGLE_TTS_LIVE_ENABLED = 'false'
$env:AZURE_TTS_LIVE_ENABLED = 'false'
$env:CARTESIA_TTS_LIVE_ENABLED = 'false'

if ($MaxRequestCount -ne 1) { throw "REQUEST_LIMIT_EXCEEDED: MaxRequestCount yalnız 1" }

$Maven = Resolve-MavenCommand
Write-Utf8Line "ADIM 36 LIVE APPROVE (ağ çağrısı yok)"
Write-Utf8Line "provider=$Provider voice=$Voice budget=$MaxBudgetUsd"

$argList = @(
    '-Provider', $Provider,
    '-SelectionId', $SelectionId,
    '-Voice', $Voice,
    '-MaxBudgetUsd', "$MaxBudgetUsd",
    '-Confirmation', $Confirmation,
    '-MaxRequestCount', "$MaxRequestCount",
    '-ApproveOnly'
)
$joined = ($argList | ForEach-Object {
    if ($_ -match '\s') { '"' + $_ + '"' } else { $_ }
}) -join ' '

& $Maven -q "-Dexec.mainClass=Adim36LiveGenerateApp" "-Dexec.args=$joined" exec:java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Utf8Line "APPROVAL_CREATED"
exit 0
