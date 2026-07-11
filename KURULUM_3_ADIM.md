# Kurulum — 3 Adım

## 1. Java 21 + Maven hazırla

- **Java 21** JDK kurulu olmalı (`java -version`)
- **Maven 3.9+** PATH'te veya `MAVEN_CMD` ortam değişkeni ile tanımlı
- **PowerShell 5.1+** (Windows)

Opsiyonel: FFmpeg, Tesseract, Piper (ses/OCR yerel test için).

## 2. Repo klasöründe derle ve doğrula

```powershell
cd turkce-eser-otomasyon
mvn -q -DskipTests compile
powershell -ExecutionPolicy Bypass -File .\final-release-check.ps1
```

Başarılı çıktı: `FINAL RELEASE CHECK: BASARILI`

## 3. Web paneli aç

```powershell
powershell -ExecutionPolicy Bypass -File .\web-panel.ps1
```

Tarayıcı: **http://127.0.0.1:8787/demo**

---

**Teslim paketi oluşturmak için (Adım 33):**

```powershell
powershell -ExecutionPolicy Bypass -File .\teslim-paketi-olustur.ps1
powershell -ExecutionPolicy Bypass -File .\teslim-paketi-kontrol.ps1
```

Çıktı: `Desktop\turkce-eser-final-teslim\`

**Güvenlik:** API anahtarlarını repoya veya ZIP'e koymayın. Ortam değişkeni kullanın (`FINAL_KURULUM_REHBERI.md`).
