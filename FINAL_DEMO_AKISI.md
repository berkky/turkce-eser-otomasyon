# Final Demo Akışı — 5–7 Dakika

## Hazırlık (30 sn)

```powershell
powershell -ExecutionPolicy Bypass -File .\web-panel.ps1
```

Tarayıcı: http://127.0.0.1:8787/demo

## Gösterim sırası

### 1. Patron demo — `/demo` (45 sn)

Değer önerisi, timeline, Adım 28–32 özetleri, **Final Demo Ready** paneli.

### 2. Eser listesi — `/eserler` (30 sn)

ESER-00005 Kaşağı ve ESER-00006 Astronomi.

### 3. Kaşağı detay — `/eser/5` (60 sn)

Metadata, önizleme, maliyet planı, demo kutusu.

### 4. Kalite paneli — `/kalite` (45 sn)

Sağlayıcı karşılaştırması, önizleme oynatıcı.

### 5. Telaffuz — `/telaffuz` (30 sn)

Sözlük tablosu, METIN_NORMALIZE notları.

### 6. Alignment — `/alignment` ve `/eser/5/alignment` (60 sn)

Mock/demo fixture okuma takibi, SRT/VTT linkleri.

### 7. Üretim kapısı — `/uretim` (45 sn)

Plan, onay, kuyruk — **gerçek TTS başlamaz**.

### 8. Büyük eser risk — `/eser/6/uretim` (30 sn)

Yüksek risk uyarısı, BLOCKED kuyruk davranışı.

### 9. Patron demo paketi (30 sn)

```powershell
powershell -ExecutionPolicy Bypass -File .\patron-demo-paketi.ps1
```

`Desktop\turkce-eser-demo-paketi\patron-demo-ozeti.html` açın.

## Kapanış cümlesi

> "Sistem uçtan uca hazır: arşiv, metadata, önizleme, alignment ve üretim planı. Tam üretim ve gerçek API bilinçli olarak kilitli — maliyet ve güvenlik kontrolü sizde."

## DemoFixture (önizleme yoksa)

```powershell
powershell -ExecutionPolicy Bypass -File .\elevenlabs-alignment.ps1 -EserId 5 -Mock -DemoFixture
```
