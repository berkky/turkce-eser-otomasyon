. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
. (Join-Path $PSScriptRoot "canonical-paths.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path -LiteralPath $Maven)) { throw "Maven bulunamadı: $Maven" }

$env:ELEVENLABS_OFFLINE = 'true'
$env:ELEVENLABS_LIVE_ENABLED = 'false'
$env:XAI_TTS_LIVE_ENABLED = 'false'
$env:OPENAI_TTS_LIVE_ENABLED = 'false'
$env:GOOGLE_TTS_LIVE_ENABLED = 'false'
$env:AZURE_TTS_LIVE_ENABLED = 'false'
$env:CARTESIA_TTS_LIVE_ENABLED = 'false'

$beforeStatus = (@(git status --porcelain=v1 --untracked-files=all) -join "`n")
$beforeTeslim = (@(git diff -- TESLIM_OZETI.md) -join "`n")
$paths = Get-EserCanonicalPaths
$planPaths = @(
    (Join-Path $paths.ProductionApprovals 'ESER-00005-production-plan.json'),
    (Join-Path $paths.ProductionApprovals 'ESER-00006-production-plan.json'),
    (Join-Path $paths.LegacyRoot 'eser-otomasyon-kuyruk\production-approvals\ESER-00005-production-plan.json'),
    (Join-Path $paths.LegacyRoot 'eser-otomasyon-kuyruk\production-approvals\ESER-00006-production-plan.json')
)
$planSnapshots = @{}
foreach ($path in $planPaths) {
    if (Test-Path -LiteralPath $path) {
        $planSnapshots[$path] = "$(Get-FileHash -LiteralPath $path -Algorithm SHA256)|$((Get-Item $path).LastWriteTimeUtc.Ticks)"
    }
}

function Invoke-Step {
    param([string]$Label, [scriptblock]$Action)
    Write-Utf8Line ""
    Write-TestBaslik $Label
    try {
        & $Action
        if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) {
            throw "Exit code $LASTEXITCODE"
        }
    } catch {
        Write-Utf8Line "ADIM 35 HATA: $Label - $($_.Exception.Message)"
        exit 1
    }
}

Invoke-Step "adim35CanonicalSource" {
    $source = Join-Path $paths.Metin 'ESER-00005 - Kasagi - Vikikaynak\tam-metin.txt'
    if (-not (Test-Path -LiteralPath $source) -or (Get-Item $source).Length -eq 0) {
        throw "Canonical ESER-00005 kaynağı bulunamadı."
    }
    Write-Utf8Line "OK: canonical ESER-00005 kaynak"
}

Invoke-Step "adim35MavenTest" {
    & $Maven -q clean test
}

Invoke-Step "adim35MavenPackage" {
    & $Maven -q package
}

Invoke-Step "adim35Dogrulama" {
    Invoke-MavenExecTimeout -Maven $Maven -MainClass "Adim35Dogrulama" `
        -StepLabel "adim35" -TimeoutSeconds 240
}

Invoke-Step "adim34Regression" {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "adim34-self-test.ps1")
}

Invoke-Step "adim35SecretScan" {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "check-secrets.ps1")
}

Invoke-Step "adim35TrackedSideEffects" {
    $afterStatus = (@(git status --porcelain=v1 --untracked-files=all) -join "`n")
    $afterTeslim = (@(git diff -- TESLIM_OZETI.md) -join "`n")
    if ($beforeStatus -cne $afterStatus) { throw "Tracked/untracked Git durumu test sırasında değişti." }
    if ($beforeTeslim -cne $afterTeslim) { throw "TESLIM_OZETI.md test sırasında değişti." }
    foreach ($path in $planSnapshots.Keys) {
        $current = "$(Get-FileHash -LiteralPath $path -Algorithm SHA256)|$((Get-Item $path).LastWriteTimeUtc.Ticks)"
        if ($current -ne $planSnapshots[$path]) {
            throw "Üretim planı gereksiz yere değişti: $([System.IO.Path]::GetFileName($path))"
        }
    }
    Write-Utf8Line "OK: Git, TESLIM_OZETI ve canonical/legacy planlar değişmedi"
}

Write-Utf8Line ""
Write-Utf8Line "ADIM 35 DOGRULAMA: BASARILI"
exit 0
