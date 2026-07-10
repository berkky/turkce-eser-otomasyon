# Türkçe Eser Arşivleme ve Yapay Zekâ ile Seslendirme Otomasyonu

Türkçe kitap ve belgeleri otomatik arşivleyen, kataloglayan ve yapay zekâ ile seslendirmeye hazırlayan yerel üretim sistemidir.

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
powershell -ExecutionPolicy Bypass -File .\final-release-check.ps1
```

Başarılı çıktı: `FINAL RELEASE CHECK: BAŞARILI`

## Güvenlik notu

- API anahtarları **asla** ekranda, logda veya JSON'da gösterilmez
- Tam eser üretimi ve gerçek API çağrıları **varsayılan kapalı**
- Web panel yalnızca localhost'ta çalışır (127.0.0.1:8787)
- Secret scan: `check-secrets.ps1`

## Mevcut durum

**Adım 32** — Final release kalite kapısı tamamlandı. Sistem teslim hazırlığında.

| Alan | Durum |
|------|-------|
| Kaynak alma / arşiv | Tamam |
| Metadata / güvenlik | Tamam |
| Web panel / demo | Tamam |
| Önizleme / alignment | Tamam (mock + onaylı gerçek API) |
| Üretim planı / kuyruk | Tamam (TTS başlatmaz) |
| Final kalite kapısı | Tamam |
| Teslim ZIP | Adım 33 |

## GitHub kullanım notu

Repo: https://github.com/berkky/turkce-eser-otomasyon

- Anahtarları ortam değişkeni olarak tanımlayın; repoya commit etmeyin
- Kredi yokken mock mod ile tüm akış test edilebilir
- Detaylı kurulum: `FINAL_KURULUM_REHBERI.md`
- Demo akışı: `FINAL_DEMO_AKISI.md`

---

Ayrıntılı mimari: `ADIM_32_MIMARI.md` · Sürüm: **32.0.0**
