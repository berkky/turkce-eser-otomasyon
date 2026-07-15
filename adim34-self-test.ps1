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
        Write-Utf8Line "ADIM 34 HATA: $Label - $($_.Exception.Message)"
        exit 1
    }
}

Invoke-Step "adim34CanonicalPath" {
    $testHome = Join-Path ([System.IO.Path]::GetTempPath()) ("adim34-path-" + [Guid]::NewGuid().ToString("N"))
    $override = Join-Path $testHome "override-metin"
    New-Item -ItemType Directory -Force -Path $override | Out-Null
    try {
        $defaults = Get-EserCanonicalPaths -UserProfile $testHome -EnvironmentOverrides @{}
        $expected = Join-Path $testHome "Desktop\ESER"
        if ($defaults.CanonicalRoot -ne $expected -or $defaults.Metin -ne (Join-Path $expected "metin-arsivi")) {
            throw "Environment yokken canonical Desktop\ESER seçilmedi."
        }
        $overridden = Get-EserCanonicalPaths -UserProfile $testHome `
            -EnvironmentOverrides @{ ESER_METIN_ARSIVI = $override }
        if ($overridden.Metin -ne $override) {
            throw "Geçerli environment override seçilmedi."
        }
        Write-Utf8Line "OK: Java/PowerShell canonical path politikası"
    } finally {
        Remove-Item -LiteralPath $testHome -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Invoke-Step "adim34Derleme" {
    & $Maven -q -DskipTests compile
}

Invoke-Step "adim34Dogrulama" {
    Invoke-MavenExecTimeout -Maven $Maven -MainClass "Adim34Dogrulama" -StepLabel "adim34" -TimeoutSeconds 180
}

Invoke-Step "adim33Regression" {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "adim33-self-test.ps1")
}

Invoke-Step "adim34SecretScan" {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "check-secrets.ps1")
}

Write-Utf8Line ""
Write-Utf8Line "ADIM 34 DOGRULAMA: BASARILI"
exit 0
