# Türkçe Eser Arşivleme ve Yapay Zekâ ile Seslendirme Otomasyonu

Türkçe kitap ve belgeleri otomatik arşivleyen, kataloglayan ve yapay zekâ ile seslendirmeye hazırlayan yerel üretim sistemidir.

## Final Demo / Hızlı Başlangıç

```powershell
# 1) Final kalite kapısı
powershell -ExecutionPolicy Bypass -File .\final-release-check.ps1

# 2) Web paneli
powershell -ExecutionPolicy Bypass -File .\web-panel.ps1
```

**Demo URL:** http://127.0.0.1:8787/demo

**Teslim paketi oluşturma (Adım 33):**

```powershell
powershell -ExecutionPolicy Bypass -File .\teslim-paketi-olustur.ps1
powershell -ExecutionPolicy Bypass -File .\teslim-paketi-kontrol.ps1
# Yalnız bilinçli release güncellemesinde tracked TESLIM_OZETI.md senkronu:
# powershell -ExecutionPolicy Bypass -File .\teslim-paketi-olustur.ps1 -SyncRepo
```

Çıktı: `Desktop\turkce-eser-final-teslim\` — kaynak kod ZIP + gönderim dokümanları.

**Güvenlik:** API anahtarları repoda/ZIP'te yok. Tam üretim ve gerçek API varsayılan kapalı. Secret scan: `check-secrets.ps1`

**İki paket:** Patron demo paketi (sunum dosyaları) ≠ Final teslim ZIP (kaynak kod + dokümantasyon). Büyük medya/arşiv bilerek dahil edilmez.

**Canonical veri kökü:** `%USERPROFILE%\Desktop\ESER`. Java uygulamaları `EserVeriYollari`,
PowerShell scriptleri `canonical-paths.ps1` üzerinden aynı politikayı kullanır. Legacy doğrudan
Desktop veri klasörleri otomatik seçilmez, yazılmaz veya taşınmaz; yalnız
`LEGACY_DATA_ROOT_DETECTED` uyarısıyla raporlanır.

**Kaşağı pasaj seçimi (Adım 35, offline):**

```powershell
mvn -q -Padim35PasajAdaylari exec:java
powershell -ExecutionPolicy Bypass -File .\adim35-self-test.ps1
```

Panel: `/ab-test/pasajlar/ESER-00005`. Pasaj onayı ses üretmez; OpenAI/xAI hazırlık
profilleri `LIVE_GENERATION_LOCKED` durumunda kalır.

---

## Ne yapar?

- PDF, EPUB ve web kaynaklarından eser alır
- Metadata çıkarır ve Excel kataloğa yazar
- Metni TTS parçalarına böler
- Ses önizlemesi ve kalite paneli sunar
- Okuma takibi (alignment) ve üretim planı oluşturur
- Yerel web paneli ile tüm süreci izlemenizi sağlar

## Hızlı başlangıç

```powershell
git clone https://github.com/berkky/turkce-eser-otomasyon.git
cd turkce-eser-otomasyon
mvn -q -DskipTests compile
powershell -ExecutionPolicy Bypass -File .\web-panel.ps1
```

Tarayıcı: **http://127.0.0.1:8787/demo**

## Demo panel

Patron ve teknik olmayan izleyiciler için hazır demo sayfası:

- 7 adımlı üretim timeline
- ESER-00005 (Kaşağı) ve ESER-00006 (Astronomi) örnekleri
- Adım 32 **Final Demo Ready** kalite özeti

## Test komutu

```powershell
powershell -ExecutionPolicy Bypass -File .\adim34-self-test.ps1
powershell -ExecutionPolicy Bypass -File .\adim33-self-test.ps1
powershell -ExecutionPolicy Bypass -File .\final-release-check.ps1
```

Başarılı çıktı: `ADIM 34 DOGRULAMA: BASARILI` / `FINAL RELEASE CHECK: BAŞARILI`

## Türkçe TTS A/B laboratuvarı

```powershell
# Yapılandırma durumu; API çağrısı yok
powershell -ExecutionPolicy Bypass -File .\tts-ab-lab.ps1 -Status

# FFmpeg ile tamamen yerel mock kör paket
powershell -ExecutionPolicy Bypass -File .\tts-ab-lab.ps1 -Mock -Seed 340034
```

Kör değerlendirme: `http://127.0.0.1:8787/ab-test`. xAI canlı modu varsayılan kapalıdır,
yalnız ESER-00005 için tek seçilmiş voice, ortam kapısı ve iki açık CLI onayıyla çalışır.
Detaylar: `docs/AB_TEST_UYGULAMA_REHBERI.md`.

## Güvenlik notu

- API anahtarları **asla** ekranda, logda veya JSON'da gösterilmez
- Tam eser üretimi ve gerçek API çağrıları **varsayılan kapalı**
- Web panel yalnızca localhost'ta çalışır (127.0.0.1:8787)
- Secret scan: `check-secrets.ps1`

## Mevcut durum

**Adım 34** — Türkçe TTS A/B laboratuvarı, xAI adaptörü ve kör değerlendirme paketi eklendi.

| Alan | Durum |
|------|-------|
| Kaynak alma / arşiv | Tamam |
| Metadata / güvenlik | Tamam |
| Web panel / demo | Tamam |
| Önizleme / alignment | Tamam (mock + onaylı gerçek API) |
| Üretim planı / kuyruk | Tamam (TTS başlatmaz) |
| Final kalite kapısı | Tamam |
| Teslim ZIP + gönderim | Tamam |

## GitHub kullanım notu

Repo: https://github.com/berkky/turkce-eser-otomasyon

- Anahtarları ortam değişkeni olarak tanımlayın; repoya commit etmeyin
- Kredi yokken mock mod ile tüm akış test edilebilir
- Detaylı kurulum: `FINAL_KURULUM_REHBERI.md` · 3 adım: `KURULUM_3_ADIM.md`
- Demo akışı: `FINAL_DEMO_AKISI.md` · 5 dk: `DEMO_5_DAKIKA.md`
- Gönderim mesajları: `GONDERIM_MESAJLARI.md`

---

Ayrıntılı mimari: `ADIM_32_MIMARI.md` · Sürüm: **33.0.0**
