# Adım 28 — Canlı ElevenLabs Önizleme, Telaffuz Sözlüğü ve Maliyet Onay Akışı

## Özet

Bu adım, gerçek ElevenLabs kredisi açıldığında **yalnızca açık onaylı kısa önizleme** üretimini güvenli hale getirir; telaffuz notlarını TTS metin normalizasyonuna bağlar; tam eser üretimini **kapalı** tutar.

## Canlı ElevenLabs önizleme nasıl çalışır?

1. **CLI:** `powershell -ExecutionPolicy Bypass -File .\elevenlabs-onizleme.ps1 -EserId 5`
2. **Web:** `POST /islemler` — `aksiyon=elevenlabs-onizleme`, `eserId=5`, geçerli `nonce`
3. Varsayılan eser: **ESER-00005 Kaşağı**
4. Hedef: ~60–90 sn (450–1100 karakter aralığı)
5. Kredi kontrolü → voice doğrulama → `.partial` → `.mp3` rename
6. Aynı `requestHash` varsa API çağrısı yapılmaz (idempotent)
7. Başarıda `preview-elevenlabs.json`, `preview-input.txt` güncellenir
8. `-RefreshPanel` ile kalite paneli yenilenir (script varsayılan: açık)

## Kredi yoksa ne olur?

- `ElevenLabsFabrika.durumOzeti()` → `KAPALI` / kredi yok mesajı
- Önizleme servisi API çağrısı yapmaz; `preview-elevenlabs.json` durum: `BASARISIZ`
- Web ve `/api/elevenlabs/status` güvenli JSON döner (anahtar sızdırmaz)
- Testler `ELEVENLABS_MOCK=true` ile mock modda çalışır

## Telaffuz notları nasıl uygulanır?

- Dosya: `{SES_KALITE_PANEL}/telaffuz-notlari.json`
- `TelaffuzSozluguService` — okuma/yazma, eski şema migrasyonu
- `TelaffuzNormalizerService` — yalnızca `AKTIF` + `METIN_NORMALIZE` kuralları
- Kelime sınırı, kontrollü büyük/küçük harf, tekrar uygulama engeli
- Orijinal metin arşivde kalır; TTS girdisi `preview-input.txt` içinde normalize edilir
- `preview-elevenlabs.json`: `appliedPronunciationNotes`, `normalizationWarnings`, karakter sayıları

**Not:** Canlı ElevenLabs pronunciation dictionary API bu adımda **zorunlu değil**.

## Neden tam eser üretimi kapalı?

- Maliyet riski (özellikle büyük eserler)
- Patron demo ve web panel **salt okunur / onaylı** akış
- `tamUretimKapali: true` tüm plan API'lerinde
- Web panelinde tam üretim başlatma butonu yok

## ESER-00005 neden test eseri?

- Kısa metin, düşük kredi riski
- Vikikaynak kaynağı, demo akışında kanıtlanmış pipeline
- Web önizleme whitelist: yalnızca eser 5

## ESER-00006 neden büyük eser korumasında?

- ~500K+ karakter, yüksek ElevenLabs maliyeti
- `ElevenLabsOnizlemeService` eser 6 için exception
- `TtsMaliyetPlanService` → `BUYUK_ESER_MALIYET_ONAYI`
- Gerçek TTS/önizleme bu adımda **kesinlikle** yapılmaz

## Forced alignment (sonraki adım)

- `AlignmentPlan` / `AlignmentResult` placeholder
- `preview-elevenlabs.json` → `alignmentStatus: NOT_REQUESTED`
- **Adım 29 veya sonrasında** forced alignment ile ses-metin hizalama ve altyazı/okuma takibi yapılabilir.

## API endpointleri

| Endpoint | Açıklama |
|----------|----------|
| `GET /api/elevenlabs/status` | Kredi/mock durumu (anahtarsız) |
| `GET /api/telaffuz` | Telaffuz sözlüğü JSON |
| `GET /api/tts-plan/{id}` | Maliyet planı JSON |
| `GET /eser/{id}/tts-plan` | Aynı plan JSON |
| `POST /islemler` + `elevenlabs-onizleme` | Onaylı önizleme (eser 5) |

## Güvenlik

- Localhost only, CSRF nonce
- Shell concatenation yok — doğrudan Java servis
- API anahtarı UI/JSON/log dosyalarında görünmez
- Path traversal, 0-byte media korumaları korunur
