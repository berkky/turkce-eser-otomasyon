. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path $Maven)) { throw "Maven bulunamadi: $Maven" }

$steps = @(
    @{ Label = 'adim29derleme'; Type = 'compile' },
    @{ Label = 'adim21'; MainClass = 'Adim21Dogrulama' },
    @{ Label = 'adim23meta'; MainClass = 'Adim23Dogrulama' },
    @{ Label = 'adim24'; MainClass = 'Adim24Dogrulama' },
    @{ Label = 'adim25'; MainClass = 'Adim25Dogrulama' },
    @{ Label = 'adim26'; MainClass = 'Adim26Dogrulama'; Timeout = 180 },
    @{ Label = 'adim27'; MainClass = 'Adim27Dogrulama'; Timeout = 180 },
    @{ Label = 'adim28'; MainClass = 'Adim28Dogrulama'; Timeout = 240 },
    @{ Label = 'adim29'; MainClass = 'Adim29Dogrulama'; Timeout = 300 }
)

foreach ($step in $steps) {
    Write-Utf8Line ""
    try {
        if ($step.Type -eq 'compile') {
            Write-TestBaslik $step.Label
            & $Maven -q -DskipTests compile
            if ($LASTEXITCODE -ne 0) { throw "Derleme basarisiz (exit $LASTEXITCODE)" }
        } else {
            $timeout = if ($step.Timeout) { $step.Timeout } else { 300 }
            Invoke-MavenExecTimeout -Maven $Maven -MainClass $step.MainClass -StepLabel $step.Label -TimeoutSeconds $timeout
        }
    } catch {
        Write-Utf8Line ""
        Write-TestBaslik 'adim29selftestFail'
        Write-Utf8Line ($step.Label + " - " + $_.Exception.Message)
        exit 1
    }
}

Write-Utf8Line ""
Write-TestBaslik 'adim29fixture'
$fixtureScript = Join-Path $PSScriptRoot 'elevenlabs-alignment.ps1'
& powershell -ExecutionPolicy Bypass -File $fixtureScript -EserId 5 -Mock -DemoFixture
if ($LASTEXITCODE -ne 0) { throw "DemoFixture script basarisiz (exit $LASTEXITCODE)" }

$alignDir = Join-Path $env:USERPROFILE 'Desktop\ses-arsivi\_alignment'
$vttPath = Join-Path $alignDir 'ESER-00005-preview-subtitles.vtt'
$srtPath = Join-Path $alignDir 'ESER-00005-preview-subtitles.srt'
$jsonPath = Join-Path $alignDir 'ESER-00005-preview-alignment.json'
foreach ($p in @($vttPath, $srtPath, $jsonPath)) {
    if (-not (Test-Path $p)) { throw "DemoFixture cikti eksik: $p" }
}
$vtt = Get-Content $vttPath -Raw -Encoding UTF8
if ($vtt -notmatch '^WEBVTT') { throw 'VTT WEBVTT ile baslamiyor' }
$srt = Get-Content $srtPath -Raw -Encoding UTF8
if ($srt -notmatch '-->') { throw 'SRT format hatali' }
$json = Get-Content $jsonPath -Raw -Encoding UTF8
if ($json -notmatch 'demoFixture' -or $json -notmatch 'true') { throw 'demoFixture JSON alani yok' }
if ($json -match 'sk_[a-zA-Z0-9]{10,}' -or $json -match 'C:\\Users\\') { throw 'JSON guvenlik sizintisi' }

Write-Utf8Line ""
Write-TestBaslik 'adim29selftestOk'
exit 0
