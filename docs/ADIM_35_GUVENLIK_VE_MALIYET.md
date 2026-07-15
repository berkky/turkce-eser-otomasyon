# Adım 35 Güvenlik ve Maliyet

## Güvenlik kapıları

- Yalnız ESER-00005 ve canonical kaynak kabul edilir.
- Fixture kaynak hazırlık onayında reddedilir.
- Localhost dışı web erişimi 403 döner.
- POST işlemleri tek kullanımlık, 15 dakikalık işlem tokenı ve benzersiz submission ID ister.
- Seçim, kaynak/metin onayı ve hak beyanı olmadan approval oluşturulmaz.
- Kaynak/original/normalize SHA-256 yeniden doğrulanır.
- Atomic write ve 0-byte kontrolü uygulanır.
- Public JSON’da mutlak kullanıcı yolu veya secret bulunmaz.
- Legacy köklere yazılmaz.

## Maliyet

xAI tahmini, pasajın gerçek karakter sayısı üzerinden 15 USD / 1.000.000 karakter
profilidir. OpenAI değeri yalnız belirsizlikli planlama tahminidir ve
`ESTIMATED_ONLY` olarak işaretlenir; actual cost değildir. Fiyat tarihi ve kaynak
notu profile yazılır.

Toplam tahmin 1 USD’yi aşarsa durum `BUDGET_REVIEW_REQUIRED` olur. Aşmasa bile
`maxAllowedCostUsd=0`, `budgetApproved=false` ve `liveGenerationApproved=false`
kalır. Adım 35 hiçbir ödeme veya API çağrısı başlatamaz.

Self-test; ElevenLabs offline, tüm live flag’leri false yapar ve Adım 21–34
regresyonunu aynı kapılarla çalıştırır.
