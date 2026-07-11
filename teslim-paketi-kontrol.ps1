param(
    [string]$TeslimKlasoru = "C:\Users\Lenovo\Desktop\turkce-eser-final-teslim"
)

. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"

$zipAdi = "turkce-eser-otomasyon-final.zip"
$zipYolu = Join-Path $TeslimKlasoru $zipAdi
$shaDosya = Join-Path $TeslimKlasoru "teslim-paketi-sha256.txt"
$raporDosya = Join-Path $TeslimKlasoru "teslim-paketi-kontrol-raporu.md"

$bulgular = [System.Collections.Generic.List[string]]::new()
$uyarilar = [System.Collections.Generic.List[string]]::new()
$basarili = $true
$maxZipBoyut = 100MB

function Add-Bulgu {
    param([string]$Mesaj)
    $script:basarili = $false
    $bulgular.Add($Mesaj) | Out-Null
}

function Add-Uyari {
    param([string]$Mesaj)
    $uyarilar.Add($Mesaj) | Out-Null
}

Write-Utf8Line ""
Write-TestBaslik 'teslimPaketiKontrol'

# ZIP var mi?
if (-not (Test-Path $zipYolu)) {
    Add-Bulgu "ZIP bulunamadi: $zipYolu"
} else {
    Write-Utf8Line "OK: ZIP mevcut"
    $zipSize = (Get-Item $zipYolu).Length
    if ($zipSize -gt $maxZipBoyut) {
        Add-Uyari "ZIP boyutu buyuk: $([math]::Round($zipSize / 1MB, 2)) MB (limit $([math]::Round($maxZipBoyut / 1MB)) MB)"
    } else {
        Write-Utf8Line ("OK: ZIP boyutu makul ({0:N0} byte)" -f $zipSize)
    }
}

$zipEntries = @()
if (Test-Path $zipYolu) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($zipYolu)
    try {
        $zipEntries = @($archive.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
    } finally {
        $archive.Dispose()
    }
}

function Test-ZipContains {
    param([string]$Pattern)
    return ($zipEntries | Where-Object { $_ -like $Pattern }).Count -gt 0
}

# .git yok
if (Test-ZipContains '.git/*' -or Test-ZipContains '*/.git/*') {
    Add-Bulgu 'ZIP icinde .git klasoru var'
} else {
    Write-Utf8Line 'OK: .git yok'
}

# target yok
if (Test-ZipContains 'target/*' -or Test-ZipContains '*/target/*') {
    Add-Bulgu 'ZIP icinde target klasoru var'
} else {
    Write-Utf8Line 'OK: target yok'
}

# README var
if (-not (Test-ZipContains 'README.md')) {
    Add-Bulgu 'ZIP icinde README.md yok'
} else {
    Write-Utf8Line 'OK: README.md var'
}

# Final dokumanlar
foreach ($ad in @('FINAL_KURULUM_REHBERI.md', 'FINAL_DEMO_AKISI.md')) {
    if (-not (Test-ZipContains $ad)) {
        Add-Bulgu "ZIP icinde $ad yok"
    } else {
        Write-Utf8Line "OK: $ad var"
    }
}

# Final release rapor veya ozet
$releaseDir = Join-Path ([Environment]::GetFolderPath('Desktop')) 'turkce-eser-final-release'
$releaseRapor = Join-Path $releaseDir 'final-release-report.json'
$releaseOzet = Join-Path $releaseDir 'final-release-summary.md'
if ((Test-Path $releaseRapor) -or (Test-Path $releaseOzet) -or (Test-ZipContains 'FINAL_RELEASE_NOTES.md')) {
    Write-Utf8Line 'OK: Final release raporu/ozeti mevcut'
} else {
    Add-Uyari 'Final release raporu bulunamadi (final-release-check.ps1 calistirin)'
}

# Teslim dokumanlari (cikti klasorunde)
foreach ($ad in @('GONDERIM_MESAJLARI.md', 'PATRON_KISA_OZET.md', 'TEKNIK_KISIYE_NOT.md')) {
    $p = Join-Path $TeslimKlasoru $ad
    if (-not (Test-Path $p)) {
        Add-Bulgu "Teslim klasorunde $ad yok"
    } else {
        Write-Utf8Line "OK: $ad (teslim klasoru)"
    }
}

# Secret taramasi — ZIP icerigi
$secretPatterns = @(
    @{ Name = 'sk- key'; Regex = 'sk-[A-Za-z0-9]{20,}' },
    @{ Name = 'Google AI key'; Regex = 'AIza[0-9A-Za-z_-]{30,}' },
    @{ Name = 'ELEVENLABS_API_KEY deger'; Regex = 'ELEVENLABS_API_KEY\s*=\s*["'']?(?!dummy|test-key|placeholder|your)[A-Za-z0-9_-]{8,}' }
)
$allowInZip = @('dummy-test-api-key', 'test-key-adim', 'sk-test', 'sk-placeholder', 'YOUR_API_KEY', 'placeholder', 'ornek', 'API anahtar')

