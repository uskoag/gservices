package uskoag.gservices;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class SheetsService {

    public static Sheets sheets(OAuthToken oauth) throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        return new Sheets.Builder(
            transport, GsonFactory.getDefaultInstance(),
            oauth.credentials(transport)
        ).setApplicationName(oauth.appName)
        .build();
    }

    /**
     * The wallet-aware route. {@link ServiceAccess} carries both halves — an initializer that puts the
     * right header on each call, and, when a wallet is brokering, the loopback root URL that makes it
     * the only way out. Callers do not need to know which source answered.
     */
    public static Sheets sheets(ServiceAccess access, String appName) throws GeneralSecurityException, IOException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        var builder = new Sheets.Builder(transport, GsonFactory.getDefaultInstance(), access.initializer())
                .setApplicationName(appName);
        return access.applyTo(builder).build();
    }
}
