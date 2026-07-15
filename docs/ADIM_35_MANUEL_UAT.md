# Adım 35 Manuel UAT

1. Tüm live flag’lerini kapatın ve `ELEVENLABS_OFFLINE=true` ayarlayın.
2. `mvn -q -Padim35PasajAdaylari exec:java` ile aday paketini oluşturun.
3. `web-panel.ps1` ile localhost panelini açın.
4. `/ab-test/pasajlar/ESER-00005` sayfasına gidin.
5. Üç adayın original/normalize metinlerini ve mekanik değişikliklerini karşılaştırın.
6. Karakter, kelime, süre ve kriter puanlarını inceleyin.
7. Bir pasaj seçip **Pasajı seç** düğmesine basın.
8. `REVIEW_REQUIRED` lisans uyarısını okuyun.
9. Kaynak/metin ve kullanım hakkı kutularını yalnız gerçekten doğrulayabiliyorsanız işaretleyin.
10. Hazırlık onayını oluşturun.
11. `approved-passage.json` durumunun `PASSAGE_APPROVED_LIVE_LOCKED` olduğunu doğrulayın.
12. OpenAI ve xAI profillerinde `liveEnabled=false`, ortak taslakta
    `liveGenerationApproved=false` olduğunu doğrulayın.
13. Seçim klasöründe MP3/WAV oluşmadığını kontrol edin.
14. Sağlayıcı paneli veya abonelik endpoint’ine ağ isteği yapılmadığını doğrulayın.

Onay kutuları lisans danışmanlığı değildir. Emin değilseniz onay vermeyin; kaynak
`REVIEW_REQUIRED` olarak kalmalıdır.
