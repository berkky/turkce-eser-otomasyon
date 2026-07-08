. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$kayitli = [Environment]::GetEnvironmentVariable("FFMPEG_PATH", "User")
$bitrate = [Environment]::GetEnvironmentVariable("PIPER_MP3_BITRATE", "User")
Write-Host "FFMPEG_PATH        : $kayitli"
Write-Host "PIPER_MP3_BITRATE : $bitrate"
if ($kayitli -and (Test-Path $kayitli -PathType Leaf)) {
    & $kayitli -version | Select-Object -First 1
    & $kayitli -hide_banner -encoders 2>&1 | Select-String "libmp3lame" | Select-Object -First 1
} else {
    Write-Host "FFmpeg bulunamadi. .\ffmpeg-kurulum.ps1 calistir."
}
