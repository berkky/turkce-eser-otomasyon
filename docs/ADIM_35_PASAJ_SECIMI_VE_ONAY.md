# Adım 35 — Kaşağı Pasaj Seçimi ve Onay

Başlangıç kapısı: temiz çalışma ağacı, HEAD `47d23dc`
(`Adim 34: Turkce TTS A/B ses laboratuvari ve canonical veri yollari`).

Adım 35 yalnız canonical `%USERPROFILE%\Desktop\ESER` kökünü kullanır. ESER-00005
`tam-metin.txt`, `alim-manifest.json` ve gerçek `tts-parcalari` birlikte doğrulanır.
Kaynak bulunmazsa `SOURCE_TEXT_NOT_FOUND`, bozuksa `SOURCE_TEXT_INVALID`, 7.000
karakter eşiğini geçmezse `SOURCE_CHARACTER_COUNT_INVALID` üretilir.

`KasagiPasajService`, kaynak metni en fazla on deterministik pencereyle inceler ve
900–1.250 karakter arasındaki en yüksek puanlı üç farklı sahneyi seçer. Algoritma
sürümü `KASAGI-PASSAGE-1` olarak manifeste yazılır. Metin LLM ile değiştirilmez.
Original metin aynen saklanır; normalize metinde yalnız satır sonu, boşluk, görünmez
Unicode, HTML/OCR konuşma çizgisi ve tırnak standardizasyonu yapılır.

Çıktı:

`%USERPROFILE%\Desktop\ESER\ses-arsivi\ab-test\passage-selection\ESER-00005\<selectionId>\`

Web route’ları:

- `GET /ab-test/pasajlar`
- `GET /ab-test/pasajlar/ESER-00005`
- `POST /ab-test/pasajlar/ESER-00005/sec`
- `POST /ab-test/pasajlar/ESER-00005/onayla`
- `GET /api/ab-test/pasajlar/ESER-00005`
- `GET /api/ab-test/pasajlar/ESER-00005/durum`

Onay iki ayrı kullanıcı beyanı ister: kaynak/metin doğrulaması ve sağlayıcılara
gönderim hakkı. Sonuç `PASSAGE_APPROVED_LIVE_LOCKED` olur; OpenAI/xAI izinleri,
bütçe ve canlı üretim kapalı kalır.
