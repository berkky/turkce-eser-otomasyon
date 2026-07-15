# A/B Test Güvenlik ve Maliyet

## Zorunlu kapılar

Canlı xAI çağrısı ancak bütün koşullar aynı anda sağlanırsa yapılır:

- `XAI_TTS_LIVE_ENABLED=true`
- `--GercekApiOnayli` ve iki yazılı CLI onayı
- Eser tam olarak `ESER-00005`
- Onaylı kaynak SHA-256 ve 1.500 karakterden kısa metin
- `dryRun=false`
- Tek seçilmiş voice
- API anahtarı ve kalan deney bütçesi

Varsayılan toplam bütçe 1 USD'dir. xAI, Google ve ElevenLabs karakter fiyat profilleri; Cartesia kredi
profili; token bazlı sağlayıcılar için “tahmini” fiyat türü kullanılır. Retry denemeleri de çağrıdan önce
rezerve edilir. Eksik sağlayıcı `NOT_CONFIGURED` olur; uygulama çökmez ve başka ücretli sağlayıcıya
kendiliğinden geçmez.

## Gizlilik

API anahtarları yalnız ortam değişkenlerindedir. Manifest modeli key/header alanı içermez.
`provider-mapping.private.json` public paket allowlist'inde değildir; final teslim scripti ayrıca
`*.private.json` dosyalarını dışlar. Kör MP3'lerde `-map_metadata -1` ile ID3/metadata temizlenir.
Regression ve final doğrulama zinciri `ELEVENLABS_OFFLINE=true` kullanarak abonelik/voice durum
API'lerine de çıkmadan çalışır; bu bayrak gerçek üretim yapılandırmasının yerine geçmez.

## Tam kitap geçiş kapıları

Tam kitap üretimi bu adımda yoktur. Geçiş için lisansın yazılı doğrulanması, birden fazla kör testte
önceden belirlenmiş kalite eşiği, uzun dinleme yoruculuğu kontrolü, telaffuz hata bütçesi, maliyet
onayı, retry/hata oranı ve manuel örnekleme tamamlanmalıdır. ESER-00006 hiçbir kısa deney veya pilot
gerekçesiyle canlı sağlayıcıya gönderilmez.
