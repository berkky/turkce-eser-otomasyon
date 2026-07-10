param(
    [switch]$Quiet
)

. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$bulgular = @()

$excludeDirs = @('target', '.git', 'node_modules', '.idea', '.cursor', 'dist', 'build')
$extensions = @('*.java', '*.ps1', '*.md', '*.json', '*.xml', '*.properties', '*.env*', '*.yml', '*.yaml', '*.txt', '*.html', '*.css', '*.js')

$allowPatterns = @(
    'dummy-test-api-key-not-real',
    'test-key-adim\d+',
    'sk-test',
    'sk-placeholder',
    'your[_-]?api[_-]?key',
    'YOUR_API_KEY',
    'xxx+',
    'örnek',
    'placeholder',
    'asla gösterilmez',
    'gösterilmez',
    'API anahtar',
    'api key yok',
    'ortam değişken',
    'environment variable',
    'System\.getenv',
    'TtsLaboratuvarYardimci',
    'gizliDegerleriTemizle',
    'apiKeySizintisiVar',
    'check-secrets',
    'secret scan',
    'sk- ile başlayan'
)

function Test-AllowLine {
    param([string]$Line)
    foreach ($p in $allowPatterns) {
        if ($Line -match $p) { return $true }
    }
    return $false
}

$scanPatterns = @(
    @{ Name = 'OpenAI/ElevenLabs sk-'; Regex = 'sk-[A-Za-z0-9]{20,}' },
    @{ Name = 'Google AI key'; Regex = 'AIza[0-9A-Za-z_-]{30,}' },
    @{ Name = 'ELEVENLABS_API_KEY değer'; Regex = 'ELEVENLABS_API_KEY\s*=\s*["'']?(?!dummy|test-key|placeholder|your)[A-Za-z0-9_-]{8,}' },
    @{ Name = 'OPENAI_API_KEY değer'; Regex = 'OPENAI_API_KEY\s*=\s*["'']?(?!dummy|test-key|placeholder|your)[A-Za-z0-9_-]{8,}' },
    @{ Name = 'GEMINI_API_KEY değer'; Regex = 'GEMINI_API_KEY\s*=\s*["'']?(?!dummy|test-key|placeholder|your)[A-Za-z0-9_-]{8,}' },
    @{ Name = 'AZURE_SPEECH_KEY değer'; Regex = 'AZURE_SPEECH_KEY\s*=\s*["'']?(?!dummy|test-key|placeholder|your)[A-Za-z0-9_-]{8,}' }
)

$files = Get-ChildItem -Path $root -Recurse -Include $extensions -File -ErrorAction SilentlyContinue |
    Where-Object {
        $rel = $_.FullName.Substring($root.Length + 1)
        $skip = $false
        foreach ($d in $excludeDirs) {
            if ($rel -like "$d\*" -or $rel -like "*\$d\*") { $skip = $true; break }
        }
        -not $skip
    }

foreach ($file in $files) {
    $relPath = $file.FullName.Substring($root.Length + 1)
    $lineNum = 0
    foreach ($line in [System.IO.File]::ReadAllLines($file.FullName)) {
        $lineNum++
        if (Test-AllowLine $line) { continue }
        foreach ($pat in $scanPatterns) {
            if ($line -match $pat.Regex) {
                $bulgular += [PSCustomObject]@{
                    Dosya = $relPath
                    Satir = $lineNum
                    Tur   = $pat.Name
                    Ornek = ($line.Trim().Substring(0, [Math]::Min(80, $line.Trim().Length)))
                }
            }
        }
    }
}

if (-not $Quiet) {
    Write-Utf8Line ""
    Write-TestBaslik 'secretScan'
    if ($bulgular.Count -eq 0) {
        Write-Utf8Line "SECRET SCAN: TEMIZ ($($files.Count) dosya tarandi)"
    } else {
        Write-Utf8Line "SECRET SCAN: BULGU VAR ($($bulgular.Count) adet)"
        foreach ($b in $bulgular) {
            Write-Utf8Line ("  {0}:{1} [{2}] {3}" -f $b.Dosya, $b.Satir, $b.Tur, $b.Ornek)
        }
    }
}

if ($bulgular.Count -gt 0) { exit 1 }
exit 0
