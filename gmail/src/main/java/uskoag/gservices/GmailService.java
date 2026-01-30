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
}
