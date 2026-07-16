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

    # Yalnız snapshot'ta olmayan yeni yasaklı dosyalar — mevcut canonical approval dokunulmaz
    $forbidden = @(
        'live-generation-approval.json',
        'xai-lumen.wav', 'openai-marin.wav',
        'xAI-Grok-TTS.mp3', 'OpenAI-gpt-4o-mini-tts.mp3'
    )
    $afterLive = Get-TreeSnapshot $liveRoot
    foreach ($name in $forbidden) {
        $newHits = @($afterLive.Keys | Where-Object {
            $_.EndsWith('/' + $name) -or $_.Equals($name)
        } | Where-Object { -not $snap.LivePreview.ContainsKey($_) })
        if ($newHits.Count -gt 0) {
            throw "Canonical live-preview yeni/uygunsuz dosya: $name ($($newHits -join ', '))"
        }
    }
    # ZIP: Assert-SnapshotEqual already covers new teslimler files
    Write-Utf8Line "OK: canonical snapshot birebir ayni (approved/manifest/rights/live-preview/teslimler)"
}

Invoke-Step "adim36b0RepoAudioScan" {
    . (Join-Path $PSScriptRoot "repo-private-audio-scan.ps1")
    $preflightSrc = Get-Content -LiteralPath (Join-Path $PSScriptRoot "adim36-live-preflight.ps1") -Raw -Encoding UTF8
    if ($preflightSrc -match '(?i)Get-ChildItem[^\r\n]*-Include') {
        throw "Preflight hala Get-ChildItem -Include kullanıyor"
    }
    if ($preflightSrc -notmatch 'ToLowerInvariant\(\)\s*-in\s*@\("\.wav",\s*"\.mp3",\s*"\.zip"\)' `
            -and $preflightSrc -notmatch 'Find-RepoPrivateAudioLeaks') {
        throw "Preflight Extension ToLowerInvariant filtresi / Find-RepoPrivateAudioLeaks yok"
    }
    if ($preflightSrc -notmatch 'repo-private-audio-scan\.ps1') {
        throw "Preflight repo-private-audio-scan.ps1 yüklemiyor"
    }

    $fixture = Join-Path ([System.IO.Path]::GetTempPath()) ("adim36b0-audio-scan-" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $fixture | Out-Null
    try {
        Set-Content -LiteralPath (Join-Path $fixture 'note.ps1') -Value 'Write-Host decoy' -Encoding UTF8
        Set-Content -LiteralPath (Join-Path $fixture 'App.java') -Value 'class App {}' -Encoding UTF8
        Set-Content -LiteralPath (Join-Path $fixture 'README.md') -Value '# audio notes' -Encoding UTF8
        Set-Content -LiteralPath (Join-Path $fixture 'pom.xml') -Value '<project/>' -Encoding UTF8
        Set-Content -LiteralPath (Join-Path $fixture 'audio-notes.txt') -Value 'audio kelimesi' -Encoding UTF8

        $decoyHits = @(Find-RepoPrivateAudioLeaks -Root $fixture)
        if ($decoyHits.Count -ne 0) {
            throw ".ps1/.java/.md/.xml/audio adlı normal dosya leak sayılmamalı: $($decoyHits -join ',')"
        }

        Set-Content -LiteralPath (Join-Path $fixture 'test.wav') -Value 'RIFF' -Encoding ASCII
        $wavHits = @(Find-RepoPrivateAudioLeaks -Root $fixture)
        if (-not ($wavHits | Where-Object { $_.ToLowerInvariant() -eq 'test.wav' })) {
            throw "gerçek test.wav leak sayılmalı"
        }
        Remove-Item -LiteralPath (Join-Path $fixture 'test.wav') -Force

        Set-Content -LiteralPath (Join-Path $fixture 'test.MP3') -Value 'ID3' -Encoding ASCII
        $mp3Hits = @(Find-RepoPrivateAudioLeaks -Root $fixture)
        if (-not ($mp3Hits | Where-Object { $_.ToLowerInvariant() -eq 'test.mp3' })) {
            throw "gerçek test.MP3 leak sayılmalı"
        }
        Remove-Item -LiteralPath (Join-Path $fixture 'test.MP3') -Force

        Set-Content -LiteralPath (Join-Path $fixture 'test.ZIP') -Value 'PK' -Encoding ASCII
        $zipHits = @(Find-RepoPrivateAudioLeaks -Root $fixture)
        if (-not ($zipHits | Where-Object { $_.ToLowerInvariant() -eq 'test.zip' })) {
            throw "gerçek test.ZIP leak sayılmalı"
        }
        Remove-Item -LiteralPath (Join-Path $fixture 'test.ZIP') -Force

        New-Item -ItemType Directory -Force -Path (Join-Path $fixture 'target') | Out-Null
        New-Item -ItemType Directory -Force -Path (Join-Path $fixture 'node_modules') | Out-Null
        New-Item -ItemType Directory -Force -Path (Join-Path $fixture '.git') | Out-Null
        Set-Content -LiteralPath (Join-Path $fixture 'target\nested.wav') -Value 'x' -Encoding ASCII
        Set-Content -LiteralPath (Join-Path $fixture 'node_modules\pkg.mp3') -Value 'x' -Encoding ASCII
        Set-Content -LiteralPath (Join-Path $fixture '.git\obj.zip') -Value 'x' -Encoding ASCII
        $excludedHits = @(Find-RepoPrivateAudioLeaks -Root $fixture)
        if ($excludedHits.Count -ne 0) {
            throw "target/node_modules/.git altındaki ses/zip hariç tutulmalı: $($excludedHits -join ',')"
        }

        Set-Content -LiteralPath (Join-Path $fixture 'public-leak.wav') -Value 'x' -Encoding ASCII
        $publicHits = @(Find-RepoPrivateAudioLeaks -Root $fixture)
        $publicOut = @(Format-RepoPrivateAudioLeakPublicOutput -RelativePaths $publicHits -MaxPaths 10) -join "`n"
        $userHome = [Environment]::GetFolderPath('UserProfile')
        if ($publicOut.IndexOf($userHome, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
            throw "Public hata çıktısında mutlak user home bulunmamalı"
        }
        if ($publicOut -match '(?i)[A-Za-z]:\\Users\\') {
            throw "Public hata çıktısında mutlak Users yolu bulunmamalı"
        }
        if ($publicOut -notmatch 'PREFLIGHT_FAIL REPO_PRIVATE_AUDIO_LEAK') {
            throw "Public hata kodu eksik"
        }
        if ($publicOut -notmatch 'LEAK_PATH public-leak\.wav') {
            throw "Public çıktıda göreli LEAK_PATH beklenir"
        }

        $repoLeaks = @(Find-RepoPrivateAudioLeaks -Root $PSScriptRoot)
        Write-Utf8Line "REPO_PRIVATE_AUDIO_LEAK_COUNT=$($repoLeaks.Count)"
        if ($repoLeaks.Count -ne 0) {
            throw "Gerçek repoda audio/zip sızıntısı: $($repoLeaks -join ',')"
        }
        Write-Utf8Line "OK: repo audio scan Extension filtresi (Include yok); decoy/exclude/case/public path"
    } finally {
        Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue
    }
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
