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
```

Çıktı: `Desktop\turkce-eser-final-teslim\` — kaynak kod ZIP + gönderim dokümanları.

**Güvenlik:** API anahtarları repoda/ZIP'te yok. Tam üretim ve gerçek API varsayılan kapalı. Secret scan: `check-secrets.ps1`

**İki paket:** Patron demo paketi (sunum dosyaları) ≠ Final teslim ZIP (kaynak kod + dokümantasyon). Büyük medya/arşiv bilerek dahil edilmez.

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
powershell -ExecutionPolicy Bypass -File .\adim33-self-test.ps1
powershell -ExecutionPolicy Bypass -File .\final-release-check.ps1
```

Başarılı çıktı: `ADIM 33 DOĞRULAMA: BAŞARILI` / `FINAL RELEASE CHECK: BAŞARILI`

## Güvenlik notu

- API anahtarları **asla** ekranda, logda veya JSON'da gösterilmez
- Tam eser üretimi ve gerçek API çağrıları **varsayılan kapalı**
- Web panel yalnızca localhost'ta çalışır (127.0.0.1:8787)
- Secret scan: `check-secrets.ps1`

## Mevcut durum

**Adım 33** — Final teslim paketi ve gönderim hazırlığı tamamlandı.

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
