package uskoag.gservices;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class DriveService {

    public static Drive drive(OAuthToken oauth) throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        return new Drive.Builder(
            transport, GsonFactory.getDefaultInstance(),
            oauth.credentials(transport)
        ).setApplicationName(oauth.appName)
        .build();
    }

    /**
     * The wallet-aware route. {@link ServiceAccess} carries both halves — an initializer that puts the
     * right header on each call, and, when a wallet is brokering, the loopback root URL that makes it the
     * only way out. Callers do not need to know which source answered.
     *
     * <p>This is the one that lets gdrivecli stop holding tokens, and with them the read-only/read-write
     * app-key split: that split exists only because the caller holds the credential and therefore has to
     * hold a deliberately weak one. When the broker classifies each request where the request actually is,
     * one client serves every tier and the narrowing happens per call instead of per key.
     */
    public static Drive drive(ServiceAccess access, String appName)
            throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        var builder = new Drive.Builder(transport, GsonFactory.getDefaultInstance(), access.initializer())
                .setApplicationName(appName);
        return access.applyTo(builder).build();
    }
}
