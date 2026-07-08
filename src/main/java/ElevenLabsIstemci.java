import java.nio.file.Path;
import java.util.List;

/**
 * ElevenLabs API soyutlaması — gerçek istemci ve mock test istemcisi için ortak arayüz.
 */
public interface ElevenLabsIstemci {
    String getModel();

    ElevenLabsClient.AbonelikBilgisi abonelikBilgisiniGetir() throws Exception;

    ElevenLabsClient.ModelKrediBilgisi modelKrediBilgisiniGetir() throws Exception;

    List<ElevenLabsClient.Ses> sesleriListele() throws Exception;

    boolean sesIdDogrula(String voiceId) throws Exception;

    boolean modelDestekleniyorMu(String modelId) throws Exception;

    ElevenLabsClient.SesUretimSonucu sesUret(String metin,
                                             ElevenLabsClient.Ses ses,
                                             Path ciktiDosyasi) throws Exception;

    ElevenLabsClient.SesUretimSonucu sesUret(String metin,
                                             String oncekiMetin,
                                             String sonrakiMetin,
                                             ElevenLabsClient.Ses ses,
                                             Path ciktiDosyasi) throws Exception;
}
