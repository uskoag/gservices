package uskoag.gservices.sheetcli;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.sheets.v4.*;
import java.nio.file.Path;
import java.util.List;
import uskoag.gservices.*;

/**
 * Clients come from the wallet; this tool holds no credential. Each verb asks for the narrowest client
 * that covers it: history and diff never hold a token that could change the spreadsheet.
 */
final class Auth {

    static final String APP_NAME = "SpreadsheetCli-v1.0", PROFILE = "gsheets";
    static final Path LEGACY = Path.of(System.getProperty("user.home"), "uskoag", "gservices", "spreadsheet_cli");

    static String email;
    private static Sheets sheetsRead, sheetsWrite;
    private static Drive drive;
    private static ServiceAccess activity, export;

    private Auth() {}

    private static ServiceAccess access(String api, List<String> scopes) throws Exception {
        var spec = AccessSpec.of(api, PROFILE, APP_NAME, email, scopes).legacyRoot(LEGACY.toString());
        return Credentials.access(spec, () -> AppKeyPrompt.ask("uskoag-spreadsheet-cli"));
    }

    static Sheets sheetsRead() throws Exception {
        if (sheetsRead == null) sheetsRead = SheetsService.sheets(access("sheets", List.of(SheetsScopes.SPREADSHEETS_READONLY)), APP_NAME);
        return sheetsRead;
    }

    static Sheets sheetsWrite() throws Exception {
        if (sheetsWrite == null) sheetsWrite = SheetsService.sheets(access("sheets", List.of(SheetsScopes.SPREADSHEETS, SheetsScopes.DRIVE)), APP_NAME);
        return sheetsWrite;
    }

    static Drive drive() throws Exception {
        if (drive == null) drive = DriveService.drive(access("drive", List.of(DriveScopes.DRIVE_READONLY)), APP_NAME);
        return drive;
    }

    /** Drive Activity v2, fronted by the wallet as "driveactivity". */
    static ServiceAccess activity() throws Exception {
        if (activity == null) activity = access("driveactivity", List.of("https://www.googleapis.com/auth/drive.activity.readonly"));
        return activity;
    }

    /** Earlier versions: revision exportLinks point at docs.google.com, fronted read-only by the wallet as "docsexport". */
    static ServiceAccess export() throws Exception {
        if (export == null) export = access("docsexport", List.of(DriveScopes.DRIVE_READONLY));
        return export;
    }
}
