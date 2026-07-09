# Proje Durumu — Adım 29

## Tamamlanan çekirdekler

- Kaynak alımı, arşiv, metadata, TTS kuyruğu
- ElevenLabs önizleme, telaffuz, maliyet planı (Adım 28)
- **Mock forced alignment, SRT/VTT, okuma takibi UI (Adım 29)**
- Yerel web MVP (localhost:8787)

## Doğrulama

```powershell
powershell -ExecutionPolicy Bypass -File .\adim29-self-test.ps1
powershell -ExecutionPolicy Bypass -File .\elevenlabs-alignment.ps1 -EserId 5 -Mock -DemoFixture
powershell -ExecutionPolicy Bypass -File .\patron-demo-paketi.ps1
powershell -ExecutionPolicy Bypass -File .\web-panel.ps1
```

## Sonraki adım (30)

Gerçek ElevenLabs forced alignment API ve onaylı tam eser üretim akışı.
