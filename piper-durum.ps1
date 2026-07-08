. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$LocalPython = Join-Path $ProjectDir ".venv-piper\Scripts\python.exe"
$Python = if ($env:PIPER_PYTHON) { $env:PIPER_PYTHON } else { $LocalPython }
$VoiceDir = if ($env:PIPER_DATA_DIR) { $env:PIPER_DATA_DIR } else { Join-Path ([Environment]::GetFolderPath("Desktop")) "piper-voices" }
$Voice = if ($env:PIPER_VOICE) { $env:PIPER_VOICE } else { "tr_TR-dfki-medium" }

Write-Host "PIPER DURUMU"
Write-Host "Python     : $Python"
Write-Host "Model yolu : $VoiceDir"
Write-Host "Ses modeli : $Voice"
Write-Host "Python var : $(Test-Path $Python)"
Write-Host "ONNX var   : $(Test-Path (Join-Path $VoiceDir ($Voice + '.onnx')))"
Write-Host "JSON var   : $(Test-Path (Join-Path $VoiceDir ($Voice + '.onnx.json')))"

if (Test-Path $Python) {
    & $Python -m piper --help | Select-Object -First 5
}
