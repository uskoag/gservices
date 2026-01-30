package uskoag.gservices;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.UpdateSheetPropertiesRequest;
import com.google.api.services.sheets.v4.model.SheetProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line tool for Google Sheets operations.
 *
 * Usage:
 *   uskoag-sheetcli read <spreadsheetId> <sheetName> <address>
 *   uskoag-sheetcli read <spreadsheetId> <sheetName> <range_address>
 *   uskoag-sheetcli write <spreadsheetId> <sheetName> <address> "<value>"
 *   uskoag-sheetcli write <spreadsheetId> <sheetName> <range_address> "<json_values>"
 */
public class SpreadsheetCli {

    private static final String APP_NAME = "SpreadsheetCli-v1.0";
    private static final String APP_KEY = "uskoag-spreadsheet-cli-key-2025";
    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), "uskoag", "gservices", "spreadsheet_cli");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("SpreadsheetCli.xml");

    private static Sheets sheetsService;
    private static CliConfig config;
    private static boolean verbose = false;

    public static void main(String[] args) {
        try {
            // Check for verbose flag
            List<String> argList = new ArrayList<>(List.of(args));
            verbose = argList.remove("--verbose") || argList.remove("-v");
            args = argList.toArray(new String[0]);

            if (args.length < 2) {
                logError("Usage: uskoag-sheetcli [--verbose|-v] <command> <spreadsheetId> [args...]");
                logError("Commands:");
                logError("  read <spreadsheetId> <sheetName> <address|range>");
                logError("  write <spreadsheetId> <sheetName> <address|range> \"<value>\"");
                logError("  listsheet <spreadsheetId>");
                logError("  createsheet <spreadsheetId> <sheetName>");
                logError("  renamesheet <spreadsheetId> <oldSheetName> <newSheetName>");
                logError("Options:");
                logError("  --verbose, -v  Show detailed logging");
                System.exit(1);
            }

            String command = args[0].toLowerCase();
            String spreadsheetId = args[1];

            // Initialize
            initializeConfig();
            initializeSheetsService();

            // Execute command (permission checks happen inside handlers)
            switch (command) {
                case "read" -> {
                    if (args.length < 4) {
                        logError("ERROR: read command requires: <spreadsheetId> <sheetName> <address>");
                        System.exit(1);
                    }
                    handleRead(spreadsheetId, args[2], args[3]);
                }
                case "write" -> {
                    if (args.length < 5) {
                        logError("ERROR: write command requires: <spreadsheetId> <sheetName> <address> <value>");
                        System.exit(1);
                    }
                    handleWrite(spreadsheetId, args[2], args[3], args[4]);
                }
                case "listsheet" -> {
                    handleListSheets(spreadsheetId);
                }
                case "createsheet" -> {
                    if (args.length < 3) {
                        logError("ERROR: createsheet command requires: <spreadsheetId> <sheetName>");
                        System.exit(1);
                    }
                    handleCreateSheet(spreadsheetId, args[2]);
                }
                case "renamesheet" -> {
                    if (args.length < 4) {
                        logError("ERROR: renamesheet command requires: <spreadsheetId> <oldSheetName> <newSheetName>");
                        System.exit(1);
                    }
                    handleRenameSheet(spreadsheetId, args[2], args[3]);
                }
                default -> {
                    logError("ERROR: Unknown command: " + command);
                    logError("Valid commands: read, write, listsheet, createsheet, renamesheet");
                    System.exit(1);
                }
            }

        } catch (Exception e) {
            logError("ERROR: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void initializeConfig() throws IOException {
        config = new CliConfig(CONFIG_FILE, verbose);
        logInfo("Config loaded from: " + CONFIG_FILE);
    }

    private static void initializeSheetsService() throws IOException, GeneralSecurityException {
        logInfo("Initializing Google Sheets service...");

        var oauth = OAuthToken.oauthToken(APP_NAME, APP_KEY,
                SheetsScopes.SPREADSHEETS,
                SheetsScopes.DRIVE)
            .allCredsDirFromHome("uskoag", "gservices", "spreadsheet_cli")
            .defaultCredential();

        var transport = GoogleNetHttpTransport.newTrustedTransport();
        sheetsService = SheetsService.sheets(oauth);

        logInfo("Sheets service initialized successfully");
    }

    private static boolean hasPermission(String spreadsheetId, String operation) {
        return config.hasPermission(spreadsheetId, operation);
    }

    private static void handleRead(String spreadsheetId, String sheetName, String address) throws IOException {
        // SECURITY: Enforce permission check BEFORE API call
        if (!hasPermission(spreadsheetId, "read")) {
            logError("PERMISSION DENIED: No read permission for spreadsheet: " + spreadsheetId);
            System.exit(1);
        }

        logInfo("Reading from: " + sheetName + "!" + address);

        String range = sheetName + "!" + address;
        ValueRange response = sheetsService.spreadsheets().values()
            .get(spreadsheetId, range)
            .execute();

        List<List<Object>> values = response.getValues();

        if (values == null || values.isEmpty()) {
            logInfo("No data found");
            return;
        }

        // Determine if single cell or range
        if (isSingleCell(address)) {
            // Single cell - output plain string
            Object cellValue = values.get(0).get(0);
            System.out.println(cellValue != null ? cellValue.toString() : "");
        } else {
            // Range - output JSON-like format: "cell address": "value"
            String[] parts = parseRange(address);
            if (parts != null) {
                int startRow = getRowNumber(parts[0]);
                char startCol = getColumnLetter(parts[0]);

                for (int i = 0; i < values.size(); i++) {
                    List<Object> row = values.get(i);
                    for (int j = 0; j < row.size(); j++) {
                        char col = (char)(startCol + j);
                        int rowNum = startRow + i;
                        String cellAddr = "" + col + rowNum;
                        Object cellValue = row.get(j);
                        System.out.println("\"" + cellAddr + "\": \"" + (cellValue != null ? cellValue.toString() : "") + "\"");
                    }
                }
            }
        }
    }

    private static void handleWrite(String spreadsheetId, String sheetName, String address, String value) throws IOException {
        // SECURITY: Enforce permission check BEFORE API call
        if (!hasPermission(spreadsheetId, "write")) {
            logError("PERMISSION DENIED: No write permission for spreadsheet: " + spreadsheetId);
            System.exit(1);
        }

        logInfo("Writing to: " + sheetName + "!" + address);

        String range = sheetName + "!" + address;
        ValueRange body;

        if (isSingleCell(address)) {
            // Single cell write
            body = new ValueRange().setValues(List.of(List.of(value)));
        } else {
            // Range write - parse JSON-like input
            List<List<Object>> values = parseWriteValues(value);
            body = new ValueRange().setValues(values);
        }

        sheetsService.spreadsheets().values()
            .update(spreadsheetId, range, body)
            .setValueInputOption("USER_ENTERED")
            .execute();

        System.out.println("SUCCESS: Written to " + sheetName + "!" + address);
        logInfo("Write operation completed successfully");
    }

    private static boolean isSingleCell(String address) {
        // Single cell format: A1, B2, AA10, etc.
        // Range format: A1:B2, A1:A10, etc.
        return !address.contains(":");
    }

    private static String[] parseRange(String address) {
        if (address.contains(":")) {
            return address.split(":");
        }
        // Single cell treated as 1x1 range
        return new String[]{address, address};
    }

    private static int getRowNumber(String cellAddress) {
        // Extract row number from cell address (e.g., "A1" -> 1, "B23" -> 23)
        return Integer.parseInt(cellAddress.replaceAll("[A-Z]+", ""));
    }

    private static char getColumnLetter(String cellAddress) {
        // Extract column letter from cell address (e.g., "A1" -> 'A', "B23" -> 'B')
        return cellAddress.replaceAll("[0-9]+", "").charAt(0);
    }

    private static List<List<Object>> parseWriteValues(String jsonLikeValue) {
        // For now, simple implementation - treat as single value or comma-separated
        // This can be enhanced to parse actual JSON format
        List<List<Object>> values = new ArrayList<>();

        // If starts with [ or {, attempt to parse as structured data
        if (jsonLikeValue.startsWith("[") || jsonLikeValue.startsWith("{")) {
            // For simplicity, split by common delimiters
            // Production version would use proper JSON parser
            String cleaned = jsonLikeValue.replaceAll("[\\[\\]\\{\\}]", "");
            String[] parts = cleaned.split(",");

            List<Object> row = new ArrayList<>();
            for (String part : parts) {
                row.add(part.trim().replaceAll("\"", ""));
            }
            values.add(row);
        } else {
            // Single value
            values.add(List.of(jsonLikeValue));
        }

        return values;
    }

    private static void handleListSheets(String spreadsheetId) throws IOException {
        // SECURITY: Enforce permission check BEFORE API call
        if (!hasPermission(spreadsheetId, "read")) {
            logError("PERMISSION DENIED: No read permission for spreadsheet: " + spreadsheetId);
            System.exit(1);
        }

        logInfo("Listing sheets in spreadsheet: " + spreadsheetId);

        Spreadsheet spreadsheet = sheetsService.spreadsheets()
            .get(spreadsheetId)
            .execute();

        for (Sheet sheet : spreadsheet.getSheets()) {
            String sheetName = sheet.getProperties().getTitle();
            Integer sheetId = sheet.getProperties().getSheetId();
            System.out.println(sheetName + " (ID: " + sheetId + ")");
        }

        logInfo("Listed " + spreadsheet.getSheets().size() + " sheet(s)");
    }

    private static void handleCreateSheet(String spreadsheetId, String sheetName) throws IOException {
        // SECURITY: Enforce permission check BEFORE API call
        if (!hasPermission(spreadsheetId, "write")) {
            logError("PERMISSION DENIED: No write permission for spreadsheet: " + spreadsheetId);
            System.exit(1);
        }

        logInfo("Creating sheet: " + sheetName);

        AddSheetRequest addSheetRequest = new AddSheetRequest()
            .setProperties(new SheetProperties().setTitle(sheetName));

        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
            .setRequests(List.of(new Request().setAddSheet(addSheetRequest)));

        sheetsService.spreadsheets()
            .batchUpdate(spreadsheetId, batchRequest)
            .execute();

        System.out.println("SUCCESS: Created sheet '" + sheetName + "'");
        logInfo("Sheet created successfully");
    }

    private static void handleRenameSheet(String spreadsheetId, String oldSheetName, String newSheetName) throws IOException {
        // SECURITY: Enforce permission check BEFORE API call
        if (!hasPermission(spreadsheetId, "write")) {
            logError("PERMISSION DENIED: No write permission for spreadsheet: " + spreadsheetId);
            System.exit(1);
        }

        logInfo("Renaming sheet from '" + oldSheetName + "' to '" + newSheetName + "'");

        // First, get the sheet ID by name
        Spreadsheet spreadsheet = sheetsService.spreadsheets()
            .get(spreadsheetId)
            .execute();

        Integer sheetId = null;
        for (Sheet sheet : spreadsheet.getSheets()) {
            if (sheet.getProperties().getTitle().equals(oldSheetName)) {
                sheetId = sheet.getProperties().getSheetId();
                break;
            }
        }

        if (sheetId == null) {
            logError("ERROR: Sheet '" + oldSheetName + "' not found");
            System.exit(1);
        }

        // Rename the sheet
        UpdateSheetPropertiesRequest updateRequest = new UpdateSheetPropertiesRequest()
            .setProperties(new SheetProperties()
                .setSheetId(sheetId)
                .setTitle(newSheetName))
            .setFields("title");

        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
            .setRequests(List.of(new Request().setUpdateSheetProperties(updateRequest)));

        sheetsService.spreadsheets()
            .batchUpdate(spreadsheetId, batchRequest)
            .execute();

        System.out.println("SUCCESS: Renamed sheet from '" + oldSheetName + "' to '" + newSheetName + "'");
        logInfo("Sheet renamed successfully");
    }

    private static void logInfo(String message) {
        if (verbose) {
            System.err.println("[INFO] " + message);
        }
    }

    private static void logError(String message) {
        System.err.println("[ERROR] " + message);
    }
}
