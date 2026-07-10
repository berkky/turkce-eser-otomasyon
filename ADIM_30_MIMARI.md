# Adım 30 — Gerçek Forced Alignment API Kapısı

## Özet

ElevenLabs `POST /v1/forced-alignment` endpoint'i güvenli multipart istemci ile entegre edildi. Varsayılan kapalı; yalnızca açık onaylı CLI komutu gerçek API'ye gider.

## Mock alignment nedir?

Kredi veya demo sırasında gerçek API'ye gitmeden segment/kelime zamanlaması üretir. `source: MOCK`.

## DemoFixture nedir?

Gerçek preview MP3 olmadan patron demosu için `_alignment/_fixture/` altında güvenli kaynak kullanır. `source: DEMO_FIXTURE`, `demoFixture: true`.

## Gerçek Forced Alignment ne zaman çalışır?

```powershell
powershell -ExecutionPolicy Bypass -File .\elevenlabs-alignment.ps1 -EserId 5 -GercekApiOnayli
```

Koşullar:
- ESER-00005
- `preview-elevenlabs.mp3` (≥128 byte, gerçek önizleme)
- `preview-input.txt` (dolu metin)
- `ELEVENLABS_API_KEY` tanımlı
- Kredi/abonelik hazır
- `-Mock` ve `-DemoFixture` **yok**

## Neden -GercekApiOnayli şart?

Otomatik API çağrısı maliyet ve güvenlik riski taşır. Açık onay bilinçli işlem gerektirir.

## ESER-00006 neden engelli?

Büyük eser — alignment maliyet/onay gerektirir. Mock ve gerçek alignment engelli.

## Gerçek preview olmadan alignment neden yapılamaz?

Forced alignment ses dosyası ile metni eşleştirir; sahte veya eksik audio güvenilir sonuç üretmez.

## API key neden loglanmaz?

Güvenlik — anahtar UI, JSON, log ve dosyalarda görünmez.

## HTTP hata eşlemesi

| Kod | Kullanıcı mesajı |
|-----|------------------|
| 401/403 | Yetki/API anahtarı problemi |
| 402/429 | Kredi/rate limit |
| 400/422 | Girdi problemi |
| 5xx | Sağlayıcı hatası (retryable) |

## Adım 31'de ne yapılacak?

- Onaylı tam eser üretim akışı
- Alignment kalite metrikleri ve drift analizi
- İsteğe bağlı karakter düzeyi UI
