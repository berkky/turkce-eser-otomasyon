# 5 Dakikalık Demo Akışı

**Hazırlık:** `powershell -ExecutionPolicy Bypass -File .\web-panel.ps1`  
**URL:** http://127.0.0.1:8787

---

## 1. `/demo` (45 sn)

*"Bu ana demo sayfası: projenin 7 adımlı üretim timeline'ını ve Adım 32 final kalite özetini gösteriyor. Yerel MVP; gerçek üretim varsayılan kapalı."*

## 2. `/eserler` (30 sn)

*"Katalogdaki örnek eserler: ESER-00005 Kaşağı ve ESER-00006 Astronomi. Her biri metadata, önizleme ve üretim planına bağlı."*

## 3. `/eser/5` (45 sn)

*"Kaşağı detay sayfası: metadata, maliyet planı ve demo kutusu. Önizleme mock veya onaylı kısa gerçek API ile çalışabilir."*

## 4. `/kalite` (30 sn)

*"Ses kalite paneli: sağlayıcı karşılaştırması ve önizleme oynatıcı. Karar vermeden önce kalite/maliyet görünür."*

## 5. `/telaffuz` (25 sn)

*"Telaffuz sözlüğü: Türkçe özel isimler ve normalizasyon notları. TTS öncesi metin düzeltmeleri burada takip edilir."*

## 6. `/alignment` (30 sn)

*"Okuma takibi özeti: ses-metin hizalama, SRT/VTT export linkleri. Demo fixture ile gösterim yapılabilir."*

## 7. `/uretim` (30 sn)

*"Üretim kapısı: plan, onay ve kuyruk durumu. Bilinçli olarak gerçek TTS başlatmaz — maliyet kontrolü sizde."*

## 8. `/eser/6/uretim` (30 sn)

*"Büyük eser risk örneği: Astronomi için yüksek maliyet uyarısı ve BLOCKED kuyruk davranışı."*

---

**Kapanış:** *"Sistem uçtan uca hazır: arşiv, metadata, önizleme, alignment ve üretim planı. Tam üretim ve canlı API bilinçli kilitli."*

Detaylı akış: `FINAL_DEMO_AKISI.md`
