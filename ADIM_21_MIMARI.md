# Adım 21 — Eser Fabrikası Mimarisi

## Hedef

Tek bir kaynak girdisini kalıcı, izlenebilir ve tekrar üretmeyen bir eser kaydına dönüştürmek.

## Ana bileşenler

- `ArchiveOrgCozumleyici`: identifier, alt yol, aday puanlama, ana kaynak ve metin yedeği.
- `MetadataCikarmaService`: ilk 3 sayfa/bölüm, Gemini/OpenAI ve kural tabanlı birleşim.
- `EserMetadata`: normalize edilmiş künye, güven puanı ve kanıt.
- `EserKatalogService`: atomik XLSX yazma, Eser ID güncelleme, kontrol satırı vurgusu.
- `EserKaynakAlimService`: transaction benzeri arşiv/metin/TTS/katalog orkestrasyonu.
- `KaynakAlimOrkestratoruApp`: interaktif ve komut satırı arayüzü.

## Archive.org seçim algoritması

1. `/details/{identifier}/{alt-yol}` ayrıştırılır.
2. `/metadata/{identifier}` üzerinden dosya listesi alınır.
3. PDF, EPUB, TXT ve HTML adayları filtrelenir.
4. Tam yol, son dosya adı, kelime kesişimi, ortak klasör ve format puanlanır.
5. En yüksek puanlı dosya ana kaynak olur.
6. Aynı hedefe ait EPUB/OCR TXT varsa tam metin yedeği olarak seçilir.
7. `inspect-archive` ile indirmeden önce sonuç görülebilir.

## Metadata güven modeli

Alanlar şu kaynaklardan birleştirilir:

1. Yapay zekâ sonucu
2. URL alt eser ipucu
3. EPUB gömülü metadata / çıkarılan metin
4. Archive.org item metadata
5. Dosya adı

Başlık, yazar, yayınevi, yıl, ISBN, seçimin gücü ve AI sonucu ağırlıklandırılır. Yeterli güven yoksa kayıt silinmez; `KONTROL_GEREKIYOR` olarak Excel'e yazılır.

## Dosya bütünlüğü

- Kaynak SHA-256 aynıysa eser yeniden işlenmez.
- Arşiv ve metin klasörleri geçici klasörde hazırlanıp atomik taşınır.
- Excel geçici dosyaya yazılıp atomik olarak değiştirilir.
- TTS üretimi başlamaz; yalnız metin parçaları hazırlanıp kuyruk kaydı açılır.
- Adım 18'in mevcut MP3/M4B algılama koruması korunur.

## Bilinen sınırlar

- Archive.org dosya adlandırmaları tutarsızsa `inspect-archive` sonucu kontrol edilmelidir.
- Metin yedeği bulunmayan taranmış PDF yerel OCR gerektirir.
- AI çıktısı doğrulama desteklidir ancak insan onayı gerektiren satırlar Excel'de işaretlenir.
