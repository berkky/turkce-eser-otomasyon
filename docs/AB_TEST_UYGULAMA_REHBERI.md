# A/B Test Uygulama Rehberi

## Mock deney

```powershell
powershell -ExecutionPolicy Bypass -File .\tts-ab-lab.ps1 -Status
powershell -ExecutionPolicy Bypass -File .\tts-ab-lab.ps1 -Mock -Seed 340034
```

Mock komutu ücretli API çağrısı yapmaz. FFmpeg ve ffprobe gerektirir; çıktıyı
daima canonical `Desktop\ESER\ses-arsivi\ab-test\` altında üretir.
Legacy Desktop veri klasörleri bulunursa uyarı verilir fakat okunmaz, yazılmaz veya taşınmaz.

Yerel Kaşağı metninden aday seçmek için `-Mock -LocalSource` kullanılır. Canlı kullanımda program
önce seçilen metni, karakter/kelime sayısını ve SHA-256 değerini gösterir. Metin ve ücretli çağrı için
iki ayrı açık onay ister.

`FIXTURE` kaynak yalnız mock altyapı doğrulaması içindir; kalite veya ticari seçim kanıtı değildir.
Canlı kullanım için `APPROVED_ARCHIVE_TEXT`, kullanıcı onayı, dosyayla eşleşen SHA-256 ve doğrulanmış
lisans notu birlikte zorunludur.

## xAI canlı tek aday

```powershell
$env:XAI_API_KEY = "your-api-key"
$env:XAI_TTS_VOICE = "sal"
$env:XAI_TTS_LANGUAGE = "tr"
$env:XAI_TTS_LIVE_ENABLED = "true"
powershell -ExecutionPolicy Bypass -File .\tts-ab-lab.ps1 -Live -Voice sal -GercekApiOnayli
```

`lumen`, `ursa` ve `sal` konfigürasyon adayları korunur. xAI'nin güncel resmî listesinde doğrulanmayan
bir voice 404 döndürürse deney `UNKNOWN_VOICE` olarak başarısız olur; otomatik başka voice denenmez.

## Değerlendirme

Web panelini başlatıp `http://127.0.0.1:8787/ab-test` adresini açın. Dinleyici yalnız `Örnek A`,
`Örnek B` gibi kodları görür. Sonuç CSV'si UTF-8 BOM içerir ve Excel'de Türkçe karakterleri korur.
