# Adım 18 — Arşiv Uzlaştırma ve Tekrar Üretim Koruması

Bu sürümün amacı daha önce tamamlanmış sesli eserlerin yeni üretim kuyruğunda yanlışlıkla sıfırdan üretilmesini engellemektir.

## Yeni davranış

- `sync` artık yalnızca metin arşivini değil, karşılık gelen ses arşivini de tarar.
- Güçlü nihai paket sinyali (`.m4b` + tam `.mp3` veya paket manifesti) bulunan eser `TAMAMLANDI` durumuna alınır.
- Nihai dosyaların SHA-256 değerleri ve mevcut TTS kaynak parçalarının birleşik özeti kuyruğa kaydedilir.
- Aynı kaynak özetiyle sonraki çalıştırmalarda yeniden TTS üretimi kesin olarak atlanır.
- Kaynak metin değişirse eski paket otomatik olarak yeni kaynakla eşleştirilmez; iş yeniden doğrulama ister.
- `run` komutu tamamlanmış işlerde ses üretmeden güvenli şekilde döner.

## Komut

```powershell
& "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" -q "-Dexec.mainClass=UretimOrkestratoruApp" "-Dexec.args=reconcile" exec:java
```

Türk Korsanları gibi eski sistemde yalnızca son MP3/M4B dosyaları saklanan eserler bu komutla tamamlanmış olarak bağlanır.
