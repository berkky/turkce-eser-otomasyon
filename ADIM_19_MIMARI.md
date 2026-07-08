# Adım 19 — Birleşik Kaynak Alım Orkestratörü

## Amaç

Üretim kuyruğuna yalnız önceden hazırlanmış `tts-parcalari` klasörleri vermek yerine, farklı kaynak türlerini tek giriş katmanından alıp şu zinciri otomatik kurmak:

```text
PDF / EPUB / TXT / HTML / URL / Archive.org
    -> indirme ve SHA-256 tekrar kontrolü
    -> tam metin ve bölüm çıkarma
    -> metin-arsivi
    -> TTS parçalaması
    -> kalıcı üretim kuyruğu
```

## Yeni bileşenler

- `KaynakAlimOrkestratoruApp`: etkileşimli ve komut satırı giriş noktası.
- `EserKaynakAlimService`: atomik arşivleme, eser kimliği, tekrar tespiti, manifest ve kuyruk senkronizasyonu.
- `BelgeMetinCikarmaService`: PDF, EPUB, TXT, Markdown ve HTML tam metin çıkarımı.
- `ArchiveOrgCozumleyici`: Archive.org identifier çözümleme, metadata okuma ve uygun dosyayı otomatik seçme.
- `KaynakAlimSonucu` ve `KaynakAlimTuru`: sonuç ve tür modeli.

## Güvenilirlik ilkeleri

1. Kaynak dosyanın SHA-256 özeti daha önce alınmışsa ikinci eser oluşturulmaz.
2. Metin arşivi geçici klasörde hazırlanır ve yalnız tamamlanınca atomik olarak taşınır.
3. Taranmış/görsel PDF sessizce boş metin olarak kabul edilmez; `OCR gerekli` hatasıyla durur.
4. Her eser `alim-manifest.json`, `tam-metin.txt`, `bolumler`, `tts-parcalari` ve `_hazir.flag` içerir.
5. Kaynak alımı tamamlanınca üretim kuyruğu otomatik senkronize edilir.
6. Archive.org seçiminde orijinal EPUB, orijinal PDF ve metin türevleri kalite sırasıyla değerlendirilir.
7. Mevcut Adım 18 tekrar üretim koruması aynen korunur.

## PDF davranışı

Metin tabanlı PDF'ler PDFBox ile 20 sayfalık mantıksal bölümlere ayrılır. Toplam metin çok azsa dosya taranmış sayılır ve yerel Türkçe OCR fazına bırakılır. Adım 19 sahte veya boş metin üretmez.

## Sonraki mimari faz

Adım 20'nin hedefi yerel Türkçe OCR işçisi, görev önceliği, kaynak lisans kapısı ve web kontrol paneli API katmanıdır.
