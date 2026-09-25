package uskoag.gservices.docs;

import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.DocsScopes;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import java.util.List;
import uskoag.gservices.*;

/**
 * Clients come from the wallet; this tool holds no credential. Docs and Drive are asked for
 * separately so each call is served by the narrowest token that covers it — the docx export is a
 * Drive read and never needs a token that could rewrite the document.
 */
public final class Auth {

    static final String APP_NAME = "uskoag-gdocscli", PROFILE = "gdocs";

    static String email;

    private static Docs docsSvc;
    private static Drive driveSvc;

    private Auth() {}

    private static String email() {
        if (email == null || email.isBlank()) Out.die("--email/-e <account> is required; " + APP_NAME + " has no default account");
        return email;
    }

    private static ServiceAccess access(String api, List<String> scopes) throws Exception {
        return Credentials.access(AccessSpec.of(api, PROFILE, APP_NAME, email(), scopes));
    }

    static Docs docs() throws Exception {
        if (docsSvc == null) docsSvc = DocsService.docs(access("docs", List.of(DocsScopes.DOCUMENTS)), APP_NAME);
        return docsSvc;
    }

    static Drive drive() throws Exception {
        if (driveSvc == null) driveSvc = DriveService.drive(access("drive", List.of(DriveScopes.DRIVE_READONLY)), APP_NAME);
        return driveSvc;
    }
}
