. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
. (Join-Path $PSScriptRoot "canonical-paths.ps1")
. (Join-Path $PSScriptRoot "maven-resolve.ps1")
$ErrorActionPreference = "Stop"
$Maven = Resolve-MavenCommand

$env:ELEVENLABS_OFFLINE = 'true'
$env:ELEVENLABS_LIVE_ENABLED = 'false'
$env:XAI_TTS_LIVE_ENABLED = 'false'
$env:OPENAI_TTS_LIVE_ENABLED = 'false'
$env:GOOGLE_TTS_LIVE_ENABLED = 'false'
$env:AZURE_TTS_LIVE_ENABLED = 'false'
$env:CARTESIA_TTS_LIVE_ENABLED = 'false'

function Get-FileHashSafe([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
}

function Get-TreeSnapshot([string]$Root) {
    $map = @{}
    if (-not (Test-Path -LiteralPath $Root)) { return $map }
    Get-ChildItem -LiteralPath $Root -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
        $rel = $_.FullName.Substring((Resolve-Path -LiteralPath $Root).Path.Length).TrimStart('\','/')
        $map[$rel.Replace('\','/')] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    }
    return $map
}

function Assert-SnapshotEqual($Before, $After, [string]$Label) {
    $beforeKeys = @($Before.Keys | Sort-Object)
    $afterKeys = @($After.Keys | Sort-Object)
    if (($beforeKeys -join '|') -cne ($afterKeys -join '|')) {
        throw "$Label dosya listesi degisti"
    }
    foreach ($key in $beforeKeys) {
        if ($Before[$key] -cne $After[$key]) {
            throw "$Label hash degisti: $key"
        }
    }
}

$beforeStatus = (@(git status --porcelain=v1 --untracked-files=all) -join "`n")
$beforeTeslim = (@(git diff -- TESLIM_OZETI.md) -join "`n")
$paths = Get-EserCanonicalPaths
$sel = Join-Path $paths.Ses 'ab-test\passage-selection\ESER-00005\PASSAGE-ESER-00005-20260715-183836-DF7B8C'
$approved = Join-Path $sel 'approved-passage.json'
$manifest = Join-Path $sel 'selection-manifest.json'
$rightsDir = Join-Path $sel 'rights'
$liveRoot = Join-Path $paths.Ses 'ab-test\live-preview\ESER-00005'
$teslimler = Join-Path $paths.CanonicalRoot 'teslimler'

$snap = @{
    Approved = Get-FileHashSafe $approved
    Manifest = Get-FileHashSafe $manifest
    Rights = Get-TreeSnapshot $rightsDir
    LivePreview = Get-TreeSnapshot $liveRoot
    Teslimler = Get-TreeSnapshot $teslimler
}

function Invoke-Step {
    param([string]$Label, [scriptblock]$Action)
    Write-Utf8Line ""
    Write-TestBaslik $Label
    try {
        & $Action
        if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) { throw "Exit code $LASTEXITCODE" }
    } catch {
        Write-Utf8Line "ADIM 36A HATA: $Label - $($_.Exception.Message)"
        exit 1
    }
}

Invoke-Step "adim36aLiveFlags" {
    if ($env:ELEVENLABS_OFFLINE -ne 'true') { throw "ELEVENLABS_OFFLINE acik" }
    foreach ($name in @(
            'ELEVENLABS_LIVE_ENABLED', 'XAI_TTS_LIVE_ENABLED', 'OPENAI_TTS_LIVE_ENABLED',
            'GOOGLE_TTS_LIVE_ENABLED', 'AZURE_TTS_LIVE_ENABLED', 'CARTESIA_TTS_LIVE_ENABLED')) {
        if ((Get-Item -Path "Env:$name").Value -ne 'false') { throw "$name acik" }
    }
    Write-Utf8Line "OK: live flag'ler kapali"
}

Invoke-Step "adim36aMavenTest" { & $Maven -q clean test }
Invoke-Step "adim36aMavenPackage" { & $Maven -q package }

Invoke-Step "adim36aDogrulama" {
    Write-TestBaslik "adim36a"
    & $Maven -q "-Dexec.mainClass=Adim36ADogrulama" exec:java
    if ($LASTEXITCODE -ne 0) { throw "Adim36ADogrulama exit $LASTEXITCODE" }
}

Invoke-Step "adim35Regression" {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "adim35-self-test.ps1")
}

Invoke-Step "adim36aCanonicalGuard" {
    if ($null -eq $snap.Approved) { throw "approved-passage yok" }
    $afterApproved = Get-FileHashSafe $approved
    if ($afterApproved -cne $snap.Approved) { throw "Canonical approved-passage degisti" }
    $afterManifest = Get-FileHashSafe $manifest
    if ($afterManifest -cne $snap.Manifest) { throw "selection-manifest degisti" }
    Assert-SnapshotEqual $snap.Rights (Get-TreeSnapshot $rightsDir) "rights"
    Assert-SnapshotEqual $snap.LivePreview (Get-TreeSnapshot $liveRoot) "canonical live-preview"
    Assert-SnapshotEqual $snap.Teslimler (Get-TreeSnapshot $teslimler) "canonical teslimler"

    $forbidden = @(
        'live-generation-approval.json',
        'xai-lumen.wav', 'openai-marin.wav',
        'xAI-Grok-TTS.mp3', 'OpenAI-gpt-4o-mini-tts.mp3'
    )
    foreach ($name in $forbidden) {
        $hits = Get-ChildItem -LiteralPath $liveRoot -Recurse -Filter $name -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch 'adim36a-|Temp|AppData\\Local\\Temp' }
        if ($hits) {
            throw "Canonical live-preview yeni/uygunsuz dosya: $name"
        }
    }
    $zipHits = Get-ChildItem -LiteralPath $teslimler -Filter 'AHMET-BEY-SES-KARSILASTIRMASI-*-PUBLIC.zip' -ErrorAction SilentlyContinue
    # Only fail if NEW compared to snapshot — Assert-SnapshotEqual already covers this
    Write-Utf8Line "OK: canonical snapshot birebir ayni (approved/manifest/rights/live-preview/teslimler)"
}

Invoke-Step "adim36aSecretScan" {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "check-secrets.ps1")
}

Invoke-Step "adim36aTrackedSideEffects" {
    $afterStatus = (@(git status --porcelain=v1 --untracked-files=all) -join "`n")
    $afterTeslim = (@(git diff -- TESLIM_OZETI.md) -join "`n")
    if ($beforeStatus -cne $afterStatus) { throw "Git durumu test sirasinda degisti." }
    if ($beforeTeslim -cne $afterTeslim) { throw "TESLIM_OZETI.md test sirasinda degisti." }
    Write-Utf8Line "OK: Git ve TESLIM_OZETI degismedi"
}

Write-Utf8Line ""
Write-Utf8Line "ADIM 36A DOGRULAMA: BASARILI"
exit 0
