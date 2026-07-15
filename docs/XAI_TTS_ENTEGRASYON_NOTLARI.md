# xAI TTS Entegrasyon Notları

## Sözleşme

- Endpoint: `POST https://api.x.ai/v1/tts`
- Metin üst sınırı: 15.000 karakter; A/B canlı deney sınırı ayrıca 1.500 karakterden küçüktür.
- Dil varsayılanı: `tr`
- Hız: `1.0`
- Öncelik: WAV 44.1 kHz; alternatif MP3 44.1 kHz / 192 kbps
- Fiyat profili: 15 USD / 1 milyon giriş karakteri

Anahtar yalnız `XAI_API_KEY` ortam değişkeninden okunur. Kod, manifest, hata ve loglarda Authorization
header veya anahtar bulunmaz. `XAI_TTS_LIVE_ENABLED` varsayılan olarak `false` değerindedir.

401, 403, 404, 408, 429, 500 ve 503 tipli hata kodlarına çevrilir. Yalnız 429, 500 ve 503 sınırlı
exponential backoff ile tekrar edilir. Her deneme bütçe rezervasyonuna dahildir. Boş body, 0 byte,
yanlış MIME veya WAV/MP3 başlığı bulunmayan çıktı reddedilir; runtime akışında FFprobe ile oynatılabilirlik
ayrıca doğrulanır.

HTTP transport testte yerel fake `HttpServer` endpoint'ine enjekte edilir; testler gerçek xAI API'sine
gitmez. Aynı request hash için başarılı artifact ve state dosyası varsa ikinci ücretli çağrı yapılmaz.

WebSocket TTS Adım 34 kapsamında uygulanmamıştır. Gelecekte ayrı bir streaming transport sözleşmesi
eklenebilir; unary ve WebSocket yaşam döngüleri aynı sınıfta karıştırılmamalıdır.
