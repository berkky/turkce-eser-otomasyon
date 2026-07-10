# Türkçe Eser Otomasyonu — Adım 31

## Onaylı Tam Eser Üretim Kapısı (Adım 31) + Önceki sürümler

## Bu sürümde yenilikler (Adım 31)

- Tam eser üretim **planı** (maliyet/kredi/risk) — TTS başlatmaz
- Onay taslağı ve güvenli kuyruk kaydı (`production-approvals/`)
- ESER-00005: düşük/orta risk örneği; ESER-00006: yüksek risk
- Web: `/uretim`, `/api/uretim-plan/{id}`, `/api/uretim-kuyruk`
- `ADIM_31_MIMARI.md`

## Komutlar

```powershell
powershell -ExecutionPolicy Bypass -File .\adim31-self-test.ps1
powershell -ExecutionPolicy Bypass -File .\tam-eser-plan.ps1 -EserId 5
powershell -ExecutionPolicy Bypass -File .\tam-eser-onay-taslagi.ps1 -EserId 5
powershell -ExecutionPolicy Bypass -File .\tam-eser-kuyruga-al.ps1 -EserId 5 -Onayli
powershell -ExecutionPolicy Bypass -File .\web-panel.ps1
```

- Üretim kapısı: http://127.0.0.1:8787/uretim
- ESER-00005: http://127.0.0.1:8787/eser/5/uretim
- Demo: http://127.0.0.1:8787/demo

Ayrıntılı mimari: `ADIM_31_MIMARI.md`
