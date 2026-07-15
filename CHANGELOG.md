# Değişiklik Günlüğü

## 35.0.0 — Gerçek Kaşağı pasaj seçimi ve canlı önizleme hazırlığı
- Canonical ESER-00005 metninden deterministik, 900–1.250 karakterlik üç pasaj adayı.
- Original/normalize hash, mekanik değişiklik kaydı, kaynak ve `REVIEW_REQUIRED` lisans statüsü.
- Localhost pasaj seçim/onay ekranı; tek kullanımlık token ve duplicate submission koruması.
- OpenAI/xAI profil taslakları ve ortak `LIVE_GENERATION_LOCKED` dosyası; ağ/ses üretimi yok.
- Adım 35 offline doğrulama, UAT, güvenlik ve kaynak/lisans dokümantasyonu.

## 34.0.0 — Türkçe TTS A/B ses laboratuvarı
- ESER-00005 için sabit kaynak hash, deney/aday/manifest modelleri ve mock-varsayılan orkestrasyon.
- xAI unary TTS adaptörü; live/CLI/eser/karakter/bütçe/idempotency kapıları ve yerel fake HTTP testleri.
- FFmpeg iki geçişli -16 LUFS / -1 dBTP normalizasyonu, metadata temizliği ve deterministic kör mapping.
- `/ab-test` localhost değerlendirme ekranı, UTF-8 BOM CSV ve submission ID tekrar koruması.
- Public paket allowlist'i; `provider-mapping.private.json` dışlama güvencesi.
- `adim34-self-test.ps1` ve Adım 34 güvenlik/metodoloji dokümantasyonu.
- Java/PowerShell için tek `%USERPROFILE%\Desktop\ESER` canonical veri kökü; legacy kökler salt
  tespit edilir ve aktif cache/yazma hedefi olmaz.

## 33.0.0 — Final teslim paketi ve gönderim hazırlığı
- `teslim-paketi-olustur.ps1` — kaynak kod ZIP + teslim klasörü.
- `teslim-paketi-kontrol.ps1` — güvenlik kontrolü, SHA-256, rapor.
- Gönderim dokümanları: `GONDERIM_MESAJLARI.md`, `PATRON_KISA_OZET.md`, `TEKNIK_KISIYE_NOT.md`, `KURULUM_3_ADIM.md`, `DEMO_5_DAKIKA.md`.
- `adim33-self-test.ps1`, `Adim33Dogrulama.java`, `FINAL_RELEASE_DOGRULAMA.md`.
- README Final Demo / teslim paketi bölümü.

## 32.0.0 — Final release kalite kapısı
- `final-release-check.ps1` tek komut teslim doğrulaması.
- `check-secrets.ps1` API anahtarı taraması.
- `FINAL_*.md` teslim dokümantasyonu.
- `/demo` Adım 32 Final Demo Ready paneli.
- Patron paketine final notlar eklendi.
- `Adim32Dogrulama`, `adim32-self-test.ps1`, `ADIM_32_MIMARI.md`.

## 31.0.0 — Onaylı tam eser üretim kapısı ve maliyet kuyruğu
- `TamEserUretim*` plan/onay/kuyruk modeli ve servisleri.
- `production-approvals/` altında güvenli JSON (.partial + rename).
- Scriptler: `tam-eser-plan.ps1`, `tam-eser-onay-taslagi.ps1`, `tam-eser-kuyruga-al.ps1`.
- Web: `/uretim`, `/api/uretim-plan/{id}`, `/api/uretim-kuyruk`; gerçek TTS yok.
- `Adim31Dogrulama`, `adim31-self-test.ps1`, `ADIM_31_MIMARI.md`.

## 30.0.0 — Gerçek forced alignment API kapısı
- ElevenLabs `POST /v1/forced-alignment` multipart istemci (`AlignmentApiClient`).
- `-GercekApiOnayli` açık onay; web panelden gerçek API kapalı.
- `AlignmentHata`, `source`/`realApiUsed`/`demoFixture` ayrımı.
- `Adim30Dogrulama`, `adim30-self-test.ps1`, `ADIM_30_MIMARI.md`.

