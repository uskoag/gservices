package uskoag.gservices;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.Docs;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class DocsService {

    public static Docs docs(OAuthToken oauth) throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        return new Docs.Builder(
            transport, GsonFactory.getDefaultInstance(),
            oauth.credentials(transport)
        ).setApplicationName(oauth.appName)
        .build();
    }
}
