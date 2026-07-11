param(
    [string]$CiktiKlasoru = "C:\Users\Lenovo\Desktop\turkce-eser-final-teslim"
)

. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$zipAdi = "turkce-eser-otomasyon-final.zip"
$zipYolu = Join-Path $CiktiKlasoru $zipAdi

$excludeDirNames = @('.git', 'target', '.idea', '.vscode', '.cursor', 'node_modules', 'dist', 'build')
$excludeFilePatterns = @('*.class', '.env', '.env.*', 'credentials.json', 'credentials.*.json')
$sensitivePathFragments = @(
    'C:\Users\Lenovo\Desktop\arsiv',
    'C:\Users\Lenovo\Desktop\metin-arsivi',
    'C:\Users\Lenovo\Desktop\ses-arsivi',
    'C:\Users\Lenovo\Desktop\eser-otomasyon-kuyruk'
)

$teslimDokumanlari = @(
    'TESLIM_OZETI.md',
    'GONDERIM_MESAJLARI.md',
    'TEKNIK_KISIYE_NOT.md',
    'PATRON_KISA_OZET.md',
    'KURULUM_3_ADIM.md',
    'DEMO_5_DAKIKA.md',
    'GITHUB_LINKI.txt',
    'FINAL_RELEASE_DOGRULAMA.md'
)

function Test-ExcludedPath {
    param([string]$RelativePath)
    $norm = $RelativePath -replace '\\', '/'
    foreach ($d in $excludeDirNames) {
        if ($norm -eq $d -or $norm -like "$d/*" -or $norm -like "*/$d/*") { return $true }
    }
    foreach ($frag in $sensitivePathFragments) {
        if ($RelativePath -like "*$frag*") { return $true }
    }
    $leaf = Split-Path $RelativePath -Leaf
    foreach ($pat in $excludeFilePatterns) {
        if ($leaf -like $pat) { return $true }
    }
    return $false
}

function Copy-ProjectFile {
    param(
        [string]$Source,
        [string]$DestRoot,
        [string]$RelativePath
    )
    if (Test-ExcludedPath $RelativePath) { return }
    $dest = Join-Path $DestRoot $RelativePath
    $destDir = Split-Path $dest -Parent
    if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Force -Path $destDir | Out-Null }
    Copy-Item -Path $Source -Destination $dest -Force
}

Write-Utf8Line ""
Write-TestBaslik 'teslimPaketiOlustur'
Write-Utf8Line "Kaynak: $root"
Write-Utf8Line "Hedef:  $CiktiKlasoru"

New-Item -ItemType Directory -Force -Path $CiktiKlasoru | Out-Null

