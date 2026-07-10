# Final Güvenlik Notları

## API anahtarı saklama politikası

- Anahtarlar **yalnızca ortam değişkenlerinde** tutulur
- Repoya, loglara, JSON API yanıtlarına ve web UI'a **yazılmaz**
- `WebGuvenlikService.gizliDegerleriTemizle` filtresi aktif
- `check-secrets.ps1` ile düzenli tarama

## Varsayılan kapalı kapılar

| Alan | Durum |
|------|-------|
| Tam eser TTS | Kapalı |
| Web panel API | Kapalı |
| ESER-00006 üretim | Engelli / BLOCKED |
| Otomatik kuyruk üretimi | Yok |

## Gerçek API çağrıları

Yalnızca açık onaylı komutlarla:

- `elevenlabs-onizleme.ps1` (ESER-00005, kısa önizleme)
- `elevenlabs-alignment.ps1 -GercekApiOnayli`
- Tam eser üretimi: **henüz yok** (Adım 32)

## Path traversal koruması

- `WebGuvenlikService.guvenliAltDosya` — tüm dosya erişimleri
- API yanıtlarında tam dosya yolu yok (`yolMaskele`)

## 0 byte media koruması

- `/media/preview/` — minimum MP3 boyutu kontrolü
- Geçersiz dosyalar 404 döner

## Localhost kısıtı

- Web sunucusu `127.0.0.1:8787` — dış erişim reddedilir
- `WebGuvenlikService.localhostZorunlu`

## Secret scan

```powershell
powershell -ExecutionPolicy Bypass -File .\check-secrets.ps1
```

Taranan formatlar: `sk-...`, `AIza...`, `*_API_KEY=` gerçek değerler.

False positive: `dummy-test-api-key-not-real`, test fixture anahtarları.

## Telif ve veri

- ESER dosyaları yerel arşivde kalır
- Demo verileri gerçek kullanıcı verisi değildir
- Ticari kullanım öncesi lisans danışmanlığı önerilir
