# Adım 32 — Final Release Kalite Kapısı ve Teslim Hazırlığı

## Amaç

Yeni ürün özelliği eklemeden projeyi teslim edilebilir hale getirmek: tek komut kalite kontrolü, secret scan, final dokümantasyon, demo etiketi.

## Bileşenler

| Dosya | Rol |
|-------|-----|
| `final-release-check.ps1` | Ana kalite kapısı orkestrasyonu |
| `check-secrets.ps1` | API anahtarı taraması |
| `adim32-self-test.ps1` | Adım 21–32 regression |
| `Adim32Dogrulama.java` | Java doğrulamaları |
| `FinalReleaseDurumService.java` | Rapor okuma (web bilgi) |
| `FINAL_*.md` | Teslim dokümantasyonu |

## final-release-check.ps1 akışı

1. Maven compile
2. adim31-self-test.ps1 (tam mod)
3. patron-demo-paketi.ps1
4. elevenlabs-durum.ps1
5. elevenlabs-alignment.ps1 -Mock -DemoFixture
6. tam-eser-plan/onay/kuyruk scriptleri
7. check-secrets.ps1
8. git status

`-TestMode`: regression ve demo scriptleri atlanır (hızlı doğrulama).

## Rapor çıktısı

`Desktop\turkce-eser-final-release\`

- `final-release-report.json`
- `final-release-summary.md`

## Web panel

`/demo` → Adım 32 **Final Demo Ready** bilgi paneli (test çalıştırmaz).

## Adım 33

Teslim ZIP paketi ve gönderim.