## 29.0.0 — Forced alignment ve okuma takibi
- Mock forced alignment servisi ve `ses-arsivi/_alignment/` depolama.
- SRT/VTT export, okuma takibi UI (`/eser/5/alignment`).
- API: `/api/alignment`, segments, subtitles.
- `elevenlabs-alignment.ps1`, `Adim29Dogrulama`, `adim29-self-test.ps1`.
- `ADIM_29_MIMARI.md`

## 28.0.0 — ElevenLabs önizleme, telaffuz ve maliyet planı
- Güvenli ElevenLabs kısa önizleme (ESER-00005, kredi/idempotent koruma).
- `TelaffuzSozluguService`, `TelaffuzNormalizerService`, `TtsMaliyetPlanService`.
- Web: `POST elevenlabs-onizleme`, `/api/tts-plan/{id}`, `/api/telaffuz`, `/api/elevenlabs/status`.
- Demo Adım 28 bölümü; patron paketi güncellendi.
- `ADIM_28_MIMARI.md`, `adim28-self-test.ps1`, `Adim28Dogrulama.java`.

## 27.0.0 — Patron demo akışı ve sunum modu
- `/demo` patron demo sayfası: timeline, metrikler, önce/sonra, güvenlik uyarıları.
- Demo API: `/api/demo`, `/api/demo/metrikler`, `/api/demo/akis`.
- `patron-demo-paketi.ps1` ile masaüstü sunum paketi üretimi.
- ESER-00005/00006 demo rol açıklamaları eser detay sayfasında.
- `DEMO_SENARYOSU.md`, `IS_MODELI_NOTU.md`, `ADIM_27_MIMARI.md` eklendi.

## 26.0.0 — Yerel web MVP kontrol paneli
- Localhost web paneli eklendi (`YerelWebSunucuApp`, port 8787).
- Dashboard, eser listesi/detay, kalite, sistem, kuyruk, dokümantasyon sayfaları eklendi.
- Güvenli JSON API ve `/media/preview/{safeId}` ses endpoint'i eklendi.
- CSRF benzeri nonce ile güvenli işlemler (kalite yenile, eser tara).
- `web-panel.ps1` ve `adim26-self-test.ps1` eklendi.
- API anahtarı sızıntı filtresi, path traversal ve 0 byte media koruması.
- Tam eser üretimi web panelinden başlatılamaz.

## 25.0.0 — Ses önizleme kalite paneli
- Statik HTML kalite paneli ve sağlayıcı karşılaştırma raporu eklendi.
- SesOnizlemeKesifService ile önizleme keşfi (salt okunur) eklendi.
- İnsan puanlama (`kalite-degerlendirmeleri.json`) ve telaffuz notları eklendi.
- Mock demo verisi scripti eklendi; mock açıkça işaretleniyor.
- Self-test UTF-8 başlık düzeltmeleri (Write-Utf8Line, JAVA_TOOL_OPTIONS).
- ESER-00006 büyük eser koruması panel raporunda görünüyor.

## 24.0.0 — ElevenLabs Premium Türkçe TTS entegrasyonu
- ElevenLabs kredi kontrolü, voice/model doğrulama ve güvenli hata yönetimi eklendi.
- 60–90 saniyelik önizleme üretimi (`elevenlabs-onizleme.ps1`) ve `requestHash` idempotency eklendi.
- `TurkishSpeechTextNormalizer` ve `ElevenLabsModelPolitikasi` eklendi.
- Mock mod (`ELEVENLABS_MOCK`) ve `adim24-self-test.ps1` eklendi.
- Tam eser üretimi varsayılan kapalı; açık `EVET` onayı zorunlu.
- ESER-00006 büyük eser otomatik seslendirme engellendi.
- API anahtarı hiçbir logda gösterilmiyor.

