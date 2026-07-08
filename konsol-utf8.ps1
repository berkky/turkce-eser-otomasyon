# Windows PowerShell 5.1, Windows Terminal ve Java/Maven icin ortak UTF-8 ayari.
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
try { [Console]::InputEncoding = $Utf8NoBom } catch { }
try { [Console]::OutputEncoding = $Utf8NoBom } catch { }
$global:OutputEncoding = $Utf8NoBom
try { & "$env:SystemRoot\System32\chcp.com" 65001 *> $null } catch { }

$Utf8JvmOptions = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
if ([string]::IsNullOrWhiteSpace($env:MAVEN_OPTS)) {
    $env:MAVEN_OPTS = $Utf8JvmOptions
} elseif ($env:MAVEN_OPTS -notmatch 'sun\.stdout\.encoding') {
    $env:MAVEN_OPTS = ($env:MAVEN_OPTS.Trim() + " " + $Utf8JvmOptions)
}
if ([string]::IsNullOrWhiteSpace($env:JAVA_TOOL_OPTIONS)) {
    $env:JAVA_TOOL_OPTIONS = $Utf8JvmOptions
} elseif ($env:JAVA_TOOL_OPTIONS -notmatch 'sun\.stdout\.encoding') {
    $env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS.Trim() + " " + $Utf8JvmOptions)
}

function Write-Utf8Line([string]$Text) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text + [Environment]::NewLine)
    $stdout = [Console]::OpenStandardOutput()
    $stdout.Write($bytes, 0, $bytes.Length)
    $stdout.Flush()
}

function Write-TestBaslik([string]$Anahtar) {
    $map = @{
        'adim21' = '--- ADIM 21 GERİYE DÖNÜK DOĞRULAMA ---'
        'adim22' = '--- ADIM 22 GÜVENLİ GERİYE DÖNÜK DOĞRULAMA ---'
        'adim23meta' = '--- ADIM 23 METADATA GÜVENLİK DOĞRULAMA ---'
        'adim23derleme' = '--- ADIM 23 MAVEN DERLEME ---'
        'adim24' = '--- ADIM 24 ELEVENLABS DOĞRULAMA ---'
        'adim24derleme' = '--- ADIM 24 MAVEN DERLEME ---'
        'adim25' = '--- ADIM 25 SES KALİTE PANELİ DOĞRULAMA ---'
        'adim25derleme' = '--- ADIM 25 MAVEN DERLEME ---'
        'panel' = '--- SES KALİTE PANELİ ---'
        'panelNot' = 'Not: Gerçek ElevenLabs API çağrısı yapılmaz.'
        'demo' = '--- KALİTE PANELİ DEMO VERİSİ ---'
    }
    if ($map.ContainsKey($Anahtar)) {
        Write-Utf8Line $map[$Anahtar]
    } else {
        Write-Utf8Line $Anahtar
    }
}
