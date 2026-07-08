# Adım 20 — Türkçe Konsol ve UTF-8 Güvence Katmanı

Bu sürüm, Windows PowerShell 5.1, Windows Terminal, Maven ve Java arasındaki kodlama uyuşmazlığını giderir.

## Çözüm

- Tüm PowerShell giriş noktaları `konsol-utf8.ps1` dosyasını yükler.
- Konsol giriş/çıkışı UTF-8 olarak ayarlanır ve Windows kod sayfası 65001'e geçirilir.
- Maven JVM'i UTF-8 dosya, standart çıktı ve standart hata kodlamasıyla başlatılır.
- Java ana uygulamaları `Utf8Konsol` ile standart çıktı ve hatayı doğrudan UTF-8'e bağlar.
- PowerShell dosyaları Windows PowerShell 5.1 uyumluluğu için UTF-8 BOM ile paketlenir.

## Korunan işlevler

Adım 19 kaynak alımı, Adım 18 tekrar üretim koruması ve Adım 17 üretim orkestratörü değiştirilmeden korunur.
