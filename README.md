# Türkçe Eser Otomasyonu — Adım 25
## Ses Önizleme Kalite Paneli ve Sağlayıcı Karşılaştırma

Bu paket **tam projedir; patch değildir**. Adım 17–24 özelliklerini eksiksiz içerir.

## Bu sürümde yenilikler

- Statik HTML ses kalite paneli (`ses-kalite-panel.ps1`)
- Önizleme keşif sistemi (ElevenLabs, Piper, Google klasörleri)
- İnsan puanlama şablonu (`kalite-degerlendirmeleri.json`)
- Telaffuz notları (`telaffuz-notlari.json`)
- Sağlayıcı karşılaştırma raporu (HTML + Markdown + CSV)
- UTF-8 self-test başlık düzeltmeleri
- Mock demo verisi (`kalite-panel-demo-verisi.ps1`)

## Güvenlik

- Gerçek ElevenLabs API çağrısı panel tarafından yapılmaz.
- Var olan arşiv dosyalarına yazılmaz; yalnızca `ses-arsivi_kalite-panel` klasörüne rapor üretilir.
- Mock önizlemeler açıkça işaretlenir.

## Komutlar

```powershell
# Self-test
powershell -ExecutionPolicy Bypass -File .\adim25-self-test.ps1

# Kalite paneli üret
powershell -ExecutionPolicy Bypass -File .\ses-kalite-panel.ps1

# Demo mock veri + panel
powershell -ExecutionPolicy Bypass -File .\kalite-panel-demo-verisi.ps1
```

Panel yolu: `C:\Users\Lenovo\Desktop\ses-arsivi_kalite-panel\index.html`

Ayrıntılı mimari: `ADIM_25_MIMARI.md`