if (Test-Path $zipYolu) {
    $tempExtract = Join-Path ([System.IO.Path]::GetTempPath()) ("teslim-kontrol-" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $tempExtract | Out-Null
    try {
        Expand-Archive -Path $zipYolu -DestinationPath $tempExtract -Force
        $scanFiles = Get-ChildItem -Path $tempExtract -Recurse -File -Include '*.java','*.ps1','*.md','*.json','*.xml','*.properties','*.env*','*.txt','*.js','*.html' -ErrorAction SilentlyContinue
        $secretHit = $false
        foreach ($f in $scanFiles) {
            $lineNum = 0
            foreach ($line in [System.IO.File]::ReadAllLines($f.FullName)) {
                $lineNum++
                $allowed = $false
                foreach ($a in $allowInZip) {
                    if ($line -match [regex]::Escape($a) -or $line -match $a) { $allowed = $true; break }
                }
                if ($allowed) { continue }
                foreach ($pat in $secretPatterns) {
                    if ($line -match $pat.Regex) {
                        $rel = $f.FullName.Substring($tempExtract.Length + 1)
                        Add-Bulgu "Secret bulgu ZIP icinde: $rel satir $lineNum [$($pat.Name)]"
                        $secretHit = $true
                    }
                }
                if ($line -match 'C:\\Users\\Lenovo\\Desktop\\(arsiv|metin-arsivi|ses-arsivi|eser-otomasyon-kuyruk)') {
                    $rel = $f.FullName.Substring($tempExtract.Length + 1)
                    Add-Uyari "Yerel path sizintisi: $rel satir $lineNum"
                }
            }
        }
        if (-not $secretHit) { Write-Utf8Line 'OK: API key / sk- bulgusu yok' }
    } finally {
        if (Test-Path $tempExtract) { Remove-Item $tempExtract -Recurse -Force -ErrorAction SilentlyContinue }
    }
}

# SHA-256
$sha256 = $null
if (Test-Path $zipYolu) {
    $hash = Get-FileHash -Path $zipYolu -Algorithm SHA256
    $sha256 = $hash.Hash
    $shaMetin = @(
        "Dosya: $zipAdi",
        "Yol: $zipYolu",
        "SHA-256: $sha256",
        "Tarih: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
        "Boyut: $((Get-Item $zipYolu).Length) byte"
    ) -join [Environment]::NewLine
    Set-Content -Path $shaDosya -Value $shaMetin -Encoding UTF8
    Write-Utf8Line "OK: SHA-256 olusturuldu -> $shaDosya"
} else {
    Add-Bulgu 'SHA-256 uretilemedi (ZIP yok)'
}

# Rapor
$sonuc = if ($basarili) { 'BASARILI' } else { 'BASARISIZ' }
$bulguMetin = if ($bulgular.Count -eq 0) { '- Yok' } else { ($bulgular | ForEach-Object { "- $_" }) -join "`n" }
$uyariMetin = if ($uyarilar.Count -eq 0) { '- Yok' } else { ($uyarilar | ForEach-Object { "- $_" }) -join "`n" }
$rapor = @(
    '# Teslim Paketi Kontrol Raporu',
    '',
    "- Sonuc: **$sonuc**",
    "- Tarih: $(Get-Date -Format 'yyyy-MM-dd HH:mm')",
    "- ZIP: $zipYolu",
    "- SHA-256: $sha256",
    '',
    '## Bulgular',
    '',
    $bulguMetin,
    '',
    '## Uyarilar',
    '',
    $uyariMetin,
    '',
    '## Kontrol listesi',
    '',
    '- [x] ZIP var',
    '- [x] .git yok',
    '- [x] target yok',
    '- [x] README var',
    '- [x] FINAL_KURULUM_REHBERI.md var',
    '- [x] FINAL_DEMO_AKISI.md var',
    '- [x] Secret scan',
    '- [x] SHA-256',
    '',
    "Son satir: TESLIM PAKETI KONTROL: $sonuc"
) -join [Environment]::NewLine
Set-Content -Path $raporDosya -Value $rapor -Encoding UTF8

Write-Utf8Line ""
if ($basarili) {
    Write-Utf8Line 'TESLIM PAKETI KONTROL: BASARILI'
    exit 0
} else {
    Write-Utf8Line 'TESLIM PAKETI KONTROL: BASARISIZ'
    foreach ($b in $bulgular) { Write-Utf8Line "  ! $b" }
    exit 1
}
