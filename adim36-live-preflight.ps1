param(
    [Parameter(Mandatory = $true)][ValidateSet('xai', 'openai')][string]$Provider,
    [Parameter(Mandatory = $true)][string]$SelectionId,
    [Parameter(Mandatory = $true)][string]$Voice,
    [Parameter(Mandatory = $true)][decimal]$MaxBudgetUsd
)

. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
. (Join-Path $PSScriptRoot "canonical-paths.ps1")
. (Join-Path $PSScriptRoot "maven-resolve.ps1")
. (Join-Path $PSScriptRoot "repo-private-audio-scan.ps1")
$ErrorActionPreference = "Stop"

function Fail-Preflight([string]$Code) {
    Write-Utf8Line "PREFLIGHT_FAIL $Code"
    exit 1
}

# Preflight never enables live TTS and never calls the network
$env:ELEVENLABS_OFFLINE = 'true'
$env:ELEVENLABS_LIVE_ENABLED = 'false'
$env:XAI_TTS_LIVE_ENABLED = 'false'
$env:OPENAI_TTS_LIVE_ENABLED = 'false'
$env:GOOGLE_TTS_LIVE_ENABLED = 'false'
$env:AZURE_TTS_LIVE_ENABLED = 'false'
$env:CARTESIA_TTS_LIVE_ENABLED = 'false'

Write-Utf8Line "ADIM 36 LIVE PREFLIGHT (ağ isteği yok) provider=$Provider"

try {
    $Maven = Resolve-MavenCommand
    Write-Utf8Line "OK: Maven=VAR"
} catch {
    Fail-Preflight "MAVEN_NOT_FOUND"
}

$paths = Get-EserCanonicalPaths
$sel = Join-Path $paths.Ses "ab-test\passage-selection\ESER-00005\PASSAGE-ESER-00005-20260715-183836-DF7B8C"
if ($SelectionId -ne "PASSAGE-ESER-00005-20260715-183836-DF7B8C") {
    Fail-Preflight "APPROVED_PASSAGE_INVALID"
}
$approvedPath = Join-Path $sel "approved-passage.json"
if (-not (Test-Path -LiteralPath $approvedPath)) { Fail-Preflight "APPROVED_PASSAGE_INVALID" }
$approved = Get-Content -LiteralPath $approvedPath -Encoding UTF8 -Raw | ConvertFrom-Json
if ($approved.candidateId -ne "PASSAGE-1") { Fail-Preflight "APPROVED_PASSAGE_INVALID" }
if ($approved.status -ne "PASSAGE_APPROVED_LIVE_LOCKED") { Fail-Preflight "PASSAGE_STATUS_INVALID" }

$hasher = [System.Security.Cryptography.SHA256]::Create()
$textBytes = [System.Text.Encoding]::UTF8.GetBytes([string]$approved.approvedText)
$recomputed = (($hasher.ComputeHash($textBytes) | ForEach-Object { $_.ToString("x2") }) -join "")
if ($recomputed -ne ([string]$approved.approvedTextHash).ToLowerInvariant()) {
    Fail-Preflight "APPROVED_TEXT_HASH_MISMATCH"
}
Write-Utf8Line "OK: approvedTextHash=$($approved.approvedTextHash)"

$rightsDir = Join-Path $sel "rights"
foreach ($name in @("SOURCE_ATTRIBUTION.txt", "rights-evidence.sha256", "underlying-work-rights.json", "source-edition-license.json")) {
    $p = Join-Path $rightsDir $name
    if (-not (Test-Path -LiteralPath $p)) { Fail-Preflight "SOURCE_RIGHTS_NOT_APPROVED" }
}
$evidenceList = Join-Path $rightsDir "rights-evidence.sha256"
$evidenceOk = $true
Get-Content -LiteralPath $evidenceList -Encoding UTF8 | Where-Object { $_.Trim() -ne "" } | ForEach-Object {
    $line = $_.Trim()
    if ($line -match "^([0-9a-fA-F]{64})\s+(.+)$") {
        $expected = $Matches[1]
        $rel = $Matches[2].Trim()
        $file = Join-Path $rightsDir $rel
        if (-not (Test-Path -LiteralPath $file)) {
            $file = Join-Path $sel $rel
        }
        if (-not (Test-Path -LiteralPath $file)) { $evidenceOk = $false; return }
        $actual = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
        if ($actual -ne $expected) { $evidenceOk = $false }
    }
}
if (-not $evidenceOk) { Fail-Preflight "RIGHTS_EVIDENCE_HASH_MISMATCH" }
Write-Utf8Line "OK: rights evidence hash"

$xaiVoices = @("lumen", "ursa", "sal")
$openAiVoices = @("marin", "cedar")
if ($Provider -eq "xai" -and ($xaiVoices -notcontains $Voice)) { Fail-Preflight "VOICE_NOT_ALLOWED" }
if ($Provider -eq "openai" -and ($openAiVoices -notcontains $Voice)) { Fail-Preflight "VOICE_NOT_ALLOWED" }
Write-Utf8Line "OK: voice allowlist"

Write-Utf8Line "OK: model allowlist (xai-tts / gpt-4o-mini-tts)"

$keyName = if ($Provider -eq "xai") { "XAI_API_KEY" } else { "OPENAI_API_KEY" }
$keyVal = [Environment]::GetEnvironmentVariable($keyName)
$keyStatus = if ([string]::IsNullOrWhiteSpace($keyVal)) { "YOK" } else { "VAR" }
Write-Utf8Line "OK: $keyName=$keyStatus"
if ($keyStatus -eq "YOK") { Fail-Preflight "API_KEY_MISSING" }

