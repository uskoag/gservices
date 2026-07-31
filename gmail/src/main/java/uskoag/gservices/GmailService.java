package uskoag.gservices;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class GmailService {

    public static Gmail gmail(OAuthToken oauth) throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        return new Gmail.Builder(
            transport, GsonFactory.getDefaultInstance(),
            oauth.credentials(transport)
        ).setApplicationName(oauth.appName)
        .build();
    }

    /**
     * The wallet-aware route. {@link ServiceAccess} carries the initializer and, when a wallet is
     * brokering, the loopback root URL that makes it the only way out.
     */
    public static Gmail gmail(ServiceAccess access, String appName) throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        var builder = new Gmail.Builder(transport, GsonFactory.getDefaultInstance(), access.initializer())
                .setApplicationName(appName);
        return access.applyTo(builder).build();
    }

    /**
     * Builds a Gmail client from an already-stored credential, <em>without</em> launching the
     * interactive browser flow. Returns {@code null} if no token is stored for this app-key
     * (i.e. the account is not logged in) — useful for probing login state.
     */
    public static Gmail gmailIfAuthorized(OAuthToken oauth) throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        var credential = oauth.loadStoredCredential(transport);
        if (credential == null) return null;
        return new Gmail.Builder(
            transport, GsonFactory.getDefaultInstance(), credential
        ).setApplicationName(oauth.appName)
        .build();
    }
}
