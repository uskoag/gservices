package uskoag.gservices;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class SheetsService {

    public static Sheets sheets(OAuthToken oauth) throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        return new Sheets.Builder(
            transport, GsonFactory.getDefaultInstance(),
            keepPlusInPath(oauth.credentials(transport))
        ).setApplicationName(oauth.appName)
        .build();
    }

    /**
     * Puts back the one piece of URL escaping google-http-client throws away, so a tab named
     * "spark-1+2" can be read at all.
     *
     * <p>A values range travels in the URL path, and the client encodes it twice on the way out.
     * {@code UriTemplate} gets it right — "spark-1+2!A1:C3" becomes "spark-1%2B2%21A1%3AC3" — and
     * then {@code AbstractGoogleClientRequest} wraps that string in a {@code GenericUrl}, whose
     * constructor decodes every path part and whose {@code build()} re-encodes them with an escaper
     * that lists '+' as a safe path character. So "%2B" comes back out as a bare '+', Google's
     * frontend form-decodes it to a space, and the range fails to parse as "spark-1 2!A1:C3". The
     * round trip is not the identity, and '+' is the only character it loses: '/', '%', '#', '?'
     * and space all survive it intact.
     *
     * <p>This runs as the last step of building each request — after the client has assembled the
     * URL, and only when a '+' actually reached the path — and rebuilds it verbatim so nothing
     * re-encodes it a third time. An ordinary range carries no '+' and is left exactly as it was.
     */
    private static HttpRequestInitializer keepPlusInPath(HttpRequestInitializer delegate) {
        return request -> {
            if (delegate != null) delegate.initialize(request);
            var url = request.getUrl();
            if (url == null) return;
            var built = url.build();
            int q = built.indexOf('?');
            var path = (q < 0) ? built : built.substring(0, q);
            if (path.indexOf('+') < 0) return;
            request.setUrl(new GenericUrl(
                path.replace("+", "%2B") + ((q < 0) ? "" : built.substring(q)), true));
        };
    }

    /**
     * The wallet-aware route. {@link ServiceAccess} carries both halves — an initializer that puts the
     * right header on each call, and, when a wallet is brokering, the loopback root URL that makes it
     * the only way out. Callers do not need to know which source answered.
     */
    public static Sheets sheets(ServiceAccess access, String appName) throws GeneralSecurityException, IOException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        var builder = new Sheets.Builder(transport, GsonFactory.getDefaultInstance(),
                        keepPlusInPath(access.initializer()))
                .setApplicationName(appName);
        return access.applyTo(builder).build();
    }
}
