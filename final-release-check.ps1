param(
    [switch]$TestMode
)

. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"

$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path $Maven)) { throw "Maven bulunamadi: $Maven" }

$env:ELEVENLABS_OFFLINE = 'true'
$env:ELEVENLABS_LIVE_ENABLED = 'false'
$env:XAI_TTS_LIVE_ENABLED = 'false'
$env:OPENAI_TTS_LIVE_ENABLED = 'false'
$env:GOOGLE_TTS_LIVE_ENABLED = 'false'
$env:AZURE_TTS_LIVE_ENABLED = 'false'
$env:CARTESIA_TTS_LIVE_ENABLED = 'false'

$reportDir = Join-Path ([Environment]::GetFolderPath('Desktop')) 'turkce-eser-final-release'
$warnings = [System.Collections.Generic.List[string]]::new()
$failedStep = $null

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Action
    )
    if ($failedStep) { return }
    Write-Utf8Line ""
    Write-TestBaslik $Name
    try {
        & $Action
        if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) {
            throw "Exit code $LASTEXITCODE"
        }
    } catch {
        $script:failedStep = $Name
        Write-Utf8Line "HATA: $Name - $($_.Exception.Message)"
    }
}

function Invoke-ElevenLabsMock {
    param([scriptblock]$Action)
    $onceki = $env:ELEVENLABS_MOCK
    try {
        $env:ELEVENLABS_MOCK = 'true'
        & $Action
        $kod = $LASTEXITCODE
        if ($null -ne $kod -and $kod -ne 0) {
            throw "Exit code $kod"
        }
    } finally {
        if ($null -eq $onceki) {
            Remove-Item Env:ELEVENLABS_MOCK -ErrorAction SilentlyContinue
        } else {
            $env:ELEVENLABS_MOCK = $onceki
        }
    }
    $global:LASTEXITCODE = 0
}

function Invoke-ElevenLabsOffline {
    param([scriptblock]$Action)
    $onceki = $env:ELEVENLABS_OFFLINE
    try {
        $env:ELEVENLABS_OFFLINE = 'true'
        & $Action
        $kod = $LASTEXITCODE
        if ($null -ne $kod -and $kod -ne 0) {
            throw "Exit code $kod"
        }
    } finally {
        if ($null -eq $onceki) {
            Remove-Item Env:ELEVENLABS_OFFLINE -ErrorAction SilentlyContinue
        } else {
            $env:ELEVENLABS_OFFLINE = $onceki
        }
    }
    $global:LASTEXITCODE = 0
}

function Get-GitHash {
    try {
        $h = git -C $PSScriptRoot rev-parse --short HEAD 2>$null
        if ($h) { return $h.Trim() }
    } catch { }
    return ""
}

function Get-GitClean {
    try {
        $s = git -C $PSScriptRoot status --porcelain 2>$null
        return [string]::IsNullOrWhiteSpace($s)
    } catch { }
    return $false
}

function Write-ReleaseReport {
    param(
        [bool]$Build,
        [bool]$Regression,
        [bool]$Secret,
        [bool]$Demo,
        [bool]$GitClean,
        [string]$Result
    )
    New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
    $version = "32.0.0"
    try {
        $pom = [xml](Get-Content (Join-Path $PSScriptRoot 'pom.xml') -Raw)
        $version = $pom.project.version
    } catch { }

    $report = [ordered]@{
        timestamp = (Get-Date -Format 'o')
        buildPassed = $Build
        regressionPassed = $Regression
        secretScanPassed = $Secret
        demoPackagePassed = $Demo
        webPanelManualCheckRequired = $true
        gitStatusClean = $GitClean
        version = $version
        commitHash = (Get-GitHash)
        warnings = @($warnings)
        result = $Result
        testMode = [bool]$TestMode
    }

    $jsonPath = Join-Path $reportDir 'final-release-report.json'
    $report | ConvertTo-Json -Depth 5 | Set-Content -Path $jsonPath -Encoding UTF8

    $uyariMetin = if ($warnings.Count -eq 0) { '- Yok' } else { ($warnings | ForEach-Object { "- $_" }) -join "`n" }
    $buildDurum = if ($Build) { 'GECTI' } else { 'BASARISIZ' }
    $regDurum = if ($Regression) { 'GECTI' } else { 'BASARISIZ / ATLANDI' }
    $secretDurum = if ($Secret) { 'TEMIZ' } else { 'BULGU' }
    $demoDurum = if ($Demo) { 'GECTI' } else { 'BASARISIZ / ATLANDI' }
    $gitDurum = if ($GitClean) { 'EVET' } else { 'HAYIR' }
    $tarih = Get-Date -Format 'yyyy-MM-dd HH:mm'
    $hash = Get-GitHash
    $md = @(
        '# Final Release Ozeti',
        '',
        "- Sonuc: $Result",
        "- Surum: $version",
        "- Tarih: $tarih",
        "- Commit: $hash",
        '',
        '## Kontroller',
        "- Build: $buildDurum",
        "- Regression: $regDurum",
        "- Secret scan: $secretDurum",
        "- Demo paketi: $demoDurum",
        "- Git temiz: $gitDurum",
        '- Web panel manuel: GEREKLI',
        '',
        '## Uyarilar',
        '',
        $uyariMetin,
        '',
        "Rapor JSON: $jsonPath"
    ) -join [Environment]::NewLine
    Set-Content -Path (Join-Path $reportDir 'final-release-summary.md') -Value $md -Encoding UTF8
}

