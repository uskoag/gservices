package uskoag.gservices;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class CalendarService {

    public static Calendar calendar(OAuthToken oauth) throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        return new Calendar.Builder(
            transport, GsonFactory.getDefaultInstance(),
            oauth.credentials(transport)
        ).setApplicationName(oauth.appName)
        .build();
    }

    /**
     * The wallet-aware route. {@link ServiceAccess} carries the initializer and, when a wallet is
     * brokering, the loopback root URL that makes it the only way out.
     */
    public static Calendar calendar(ServiceAccess access, String appName) throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        var builder = new Calendar.Builder(transport, GsonFactory.getDefaultInstance(), access.initializer())
                .setApplicationName(appName);
        return access.applyTo(builder).build();
    }

    /**
     * Builds a Calendar client from an already-stored credential, <em>without</em> launching the
     * interactive browser flow. Returns {@code null} if no token is stored for this app-key
     * (i.e. the account is not logged in) — useful for probing login state.
     */
    public static Calendar calendarIfAuthorized(OAuthToken oauth) throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        var credential = oauth.loadStoredCredential(transport);
        if (credential == null) return null;
        return new Calendar.Builder(
            transport, GsonFactory.getDefaultInstance(), credential
        ).setApplicationName(oauth.appName)
        .build();
    }
}
