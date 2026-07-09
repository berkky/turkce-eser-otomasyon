# Adım 26 — Yerel Web MVP Kontrol Paneli Mimarisi

## Amaç

Projeyi yalnızca terminalden çalışan teknik araç olmaktan çıkarıp, **localhost** üzerinde demo gösterilebilir yerel web MVP'ye dönüştürmek. Bu adımda gerçek TTS üretimi zorunlu değildir.

## Web panel nasıl başlatılır?

```powershell
powershell -ExecutionPolicy Bypass -File .\web-panel.ps1
```

Varsayılan URL: **http://127.0.0.1:8787**

Self-test:

```powershell
powershell -ExecutionPolicy Bypass -File .\adim26-self-test.ps1
```

## Neden localhost ile sınırlı?

- API anahtarları ve arşiv yolları hassastır.
- Demo ortamında dış erişim güvenlik riski oluşturur.
- `com.sun.net.httpserver.HttpServer` yalnızca `127.0.0.1` adresine bağlanır.
- Non-localhost istekleri 403 ile reddedilir.

## Sayfalar

| Route | Açıklama |
|-------|----------|
| `GET /` | Ana dashboard — özet kartlar |
| `GET /eserler` | Eser listesi, arama/filtre (client-side JS) |
| `GET /eser/{id}` | Eser detay, önizleme oynatıcı |
| `GET /kalite` | Ses kalite paneli (web içi görünüm) |
| `GET /sistem` | Sistem durumu (env TANIMLI/YOK) |
| `GET /kuyruk` | Üretim kuyruğu (salt okunur) |
| `GET /islemler` | Güvenli işlemler (POST + nonce) |
| `GET /docs` | Proje dokümantasyonu |
| `GET /telaffuz` | Telaffuz notları JSON görünümü |
| `GET /media/preview/{safeId}` | Güvenli ses önizleme |
| `GET /api/status` | Dashboard JSON |
| `GET /api/eserler` | Eser listesi JSON |
| `GET /api/eser/{id}` | Eser detay JSON |
| `GET /api/kalite` | Kalite panel JSON |
| `GET /api/kuyruk` | Kuyruk JSON |

## Hangi işlemler read-only?

- Tüm GET sayfaları ve API endpointleri salt okunurdur.
- Eser arşivi, ses dosyaları, Excel ve metadata **taşınmaz/silinmez**.

## Güvenli POST işlemleri (`/islemler`)

- Kalite panelini yeniden oluştur
- Sistem durumunu yenile
- Eser listesini yeniden tara

Kurallar:

- CSRF benzeri tek kullanımlık nonce (10 dk)
- Yalnızca localhost
- Shell/PowerShell komutu çalıştırılmaz — doğrudan Java servisleri çağrılır
- **Tam eser üretimi başlatılamaz**

## Güvenlik kuralları

1. API anahtarları hiçbir HTML, JSON veya logda gösterilmez.
2. Path traversal engellenir; yalnızca izinli uzantılar serve edilir.
3. Ses dosyaları yalnızca `ses-arsivi` altından, güvenli `safeId` ile sunulur.
4. 0 byte medya reddedilir.
5. Dosya yolları maskelenir (`…/ESER-00005/...`).
6. Gerçek ElevenLabs API çağrısı web panelinden otomatik yapılmaz.

## Demo akışı

1. `web-panel.ps1` ile paneli başlatın.
2. Ana sayfada eser ve önizleme sayılarını kontrol edin.
3. **Eserler** → ESER-00005 ve ESER-00006 görünür.
4. ESER-00006 detayında büyük eser uyarısı ve önizleme listesi.
5. **Ses Kalite** → mock önizlemeleri dinleyin.
6. **Sistem Durumu** → FFmpeg, Piper, env TANIMLI/YOK.
7. **Güvenli İşlemler** → kalite panelini yenileyin.

## Çekirdek sınıflar

- `YerelWebSunucuApp` — giriş noktası
- `YerelWebSunucu` — HttpServer router
- `WebOrtam` — yol ve port sabitleri
- `WebGuvenlikService` — localhost, traversal, API key filtresi
- `WebEserService` — eser listesi/detay
- `WebKalitePanelService` — kalite panel entegrasyonu
- `WebSistemDurumService` — sistem özeti
- `WebIslemService` — güvenli POST işlemleri
- `WebTemplateService` — HTML şablonları
- `WebStaticAssetService` — CSS/JS ve dosya sunumu

## Statik kalite paneli korunur

Mevcut `ses-arsivi_kalite-panel` çıktısı (index.html, kalite-panel.json, CSV, MD) korunur. Web panel aynı veriyi okur veya eksikse `SesKalitePanelService` ile yeniden üretir.

## Adım 27 geçiş planı

- Web panelden canlı ElevenLabs önizleme tetikleme (onay + mock fallback)
- Telaffuz sözlüğü düzenleme UI
- SSE/WebSocket ile kuyruk canlı güncelleme
- İsteğe bağlı kimlik doğrulama (yerel token)
- Tam eser üretimi için ayrı onay akışı (bu adımda yok)