$buildOk = $false
$regressionOk = $false
$secretOk = $false
$demoOk = $false
$gitClean = Get-GitClean
if (-not $gitClean) { $warnings.Add('Git working tree temiz degil - teslim oncesi kontrol edin.') }

Write-Utf8Line ""
Write-Utf8Line "========================================"
Write-Utf8Line "FINAL RELEASE CHECK (Adim 32)"
if ($TestMode) { Write-Utf8Line "Mod: TEST (kisaltilmis)" }
Write-Utf8Line "========================================"

Invoke-Step 'finalBuild' {
    & $Maven -q -DskipTests compile
}

if (-not $failedStep) { $buildOk = $true }

if (-not $TestMode) {
    Invoke-Step 'finalRegression' {
        Invoke-ElevenLabsOffline {
            & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'adim31-self-test.ps1')
        }
    }
    if (-not $failedStep) { $regressionOk = $true }

    Invoke-Step 'finalDemoPaketi' {
        Invoke-ElevenLabsOffline {
            & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'patron-demo-paketi.ps1')
        }
    }
    if (-not $failedStep) { $demoOk = $true }

    Invoke-Step 'finalElevenLabsDurum' {
        Invoke-ElevenLabsOffline {
            & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'elevenlabs-durum.ps1')
        }
    }

    Invoke-Step 'finalAlignmentFixture' {
        & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'elevenlabs-alignment.ps1') -EserId 5 -Mock -DemoFixture
    }

    Invoke-Step 'finalTamEserPlan5' {
        Invoke-ElevenLabsMock {
            & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'tam-eser-plan.ps1') -EserId 5
        }
    }

    Invoke-Step 'finalTamEserPlan6' {
        Invoke-ElevenLabsMock {
            & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'tam-eser-plan.ps1') -EserId 6
        }
    }

    Invoke-Step 'finalTamEserOnay' {
        Invoke-ElevenLabsMock {
            & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'tam-eser-onay-taslagi.ps1') -EserId 5
        }
    }

    Invoke-Step 'finalTamEserKuyruk' {
        Invoke-ElevenLabsMock {
            & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'tam-eser-kuyruga-al.ps1') -EserId 5 -Onayli
        }
    }
} else {
    $regressionOk = $true
    $demoOk = $true
    $warnings.Add('TestMode: regression ve demo scriptleri atlandi.')
}

Invoke-Step 'finalSecretScan' {
    & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'check-secrets.ps1')
}
if (-not $failedStep) { $secretOk = $true }

Invoke-Step 'finalGitStatus' {
    Write-Utf8Line "Git durumu:"
    git -C $PSScriptRoot status --short 2>&1 | ForEach-Object { Write-Utf8Line $_ }
    if (-not $gitClean) {
        Write-Utf8Line "Uyari: Working tree temiz degil."
    } else {
        Write-Utf8Line "Git: temiz"
    }
    $global:LASTEXITCODE = 0
}

Write-Utf8Line ""
if ($failedStep) {
    Write-ReleaseReport -Build $buildOk -Regression $regressionOk -Secret $secretOk -Demo $demoOk -GitClean $gitClean -Result 'BASARISIZ'
    Write-Utf8Line "FINAL RELEASE CHECK: BASARISIZ - su adimda kaldi: $failedStep"
    Write-Utf8Line "Rapor: $reportDir"
    exit 1
}

Write-ReleaseReport -Build $buildOk -Regression $regressionOk -Secret $secretOk -Demo $demoOk -GitClean $gitClean -Result 'BASARILI'
Write-Utf8Line "FINAL RELEASE CHECK: BASARILI"
Write-Utf8Line "Rapor: $reportDir\final-release-report.json"
exit 0
