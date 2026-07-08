import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KaynakBilgisi {
    public String kaynak_url;
    public String indirme_url;
    public String lisans;
    public String archive_identifier;
    public String archive_dosya_adi;
    public String archive_item_basligi;

    public KaynakBilgisi() {
    }

    public KaynakBilgisi(String kaynakUrl,
                         String indirmeUrl,
                         String lisans,
                         String archiveIdentifier,
                         String archiveDosyaAdi,
                         String archiveItemBasligi) {
        this.kaynak_url = kaynakUrl;
        this.indirme_url = indirmeUrl;
        this.lisans = lisans;
        this.archive_identifier = archiveIdentifier;
        this.archive_dosya_adi = archiveDosyaAdi;
        this.archive_item_basligi = archiveItemBasligi;
    }

    public boolean varMi() {
        return dolu(kaynak_url) || dolu(indirme_url) || dolu(archive_identifier);
    }

    private static boolean dolu(String deger) {
        return deger != null && !deger.isBlank() && !"null".equalsIgnoreCase(deger.trim());
    }
}
