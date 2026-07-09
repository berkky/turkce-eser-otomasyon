# Adım 27 — Patron Demo Akışı ve Sunum Modu

## Amaç

Projeyi 5–7 dakikada patrona veya teknik olmayan karar vericiye gösterebilecek demo moduna dönüştürmek. Riskli üretim yapılmaz; ESER-00005 ve ESER-00006 gerçek veriyle akış gösterilir.

## Demo sayfası

```
GET /demo
```

Bölümler: değer önerisi, 7 adımlı timeline, canlı metrikler, örnek eserler, önce/sonra, yapıldı/kaldı, güvenlik uyarıları, hızlı linkler.

## API

| Route | Açıklama |
|-------|----------|
| `GET /api/demo` | Tam demo JSON |
| `GET /api/demo/metrikler` | Metrikler |
| `GET /api/demo/akis` | 7 adımlı akış |

## Patron sunum paketi

```powershell
powershell -ExecutionPolicy Bypass -File .\patron-demo-paketi.ps1
```

Çıktı: `Desktop\turkce-eser-demo-paketi\`

Dosyalar: patron-demo-ozeti.html/md, teknik-ozet.md, demo-akisi.md, sonraki-adimlar.md, github-ve-kurulum.md, metrikler.json

Web durumu: `GET /demo/paket` (salt okunur)

## Çekirdek sınıflar

- `DemoAkisService` — 7 adımlı akış
- `DemoMetrikService` — metrikler + git commit
- `DemoDegerOnerisiService` — önce/sonra, yapıldı/kaldı
- `DemoGuvenlikService` — uyarılar, GitHub URL
- `DemoSenaryo` — ESER-00005/00006 rolleri
- `DemoRaporService` — patron paketi üretimi
- `PatronDemoPaketiApp` — CLI

## Güvenlik

- Gerçek TTS üretimi başlatılmaz
- ElevenLabs API otomatik çağrılmaz
- API anahtarları gösterilmez
- Localhost only
- Simülasyon notu her demo sayfasında görünür

## Demo akışı (7 adım)

1. Kaynak seçildi
2. Eser arşivlendi
3. Metadata çıkarıldı
4. Metin bölündü
5. TTS kuyruğu hazırlandı
6. Kalite paneline aktarıldı
7. Web MVP'de gösterildi

## Adım 28 önerisi

- Onaylı canlı ElevenLabs önizleme
- Demo sayfasında canlı metrik yenileme (SSE)
- PDF export (opsiyonel, harici araç)
- Çok dilli patron özeti
