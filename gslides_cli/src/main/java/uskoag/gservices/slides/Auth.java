package uskoag.gservices.slides;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.SlidesScopes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import uskoag.gservices.DriveService;
import uskoag.gservices.OAuthToken;
import uskoag.gservices.SlidesService;

/**
 * Every verb authorises with the SAME union of scopes, deliberately.
 *
 * OAuthToken keys its token directory by md5(appKey) alone and never by scopes, and the
 * Google client library reuses a stored token without re-prompting even after the
 * requested scopes grow. Per-verb scopes would therefore mean the first verb ever run
 * wins, and every wider verb then fails with an opaque 403. One union avoids that.
 */
public final class Auth {

    static final String
            APP_NAME = "GSlidesCli-v1.0",
            DEFAULT_APP_KEY = "uskoag-gslides-cli-key-2026";

    static final Path
            OWN_CREDS = Paths.get(System.getProperty("user.home"), "uskoag", "gservices", "gslides_cli"),
            SHARED_CREDS = Paths.get(System.getProperty("user.home"), "uskoag", "gdrive_gdocs_auth");

    static String appKey = DEFAULT_APP_KEY, email;

    private static Slides slidesSvc;
    private static Drive driveSvc;

    private Auth() {}

    static OAuthToken token() {
        var t = OAuthToken.oauthToken(APP_NAME, appKey,
                        SlidesScopes.PRESENTATIONS, DriveScopes.DRIVE_READONLY, DriveScopes.DRIVE_FILE)
                .allCredsDir(credsDir());
        return email != null ? t.credential(email) : t.defaultCredential();
    }

    static Slides slides() throws Exception {
        if (slidesSvc == null) slidesSvc = SlidesService.slides(token());
        return slidesSvc;
    }

    static Drive drive() throws Exception {
        if (driveSvc == null) driveSvc = DriveService.drive(token());
        return driveSvc;
    }

    /** The export endpoint is plain HTTP with a bearer token, not an API client call. */
    static Credential credential() throws Exception {
        return token().credentials(GoogleNetHttpTransport.newTrustedTransport());
    }

    static void reauth() throws Exception {
        var gone = token().deleteStoredCredential();
        Out.success(gone ? "stored token deleted; next call will re-prompt for consent"
                         : "no stored token for this app-key");
    }

    /**
     * Prefer the per-tool directory, as uskoag-sheetcli and uskoag-gmailcli do, but fall
     * back to the shared one so an account already set up for the other google tools
     * needs no second credentials.json.
     */
    private static Path credsDir() {
        if (usable(OWN_CREDS)) { Out.info("credentials dir: " + OWN_CREDS); return OWN_CREDS; }
        if (usable(SHARED_CREDS)) { Out.info("credentials dir (shared fallback): " + SHARED_CREDS); return SHARED_CREDS; }
        Out.die("No credentials.json found under " + OWN_CREDS + " or " + SHARED_CREDS
                + " -- place a Desktop-app OAuth client at <dir>\\<your-email>\\credentials.json");
        return null;
    }

    private static boolean usable(Path base) {
        if (!Files.isDirectory(base)) return false;
        if (email != null) return Files.exists(base.resolve(email).resolve("credentials.json"));
        try (var s = Files.list(base)) {
            return s.filter(Files::isDirectory).anyMatch(d -> Files.exists(d.resolve("credentials.json")));
        } catch (Exception e) {
            return false;
        }
    }
}
