# İş Modeli Notu

## Hedef Kullanıcılar

- Yayınevleri ve dijital içerik ekipleri
- Kütüphane ve arşiv kurumları
- Eğitim/teknoloji girişimleri (sesli içerik üretimi)
- Bireysel geliştiriciler ve araştırmacılar

## Kullanım Senaryoları

1. **Dijital arşiv oluşturma** — Dağınık PDF/EPUB'ları standart ESER-* yapısına dönüştürme
2. **Katalog yönetimi** — Excel + metadata JSON ile merkezi künye takibi
3. **Sesli kitap üretim hazırlığı** — Metin bölme, TTS parçalama, sağlayıcı karşılaştırma
4. **Kalite değerlendirme** — Önizleme, insan puanı, telaffuz notları
5. **Yerel demo/sunum** — Patron veya yatırımcıya 5 dakikalık canlı gösterim

## MVP Değer Önerisi

Mevcut sistem, Türkçe eserler için uçtan uca **yerel** bir üretim hattının çalışır prototipini sunar:

- Kaynak alma → arşivleme → metadata → metin bölme → TTS hazırlık → kalite paneli → web kontrol

Bulut bağımlılığı minimum; hassas veriler localhost'ta kalır.

## Potansiyel Gelir Modelleri

- **Kurumsal lisans** — Yayınevi/kütüphane için özelleştirilmiş kurulum
- **Üretim hizmeti** — Eser başına seslendirme ve paketleme (maliyet + marj)
- **SaaS (gelecek)** — Bulut panel + API (güvenlik ve lisans modülü ile)
- **Danışmanlık** — TTS sağlayıcı seçimi, maliyet optimizasyonu, telaffuz sözlüğü

Abartılı iddia yok: sistem "her kitabı yasal olarak seslendirir" demez.

## Riskler

| Risk | Açıklama |
|------|----------|
| Telif/lisans | Her eser için ayrı hak değerlendirmesi gerekir |
| TTS maliyeti | Büyük eserlerde ElevenLabs kredisi yüksek olabilir |
| Metadata hataları | OCR/yerel çıkarım KONTROL_GEREKIYOR üretebilir |
| Tek kullanıcı | Şu an çok kullanıcılı bulut dağıtım yok |

## Gerekli Sonraki Yatırımlar

1. Canlı ElevenLabs önizleme (onaylı, kredi korumalı)
2. Lisans/hak yönetim modülü
3. Telaffuz sözlüğü ve normalizasyon
4. Bulut dağıtım ve kimlik doğrulama (isteğe bağlı)
5. Tam eser üretim onay akışı ve maliyet tahmini UI

## Uyarı

Bu not bir iş planı taslağıdır; hukuki veya mali tavsiye değildir. Ticari kullanım öncesi telif ve sözleşme danışmanlığı önerilir.
