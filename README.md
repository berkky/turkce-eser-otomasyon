# Türkçe Eser Otomasyonu — Adım 28
## Patron Demo Akışı ve Sunum Modu (Adım 27) + ElevenLabs Önizleme (Adım 28)

Bu paket **tam projedir; patch değildir**. Adım 17–27 özelliklerini eksiksiz içerir.

## Bu sürümde yenilikler (Adım 28)

- Canlı ElevenLabs **kısa önizleme** (yalnızca ESER-00005, açık onay)
- Telaffuz sözlüğü + `METIN_NORMALIZE` entegrasyonu
- TTS maliyet/kredi planı (`/api/tts-plan/{id}`)
- Web: `POST /islemler` → `elevenlabs-onizleme`
- API: `/api/elevenlabs/status`, `/api/telaffuz`
- Demo sayfasında Adım 28 premium önizleme bölümü
- `ADIM_28_MIMARI.md`

## Önceki sürüm (Adım 27)

- Patron demo sayfası (`/demo`) — 7 adımlı timeline, metrikler, önce/sonra
- Demo API: `/api/demo`, `/api/demo/metrikler`, `/api/demo/akis`
- Patron sunum paketi (`patron-demo-paketi.ps1`)
- ESER-00005/00006 demo rol açıklamaları
- `DEMO_SENARYOSU.md` — 5 dakikalık sunum rehberi
- `IS_MODELI_NOTU.md` — iş modeli taslağı

## Komutlar

```powershell
# Self-test (Adım 21–28)
powershell -ExecutionPolicy Bypass -File .\adim28-self-test.ps1

# ElevenLabs önizleme (kredi/mock gerekir)
powershell -ExecutionPolicy Bypass -File .\elevenlabs-onizleme.ps1 -EserId 5

# Web panel + demo
powershell -ExecutionPolicy Bypass -File .\web-panel.ps1

# Patron sunum paketi
powershell -ExecutionPolicy Bypass -File .\patron-demo-paketi.ps1
```

- Web panel: http://127.0.0.1:8787
- Patron demo: http://127.0.0.1:8787/demo
- Demo paketi: `Desktop\turkce-eser-demo-paketi\`

Ayrıntılı mimari: `ADIM_28_MIMARI.md`
