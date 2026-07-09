# Demo Senaryosu — 5 Dakikalık Patron Sunumu

## Hazırlık (30 sn)

1. `powershell -ExecutionPolicy Bypass -File .\web-panel.ps1`
2. Tarayıcıda http://127.0.0.1:8787/demo açın
3. İsteğe bağlı: `patron-demo-paketi.ps1` ile masaüstü paketini oluşturun

## Konuşma Akışı (5–7 dk)

### 1. Değer önerisi (45 sn) — `/demo`

> "Bu sistem, Türkçe eserleri PDF, EPUB ve web kaynaklarından alıp otomatik arşivleyen, kataloglayan ve yapay zekâ ile seslendirmeye hazırlayan yerel bir üretim hattıdır."

- Timeline'a işaret edin: 7 adımlı uçtan uca akış
- **DUR:** Patronun "ne işe yarıyor?" sorusuna bu cümleyle cevap verin

### 2. Örnek eserler (90 sn) — `/demo` → `/eser/5`

**ESER-00005 Kaşağı:**
- Kısa eser, Vikikaynak kaynağı
- ElevenLabs önizleme testi için ideal
- `/eser/5` → demo kutusu, metadata, önizleme oynatıcı

**ESER-00006 Astronomi:**
- Büyük eser, Archive.org kaynağı
- `/eser/6` → büyük eser uyarısı, KONTROL_GEREKIYOR metadata

> "Sistem hem kısa hem büyük eserleri aynı pipeline'dan geçirir; büyük eserlerde maliyet koruması devreye girer."

### 3. Adım 28 — Premium önizleme (60 sn) — `/demo` Adım 28 bölümü

- Kredi varsa: "Kaşağı için önizleme üretilebilir"
- Kredi yoksa: "ElevenLabs kredisi bekleniyor"
- Önizleme varsa: audio player + kalite paneli linki
- `/islemler` veya `/eser/5` → onaylı önizleme formu (tam üretim kapalı)

### 4. Kalite ve maliyet kontrolü (60 sn) — `/kalite` ve `/telaffuz`

- Sağlayıcı karşılaştırması (Piper / ElevenLabs)
- Telaffuz sözlüğü tablosu (`METIN_NORMALIZE` / dictionary adayı)
- `/api/tts-plan/5` — salt okunur maliyet planı

### 5. Güvenlik ve ürünleşme (60 sn) — `/sistem` veya `/demo` güvenlik bölümü

- Localhost only, API anahtarları gizli
- Metadata KONTROL_GEREKIYOR = güvenlik bilinçli tasarım
- GitHub: https://github.com/berkky/turkce-eser-otomasyon

### 6. Kapanış (30 sn)

- Önce/sonra karşılaştırması
- Sonraki adım: forced alignment (Adım 29)
- **DUR:** Soru-cevap

## ElevenLabs Kredisi Yoksa

> "Sistem kredi kontrolü yapıyor. Kredi yokken premium üretim başlamaz; mock mod ile tüm akış test edilebilir. Bu, maliyet sürprizini önler."

## Sık Sorulan Sorular

| Soru | Kısa Cevap |
|------|------------|
| Bu proje ne işe yarıyor? | Türkçe eserleri otomatik arşivleyip sesli kitap üretimine hazırlar. |
| Sadece kitap mı? | Hayır — PDF, EPUB, web, Archive.org desteklenir. |
| Dosyaları nasıl buluyor? | Kaynak alım pipeline'ı URL, dosya ve Archive.org'dan seçer. |
| Excel'e yazıyor mu? | Evet, otomatik katalog güncellemesi yapılır. |
| Seslendirme hazır mı? | Altyapı hazır; tam üretim onay ve kredi gerektirir. |
| Maliyeti nasıl kontrol ediyor? | Kredi kontrolü, önizleme, büyük eser koruması, idempotency. |
| Yasal/hak durumu? | Her eser için ayrı lisans değerlendirmesi gerekir; sistem otomatik hak onayı vermez. |
| Bundan sonra ne gerekiyor? | Forced alignment, onaylı tam eser üretim akışı (Adım 29+). |
