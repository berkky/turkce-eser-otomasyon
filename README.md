# Türkçe Eser Otomasyonu — Adım 30

## Gerçek Forced Alignment API Kapısı (Adım 30) + Önceki sürümler

## Bu sürümde yenilikler (Adım 30)

- ElevenLabs `POST /v1/forced-alignment` multipart entegrasyonu
- `-GercekApiOnayli` açık onay kapısı (varsayılan kapalı)
- `AlignmentHata` güvenli hata modeli
- `source`: ELEVENLABS / MOCK / DEMO_FIXTURE ayrımı
- Web: alignment kaynağı, gerçek API durumu, son hata
- `ADIM_30_MIMARI.md`

## Komutlar

```powershell
powershell -ExecutionPolicy Bypass -File .\adim30-self-test.ps1
powershell -ExecutionPolicy Bypass -File .\elevenlabs-alignment.ps1 -EserId 5 -Mock -DemoFixture
powershell -ExecutionPolicy Bypass -File .\elevenlabs-alignment.ps1 -EserId 5 -GercekApiOnayli
powershell -ExecutionPolicy Bypass -File .\web-panel.ps1
```

- Web: http://127.0.0.1:8787/alignment
- Okuma takibi: http://127.0.0.1:8787/eser/5/alignment

Ayrıntılı mimari: `ADIM_30_MIMARI.md`
