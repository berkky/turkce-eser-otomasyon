. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
. (Join-Path $PSScriptRoot "canonical-paths.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path -LiteralPath $Maven)) { throw "Maven bulunamadi: $Maven" }

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
$canonicalSelection = Join-Path $paths.Ses `
    'ab-test\passage-selection\ESER-00005\PASSAGE-ESER-00005-20260715-183836-DF7B8C'
$canonicalApproved = Join-Path $canonicalSelection 'approved-passage.json'
$canonicalRights = Join-Path $canonicalSelection 'rights\underlying-work-rights.json'
$canonicalBefore = $null
if (Test-Path -LiteralPath $canonicalApproved) {
    $canonicalBefore = Get-FileHash -LiteralPath $canonicalApproved -Algorithm SHA256
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
        Write-Utf8Line "ADIM 35B HATA: $Label - $($_.Exception.Message)"
        exit 1
    }
}

Invoke-Step "adim35bLiveFlags" {
    if ($env:ELEVENLABS_OFFLINE -ne 'true') { throw "ELEVENLABS_OFFLINE acik" }
    foreach ($name in @(
            'ELEVENLABS_LIVE_ENABLED', 'XAI_TTS_LIVE_ENABLED', 'OPENAI_TTS_LIVE_ENABLED',
            'GOOGLE_TTS_LIVE_ENABLED', 'AZURE_TTS_LIVE_ENABLED', 'CARTESIA_TTS_LIVE_ENABLED')) {
        if ((Get-Item -Path "Env:$name" -ErrorAction SilentlyContinue).Value -ne 'false') {
            throw "$name acik"
        }
    }
    Write-Utf8Line "OK: live flag'ler kapali"
}

Invoke-Step "adim35bMavenTest" {
    & $Maven -q clean test
}

Invoke-Step "adim35bMavenPackage" {
    & $Maven -q package
}

Invoke-Step "adim35bDogrulama" {
    Invoke-MavenExecTimeout -Maven $Maven -MainClass "Adim35BDogrulama" `
        -StepLabel "adim35b" -TimeoutSeconds 300
}

Invoke-Step "adim35Regression" {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "adim35-self-test.ps1")
}

Invoke-Step "adim35bCanonicalGuard" {
    if (-not (Test-Path -LiteralPath $canonicalSelection)) {
        throw "Canonical selection bulunamadi: $canonicalSelection"
    }
    if (Test-Path -LiteralPath $canonicalApproved) {
        if ($null -ne $canonicalBefore) {
            $current = Get-FileHash -LiteralPath $canonicalApproved -Algorithm SHA256
            if ($current.Hash -ne $canonicalBefore.Hash) {
                throw "Canonical approved-passage test sirasinda degisti."
            }
        }
    } else {
        Write-Utf8Line "OK: canonical approved-passage kullanici onayi olmadan yok"
    }
    if (-not (Test-Path -LiteralPath $canonicalRights)) {
        throw "Canonical rights kaydi eksik; Adim35PasajApp rights komutu ile hazirlanmali."
    }
    Write-Utf8Line "OK: canonical selection kullanici adina onaylanmadi"
}

Invoke-Step "adim35bSecretScan" {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "check-secrets.ps1")
}

Invoke-Step "adim35bTrackedSideEffects" {
    $afterStatus = (@(git status --porcelain=v1 --untracked-files=all) -join "`n")
    $afterTeslim = (@(git diff -- TESLIM_OZETI.md) -join "`n")
    if ($beforeStatus -cne $afterStatus) { throw "Tracked/untracked Git durumu test sirasinda degisti." }
    if ($beforeTeslim -cne $afterTeslim) { throw "TESLIM_OZETI.md test sirasinda degisti." }
    Write-Utf8Line "OK: Git ve TESLIM_OZETI degismedi"
}

Write-Utf8Line ""
Write-Utf8Line "ADIM 35B DOGRULAMA: BASARILI"
exit 0
