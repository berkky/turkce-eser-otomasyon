# Adım 23 — Metadata Güvenlik ve Kurtarma

## Amaç
OCR ve kural tabanlı künye çıkarımında oluşabilecek hatalı başlık/yazar sonuçlarının arşiv dosyasını, JSON manifestlerini ve Excel kataloğunu bozmasını engellemek.

## Güvenlik ilkeleri
- OCR/yerel künye sonucu otomatik olarak `HAZIR` yapılmaz.
- Bilinen Archive.org kanonik başlığı yerel OCR başlığıyla değiştirilmez.
- Sayfa aralığı, bölüm başlığı, cümle parçası ve yayın açıklaması başlık/yazar olarak reddedilir.
- Yerel onarım kaynak dosyanın adını değiştirmez.
- Her metadata onarım/kurtarma öncesinde kalıcı geçmiş klasörü oluşturulur.
- Manifest, arşiv metadata JSON'u ve Excel kataloğu atomik olarak güncellenir.
- Hata halinde bütün değişiklikler ve dosya adı geri alınır.

## Kurtarma komutu
```powershell
powershell -ExecutionPolicy Bypass -File .\metadata-kurtar.ps1 -EserId 6
```

Kurtarma; Archive.org dosya adından kanonik başlığı geri yükler, şüpheli yazar alanını temizler, dosya adını düzeltir, JSON ve Excel'i eşitler. TTS parçalarına dokunmaz.
