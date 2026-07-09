# Türkçe Eser Otomasyonu — Adım 26
## Yerel Web MVP Kontrol Paneli

Bu paket **tam projedir; patch değildir**. Adım 17–25 özelliklerini eksiksiz içerir.

## Bu sürümde yenilikler

- Localhost web kontrol paneli (`web-panel.ps1`) — http://127.0.0.1:8787
- Dashboard, eser listesi/detay, kalite paneli, sistem durumu, kuyruk
- Güvenli JSON API (`/api/status`, `/api/eserler`, vb.)
- Güvenli ses önizleme endpoint (`/media/preview/{safeId}`)
- CSRF benzeri nonce ile güvenli işlemler sayfası
- `com.sun.net.httpserver.HttpServer` — Spring Boot yok

## Güvenlik

- Yalnızca localhost (`127.0.0.1:8787`).
- API anahtarları hiçbir ekranda veya JSON'da gösterilmez.
- Tam eser ses üretimi web panelinden başlatılamaz.
- Gerçek ElevenLabs API çağrısı otomatik yapılmaz.
- Var olan arşiv, ses, metin ve Excel dosyalarına dokunulmaz.

## Komutlar

```powershell
# Self-test (Adım 21–26)
powershell -ExecutionPolicy Bypass -File .\adim26-self-test.ps1

# Web paneli başlat
powershell -ExecutionPolicy Bypass -File .\web-panel.ps1

# Kalite paneli statik çıktı (korunur)
powershell -ExecutionPolicy Bypass -File .\ses-kalite-panel.ps1
```

Web panel URL: **http://127.0.0.1:8787**

Ayrıntılı mimari: `ADIM_26_MIMARI.md`
