# Türkçe Sesli Eser Platformu — Güncel Yol Haritası

## Ürün tanımı

Platform yalnız kitap değil; kitap, makale, haber, blog, sosyal gönderi ve yetkili özel metinleri “eser” olarak alır. Her eseri kaynak, künye, lisans notu, tam metin, bölüm, TTS parçası ve ses paketiyle izlenebilir biçimde yönetir.

## Tamamlanan çekirdek — Adım 1–22

- PDF/EPUB/TXT/Markdown/HTML ve URL alımı
- Archive.org identifier, alt klasör ve doğru dosya seçimi
- Ana kaynak ile EPUB/OCR metin yedeği eşleştirme
- SHA-256 tekrar tespiti
- Düzenli arşiv, tam metin, bölüm ve TTS parçası
- Excel katalogu ve Eser ID ilişkisi
- Yerel künye çıkarımı, ISBN doğrulama ve metadata onarımı
- Tesseract tabanlı isteğe bağlı Türkçe OCR
- Gemini/OpenAI olmadan çalışabilen metadata hattı
- Piper, Google Chirp ve ElevenLabs istemci/sağlayıcı sınıfları
- Kalıcı kuyruk, devam etme, maliyet sınırı ve sağlayıcı politikası
- MP3/M4B paketleme
- Mevcut ses paketinden tekrar üretim engeli
- UTF-8 Türkçe konsol standardı

## TTS sağlayıcı politikası

### ElevenLabs

Premium Türkçe anlatımın ana adayıdır. Anahtar yalnız ses üretiminde kullanılır; PDF künyesi çıkarmak için kullanılmaz. Tam eserden önce kısa ön izleme, kota/maliyet hesabı ve kullanıcı onayı zorunlu olacaktır.

### Google Cloud Chirp

Çalışan bulut yedeğidir. Maliyet koruyucu sınır ve otomatik sağlayıcı politikasıyla kullanılır.

### Piper

Ücretsiz, çevrimdışı ve sınırsız güvenli yedektir. Geliştirme, erişilebilirlik ve bulut kesintilerinde korunur.

### Gemini/OpenAI metadata

İsteğe bağlı yardımcı katmandır. Yerel künye motoru yeterliyse çağrılmaz. Anahtar veya kota olmaması kaynak alımını durdurmaz.

## Adım 23 — Premium Türkçe ses üretimi

- ElevenLabs hesap/kota doktoru
- Türkçe sesleri otomatik listeleme ve kısa ön izleme
- `eleven_multilingual_v2` ve uygun düşük gecikmeli model politikası
- Eser türüne göre anlatım profili
- 60–90 saniyelik onay ön izlemesi
- Tam üretim öncesi karakter ve tahmini maliyet onayı
- Parça bazlı devam, retry, hash cache ve sağlayıcı kilidi
- Telaffuz sözlüğü: özel ad, yabancı kelime, sayı, tarih ve kısaltma
- Aynı eser boyunca ses/ayar tutarlılığı

## Adım 24 — Yerel web uygulaması / MVP

- Spring Boot arayüzü
- URL yapıştırma ve dosya yükleme
- Çıkarılan künyeyi inceleme/düzeltme
- Eser türü, hak durumu ve yayın kapısı
- Ses ön izleme ve üretim onayı
- Kuyruk, ilerleme, maliyet ve hata ekranı
- Arşiv arama, MP3/M4B oynatma ve Excel dışa aktarma

## Adım 25 — İçerik adaptörleri ve yayın güvenliği

- Haber/RSS ve blog içerik temizleyicileri
- Sosyal gönderi/zincir veri modeli
- metin sürümleme ve yalnız değişen parçayı yeniden üretme
- lisans kanıtı, insan onayı ve yayına açma kapısı
- yedekleme, nesne depolama ve dağıtım altyapısı

## Değişmeyen güvenlik ilkeleri

- Hak/lisans belirsizse otomatik halka yayın yoktur.
- Tam eserde yüksek maliyetli TTS, ön izleme ve açık onay olmadan başlamaz.
- Kaynak metin, metadata, dosya hash’i ve üretim sağlayıcısı denetlenebilir biçimde kaydedilir.
- Tamamlanmış eser yeniden üretilmez; kaynak değişirse yeni sürüm olarak ele alınır.
