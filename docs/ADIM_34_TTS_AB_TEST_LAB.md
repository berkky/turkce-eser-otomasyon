# Adım 34 — Türkçe TTS A/B Test Laboratuvarı

Adım 34, yalnızca ESER-00005 Kaşağı'dan onaylanmış 900–1.200 karakterlik tek bir metni
sağlayıcılar arasında karşılaştırır. Tam kitap, ESER-00006 ve üretim kuyruğu bu akışa bağlı değildir.

## Mimari

- Java 21 kayıt modelleri deney, aday, kaynak, üretim, ses metriği, değerlendirme ve manifesti temsil eder.
- `TtsAbService` kaynak seçimi, klasör oluşturma, manifest, maliyet raporu ve kör paketlemeyi yönetir.
- `XaiTtsSaglayici` mock-varsayılan HTTP adaptörüdür; request hash, retry, bütçe ve canlı kapıları içerir.
- `TtsAbAudioService` ile `tts-ab-normalize.ps1`, FFprobe doğrulaması ve ortak FFmpeg loudness akışını çalıştırır.
- `TtsAbWebService`, localhost panelinde `/ab-test` rotalarını sunar.

## Deney modları

- `RAW_BASELINE`: Aynı metin, hız 1.0, sağlayıcıya özel duygu etiketi yok.
- `DIRECTED_COMMERCIAL`: Kelimeler değişmeden style/instruction/speech tag kullanılabilir ve manifestte kayıtlıdır.
- `EDITORIAL_ADAPTATION`: Geleceğe ayrılmıştır; Adım 34'te çalıştırılamaz.

Canonical çıktı kökü her zaman
`%USERPROFILE%\Desktop\ESER\ses-arsivi\ab-test\<experimentId>\` olur.
Legacy Desktop veri kökleri yalnız `LEGACY_DATA_ROOT_DETECTED` uyarısıyla raporlanır;
otomatik fallback veya migration yapılmaz.
`provider-mapping.private.json` yalnız deney yöneticisi içindir ve public paket servisi tarafından dışlanır.

Kaynak türü manifestte `FIXTURE` veya `APPROVED_ARCHIVE_TEXT` olarak açıkça yer alır.
Fixture metni ve sentetik mock sesler kalite kanıtı değildir. Canlı çağrı yalnız kullanıcı onaylı,
hash'i dosyayla eşleşen ve placeholder olmayan lisans notuna sahip arşiv metninde açılabilir.