$staging = Join-Path ([System.IO.Path]::GetTempPath()) ("teslim-staging-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $staging | Out-Null

try {
    # pom.xml
    Copy-ProjectFile -Source (Join-Path $root 'pom.xml') -DestRoot $staging -RelativePath 'pom.xml'

    # src/ (kaynak + web resources)
    Get-ChildItem -Path (Join-Path $root 'src') -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($root.Length + 1)
        Copy-ProjectFile -Source $_.FullName -DestRoot $staging -RelativePath $rel
    }

    # PowerShell scriptleri
    Get-ChildItem -Path $root -Filter '*.ps1' -File | ForEach-Object {
        Copy-ProjectFile -Source $_.FullName -DestRoot $staging -RelativePath $_.Name
    }

    # Markdown ve metin dokumanlari
    $mdPatterns = @(
        'README.md', 'PROJE_DURUMU.md', 'CHANGELOG.md', 'BUILD_NOTES.md',
        'DEMO_SENARYOSU.md', 'IS_MODELI_NOTU.md', 'KURULUM.txt',
        'ORNEK_ARCHIVE_URL_LISTESI.txt', 'PAKET_DOGRULAMA.txt', 'BU_TAM_PROJEDIR.txt',
        'TTS_ARASTIRMA_VE_YOL_HARITASI.md'
    )
    foreach ($name in $mdPatterns) {
        $src = Join-Path $root $name
        if (Test-Path $src) {
            Copy-ProjectFile -Source $src -DestRoot $staging -RelativePath $name
        }
    }

    Get-ChildItem -Path $root -File | Where-Object {
        $_.Name -like 'FINAL_*.md' -or $_.Name -like 'ADIM_*.md' -or $_.Name -in $teslimDokumanlari
    } | ForEach-Object {
        Copy-ProjectFile -Source $_.FullName -DestRoot $staging -RelativePath $_.Name
    }

    # ZIP olustur
    if (Test-Path $zipYolu) { Remove-Item $zipYolu -Force }
    Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $zipYolu -CompressionLevel Optimal

    $zipSize = (Get-Item $zipYolu).Length
    Write-Utf8Line ("ZIP olusturuldu: {0} ({1:N0} byte)" -f $zipYolu, $zipSize)

    # Teslim dokumanlarini cikti klasorune kopyala
    foreach ($ad in $teslimDokumanlari) {
        $kaynak = Join-Path $root $ad
        if (Test-Path $kaynak) {
            Copy-Item -Path $kaynak -Destination (Join-Path $CiktiKlasoru $ad) -Force
        }
    }

    # TESLIM_OZETI guncelle (SHA henuz yok; kontrol scripti ekler)
    $version = '33.0.0'
    try {
        $pom = [xml](Get-Content (Join-Path $root 'pom.xml') -Raw)
        $version = $pom.project.version
    } catch { }

    $ozet = @(
        '# Teslim Ozeti',
        '',
        "Proje: Turkce Eser Arsivleme, Kataloglama ve Yapay Zeka ile Seslendirme Otomasyonu",
        "Surum: Adim 33 - Final Teslim Paketi",
        "Maven surumu: $version",
        "Tarih: $(Get-Date -Format 'yyyy-MM-dd HH:mm')",
        '',
        '## Paket turleri',
        '',
        '- **Patron demo paketi** (`Desktop\turkce-eser-demo-paketi\`): Sunum ve demonstrasyon dosyalaridir; HTML ozet, timeline, ornek ekran goruntuleri.',
        '- **Final teslim paketi** (`turkce-eser-otomasyon-final.zip`): Kaynak kod, dokumantasyon, kurulum/test scriptleri. Buyuk medya ve yerel arsiv icerigi bilerek dahil edilmez.',
        '',
        '## ZIP icerigi',
        '',
        '- src/ (Java kaynak + web panel)',
        '- pom.xml, README.md, FINAL_*.md, ADIM_*.md',
        '- PowerShell scriptleri (web panel, test, kurulum)',
        '- Ornek dokumanlar; API key veya .git yok',
        '',
        '## Dahil edilmeyenler',
        '',
        '- .git/, target/, IDE klasorleri',
        '- Yerel arsiv/ses/metin/kuyruk klasorleri',
        '- Gercek API key, .env, credential dosyalari',
        '- Buyuk audio/medya ciktilari',
        '',
        '## Sonraki adim',
        '',
        '1. `teslim-paketi-kontrol.ps1` calistirin',
        '2. SHA-256 dosyasini alici ile paylasin',
        '3. `GONDERIM_MESAJLARI.md` icinden uygun mesaji secin',
        '',
        "ZIP: $zipYolu"
    ) -join [Environment]::NewLine
    Set-Content -Path (Join-Path $CiktiKlasoru 'TESLIM_OZETI.md') -Value $ozet -Encoding UTF8
    Copy-Item -Path (Join-Path $CiktiKlasoru 'TESLIM_OZETI.md') -Destination (Join-Path $root 'TESLIM_OZETI.md') -Force

    Write-Utf8Line ""
    Write-Utf8Line "TESLIM PAKETI OLUSTUR: BASARILI"
    Write-Utf8Line "Klasor: $CiktiKlasoru"
    exit 0
}
finally {
    if (Test-Path $staging) { Remove-Item $staging -Recurse -Force -ErrorAction SilentlyContinue }
}
