. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"

function UserEnv([string]$Name) {
    return [Environment]::GetEnvironmentVariable($Name, "User")
}

function VarStatus([string]$Name) {
    $v = UserEnv $Name
    if ([string]::IsNullOrWhiteSpace($v)) {
        return "YOK"
    }
    return "TANIMLI"
}

function ElevenLabsTtsStatus {
    $apiKey = UserEnv "ELEVENLABS_API_KEY"
    $voiceId = UserEnv "ELEVENLABS_VOICE_ID"
    $model = UserEnv "ELEVENLABS_MODEL"
    if ([string]::IsNullOrWhiteSpace($model)) {
        $model = "eleven_multilingual_v2"
    }

    if ([string]::IsNullOrWhiteSpace($apiKey)) {
        return "KAPALI | ELEVENLABS_API_KEY yok"
    }

    if ([string]::IsNullOrWhiteSpace($voiceId)) {
        return "KAPALI | ELEVENLABS_VOICE_ID yok"
    }

    try {
        $j = Invoke-RestMethod `
            -Method Get `
            -Uri "https://api.elevenlabs.io/v1/user/subscription" `
            -Headers @{"xi-api-key"=$apiKey} `
            -ErrorAction Stop

        $kullanilan = [int64]$j.character_count
        $limit = [int64]$j.character_limit
        $kalan = [Math]::Max(0, $limit - $kullanilan)

        if ($kalan -le 0) {
            return "KAPALI | kredi yok: $kullanilan/$limit kullanildi"
        }

        return "HAZIR | kalan kredi: $kalan | model: $model"
    }
    catch {
        return "KAPALI | kota sorgusu basarisiz: $($_.Exception.Message)"
    }
}

Write-Host "========================================"
Write-Host "TURKCE TTS KALITE LABORATUVARI DURUMU"
Write-Host "========================================"
Write-Host ""
Write-Host ("Piper Python       : " + (VarStatus "PIPER_PYTHON"))
Write-Host ("Piper model        : " + (VarStatus "PIPER_VOICE"))
Write-Host ("FFmpeg             : " + (VarStatus "FFMPEG_PATH"))
Write-Host ("Gemini API         : " + (VarStatus "GEMINI_API_KEY"))
Write-Host ("OpenAI API         : " + (VarStatus "OPENAI_API_KEY"))
Write-Host ("ElevenLabs API     : " + (VarStatus "ELEVENLABS_API_KEY"))
Write-Host ("ElevenLabs voice   : " + (VarStatus "ELEVENLABS_VOICE_ID"))
Write-Host ("ElevenLabs TTS     : " + (ElevenLabsTtsStatus))
$modelVarsayilan = UserEnv "ELEVENLABS_MODEL"
if ([string]::IsNullOrWhiteSpace($modelVarsayilan)) { $modelVarsayilan = "eleven_multilingual_v2" }
Write-Host ("ElevenLabs model   : " + $modelVarsayilan)
Write-Host ("Azure Speech API   : " + (VarStatus "AZURE_SPEECH_KEY"))
Write-Host ("Azure region       : " + (VarStatus "AZURE_SPEECH_REGION"))
Write-Host ""
Write-Host "TANIMLI, yalnizca ortam degiskeninin mevcut oldugunu belirtir."
Write-Host "Anahtar degerleri guvenlik nedeniyle ekrana yazdirilmaz."
