# Final Kurulum Rehberi

## Gerekli araçlar

| Araç | Sürüm | Zorunlu |
|------|-------|---------|
| Java | 21 | Evet |
| Maven | 3.9+ | Evet |
| PowerShell | 5.1+ | Evet |
| FFmpeg | — | Opsiyonel (ses birleştirme) |
| Git | — | Önerilir |

## Repo klonlama

```powershell
git clone https://github.com/berkky/turkce-eser-otomasyon.git
cd turkce-eser-otomasyon
```

## Derleme

```powershell
mvn -q -DskipTests compile
```

## Web panel başlatma

```powershell
powershell -ExecutionPolicy Bypass -File .\web-panel.ps1
```

Tarayıcı: http://127.0.0.1:8787/demo

## Demo paketi oluşturma

```powershell
powershell -ExecutionPolicy Bypass -File .\patron-demo-paketi.ps1
```

Çıktı: `Desktop\turkce-eser-demo-paketi\`

## Test çalıştırma

```powershell
# Tam regression (Adım 21–32)
powershell -ExecutionPolicy Bypass -File .\adim32-self-test.ps1

# Final release kapısı (önerilen)
powershell -ExecutionPolicy Bypass -File .\final-release-check.ps1

# Secret scan
powershell -ExecutionPolicy Bypass -File .\check-secrets.ps1
```

## API anahtarları (opsiyonel)

Ortam değişkenleri — **asla repoya yazmayın:**

- `ELEVENLABS_API_KEY`
- `ELEVENLABS_VOICE_ID`
- `OPENAI_API_KEY`
- `GEMINI_API_KEY`

Kredi yokken sistem mock mod ve güvenli plan modunda çalışır.

## Sorun giderme

- Maven bulunamıyorsa: `MAVEN_CMD` ortam değişkenini ayarlayın
- Port 8787 meşgulse: başka süreçleri kapatın
- UTF-8 sorunları: `konsol-utf8.ps1` otomatik yüklenir
