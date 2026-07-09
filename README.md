# Türkçe Eser Otomasyonu — Adım 29

## Forced Alignment ve Okuma Takibi (Adım 29) + Önceki sürümler

## Bu sürümde yenilikler (Adım 29)

- Mock forced alignment (deterministic, idempotent)
- `ses-arsivi/_alignment/` çıktıları (JSON, SRT, VTT)
- Web: `/alignment`, `/eser/5/alignment`, alignment API
- Okuma takibi UI (segment vurgulama + seek)
- `elevenlabs-alignment.ps1` — `-Mock` / `-DemoFixture` / `-GercekApiOnayli`
- `ADIM_29_MIMARI.md`

## Komutlar

```powershell
powershell -ExecutionPolicy Bypass -File .\adim29-self-test.ps1
powershell -ExecutionPolicy Bypass -File .\elevenlabs-alignment.ps1 -EserId 5 -Mock -DemoFixture
powershell -ExecutionPolicy Bypass -File .\web-panel.ps1
```

- Web: http://127.0.0.1:8787/alignment
- Okuma takibi: http://127.0.0.1:8787/eser/5/alignment

Ayrıntılı mimari: `ADIM_29_MIMARI.md`
