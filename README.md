# Türkçe Eser Otomasyonu — Adım 27
## Patron Demo Akışı ve Sunum Modu

Bu paket **tam projedir; patch değildir**. Adım 17–26 özelliklerini eksiksiz içerir.

## Bu sürümde yenilikler

- Patron demo sayfası (`/demo`) — 7 adımlı timeline, metrikler, önce/sonra
- Demo API: `/api/demo`, `/api/demo/metrikler`, `/api/demo/akis`
- Patron sunum paketi (`patron-demo-paketi.ps1`)
- ESER-00005/00006 demo rol açıklamaları
- `DEMO_SENARYOSU.md` — 5 dakikalık sunum rehberi
- `IS_MODELI_NOTU.md` — iş modeli taslağı

## Komutlar

```powershell
# Self-test (Adım 21–27)
powershell -ExecutionPolicy Bypass -File .\adim27-self-test.ps1

# Web panel + demo
powershell -ExecutionPolicy Bypass -File .\web-panel.ps1

# Patron sunum paketi
powershell -ExecutionPolicy Bypass -File .\patron-demo-paketi.ps1
```

- Web panel: http://127.0.0.1:8787
- Patron demo: http://127.0.0.1:8787/demo
- Demo paketi: `Desktop\turkce-eser-demo-paketi\`

Ayrıntılı mimari: `ADIM_27_MIMARI.md`
