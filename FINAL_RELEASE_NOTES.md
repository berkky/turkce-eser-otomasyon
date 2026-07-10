# Final Release Notes — Adım 32

**Sürüm:** 32.0.0  
**Tarih:** 2026  
**Proje:** Türkçe Eser Arşivleme ve Yapay Zekâ ile Seslendirme Otomasyonu

## Özet

Bu sürüm yeni ürün özelliği eklemez. Mevcut sistemi **teslim edilebilir** hale getiren final kalite kapısıdır.

## Bu adımda yapılanlar

- `final-release-check.ps1` — tek komutla derleme, regression, demo, secret scan
- `check-secrets.ps1` — API anahtarı sızıntı taraması
- `adim32-self-test.ps1` — Adım 21–32 geriye dönük doğrulama
- Final teslim dokümantasyonu (`FINAL_*.md`)
- `/demo` sayfasında Adım 32 kalite bilgi paneli
- Patron demo paketine final notlar eklendi
- `final-release-report.json` rapor üretimi

## Bilinçli kısıtlar (değişmedi)

- Tam eser TTS üretimi **varsayılan kapalı**
- Web panelden gerçek API/TTS başlatılamaz
- Gerçek ElevenLabs/OpenAI/Google çağrıları yalnızca açık onaylı CLI komutlarıyla
- ESER-00005/00006 arşiv dosyalarına dokunulmadı

## Sonraki adım

**Adım 33** — Teslim ZIP paketi ve gönderim hazırlığı.

## Hızlı doğrulama

```powershell
powershell -ExecutionPolicy Bypass -File .\final-release-check.ps1
```

GitHub: https://github.com/berkky/turkce-eser-otomasyon
