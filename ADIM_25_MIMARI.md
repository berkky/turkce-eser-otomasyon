# Adım 25 — Ses Önizleme Kalite Paneli Mimarisi

## Neden kalite paneli?

Tam eser TTS üretimi maliyetli ve geri dönüşü zordur. Farklı sağlayıcı, model ve voice seçeneklerini **kredi harcamadan** veya **kısa önizlemelerle** karşılaştırmak; üretim kararını veriye dayandırmak için bu panel eklendi.

## Gerçek TTS üretiminden önce neden önizleme?

- Türkçe telaffuz kalitesi sağlayıcıya göre değişir.
- Büyük eserlerde (ör. Astronomi) yanlış model seçimi yüksek kredi kaybına yol açar.
- İnsan puanları ve telaffuz notları ileride otomatik üretim politikasını besler.

## Mock ve gerçek preview farkı

| Tür | Açıklama |
|-----|----------|
| **MOCK** | Test fixture; gerçek TTS değildir. Panelde açıkça işaretlenir. |
| **GERÇEK** | ElevenLabs/Piper/Google önizleme API çıktısı. |
| **BEKLENIYOR** | Henüz önizleme üretilmemiş eser. |
| **GECERSIZ** | 0 byte veya bozuk MP3. |

## Panel nasıl açılır?

```powershell
powershell -ExecutionPolicy Bypass -File .\ses-kalite-panel.ps1
```

Çıktı: `%USERPROFILE%\Desktop\ESER\ses-arsivi_kalite-panel\index.html`

Tarayıcıda `index.html` dosyasını açın. Sunucu veya internet gerekmez.

## Puanlama dosyası

`kalite-degerlendirmeleri.json` dosyasını düzenleyin:

- `humanScoreNaturalness`, `humanScorePronunciation`, vb. (1–5)
- `recommendedForFullProduction`: true/false
- `pronunciationIssues`, `reviewerNote`

Panel yeniden üretildiğinde mevcut puanlar **silinmez**.

## Telaffuz notları

`telaffuz-notlari.json` — Kaşağı, Alfa Yayınları, ISBN gibi kelimeler için notlar. Adım 26'da ElevenLabs pronunciation dictionary veya normalizasyona aktarılabilir.

## Çekirdek sınıflar

- `SesOnizlemeKaydi`, `SesKaliteDegerlendirmesi`, `SesKalitePanelRaporu`
- `SesOnizlemeKesifService` — ses-arsivi tarama (salt okunur)
- `SesKalitePanelService` — HTML/JSON/CSV/MD üretimi
- `SesKalitePanelApp` — CLI

## Güvenlik

- Gerçek ElevenLabs API çağrısı yapılmaz.
- API anahtarları loglanmaz.
- Var olan MP3/M4B dosyalarına dokunulmaz.

## Adım 26 önerisi

- Canlı ElevenLabs önizleme sonrası panelde otomatik güncelleme
- Telaffuz sözlüğünün `TurkishSpeechTextNormalizer`'a entegrasyonu
- İnsan puanlarına göre otomatik model/sağlayıcı önerisi
- İsteğe bağlı hafif yerel sunucu (live reload) — şimdilik statik HTML yeterli
