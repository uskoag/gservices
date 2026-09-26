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
        if (email == null || email.isBlank()) Out.die("--email/-e <account> is required (or set the AI account once: " + APP_NAME + " identity set --owner <email> --ai <email>)");
        return email;
    }

    private static ServiceAccess access(String api, List<String> scopes) throws Exception {
        return Credentials.access(AccessSpec.of(api, PROFILE, APP_NAME, email(), scopes));
    }

    static Docs docs() throws Exception {
        if (docsSvc == null) docsSvc = DocsService.docs(access("docs", List.of(DocsScopes.DOCUMENTS)), APP_NAME);
        return docsSvc;
    }

    private static Drive driveUploadSvc;

    /** drive.file: can create, share and trash only the files this tool itself uploaded (images for insertInlineImage). */
    static Drive driveUpload() throws Exception {
        if (driveUploadSvc == null) driveUploadSvc = DriveService.drive(access("drive", List.of(DriveScopes.DRIVE_FILE)), APP_NAME);
        return driveUploadSvc;
    }

    static Drive drive() throws Exception {
        if (driveSvc == null) driveSvc = DriveService.drive(access("drive", List.of(DriveScopes.DRIVE_READONLY)), APP_NAME);
        return driveSvc;
    }

    /** A read-only Drive client for a named account, not the acting one: `identity set` asks each account who it is. */
    static Drive driveAs(String account) throws Exception {
        return DriveService.drive(Credentials.access(AccessSpec.of("drive", PROFILE, APP_NAME, account, List.of(DriveScopes.DRIVE_READONLY))), APP_NAME);
    }

    private static ServiceAccess activityAccess;

    /** Drive Activity v2 (who edited when), fronted by the wallet as "driveactivity", scope drive.activity.readonly. */
    static ServiceAccess activityAccess() throws Exception {
        if (activityAccess == null) activityAccess = access("driveactivity", List.of("https://www.googleapis.com/auth/drive.activity.readonly"));
        return activityAccess;
    }

    private static ServiceAccess exportAccess;

    /** Earlier versions: Drive's revisions.exportLinks point at docs.google.com, which the wallet fronts read-only as "docsexport". */
    static ServiceAccess exportAccess() throws Exception {
        if (exportAccess == null) exportAccess = access("docsexport", List.of(DriveScopes.DRIVE_READONLY));
        return exportAccess;
    }
}
