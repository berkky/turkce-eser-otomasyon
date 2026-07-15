# Adım 36B — Manuel canlı üretim (ücretli çağrı; yalnız bilerek çalıştırın)

İki provider tek komutta çalışmaz. Önce xAI bitmeden OpenAI’ye geçmeyin.

API key değerini dokümana, loga veya komut satırına yazmayın. Yalnız environment variable kullanın.

## xAI akışı

1. `XAI_API_KEY` environment variable’ını güvenli biçimde tanımlayın (değerini yazdırmayın).
2. Onay oluştur (ağ yok):

```powershell
powershell -ExecutionPolicy Bypass -File .\adim36-live-approve.ps1 `
  -Provider xai `
  -SelectionId PASSAGE-ESER-00005-20260715-183836-DF7B8C `
  -Voice lumen `
  -MaxBudgetUsd 0.05 `
  -Confirmation CANLI_XAI_TTS_ONAYLI `
  -MaxRequestCount 1
```

3. Preflight (ağ yok):

```powershell
powershell -ExecutionPolicy Bypass -File .\adim36-live-preflight.ps1 `
  -Provider xai `
  -SelectionId PASSAGE-ESER-00005-20260715-183836-DF7B8C `
  -Voice lumen `
  -MaxBudgetUsd 0.05
```

`PREFLIGHT_READY` görünmeden generate çalıştırmayın.

4. Canlı üretim (tek provider, tek request):

```powershell
powershell -ExecutionPolicy Bypass -File .\adim36-live-generate.ps1 `
  -Provider xai `
  -SelectionId PASSAGE-ESER-00005-20260715-183836-DF7B8C `
  -Voice lumen `
  -MaxBudgetUsd 0.05 `
  -Confirmation CANLI_XAI_TTS_ONAYLI `
  -Live -NoRetry
```

5. Raw: `raw/xai-lumen.wav` ffprobe + SHA-256 doğrulayın.
6. Normalized: `normalized/xAI-Grok-TTS.mp3` (44100 Hz mono 192 kbps / -16 LUFS) dinleyin.
7. Manuel dinleme tamamlanmadan OpenAI adımına geçmeyin.
8. xAI sonucu başarılı olmadan OpenAI’ye geçmeyin.

## OpenAI akışı

1. `OPENAI_API_KEY` environment variable.
2. `adim36-live-approve.ps1 -Provider openai -Voice marin -MaxBudgetUsd 0.20 -Confirmation CANLI_OPENAI_TTS_ONAYLI ...`
3. `adim36-live-preflight.ps1 -Provider openai ...`
4. `adim36-live-generate.ps1 -Provider openai ... -Live -NoRetry`
5. Raw `raw/openai-marin.wav` ffprobe/hash
6. Normalized `normalized/OpenAI-gpt-4o-mini-tts.mp3`
7. Manuel dinleme
8. Üçüncü ses (Piper) ve Ahmet Bey paket üretimi ayrı adımda yapılır.

Generate script approval oluşturmaz; yalnız önceden oluşturulmuş aktif approval kullanır.
