package uskoag.gservices.slides;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.SlidesScopes;
import java.util.List;
import uskoag.gservices.AccessSpec;
import uskoag.gservices.Credentials;
import uskoag.gservices.DriveService;
import uskoag.gservices.ServiceAccess;
import uskoag.gservices.SlidesService;

/**
 * Where this tool gets its clients, and it no longer gets a credential with them.
 *
 * <p>What was here before was the whole problem in one class: a hardcoded {@code DEFAULT_APP_KEY}
 * compiled into the jar, which meant the AES passphrase for the token store shipped with the thing it
 * protected. Anyone holding the binary held the key. It also never consulted the credential seam at
 * all, so installing a wallet changed nothing for gslides while every other tool moved across.
 *
 * <p>Three separate requests are made rather than one, deliberately, and for the same reason the broker
 * exists. Editing a deck, rendering it and uploading an image want three different scopes, and asking
 * per API lets the narrowest token that covers each one serve it — an export cannot be served by a
 * token that could rewrite the deck it is rendering. The old union asked for everything once and then
 * used it for everything.
 *
 * <p>Each is resolved lazily and cached, so a command that only edits never asks for the others.
 */
public final class Auth {

    static final String APP_NAME = "uskoag-gslides";

    /** Compiled-in credential name. Not a secret, and it unlocks nothing — unlike what it replaced. */
    static final String PROFILE = "gslides";

    static String email;

    private static Slides slidesSvc;
    private static Drive driveSvc;
    private static ServiceAccess exportAccess;

    private Auth() {}

    /**
     * Refuses an absent account rather than picking one. With several accounts stored, acting as the
     * wrong one writes to the wrong organisation's deck and nothing in the output would say so.
     */
    private static String email() {
        if (email == null || email.isBlank()) {
            Out.die("--email/-e <account> is required; " + APP_NAME + " has no default account");
        }
        return email;
    }

    private static ServiceAccess access(String api, List<String> scopes) throws Exception {
        return Credentials.access(AccessSpec.of(api, PROFILE, APP_NAME, email(), scopes));
    }

    static Slides slides() throws Exception {
        if (slidesSvc == null) {
            slidesSvc = SlidesService.slides(
                    access("slides", List.of(SlidesScopes.PRESENTATIONS)), APP_NAME);
        }
        return slidesSvc;
    }

    /**
     * Drive, for uploading a local image and for copying a deck.
     *
     * <p>Both scopes are asked for because the two uses genuinely differ — an upload is
     * {@code drive.file}, reading a deck in order to copy it is {@code drive.readonly} — and which one
     * serves any individual call is settled at that call rather than here.
     */
    static Drive drive() throws Exception {
        if (driveSvc == null) {
            driveSvc = DriveService.drive(
                    access("drive", List.of(DriveScopes.DRIVE_READONLY, DriveScopes.DRIVE_FILE)),
                    APP_NAME);
        }
        return driveSvc;
    }

    /**
     * The full-resolution PNG export, which is not an API call at all.
     *
     * <p>Slides offers no API for it; the only route is an undocumented endpoint on
     * {@code docs.google.com} taking a plain bearer header. That used to mean this tool held a real
     * Google access token in its own process, which is the one thing the design does not permit. The
     * wallet now fronts that host as well, so what comes back here is the same loopback handle as
     * everywhere else and the render is classified and recorded like any other read.
     *
     * @return the root URL to build the export request against, and the initializer that authorises it
     */
    static ServiceAccess exportAccess() throws Exception {
        if (exportAccess == null) {
            exportAccess = access("slidesexport", List.of(DriveScopes.DRIVE_READONLY));
        }
        return exportAccess;
    }

    static void reauth() {
        var who = email == null || email.isBlank() ? "<email>" : email;
        Out.die("re-consent has moved to the USK OAG GServices Wallet, which holds every refresh token on"
                + " this machine.\n  Run instead:            uskoag-walletcli login " + who
                + "\n  To drop a stored token: uskoag-walletcli forget " + who);
    }
}
