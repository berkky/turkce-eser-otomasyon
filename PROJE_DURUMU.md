# Proje Durumu — Adım 34

## Adım özeti (1–32)

| Adım | Konu |
|------|------|
| 1–20 | Kaynak alma, arşiv, metadata, TTS kuyruk |
| 21–23 | Geriye dönük test, metadata güvenlik |
| 24–25 | ElevenLabs, ses kalite paneli |
| 26–27 | Web MVP, patron demo |
| 28 | Önizleme, telaffuz, maliyet planı |
| 29–30 | Alignment, gerçek API kapısı |
| 31 | Tam eser üretim planı / onay / kuyruk |
| 32 | Final release kalite kapısı |
| 33 | Final teslim paketi ve gönderim hazırlığı |
| 34 | Türkçe TTS A/B laboratuvarı, xAI ve kör değerlendirme |

## Mevcut durum (Adım 34)

- ESER-00005 için 900–1.200 karakterlik sabit kaynak hash ve mock-varsayılan deney akışı
- xAI unary TTS adaptörü; live/CLI/eser/karakter/bütçe/idempotency kapıları
- FFmpeg ortak normalizasyonu ve deterministic `ornek-A/B` kör paket
- Localhost `/ab-test` değerlendirmesi, UTF-8 CSV ve submission ID koruması
- Adım 21–33 regresyonu ve secret scan, `adim34-self-test.ps1` içinde başarıyla doğrulandı
- Tam kitap, ESER-00006 canlı TTS, WebSocket ve voice cloning kapsam dışıdır

## İki paket ayrımı

| Paket | Konum | İçerik |
|-------|-------|--------|
| Patron demo | `Desktop\turkce-eser-demo-paketi\` | Sunum/demonstrasyon |
| Final teslim | `Desktop\turkce-eser-final-teslim\` | Kaynak kod ZIP + dokümanlar |

Büyük medya ve yerel arşiv dosyaları bilerek ZIP'e konmaz.

## Demo hazır

```powershell
powershell -ExecutionPolicy Bypass -File .\web-panel.ps1
# http://127.0.0.1:8787/demo
powershell -ExecutionPolicy Bypass -File .\patron-demo-paketi.ps1
```

## Doğrulama

```powershell
powershell -ExecutionPolicy Bypass -File .\adim33-self-test.ps1
powershell -ExecutionPolicy Bypass -File .\adim34-self-test.ps1
powershell -ExecutionPolicy Bypass -File .\tts-ab-lab.ps1 -Mock -Seed 340034
powershell -ExecutionPolicy Bypass -File .\teslim-paketi-olustur.ps1
powershell -ExecutionPolicy Bypass -File .\teslim-paketi-kontrol.ps1
powershell -ExecutionPolicy Bypass -File .\final-release-check.ps1
powershell -ExecutionPolicy Bypass -File .\check-secrets.ps1
```

## Teslim durumu

**Adım 34 tamamlandı** — mock A/B paketi ve güvenli canlı xAI kapısı hazır; canlı kalite sonucu henüz üretilmedi.
