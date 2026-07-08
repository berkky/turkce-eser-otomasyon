# Adım 24 — ElevenLabs Premium Türkçe TTS Mimarisi

## Amaç

ElevenLabs'i profesyonel Türkçe ses üretimi için sisteme eklemek. Bu adımda büyük eser üretimi yapılmaz; önce güvenli kredi kontrolü, model/voice doğrulama, 60–90 saniyelik önizleme, maliyet koruması, retry ve idempotent çıktı kurulur.

## Güvenlik kuralları

1. `ELEVENLABS_API_KEY` hiçbir logda veya konsol çıktısında yazdırılmaz.
2. `.env` veya gerçek anahtar commit edilmez.
3. Kredi yoksa gerçek TTS çağrısı yapılmaz.
4. Tam eser üretimi yalnızca açık `EVET` onayı ile başlar.
5. İlk canlı test yalnızca kısa önizleme üretir.
6. ESER-00005 ve ESER-00006 arşiv/metin/kuyruk yapısı değiştirilmez.
7. Adım 17–23 özellikleri geriye dönük korunur.
8. Büyük Astronomi eseri (ESER-00006) otomatik seslendirilmez.
9. Ağ/API hatasında kontrollü mesaj verilir; yarım dosya bırakılmaz.

## Çekirdek sınıflar

| Sınıf | Görev |
|-------|-------|
| `ElevenLabsClient` | Abonelik, voice/model doğrulama, TTS, retry, `.partial` yazım |
| `ElevenLabsMockClient` | Test/mock mod; gerçek API çağrısı yok |
| `ElevenLabsFabrika` | Ortam + mock fabrika, güvenli durum özeti |
| `ElevenLabsModelPolitikasi` | Eser türüne göre model önerisi |
| `TurkishSpeechTextNormalizer` | Önizleme öncesi Türkçe konuşma temizliği |
| `ElevenLabsOnizlemeService` | 60–90 sn önizleme, `requestHash` idempotency |
| `ElevenLabsTamEserService` | Tam üretim altyapısı (varsayılan kapalı/onaylı) |

## Kredi kontrolü

- `character_count` ve `character_limit` okunur.
- Kalan = `character_limit - character_count`.
- Kalan ≤ 0 ise TTS **KAPALI**.
- Önizleme için yeterli kredi yoksa üretim yapılmaz.

## Model politikası

| Eser türü | Önerilen model |
|-----------|----------------|
| Kitap / uzun anlatı | `eleven_multilingual_v2` (varsayılan) |
| Şiir / diyalog / duygusal kısa hikâye | `eleven_v3` |
| Haber / blog / tweet / kısa içerik | `eleven_flash_v2_5` |

Ortam değişkeni: `ELEVENLABS_MODEL`

## Önizleme çıktısı

```
ses-arsivi/ESER-00005 - Kasagi - Vikikaynak/onizleme/elevenlabs/
  preview-elevenlabs.mp3
  preview-elevenlabs.json
  preview-input.txt
  preview-run.log
```

`preview-elevenlabs.json` alanları: `eserId`, `eserAdi`, `provider`, `modelId`, `voiceIdMasked`, `inputCharacterCount`, `estimatedCharacterCost`, `generatedAt`, `outputFile`, `sourceChunkIds`, `status`, `errorMessage`, `requestHash`.

Aynı `requestHash` ile geçerli önizleme varsa API çağrısı yapılmaz.

## Tam üretim neden varsayılan kapalı?

- Maliyet ve kredi riski yüksektir.
- Büyük eserlerde önce plan ve maliyet gösterilmelidir.
- Otomatik mod yalnızca Yerel Piper kullanır.
- ElevenLabs tam üretim menüden seçilse bile son onay `EVET` gerektirir.

## Komutlar

```powershell
# Durum
powershell -ExecutionPolicy Bypass -File .\elevenlabs-durum.ps1
powershell -ExecutionPolicy Bypass -File .\tts-laboratuvar-durum.ps1

# Önizleme (varsayılan ESER-00005)
powershell -ExecutionPolicy Bypass -File .\elevenlabs-onizleme.ps1 -EserId 5

# Self-test
powershell -ExecutionPolicy Bypass -File .\adim24-self-test.ps1
```

## Mock mod

Testler gerçek API'ye bağımlı değildir:

```
ELEVENLABS_MOCK=true
```

## Hata durumları

| HTTP | Davranış |
|------|----------|
| 401 | Anahtar/izin hatası |
| 403 | Yetki yetersiz |
| 402 / kota | Kredi yok mesajı, TTS çağrısı yok |
| 429 | Exponential backoff (kısa), sonra hata |
| 5xx | Retry, sonra kontrollü hata |

Kısmi MP3 `.partial` uzantısıyla yazılır; başarıda yeniden adlandırılır. 0 byte veya çok küçük dosya reddedilir.
