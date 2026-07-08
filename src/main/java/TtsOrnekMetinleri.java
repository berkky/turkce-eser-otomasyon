import java.util.List;

public final class TtsOrnekMetinleri {
    private TtsOrnekMetinleri() {
    }

    public static List<TtsUretimIstegi> tumu() {
        return List.of(
                new TtsUretimIstegi(
                        "01-edebi-anlatim",
                        "Edebi anlatım",
                        "Sabahın ilk ışıkları, kıyıdaki eski taş evlerin pencerelerine sessizce dokunuyordu. "
                                + "Denizden yükselen tuz kokusu dar sokaklara yayılırken, yıllardır açılmayan ahşap kapının önünde küçük bir zarf duruyordu. "
                                + "Zarfın üzerinde yalnızca tek bir cümle vardı: Geçmiş, hatırlanmayı bekler.",
                        "Warm, intimate audiobook narration; measured pace, subtle emotion, no theatrical exaggeration."
                ),
                new TtsUretimIstegi(
                        "02-haber",
                        "Haber metni",
                        "Ankara'da bugün düzenlenen teknoloji zirvesinde, yapay zekâ destekli erişilebilirlik araçları ele alındı. "
                                + "Uzmanlar, sesli içeriklerin görme engelli kullanıcılar için daha kolay ulaşılabilir hâle getirilmesi gerektiğini vurguladı. "
                                + "Etkinlik yarın yapılacak oturumlarla devam edecek.",
                        "Neutral, credible Turkish newsreader; clear diction, steady pace, restrained emotion."
                ),
                new TtsUretimIstegi(
                        "03-blog",
                        "Blog yazısı",
                        "Geçen hafta telefonumu bir kenara bırakıp iki saat boyunca yalnızca yürüdüm. Başta biraz garip geldi; çünkü elim sürekli cebime gidiyordu. "
                                + "Fakat yarım saat sonra çevredeki sesleri daha çok fark etmeye başladım. Bazen zihni dinlendirmek için büyük planlara değil, küçük bir molaya ihtiyaç var.",
                        "Friendly and conversational Turkish podcast/blog voice; natural pauses, sincere tone."
                ),
                new TtsUretimIstegi(
                        "04-sosyal-medya",
                        "Kısa sosyal medya metni",
                        "Bugün küçük ama önemli bir şey öğrendim: Hızlı olmak her zaman ilerlemek demek değil. Bazen durup yönünü kontrol etmek gerekiyor. "
                                + "Siz bugün kendiniz için ne yaptınız? #günlüknot #motivasyon",
                        "Short-form social media delivery; lively, authentic, not like an advertisement."
                ),
                new TtsUretimIstegi(
                        "05-telaffuz-stres",
                        "Türkçe telaffuz ve sayı testi",
                        "TÜBİTAK, TBMM ve İTÜ temsilcileri 29 Ekim 2026 Perşembe günü saat 14.30'da bir araya gelecek. "
                                + "Toplantıda yüzde 18,75 büyüme, 1 milyon 250 bin kullanıcı ve 3,5 milyar liralık yatırım konuşulacak. "
                                + "Çağrı merkezi numarası sıfır üç yüz on iki, dört yüz kırk dört, yirmi üç, yirmi üçtür.",
                        "Very clear standard Türkiye Turkish. Pronounce abbreviations, dates, decimals, percentages and phone numbers naturally."
                )
        );
    }
}
