# Adım 22 Mimari — Yerel Künye, OCR ve Metadata Onarımı

## Amaç

Metadata hattını ücretli/genel amaçlı yapay zekâ servislerinden bağımsız çalıştırmak; yeni ve mevcut eserlerde kanıtlı künye çıkarmak; arşiv, manifest ve Excel’i tutarlı biçimde güncellemek.

## Bileşenler

### YerelKunyeAnalizService

Tam metnin ilk 140.000 karakterini NFKC ile normalleştirir. Yalnız metinde açıkça bulunan veya güçlü kolektif işaretlerinden türetilebilen bilgileri çıkarır. ISBN kontrol basamağını doğrular. Bilgi uydurmaz.

### MetadataCikarmaService

Öncelik sırası:

1. Yerel ön metin/künye analizi
2. Gömülü PDF/EPUB metadata
3. Archive.org metadata ve hedef yol
4. Dosya adı
5. Yalnız yerel sonuç yetersizse isteğe bağlı Gemini/OpenAI

Birleşen alanlar için güven puanı ve kanıt üretilir. Temel kimlik ve yayın bilgisi yeterliyse kayıt `HAZIR`, aksi hâlde `KONTROL_GEREKIYOR` olur.

### YerelOcrService

PDFBox ile sayfayı PNG’ye çevirir, Tesseract CLI ile `tur+eng` OCR yapar. Sayfa çıktıları geçici klasörde tutulur ve işlem sonunda silinir. Taranmış PDF’de Archive.org metin yedeği yoksa tam metin üretiminde kullanılabilir.

### Metadata onarım işlemi

`repair-metadata` işlemi:

1. Eser ID ile metin ve arşiv klasörünü bulur.
2. `alim-manifest.json` ve `tam-metin.txt` dosyalarını okur.
3. Yerel künye analizini çalıştırır.
4. Sonuç hâlâ eksikse ve Tesseract hazırsa arşivdeki `ilk-3-sayfa.pdf` ön izlemesini OCR eder.
5. Kaynak dosyasını güvenli ada taşır.
6. Metadata JSON, manifest, bilgi metni ve Excel satırını günceller.
7. Bölümler, TTS parçaları, kuyruk ve ses paketlerine dokunmaz.

## Veri bütünlüğü

- Eser klasörleri yeniden adlandırılmaz; kalıcı kuyruk referansları korunur.
- Kaynak dosya taşımada aynı diskte atomik taşıma denenir, desteklenmiyorsa güvenli taşıma kullanılır.
- Excel mevcut Eser ID satırını günceller, yeni kopya satır oluşturmaz.
- Eski Excel sütun sırası korunur; yeni alanlar sona eklenir.
- SHA-256 kaynak kimliği değiştirilmez.

## TTS ayrımı

ElevenLabs, Piper ve Google TTS ses üretim katmanıdır. Belge künyesi çıkarma işine dahil edilmez. Metadata onarımı hiçbir TTS çağrısı yapmaz.

## Sınırlar

Yerel künye çıkarımı, ön sayfada açıkça yazmayan veya OCR kalitesi çok düşük olan bilgileri kesinleştiremez. Böyle kayıtlar bilinçli şekilde `KONTROL_GEREKIYOR` kalır. Telif/hak alanı hukuki karar değil, kaynak metinden çıkarılan katalog notudur.
