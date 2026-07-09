# Build Notes — Adım 26

- Java: 21
- Maven: 3.9.16
- Proje sürümü: 26.0.0
- Kaynak kodlama: UTF-8
- Ana doğrulama: `adim26-self-test.ps1`
- Web panel: `web-panel.ps1` → http://127.0.0.1:8787
- Panel çıktısı (statik): `Desktop\ses-arsivi_kalite-panel\`
- Tam proje paketi; patch değildir.

PowerShell self-test scriptleri UTF-8 BOM ve `Write-Utf8Line` kullanır. Java için `JAVA_TOOL_OPTIONS` ve `MAVEN_OPTS` UTF-8 ayarları `konsol-utf8.ps1` içinde tanımlıdır.

Web sunucusu `com.sun.net.httpserver.HttpServer` kullanır; Spring Boot eklenmedi.