## 23.0.0 — Metadata güvenlik ve kurtarma
- OCR sayfa aralığı/cümle parçalarının başlık veya yazar sayılması engellendi.
- Archive.org kanonik başlığı yerel onarımda korunuyor.
- Yerel/OCR onarımı otomatik `HAZIR` yapmıyor; güven 0,67 ile sınırlandırılıyor.
- Yerel metadata onarımı artık arşiv dosyasını yeniden adlandırmıyor.
- Kalıcı `_metadata-gecmisi` yedekleri eklendi.
- Bozulmuş kayıt için `metadata-kurtar.ps1` eklendi.
- JSON, Excel ve dosya adı tek işlemde güvenli biçimde kurtarılıyor.

# Changelog

## 22.0.0

- Bulut API zorunluluğunu kaldıran yerel künye motoru eklendi.
- ISBN-10/13 doğrulama, kolektif yazar, yayınevi, yıl, özgün ad, çevirmen, basım ve hak çıkarımı eklendi.
- Yerel sonuç yeterliyse OpenAI/Gemini çağrısını atlayan maliyet/kota koruması eklendi.
- Tesseract CLI ile çevrimdışı Türkçe OCR eklendi.
- Metin yedeği olmayan taranmış PDF için tam yerel OCR yedeği eklendi.
- Mevcut `KONTROL_GEREKIYOR` kayıtlarını yeniden indirmeden ve TTS üretmeden onarma eklendi.
- Metadata JSON, manifest, bilgi metni, kaynak dosya adı ve Excel satırını birlikte güncelleme eklendi.
- Excel'e Orijinal Adı, Çevirmen ve Basım Bilgisi sütunları eklendi.
- Tek eser ve toplu metadata onarım menüsü/CLI komutları eklendi.
- Adım 17–21 özellikleri korundu.

## 21.0.0

- Archive.org koleksiyon içi alt eser yolu için güçlü dosya eşleştirme eklendi.
- Ana kaynak ve EPUB/OCR TXT metin yedeği birlikte seçilebilir hâle getirildi.
- İndirmeden önce adayları gösteren `inspect-archive` komutu eklendi.
- İlk 3 sayfa/bölümden Gemini/OpenAI metadata çıkarımı tek hatta birleştirildi.
- API anahtarı yokken işlemi durdurmayan kural tabanlı metadata yedeği eklendi.
- ISBN, güven puanı, kanıt ve `KONTROL_GEREKIYOR` durumu eklendi.
- Her eser için kaynak/metadata/ön izleme alt klasörlü düzenli arşiv yapısı eklendi.
- Atomik `eser-katalogu.xlsx` kataloğu ve eski manifest uzlaştırması eklendi.
- URL listesi toplu işleme ve indirme boyutu güvenlik sınırı eklendi.
- Adım 17–20 özellikleri tam olarak korundu.

## 20.0.0

- Windows PowerShell, Maven ve Java için ortak UTF-8 konsol katmanı eklendi.
- Türkçe karakterlerin bozuk görünmesine yol açan kod sayfası ve JVM çıktı uyumsuzluğu giderildi.
- Tüm PowerShell giriş noktaları UTF-8 BOM ile standartlaştırıldı.
- Java ana uygulamalarına doğrudan UTF-8 standart çıktı/hata güvencesi eklendi.

## 19.0.0

- Birleşik kaynak alım orkestratörü eklendi.
- PDF, EPUB, TXT, Markdown ve HTML yerel dosya desteği eklendi.
- Doğrudan URL ve web sayfası alımı eklendi.
- Archive.org otomatik identifier ve dosya çözümleme eklendi.
- Kaynak SHA-256 tekrar engeli eklendi.
- Atomik metin arşivi, alım manifesti ve hata raporu eklendi.
- PDF tam metin çıkarımı ve taranmış PDF/OCR ayrımı eklendi.
- Kaynak alımından sonra TTS parçalaması ve kuyruk senkronizasyonu otomatikleştirildi.
- Adım 18 tekrar üretim koruması korundu.

## 18.0.0

- Mevcut nihai MP3/M4B paketlerini algılayarak yeniden TTS üretimini engelleyen uzlaştırma katmanı eklendi.

## 17.0.0

- Kalıcı üretim kuyruğu, sağlayıcı politikası, maliyet koruması ve MP3/M4B paketleme eklendi.
