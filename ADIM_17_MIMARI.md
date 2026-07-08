# Adım 17 Teknik Mimari

## Durum makinesi

`BEKLIYOR → HAZIR → URETILIYOR → SES_TAMAM → TAMAMLANDI`

Hata durumları:

- `DURAKLATILDI`: güvenli şekilde devam edilebilir; mevcut parçalar korunur.
- `HATALI`: yapılandırma veya kritik sistem hatası vardır.

## Ana bileşenler

- `UretimOrkestratoruApp`: etkileşimli ve komut satırı giriş noktası
- `UretimKuyruguService`: keşif, kalıcı iş durumu, atomik kayıt ve olay günlüğü
- `UretimOrkestratoruService`: planlama, limit, retry, resume ve parça üretimi
- `UretimSaglayiciSecici`: ana/yedek sağlayıcı politikası
- `UretimMaliyetKoruyucu`: bulut karakter güvenlik tavanı
- `UretimPaketlemeService`: bölüm MP3, tam MP3 ve M4B
- `UretimDosyaYardimci`: atomik yazma ve SHA-256

## Tutarlılık kuralları

1. Bir parça ancak MP3 + metadata birlikte varsa hazır kabul edilir.
2. Metin hash'i, sağlayıcı kimliği, model ve ses eşleşmelidir.
3. Bir işte üretim başladıktan sonra otomatik olarak farklı sese geçilmez.
4. Kaynak metin değişince kuyruk işi yeniden `HAZIR` olur; değişmeyen parçalar hash kontrolünden geçer.
5. Paketleme yalnız bütün parçalar hazırsa çalışır.
6. Her iş için tek süreç kilidi kullanılır.

## İş dosyaları

- `is.properties`: programın okuduğu otoriter durum
- `is.json`: insan ve harici sistemler için okunabilir ayna
- `olaylar.log`: eklemeli denetim günlüğü
- `uretim.lock`: eşzamanlı çalışma kilidi

## Gelecek uyumluluğu

Kuyruk ve manifest yapısı, sonraki Spring Boot/PostgreSQL sürümünde tablo ve olay kayıtlarına taşınabilecek şekilde açık alanlardan oluşur. Dosya sistemi sürümü, tek bilgisayarlı güvenilir üretim için referans uygulamadır.
