# Patron Kısa Özet — Adım 33

## Proje ne?

Türkçe kitap ve belgeleri bilgisayarınızda arşivleyen, kataloglayan ve yapay zekâ ile seslendirmeye **hazırlayan** yerel bir otomasyon sistemidir. Web paneli üzerinden tüm süreci izleyebilir, demo ile gösterebilirsiniz.

## Hangi problemi çözüyor?

Dağınık PDF/EPUB dosyalarını standart bir **eser** yapısına dönüştürmek, metadata çıkarmak, ses kalitesini karşılaştırmak ve üretim planını tek yerden yönetmek zaman alıcıdır. Bu sistem bu adımları tek akışta toplar; karar vermeden önce maliyet ve risk görünür olur.

## Şu an ne çalışıyor?

- Kaynak alma ve arşivleme altyapısı
- Excel + JSON katalog
- Yerel web paneli (localhost)
- Ses kalite paneli ve sağlayıcı karşılaştırması
- Okuma takibi (alignment) — mock/demo fixture ile
- Üretim planı, onay ve kuyruk — **gerçek TTS başlatmaz**
- Final kalite kapısı ve secret scan

Bu bir **yerel çalışan MVP**; mock/demo moduyla sunuma hazırdır.

## Demo nasıl açılır?

```powershell
powershell -ExecutionPolicy Bypass -File .\web-panel.ps1
```

Tarayıcı: **http://127.0.0.1:8787/demo**

5 dakikalık akış: `DEMO_5_DAKIKA.md`

## Güvenlik neden önemli?

API anahtarları ve maliyetli TTS çağrıları bilinçli olarak kilitlidir. Yanlışlıkla binlerce liralık üretim başlamasın diye onay kapıları vardır. Anahtarlar repoda ve teslim ZIP'inde yer almaz.

## Nereden gelir potansiyeli var?

- Yayınevi/kütüphane kurumsal kurulum
- Eser başına seslendirme hizmeti (maliyet + marj)
- İleride bulut panel / SaaS (henüz yok)

Detay: `IS_MODELI_NOTU.md` — abartılı iddia yok; her eser için telif değerlendirmesi gerekir.

## Neler sonraya kaldı?

- Tam otomatik canlı TTS üretimi (onay + kredi sonrası)
- Çok kullanıcılı bulut dağıtım
- Lisans/hak yönetim modülü
- Genişletilmiş telaffuz sözlüğü

## İki paket ayrımı

| Paket | Amaç |
|-------|------|
| **Patron demo paketi** | Sunum dosyaları (HTML özet, timeline) |
| **Final teslim ZIP** | Kaynak kod + dokümantasyon + kurulum |

Büyük medya ve yerel arşiv dosyaları bilerek ZIP'e konmaz.
