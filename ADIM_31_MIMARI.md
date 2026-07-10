# Adım 31 — Onaylı Tam Eser Üretim Kapısı, Maliyet Planı ve Güvenli Kuyruk

## Tam eser üretim kapısı nedir?

Adım 31, tam eser TTS üretimini **planlama + onay + kuyruk kaydı** aşamasına taşır. Sistem maliyet/kredi hesabı yapar, risk seviyesi belirler ve kullanıcı açıkça onay verirse güvenli kuyruk JSON'u yazar. **Gerçek ses üretimi bu adımda başlamaz.**

## Neden otomatik üretim yok?

- Büyük eserlerde (ESER-00006) maliyet ve kalite riski yüksek.
- ElevenLabs kredisi sınırlı; yanlışlıkla tam üretim pahalı olabilir.
- Web panel tek tıkla üretim başlatmamalı — yalnızca plan/onay/kuyruk.
- Adım 21–30 altyapısı (önizleme, alignment, kalite) korunur.

## Maliyet planı nasıl çalışır?

`TamEserUretimPlanService` mevcut `TtsMaliyetPlanService` ve eser metin parçalarını kullanır:

- `toplamKarakter`, `ttsParcaSayisi`, `tahminiDakika`
- `kalanKredi`, `tahminiKrediIhtiyaci`, `krediYeterliMi`
- `riskSeviyesi`: DUSUK / ORTA / YUKSEK / ENGELLI
- `tamUretimVarsayilanKapaliMi`: her zaman `true`

Dosya: `{kuyruk}/production-approvals/ESER-00005-production-plan.json`

## Onay taslağı ne işe yarar?

Kullanıcı maliyet planını gördükten sonra onay taslağı oluşturur. Bu kayıt **yalnızca niyet belgesidir** — `tamUretimBaslatildiMi` her zaman `false` kalır.

Dosya: `ESER-00005-production-approval-draft.json`

## Kuyruğa almak ne demek?

Onaylı iş, `production-approvals` altında kuyruk JSON'una yazılır. Mevcut `UretimKuyruguService` (is.properties) formatı **bozulmaz** — ayrı alt sistem.

## Kuyruğa alınca üretim neden başlamaz?

Adım 31 bilinçli olarak `ElevenLabsTamEserService` veya orkestrasyon TTS çağrısı yapmaz. Durum:

- ESER-00005: `READY_FOR_MANUAL_RUN`
- ESER-00006: `BLOCKED` (yüksek risk)

## ESER-00006 neden yüksek risk?

237+ TTS parça, yüksek kredi ihtiyacı, uzun üretim süresi. Plan gösterilir; onay ve kuyruk kaydı oluşturulabilir ancak gerçek üretim kapısı engellidir.

## Java sınıfları

| Sınıf | Rol |
|-------|-----|
| `TamEserUretimPlani` | Plan modeli |
| `TamEserUretimParcasi` | Parça özeti (safeName) |
| `TamEserUretimOnayi` | Onay taslağı |
| `TamEserUretimKuyrukKaydi` | Kuyruk kaydı |
| `TamEserUretimRisk` | Risk enum |
| `TamEserUretimPlanService` | Plan üretimi |
| `TamEserUretimOnayService` | Onay taslağı |
| `TamEserUretimKuyrukService` | Kuyruk (TTS yok) |
| `TamEserUretimGuvenlikService` | JSON/path güvenliği |
| `TamEserUretimStorageService` | .partial + rename |

## Komutlar

```powershell
powershell -ExecutionPolicy Bypass -File .\tam-eser-plan.ps1 -EserId 5
powershell -ExecutionPolicy Bypass -File .\tam-eser-onay-taslagi.ps1 -EserId 5
powershell -ExecutionPolicy Bypass -File .\tam-eser-kuyruga-al.ps1 -EserId 5 -Onayli
powershell -ExecutionPolicy Bypass -File .\adim31-self-test.ps1
```

## Web panel

- `GET /uretim` — genel kapı paneli
- `GET /eser/{id}/uretim` — eser planı
- `GET /api/uretim-plan/{id}` — güvenli JSON
- `GET /api/uretim-kuyruk` — kuyruk listesi
- `POST /islemler` — `tam-eser-plan`, `tam-eser-onay-taslagi`, `tam-eser-kuyruga-al`

## Güvenlik

- API anahtarı UI/JSON/log'da yok
- Full path API yanıtında yok
- Stack trace web'de yok
- POST işlemleri nonce + localhost
- Gerçek TTS başlatan endpoint yok

## Adım 32'de ne yapılacak?

- Onaylı kuyruk kaydından **manuel CLI** ile sınırlı tam eser TTS pilotu (ESER-00005)
- ESER-00006 için ek maliyet onayı ve parça limiti
- Üretim ilerleme izleme ve idempotent yeniden deneme
- Kuyruk durumundan `APPROVED_NOT_STARTED` → çalışan işe geçiş (yine web'den tek tık yok)
