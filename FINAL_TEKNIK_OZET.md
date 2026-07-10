# Final Teknik Özet

## Sistem ne yapıyor?

Türkçe eserleri PDF, EPUB ve web kaynaklarından alıp arşivleyen, metadata çıkaran, metni TTS parçalarına bölen, ses kalitesini değerlendiren ve sesli kitap üretimine hazırlayan **yerel** bir üretim hattı.

## Mimari

- **Java 21 + Maven** — iş mantığı ve servisler
- **PowerShell** — CLI scriptleri ve test orkestrasyonu
- **HttpServer** — localhost web paneli (port 8787)
- **Dosya tabanlı depolama** — arşiv, kuyruk, metadata JSON

## Veri akışı

```
Kaynak → Arşiv → Metadata → Metin bölme → TTS parçaları
    → Kuyruk → Önizleme → Kalite paneli → Alignment → Üretim planı
```

## Güvenlik kapıları

| Kapı | Davranış |
|------|----------|
| Tam üretim | Varsayılan kapalı |
| Web TTS/API | Başlatılamaz |
| Büyük eser (00006) | Yüksek risk / BLOCKED |
| POST işlemleri | Nonce + localhost |
| Media | 0 byte reddi, path traversal koruması |

## Mock / gerçek API ayrımı

- **Mock:** Test ve demo — API çağrısı yok
- **Gerçek API:** Yalnızca `-GercekApiOnayli` veya `-Onayli` CLI bayraklarıyla
- Web panel **asla** gerçek API çağırmaz

## Neden tam üretim kapalı?

- Maliyet kontrolü (ElevenLabs kredi)
- Büyük eser riski
- Açık onay ve manuel çalıştırma zorunluluğu

## Tamamlanan (Adım 1–32)

Kaynak alma, metadata güvenlik, TTS altyapısı, web MVP, patron demo, önizleme, telaffuz, alignment, üretim planı/kuyruk, final kalite kapısı.

## Sonraya bırakılan

- Canlı tam eser TTS pilotu (onaylı CLI)
- Bulut dağıtım
- Lisans/hak yönetim modülü
- Teslim ZIP (Adım 33)
