# Adım 35B — Manuel pasaj seçimi ve onayı

Cursor veya uygulama kullanıcı adına seçim/onay yapmaz. Canonical selection:
`PASSAGE-ESER-00005-20260715-183836-DF7B8C`.

1. Tüm canlı sağlayıcı flag'lerini kapatın ve web panelini localhost'ta açın.
2. `/ab-test/pasajlar/ESER-00005` sayfasında üç adayın tam original ve
   normalized metnini, farklarını, hash'lerini, offset/overlap ve skorlarını okuyun.
3. Haklar ve atıf bağlantılarını açın; iki hak katmanını ayrı inceleyin.
4. PASSAGE-1, PASSAGE-2 veya PASSAGE-3'ü kendiniz seçin. PASSAGE-1 primary blind
   test, PASSAGE-3 emotional follow-up önerisidir; öneriler otomatik seçim değildir.
5. Seçim POST'unu kullanıcı başlatsın. CSRF ve submission token tek kullanımlıdır.
6. Onay ekranında şu altı kutuyu kullanıcı işaretlesin:
   - tam metni okudum;
   - original/normalized farklarını kontrol ettim;
   - kaynak ve atıf bilgisini gördüm;
   - temel eser hakkı ile kaynak lisansının ayrı olduğunu anladım;
   - kısa TTS karşılaştırması kullanımını onaylıyorum;
   - bunun ücretli API veya ses üretimini başlatmadığını anladım.
7. Kullanıcı onay POST'unu başlatsın ve `approved-passage.json` içindeki
   `PASSAGE_APPROVED_LIVE_LOCKED` durumunu doğrulasın.
8. `liveGenerationAllowed`, `openAiAllowed`, `xaiAllowed` false; bütçe ve
   sağlayıcı sayısı sıfır olmalıdır.
9. Selection altında MP3/WAV oluşmadığını ve dış API çağrısı yapılmadığını doğrulayın.

Adım 36 ve ayrıca açık canlı üretim onayı olmadan sağlayıcı çağrısı yapılamaz.