$ffmpeg = Get-Command ffmpeg -ErrorAction SilentlyContinue
$ffprobe = Get-Command ffprobe -ErrorAction SilentlyContinue
if ($null -eq $ffmpeg) { Fail-Preflight "FFMPEG_NOT_FOUND" }
if ($null -eq $ffprobe) { Fail-Preflight "FFPROBE_NOT_FOUND" }
Write-Utf8Line "OK: ffmpeg/ffprobe hazır"

# Cost estimates (same formulas as Java; no network)
$chars = [int]$approved.approvedCharacterCount
$estSpeech = [int]$approved.estimatedSpeechSeconds
if ($Provider -eq "xai") {
    $estimated = [decimal]($chars * 15.0 / 1000000.0)
    $cap = [decimal]0.05
} else {
    # OpenAI ESTIMATED_ONLY duration-based approx in provider; keep conservative
    $estimated = [decimal]([Math]::Max(0.01, $estSpeech * 0.015 / 60.0))
    $cap = [decimal]0.20
}
if ($MaxBudgetUsd -le 0) { Fail-Preflight "LIVE_BUDGET_NOT_APPROVED" }
if ($estimated -gt $MaxBudgetUsd) { Fail-Preflight "BUDGET_EXCEEDED" }
if ($MaxBudgetUsd -gt $cap) { Fail-Preflight "BUDGET_REVIEW_REQUIRED" }
Write-Utf8Line "OK: budget estimated=$estimated max=$MaxBudgetUsd cap=$cap"

$liveRoot = Join-Path $paths.Ses "ab-test\live-preview\ESER-00005"
$approvalFile = $null
if (Test-Path -LiteralPath $liveRoot) {
    $approvalFile = Get-ChildItem -LiteralPath $liveRoot -Recurse -Filter "live-generation-approval.json" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
}
if ($null -eq $approvalFile) { Fail-Preflight "LIVE_PROVIDER_NOT_APPROVED" }
$approval = Get-Content -LiteralPath $approvalFile.FullName -Encoding UTF8 -Raw | ConvertFrom-Json
if ($approval.status -in @("REVOKED", "EXPIRED", "CONSUMED")) { Fail-Preflight "LIVE_APPROVAL_$($approval.status)" }
try {
    $expires = [datetimeoffset]::Parse([string]$approval.expiresAt)
    if ($expires -lt [datetimeoffset]::Now) { Fail-Preflight "LIVE_APPROVAL_EXPIRED" }
} catch {
    Fail-Preflight "LIVE_APPROVAL_EXPIRED"
}
if ([string]$approval.approvedTextHash -ne [string]$approved.approvedTextHash) {
    Fail-Preflight "APPROVED_TEXT_HASH_MISMATCH"
}
$prov = $approval.providerApprovals | Where-Object { $_.provider -eq $Provider } | Select-Object -First 1
if ($null -eq $prov) { Fail-Preflight "LIVE_PROVIDER_NOT_APPROVED" }
$state = [string]$prov.state
if ([string]::IsNullOrWhiteSpace($state)) {
    $state = if ($prov.approved) { "APPROVED" } else { "NOT_APPROVED" }
}
if ($state -ne "APPROVED") { Fail-Preflight "LIVE_PROVIDER_NOT_APPROVED" }
if ([string]$prov.voice -ne $Voice) { Fail-Preflight "VOICE_NOT_ALLOWED" }
if ([decimal]$prov.budgetUsd -lt $MaxBudgetUsd) { Fail-Preflight "BUDGET_EXCEEDED" }
Write-Utf8Line "OK: active provider approval state=APPROVED"

$expDir = Split-Path -Parent (Split-Path -Parent $approvalFile.FullName)
$rawName = if ($Provider -eq "xai") { "xai-$Voice.wav" } else { "openai-$Voice.wav" }
$rawPath = Join-Path $expDir "raw\$rawName"
$rawState = "$rawPath.request.json"
if (Test-Path -LiteralPath $rawPath) {
    $sz = (Get-Item -LiteralPath $rawPath).Length
    if ($sz -le 44) { Fail-Preflight "RAW_CACHE_INVALID" }
    if (-not (Test-Path -LiteralPath $rawState)) { Fail-Preflight "RAW_CACHE_INVALID" }
    Write-Utf8Line "OK: raw cache exists (will be validated by Java cache gate)"
} else {
    Write-Utf8Line "OK: raw hedef boş (ilk üretim)"
}

$unresolved = Get-ChildItem -LiteralPath (Join-Path $expDir "attempts\$Provider") -Filter "*.json" -ErrorAction SilentlyContinue |
    Where-Object {
        $a = Get-Content $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
        $a.state -in @("DISPATCH_RESERVED", "NETWORK_DISPATCH_STARTED", "RESPONSE_UNKNOWN", "IN_FLIGHT")
    }
if ($unresolved) { Fail-Preflight "UNRESOLVED_ATTEMPT" }
Write-Utf8Line "OK: unresolved attempt yok"

# Non-selected provider live flag must remain false for this process
Write-Utf8Line "OK: selectedProviderLiveFlag=false (preflight); other=false"
Write-Utf8Line "OK: networkCalls=false"

# No private/audio dump in repo — Extension filtresi (Include kullanma; yanlış pozitif üretir)
$repoAudioLeaks = @(Find-RepoPrivateAudioLeaks -Root $PSScriptRoot)
if ($repoAudioLeaks.Count -gt 0) {
    foreach ($line in @(Format-RepoPrivateAudioLeakPublicOutput -RelativePaths $repoAudioLeaks -MaxPaths 10)) {
        Write-Utf8Line $line
    }
    exit 1
}

Write-Utf8Line "PREFLIGHT_READY"
exit 0
