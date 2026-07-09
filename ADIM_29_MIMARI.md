# Adım 29 — Forced Alignment, Altyazı ve Okuma Takibi

## Forced alignment nedir?

Ses dosyası ile metin arasında kelime/segment düzeyinde zaman eşlemesi üretir. Böylece dinleme sırasında hangi kelimenin ne zaman okunduğu bilinir; altyazı ve okuma takibi mümkün olur.

## Neden önce preview üzerinden?

- Düşük maliyet ve kontrollü kredi kullanımı
- ESER-00005 kısa önizleme ile patron demo yeterli
- Tam eser alignment bu adımda **yapılmaz**
- Önizleme yoksa sistem “önce önizleme üret” der

## Mock alignment ne işe yarar?

Kredi yokken veya demo sırasında gerçek ElevenLabs API’ye gitmeden:
- Metni segmentlere böler
- Tahmini süre ve kelime zamanlaması üretir
- SRT/VTT export ve web okuma takibi UI’sını test eder
- Aynı textHash + audioHash → idempotent sonuç

## DemoFixture (patron demo)

Gerçek ElevenLabs önizleme MP3 yoksa gerçek alignment yapılamaz. Patron demosu için:

```powershell
powershell -ExecutionPolicy Bypass -File .\elevenlabs-alignment.ps1 -EserId 5 -Mock -DemoFixture
```

- Gerçek eser `onizleme/` klasörüne **dokunmaz**
- Fixture dosyaları: `ses-arsivi/_alignment/_fixture/`
- Çıktı JSON'da `source: MOCK`, `demoFixture: true`
- Gerçek ses-metin hizalama için önce gerçek preview MP3 şarttır

## Altyazı API davranışı

`/api/alignment/5/subtitles?format=vtt|srt`:
- Dosya varsa: `WEBVTT` veya SRT metni (200)
- Yoksa: 404 + düz metin veya JSON (`altyazi_yok`) — HTML hata sayfası değil

## Gerçek API ne zaman çağrılır?

Yalnızca açık onaylı komutla:

```powershell
powershell -ExecutionPolicy Bypass -File .\elevenlabs-alignment.ps1 -EserId 5 -GercekApiOnayli
```

Koşullar: preview MP3 + preview-input.txt mevcut, kredi var, ESER-00005, mock kapalı.

## Dosya yapısı

```
ses-arsivi/_alignment/
  ESER-00005-preview-alignment.json
  ESER-00005-preview-alignment.summary.json
  ESER-00005-preview-subtitles.srt
  ESER-00005-preview-subtitles.vtt
```

Web API yalnızca `safeName` döner; tam yol gösterilmez.

## SRT/VTT üretimi

`SubtitleExportService` → `SrtWriter` / `VttWriter`
- Artan segment süreleri
- UTF-8 Türkçe
- Boş segment yok
- VTT başında `WEBVTT`

## Okuma takibi UI

`/eser/5/alignment`:
- Audio player
- Segment listesi (`data-start` saniye)
- `app.js` ile aktif segment vurgusu ve tıklayınca seek

## ESER-00006 neden engelli?

Büyük eser — tam/preview alignment maliyet ve onay gerektirir. Yalnızca BLOCKED plan ve uyarı gösterilir.

## Adım 30’da ne yapılacak?

- Gerçek ElevenLabs forced alignment API bağlantısı
- Onaylı tam eser üretim akışı
- İsteğe bağlı karakter düzeyi alignment
