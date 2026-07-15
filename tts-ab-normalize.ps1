param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"

function Resolve-Tool {
    param([string]$Name, [string]$ExplicitPath)
    if ($ExplicitPath -and (Test-Path -LiteralPath $ExplicitPath -PathType Leaf)) {
        return (Resolve-Path -LiteralPath $ExplicitPath).Path
    }
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw "$Name bulunamadı. FFMPEG_PATH ayarlayın veya FFmpeg'i PATH'e ekleyin."
}

$inputFile = (Resolve-Path -LiteralPath $InputPath -ErrorAction Stop).Path
$inputInfo = Get-Item -LiteralPath $inputFile
if ($inputInfo.Length -le 0) { throw "Kaynak ses dosyası 0 byte." }

$ffmpeg = Resolve-Tool -Name "ffmpeg" -ExplicitPath $env:FFMPEG_PATH
$ffprobeExplicit = Join-Path (Split-Path -Parent $ffmpeg) "ffprobe.exe"
if (-not (Test-Path -LiteralPath $ffprobeExplicit)) {
    $ffprobeExplicit = Join-Path (Split-Path -Parent $ffmpeg) "ffprobe"
}
$ffprobe = Resolve-Tool -Name "ffprobe" -ExplicitPath $ffprobeExplicit

& $ffprobe "-v" "error" "-select_streams" "a:0" "-show_entries" "stream=codec_name" `
    "-of" "default=noprint_wrappers=1:nokey=1" $inputFile | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Kaynak dosyada oynatılabilir ses akışı bulunamadı." }

$firstArgs = @(
    "-hide_banner", "-nostats", "-i", $inputFile,
    "-af", "loudnorm=I=-16:TP=-1:LRA=11:print_format=json",
    "-f", "null", $(if ($IsLinux -or $IsMacOS) { "/dev/null" } else { "NUL" })
)
$oldPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$analysisText = (& $ffmpeg @firstArgs 2>&1 | Out-String)
$analysisExitCode = $LASTEXITCODE
$ErrorActionPreference = $oldPreference
if ($analysisExitCode -ne 0) { throw "FFmpeg loudness ölçümü başarısız: $analysisText" }

$match = [regex]::Match($analysisText, '(?s)\{\s*"input_i".*?"target_offset"\s*:\s*"[^"]+"\s*\}')
if (-not $match.Success) { throw "FFmpeg loudness ölçüm JSON'u alınamadı." }
$measure = $match.Value | ConvertFrom-Json

$filter = "loudnorm=I=-16:TP=-1:LRA=11:measured_I=$($measure.input_i):measured_LRA=$($measure.input_lra):measured_TP=$($measure.input_tp):measured_thresh=$($measure.input_thresh):offset=$($measure.target_offset):linear=true:print_format=summary"
$outputFull = [System.IO.Path]::GetFullPath($OutputPath)
$outputDir = Split-Path -Parent $outputFull
if (-not (Test-Path -LiteralPath $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

$secondArgs = @(
    "-y", "-hide_banner", "-loglevel", "error", "-i", $inputFile,
    "-vn", "-af", $filter, "-ac", "1", "-ar", "44100",
    "-codec:a", "libmp3lame", "-b:a", "192k",
    "-map_metadata", "-1", "-write_id3v1", "0", "-id3v2_version", "0",
    $outputFull
)
& $ffmpeg @secondArgs
if ($LASTEXITCODE -ne 0) {
    Remove-Item -LiteralPath $outputFull -Force -ErrorAction SilentlyContinue
    throw "FFmpeg normalizasyonu başarısız (exit $LASTEXITCODE)."
}
if (-not (Test-Path -LiteralPath $outputFull -PathType Leaf) -or
        (Get-Item -LiteralPath $outputFull).Length -le 0) {
    throw "Normalize çıktı oluşturulamadı veya 0 byte."
}

$verification = & $ffprobe "-v" "error" "-select_streams" "a:0" `
    "-show_entries" "stream=codec_name,sample_rate,channels,bit_rate:format=duration,size" `
    "-of" "json" $outputFull 2>&1
if ($LASTEXITCODE -ne 0) {
    Remove-Item -LiteralPath $outputFull -Force -ErrorAction SilentlyContinue
    throw "Normalize çıktı ffprobe doğrulamasından geçemedi: $verification"
}

Write-Output $outputFull
exit 0
