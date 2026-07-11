# Teknik Kişiye Not — Adım 33

## Stack

| Bileşen | Seçim |
|---------|-------|
| Dil | Java 21 |
| Build | Maven 3.9+ |
| Otomasyon | PowerShell 5.1+ |
| Web | `com.sun.net.httpserver.HttpServer` (yerel) |
| JSON | Jackson |
| PDF | PDFBox |

## Neden Spring Boot yok?

Bilinçli minimal bağımlılık: tek JAR derleme, hızlı başlangıç, patron/teknik kişinin Maven + Java ile hemen çalıştırabilmesi. Web paneli localhost MVP için yeterli; ileride istenirse ayrı servisle genişletilebilir.

## Localhost web panel

- Script: `web-panel.ps1`
- Adres: http://127.0.0.1:8787
- Demo giriş: `/demo`
- API: `/api/status` (anahtar maskeleme aktif)

## TTS sağlayıcıları

| Sağlayıcı | Durum |
|-----------|-------|
| ElevenLabs | Mock client + onaylı kısa gerçek API kapısı |
| Piper | Yerel TTS (opsiyonel kurulum) |
| OpenAI TTS | Stub/entegrasyon hazırlığı |
| Google Cloud TTS | Stub/entegrasyon hazırlığı |

## ElevenLabs durumu

- `elevenlabs-durum.ps1` — yapılandırma kontrolü (çağrı yapmaz)
- `elevenlabs-onizleme.ps1` — onaylı kısa önizleme
- Mock mod: kredi/anahtar yokken tam akış test edilir
- Tam eser üretimi **başlatılmaz**

## Mock / gerçek API ayrımı

- `ElevenLabsMockClient` — varsayılan demo
- Gerçek API: ortam değişkeni + açık onay flag'leri
- Web panelden tam üretim tetiklenemez
- `TamEserUretimGuvenlikService` maliyet/onay kapıları

## Test komutları

```powershell
mvn -q -DskipTests compile
powershell -ExecutionPolicy Bypass -File .\adim33-self-test.ps1
powershell -ExecutionPolicy Bypass -File .\adim32-self-test.ps1
powershell -ExecutionPolicy Bypass -File .\check-secrets.ps1
powershell -ExecutionPolicy Bypass -File .\final-release-check.ps1
powershell -ExecutionPolicy Bypass -File .\teslim-paketi-olustur.ps1
powershell -ExecutionPolicy Bypass -File .\teslim-paketi-kontrol.ps1
```

## Final release check

`final-release-check.ps1` build, regression (Adım 31 self-test), demo paketi, secret scan ve git durumunu kontrol eder. Rapor: `Desktop\turkce-eser-final-release\`

## Güvenlik notları

- `check-secrets.ps1` — sk-, AIza, env key taraması
- API key log/JSON/HTML'de maskelenir
- ZIP'te `.git`, `target`, `.env`, credential yok
- Yerel path sızıntısı minimize edildi

## Kısıtlar

- Tek kullanıcı, localhost only
- Gerçek TTS üretimi varsayılan kapalı
- Büyük arşiv/medya ZIP'e dahil değil
- OCR/Tesseract opsiyonel
- FFmpeg opsiyonel (ses birleştirme)

## GitHub

https://github.com/berkky/turkce-eser-otomasyon
