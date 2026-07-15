# Adım 35B — Kaşağı kaynak hakkı ve atıf kaydı

Bu kayıt otomatik hukuk kararı veya hukuk danışmanlığı değildir. Amaç, temel
edebî eser hakkı ile kullanılan dijital kaynak sürümünün lisansını birbirine
karıştırmadan kanıtları kullanıcı incelemesine sunmaktır.

## İki ayrı kayıt katmanı

1. Temel eser: **Kaşağı**, yazar **Ömer Seyfettin**, ölüm yılı **1920**.
   Türkiye için 5846 sayılı FSEK'in yaşam + ölümden sonra 70 yıl genel koruma
   süresi dayanağı kaydedilir. Sonuç kodu `PUBLIC_DOMAIN_BASIS_VERIFIED` olup
   yalnız kaydedilmiş dayanağı ifade eder.
2. Kaynak sürümü: Türkçe Vikikaynak Kaşağı sayfası, `oldid=172873`.
   Görünen kaynak lisansı `CC_BY_SA_RECORDED`; atıf ve BenzerPaylaşım bildirimi
   gerekir, ek koşullar uygulanabilir.

`commercialUseReady` kullanıcı hak/atıf paketini görmeden `false` kalır.
Kullanıcı onayında kabul kayda geçse bile canlı TTS kapıları açılmaz.

## Selection içindeki kanıtlar

`rights/` altında temel eser, kaynak lisansı, provenance, insan/makine okunabilir
atıf, inceleme notu ve SHA-256 kanıt listesi tutulur. Dosyalarda secret veya
yerel absolute kullanıcı yolu bulunmaz. Yazımlar UTF-8 ve atomiktir.

Yerel route'lar:

- `/ab-test/pasajlar/ESER-00005/haklar`
- `/api/ab-test/pasajlar/ESER-00005/haklar`
- `/ab-test/pasajlar/ESER-00005/atif`

GET istekleri kayıt değiştirmez ve yalnız localhost üzerinden sunulur.
