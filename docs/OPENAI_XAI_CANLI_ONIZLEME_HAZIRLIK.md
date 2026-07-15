# OpenAI + xAI Canlı Önizleme Hazırlığı

Bu adım ağ isteği veya ses üretimi yapmaz. Dosyalardaki endpoint değerleri yalnız
Adım 36 için konfigürasyon taslağıdır.

## OpenAI

- Endpoint: `POST https://api.openai.com/v1/audio/speech`
- Model: `gpt-4o-mini-tts`
- Voice adayları: `marin`, `cedar`
- Dil/format: `tr`, `wav`
- Talimatlar kelimeleri değiştirmeyen sıcak, doğal, abartısız Türkçe anlatım içindir.
- Maliyet `ESTIMATED_ONLY` işaretlidir; gerçek token/ses tüketimi iddiası değildir.

## xAI

- Endpoint: `POST https://api.x.ai/v1/tts`
- Voice adayları: `sal`, `lumen`, `ursa`
- `XAI_TTS_VOICE` yalnız taslaktaki seçimi override eder; voice erişimi doğrulanmış sayılmaz.
- Dil/format/hız: `tr`, `wav`, `1.0`
- Speech tag listesi yalnız directed taslağıdır; RAW_BASELINE çağrısında kullanılmaz.
- Maliyet 15 USD / 1.000.000 karakter planlama profiliyle tahmin edilir.

Her iki profilde `liveEnabled=false` ve `userApproved=false` kalır. API key,
Authorization header veya başka secret yazılmaz. Ortak `live-preview-draft.json`
dosyası `LIVE_GENERATION_LOCKED` durumundadır.
