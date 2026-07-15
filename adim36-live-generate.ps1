param(
    [Parameter(Mandatory = $true)][ValidateSet('xai', 'openai')][string]$Provider,
    [Parameter(Mandatory = $true)][string]$SelectionId,
    [Parameter(Mandatory = $true)][string]$Voice,
    [Parameter(Mandatory = $true)][decimal]$MaxBudgetUsd,
    [Parameter(Mandatory = $true)][string]$Confirmation,
    [switch]$Live,
    [switch]$NoRetry
)

. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
. (Join-Path $PSScriptRoot "maven-resolve.ps1")
$ErrorActionPreference = "Stop"

if (-not $Live) {
    Write-Utf8Line "DRY_RUN_ONLY"
    Write-Utf8Line "Canlı çağrı yapılmadı. -Live ve geçerli confirmation gerekir."
    exit 2
}

if ($Provider -eq 'xai' -and $Confirmation -ne 'CANLI_XAI_TTS_ONAYLI') {
    Write-Utf8Line "LIVE_CONFIRMATION_REQUIRED"
    exit 3
}
if ($Provider -eq 'openai' -and $Confirmation -ne 'CANLI_OPENAI_TTS_ONAYLI') {
    Write-Utf8Line "LIVE_CONFIRMATION_REQUIRED"
    exit 3
}

# Only selected provider live flag true; all others forced false for child process
$env:ELEVENLABS_OFFLINE = 'true'
$env:ELEVENLABS_LIVE_ENABLED = 'false'
$env:GOOGLE_TTS_LIVE_ENABLED = 'false'
$env:AZURE_TTS_LIVE_ENABLED = 'false'
$env:CARTESIA_TTS_LIVE_ENABLED = 'false'
$env:XAI_TTS_LIVE_ENABLED = 'false'
$env:OPENAI_TTS_LIVE_ENABLED = 'false'

if ($Provider -eq 'xai') {
    if ([string]::IsNullOrWhiteSpace($env:XAI_API_KEY)) {
        Write-Utf8Line "API_KEY_MISSING XAI_API_KEY=YOK"
        exit 4
    }
    $env:XAI_TTS_LIVE_ENABLED = 'true'
    Write-Utf8Line "OK: XAI_API_KEY=VAR XAI_TTS_LIVE_ENABLED=AKTIF OPENAI_TTS_LIVE_ENABLED=false"
} else {
    if ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)) {
        Write-Utf8Line "API_KEY_MISSING OPENAI_API_KEY=YOK"
        exit 4
    }
    $env:OPENAI_TTS_LIVE_ENABLED = 'true'
    Write-Utf8Line "OK: OPENAI_API_KEY=VAR OPENAI_TTS_LIVE_ENABLED=AKTIF XAI_TTS_LIVE_ENABLED=false"
}

$Maven = Resolve-MavenCommand

$argList = @(
    '-Provider', $Provider,
    '-SelectionId', $SelectionId,
    '-Voice', $Voice,
    '-MaxBudgetUsd', "$MaxBudgetUsd",
    '-Confirmation', $Confirmation,
    '-Live'
)
if ($NoRetry) { $argList += '-NoRetry' } else { $argList += '-NoRetry' }

$joined = ($argList | ForEach-Object {
    if ($_ -match '\s') { '"' + $_ + '"' } else { $_ }
}) -join ' '

Write-Utf8Line "SINGLE_PROVIDER_ONLY provider=$Provider"
$out = & $Maven -q "-Dexec.mainClass=Adim36LiveGenerateApp" "-Dexec.args=$joined" exec:java 2>&1
$out | ForEach-Object { Write-Utf8Line "$_" }
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$text = ($out | Out-String)
if ($text -match 'status=MOCK' -or $text -match 'MOCK') {
    Write-Utf8Line "LIVE_MOCK_FORBIDDEN"
    exit 5
}
$called = if ($text -match 'calledNetwork=(true|false)') { $Matches[1] } else { 'missing' }
$cache = if ($text -match 'cacheHit=(true|false)') { $Matches[1] } else { 'missing' }
if ($called -eq 'false' -and $cache -eq 'false') {
    Write-Utf8Line "LIVE_GENERATE_FAILED calledNetwork=false cacheHit=false"
    exit 6
}
Write-Utf8Line "LIVE_GENERATE_OK"
exit 0
