package uskoag.gservices;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.slides.v1.Slides;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class SlidesService {

    public static Slides slides(OAuthToken oauth) throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        return new Slides.Builder(
            transport, GsonFactory.getDefaultInstance(),
            oauth.credentials(transport)
        ).setApplicationName(oauth.appName)
        .build();
    }

    /**
     * The wallet-aware route, the same shape as {@link DriveService#drive(ServiceAccess, String)}.
     *
     * <p>{@link ServiceAccess} carries both halves — an initializer that puts the right header on each
     * call, and, when a wallet is brokering, the loopback root URL that makes it the only way out.
     * Callers do not need to know which source answered.
     */
    public static Slides slides(ServiceAccess access, String appName)
            throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        var builder = new Slides.Builder(transport, GsonFactory.getDefaultInstance(), access.initializer())
                .setApplicationName(appName);
        return access.applyTo(builder).build();
    }
}
