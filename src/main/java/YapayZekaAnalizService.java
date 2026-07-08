public final class YapayZekaAnalizService {
    private final GeminiClient gemini;
    private final OpenAIClient openAI;

    private boolean geminiDevreDisi;
    private boolean openAiDevreDisi;

    public YapayZekaAnalizService(GeminiClient gemini, OpenAIClient openAI) {
        this.gemini = gemini;
        this.openAI = openAI;
    }

    public boolean kullanilabilirServisVarMi() {
        return (gemini != null && !geminiDevreDisi) ||
                (openAI != null && !openAiDevreDisi);
    }

    public AnalizSonucu analizEt(PdfHazirlayici.PdfVerisi pdfVerisi) throws Exception {
        String geminiHatasi = null;

        if (gemini != null && !geminiDevreDisi) {
            try {
                EserBilgisi bilgi = gemini.analizEt(pdfVerisi);
                return new AnalizSonucu(bilgi, "Gemini", gemini.getModel());

            } catch (GeminiClient.KotaDolduException e) {
                geminiDevreDisi = true;
                geminiHatasi = e.getMessage();
                System.err.println("Gemini kotası doldu. OpenAI yedeğine geçiliyor...");

            } catch (GeminiClient.GeciciKullanilamazException e) {
                geminiDevreDisi = true;
                geminiHatasi = e.getMessage();
                System.err.println("Gemini uzun süre kullanılamadı. OpenAI yedeğine geçiliyor...");

            } catch (Exception e) {
                geminiDevreDisi = true;
                geminiHatasi = e.getMessage();
                System.err.println("Gemini isteği başarısız oldu. OpenAI yedeğine geçiliyor...");
                System.err.println("Gemini hatası: " + e.getMessage());
            }
        }

        if (openAI != null && !openAiDevreDisi) {
            try {
                EserBilgisi bilgi = openAI.analizEt(pdfVerisi);
                return new AnalizSonucu(bilgi, "OpenAI", openAI.getModel());

            } catch (OpenAIClient.KotaDolduException e) {
                openAiDevreDisi = true;
                throw tumServislerHatasi(geminiHatasi, e.getMessage());

            } catch (OpenAIClient.GeciciKullanilamazException e) {
                openAiDevreDisi = true;
                throw tumServislerHatasi(geminiHatasi, e.getMessage());

            } catch (Exception e) {
                openAiDevreDisi = true;
                throw tumServislerHatasi(geminiHatasi, e.getMessage());
            }
        }

        if (geminiHatasi != null) {
            throw new TumServislerKullanilamazException(
                    "Gemini başarısız oldu ve kullanılabilir OpenAI yedeği bulunamadı. Gemini: " + geminiHatasi
            );
        }

        throw new TumServislerKullanilamazException(
                "Kullanılabilir yapay zekâ servisi yok. GEMINI_API_KEY veya OPENAI_API_KEY tanımlanmalı."
        );
    }

    private TumServislerKullanilamazException tumServislerHatasi(
            String geminiHatasi,
            String openAiHatasi
    ) {
        String mesaj = "OpenAI: " + guvenliMesaj(openAiHatasi);

        if (geminiHatasi != null && !geminiHatasi.isBlank()) {
            mesaj = "Gemini: " + geminiHatasi + " | " + mesaj;
        }

        return new TumServislerKullanilamazException(mesaj);
    }

    private String guvenliMesaj(String mesaj) {
        return mesaj == null || mesaj.isBlank() ? "Bilinmeyen hata" : mesaj;
    }

    public static final class AnalizSonucu {
        private final EserBilgisi eserBilgisi;
        private final String saglayici;
        private final String model;

        public AnalizSonucu(EserBilgisi eserBilgisi, String saglayici, String model) {
            this.eserBilgisi = eserBilgisi;
            this.saglayici = saglayici;
            this.model = model;
        }

        public EserBilgisi getEserBilgisi() {
            return eserBilgisi;
        }

        public String getSaglayici() {
            return saglayici;
        }

        public String getModel() {
            return model;
        }
    }

    public static class TumServislerKullanilamazException extends Exception {
        public TumServislerKullanilamazException(String mesaj) {
            super(mesaj);
        }
    }
}
