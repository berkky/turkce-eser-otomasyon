. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path $Maven)) { throw "Maven bulunamadi: $Maven" }

$TeslimKlasoru = "C:\Users\Lenovo\Desktop\turkce-eser-final-teslim"
$ZipYolu = Join-Path $TeslimKlasoru "turkce-eser-otomasyon-final.zip"

function Invoke-Step {
    param(
        [string]$Label,
        [scriptblock]$Action
    )
    Write-Utf8Line ""
    Write-TestBaslik $Label
    try {
        & $Action
        if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) {
            throw "Exit code $LASTEXITCODE"
        }
    } catch {
        Write-Utf8Line ""
        Write-TestBaslik 'adim33selftestFail'
        Write-Utf8Line ($Label + " - " + $_.Exception.Message)
        exit 1
    }
}

Invoke-Step 'adim33derleme' {
    & $Maven -q -DskipTests compile
}

Invoke-Step 'adim32selftest' {
    & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'adim32-self-test.ps1')
}

Invoke-Step 'adim33secret' {
    & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'check-secrets.ps1')
}

Invoke-Step 'adim33finalCheck' {
    & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'final-release-check.ps1') -TestMode
}

Invoke-Step 'adim33teslimOlustur' {
    & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'teslim-paketi-olustur.ps1')
}

Invoke-Step 'adim33teslimKontrol' {
    & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'teslim-paketi-kontrol.ps1')
}

Invoke-Step 'adim33zipVar' {
    if (-not (Test-Path $ZipYolu)) { throw "ZIP yok: $ZipYolu" }
    Write-Utf8Line "OK: ZIP mevcut"
}

Invoke-Step 'adim33zipGitYok' {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ZipYolu)
    try {
        foreach ($e in $archive.Entries) {
            if ($e.FullName -like '.git*' -or $e.FullName -like '*/.git/*') {
                throw ".git ZIP icinde: $($e.FullName)"
            }
        }
    } finally { $archive.Dispose() }
    Write-Utf8Line 'OK: ZIP icinde .git yok'
}

Invoke-Step 'adim33zipTargetYok' {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ZipYolu)
    try {
        foreach ($e in $archive.Entries) {
            if ($e.FullName -like 'target/*' -or $e.FullName -like '*/target/*') {
                throw "target ZIP icinde: $($e.FullName)"
            }
        }
    } finally { $archive.Dispose() }
    Write-Utf8Line 'OK: ZIP icinde target yok'
}

Invoke-Step 'adim33teslimDokumanlari' {
    foreach ($ad in @('GONDERIM_MESAJLARI.md', 'PATRON_KISA_OZET.md', 'TEKNIK_KISIYE_NOT.md')) {
        $p = Join-Path $TeslimKlasoru $ad
        if (-not (Test-Path $p)) { throw "Eksik: $p" }
    }
    Write-Utf8Line 'OK: Teslim dokumanlari'
}

Invoke-Step 'adim33sha256' {
    $sha = Join-Path $TeslimKlasoru 'teslim-paketi-sha256.txt'
    if (-not (Test-Path $sha)) { throw "SHA-256 dosyasi yok" }
    Write-Utf8Line 'OK: SHA-256'
}

Invoke-Step 'adim33dogrulama' {
    Invoke-MavenExecTimeout -Maven $Maven -MainClass 'Adim33Dogrulama' -StepLabel 'adim33' -TimeoutSeconds 120
}

Write-Utf8Line ""
Write-Utf8Line "ADIM 33 DOGRULAMA: BASARILI"
exit 0
