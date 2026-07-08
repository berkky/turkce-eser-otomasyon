. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"

Write-Host "========================================"
Write-Host "YEREL PIPER TTS KURULUMU"
Write-Host "========================================"

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$VenvDir = Join-Path $ProjectDir ".venv-piper"
$VenvPython = Join-Path $VenvDir "Scripts\python.exe"
$VoiceDir = Join-Path ([Environment]::GetFolderPath("Desktop")) "piper-voices"
$VoiceName = "tr_TR-dfki-medium"
# Not: fettah ve fahrettin sesleri güncel ana depodan kaldırıldığı için desteklenen dfki modeli kullanılır.

function Find-Python {
    $candidates = @(
        @{ Exe = "py"; Args = @("-3.11") },
        @{ Exe = "py"; Args = @("-3.10") },
        @{ Exe = "py"; Args = @("-3") },
        @{ Exe = "python"; Args = @() }
    )

    foreach ($candidate in $candidates) {
        try {
            $version = & $candidate.Exe @($candidate.Args) --version 2>&1
            if ($LASTEXITCODE -eq 0 -and "$version" -match "Python 3\.") {
                return $candidate
            }
        }
        catch {
            # Sonraki adayı dene.
        }
    }

    return $null
}

$python = Find-Python
if ($null -eq $python) {
    Write-Host ""
    Write-Host "Python 3 bulunamadı." -ForegroundColor Red
    Write-Host "Önce Python 3.11 kur, kurulum ekranında 'Add Python to PATH' kutusunu işaretle."
    exit 1
}

Write-Host "Bulunan Python: $($python.Exe) $($python.Args -join ' ')"

if (-not (Test-Path $VenvPython)) {
    Write-Host "Piper için özel Python ortamı oluşturuluyor..."
    & $python.Exe @($python.Args) -m venv $VenvDir
    if ($LASTEXITCODE -ne 0) {
        throw "Python sanal ortamı oluşturulamadı."
    }
}
else {
    Write-Host "Mevcut Piper Python ortamı kullanılacak."
}

Write-Host "pip güncelleniyor..."
& $VenvPython -m pip install --upgrade pip
if ($LASTEXITCODE -ne 0) {
    throw "pip güncellenemedi."
}

Write-Host "Piper TTS 1.4.2 kuruluyor..."
& $VenvPython -m pip install "piper-tts==1.4.2"
if ($LASTEXITCODE -ne 0) {
    throw "piper-tts kurulamadı."
}

New-Item -ItemType Directory -Force -Path $VoiceDir | Out-Null

Write-Host "Türkçe ses modeli indiriliyor: $VoiceName"
& $VenvPython -m piper.download_voices --data-dir $VoiceDir $VoiceName
if ($LASTEXITCODE -ne 0) {
    throw "Türkçe Piper ses modeli indirilemedi."
}

Write-Host "Piper kontrol ediliyor..."
& $VenvPython -m piper --help | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Piper çalıştırma testi başarısız."
}

[Environment]::SetEnvironmentVariable("PIPER_PYTHON", $VenvPython, "User")
[Environment]::SetEnvironmentVariable("PIPER_DATA_DIR", $VoiceDir, "User")
[Environment]::SetEnvironmentVariable("PIPER_VOICE", $VoiceName, "User")
[Environment]::SetEnvironmentVariable("PIPER_USE_CUDA", "false", "User")

$env:PIPER_PYTHON = $VenvPython
$env:PIPER_DATA_DIR = $VoiceDir
$env:PIPER_VOICE = $VoiceName
$env:PIPER_USE_CUDA = "false"

Write-Host ""
Write-Host "PIPER KURULUMU TAMAMLANDI" -ForegroundColor Green
Write-Host "Python     : $VenvPython"
Write-Host "Ses modeli : $VoiceName"
Write-Host "Model yolu : $VoiceDir"
Write-Host "Çalışma    : CPU"
Write-Host ""
Write-Host "Şimdi yerel ses testini çalıştırabilirsin:"
Write-Host '& "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" "-Dexec.mainClass=PiperYerelTest" exec:java'
