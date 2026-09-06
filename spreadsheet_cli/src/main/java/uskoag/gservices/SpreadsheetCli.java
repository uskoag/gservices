package uskoag.gservices;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.GridCoordinate;
import com.google.api.services.sheets.v4.model.GridData;
import com.google.api.services.sheets.v4.model.GridProperties;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.InsertDimensionRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;
import com.google.api.services.sheets.v4.model.UpdateSheetPropertiesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Command-line tool for Google Sheets operations.
 *
 * Usage:
 *   uskoag-gsheetscli [-v] [--quiet] <command> [args...]
 *
 * Commands:
 *   read    <id> <sheet> <range> [<range2>...]  [--format kv|tsv|csv|json] [--formulas|--render R|--both]
 *   query   <id> <sheet> "<SQL>"                [--range A1notation] [--format json|tsv|csv]
 *   update  <id> <sheet> "<SQL UPDATE>"         [--range A1notation] [--dry-run] [--user-entered] [--format json|tsv]
 *   write   <id> <sheet> <address> [<value>]   [--raw|--user-entered]
 *                                               [--tsv-file F|--csv-file F|--json-file F|--tsv D|--json D]
 *   append  <id> <sheet> [<startRange>]        [--raw|--user-entered]
 *                                               [--tsv-file F|--csv-file F|--json-file F|--tsv D|--json D]
 *   clear   <id> <sheet> <range>
 *   listsheet  <id>
 *   createsheet <id> <sheetName>
 *   renamesheet <id> <oldName> <newName>
 *   freeze  <id> <sheet> [--rows N] [--cols N]
 *   format  <id> <sheet> <range> [--bg #hex] [--text #hex] [--bold] [--italic]
 *                                [--align left|center|right] [--valign top|middle|bottom] [--font-size N] [--wrap]
 *   validate <id> <sheet> <range> (--from-range Ref | --list a,b,c | --per-row-formula Tmpl) [--warn] [--clear]
 *                                 [--colors #h1,#h2,... [--color-legend]]
 *   highlight <id> <sheet> <range> (--from-range Ref | --list a,b,c | --colors-from-range Ref) [--colors #h1,#h2,...] [--clear]
 *   filter  <id> <sheet> [<range>] [--clear]
 *   grant | revoke | listperms   moved to the wallet; these print the replacement command
 *
 * Value input option (write/append):
 *   Default is RAW (data stored literally).  Use --user-entered to enable formula parsing.
 *
 * Read output formats:
 *   kv  (default): "A1": "value"
 *   tsv : tab-separated rows
 *   csv : RFC-4180 quoted CSV
 *   json: [[row1col1, row1col2, ...], ...]
 */
public class SpreadsheetCli {

    private static final String APP_NAME = "SpreadsheetCli-v1.0";

    /**
     * What the app-key became: a name, not a secret. It selects a credential and unlocks nothing, so
     * unlike the value it replaces it is safe in argv, in shell history and in an agent transcript.
     */
    private static final String PROFILE = "gsheets";

    /** Only reached when no wallet is installed; then it is typed at a hidden prompt, never passed in. */
    private static final String LEGACY_APP_KEY_HINT = "uskoag-spreadsheet-cli";

    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), "uskoag", "gservices", "spreadsheet_cli");
    // disableHtmlEscaping: formulas/notes routinely contain equals/angle-bracket/ampersand chars —
    // Gson's default HTML-safe mode would otherwise unicode-escape them in JSON output.
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static Sheets sheetsService;
    private static boolean verbose = false;
    private static boolean quiet = false;
    private static boolean walletBrokered = false;
    private static String email = null;

    public static void main(String[] args) {
        // Agents capture stdout and decode it as UTF-8.  On Windows the JVM's default
        // PrintStream uses the platform charset (cp1252), which mangles non-Latin1
        // content in cell notes/values (en-dashes, arrows, curly quotes, non-Latin
        // scripts) into a lone 0x96 byte or '?'.  Pin both streams to UTF-8.
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err), true, StandardCharsets.UTF_8));

        // Before anything else, because it is what the wallet's approval dialog shows and a request that
        // arrives before this is recorded is a request nobody can identify. Windows will not tell the
        // wallet what this process is running — ProcessHandle has no argv for its own process there — so
        // this is the only route the facts have. Secrets are replaced inside Caller; see its notes.
        uskoag.gservices.Caller.record("uskoag-gsheetscli", args);
        try {
            List<String> argList = new ArrayList<>(List.of(args));

            verbose = removeFlag(argList, "--verbose", "-v");
            quiet = removeFlag(argList, "--quiet");
            boolean wantsHelp = removeFlag(argList, "--help", "-h");
            // An account selector, not a key: safe in argv, safe in history, safe in a transcript.
            email = extractFlagValue(argList, "--email", "-e");

            // Explicit help is the data the user asked for → stdout, exit 0.
            // No args at all is a misuse → usage to stderr, exit 1.
            if (wantsHelp) {
                printHelp(System.out);
                return;
            }
            if (argList.isEmpty()) {
                printHelp(System.err);
                System.exit(1);
            }

            String command = argList.remove(0).toLowerCase();

            if (command.equals("help")) {
                printHelp(System.out);
                return;
            }

            if (command.equals("listperms")) {
                handleListPerms();
                return;
            }

            initializeSheetsService();

            switch (command) {
                case "read" -> {
                    if (argList.size() < 3) {
                        logError("read: requires <spreadsheetId> <sheetName> <range>");
                        System.exit(1);
                    }
                    String id = argList.remove(0);
                    handleRead(id, argList);
                }
                case "inspect" -> {
                    if (argList.size() < 2) {
                        logError("inspect: requires <spreadsheetId> <sheetName> [<range>]");
                        System.exit(1);
                    }
                    String id = argList.remove(0);
                    handleInspect(id, argList);
                }
                case "query" -> {
                    if (argList.size() < 3) {
                        logError("query: requires <spreadsheetId> <sheetName> \"<SQL>\"");
                        System.exit(1);
                    }
                    String id = argList.remove(0);
                    handleQuery(id, argList);
                }
                case "update" -> {
                    if (argList.size() < 3) {
                        logError("update: requires <spreadsheetId> <sheetName> \"<SQL UPDATE>\"");
                        System.exit(1);
                    }
                    String id = argList.remove(0);
                    handleUpdate(id, argList);
                }
                case "write" -> {
                    if (argList.size() < 2) {
                        logError("write: requires <spreadsheetId> <sheetName> <address> ...");
                        System.exit(1);
                    }
                    String id = argList.remove(0);
                    handleWrite(id, argList);
                }
                case "append" -> {
                    if (argList.size() < 2) {
                        logError("append: requires <spreadsheetId> <sheetName> ...");
                        System.exit(1);
                    }
                    String id = argList.remove(0);
                    handleAppend(id, argList);
                }
                case "clear" -> {
                    if (argList.size() < 3) {
                        logError("clear: requires <spreadsheetId> <sheetName> <range>");
                        System.exit(1);
                    }
                    String id = argList.remove(0);
                    handleClear(id, argList.remove(0), argList.remove(0));
                }
                case "listsheet" -> {
                    if (argList.isEmpty()) { logError("listsheet: requires <spreadsheetId>"); System.exit(1); }
                    handleListSheets(argList.remove(0));
                }
                case "createsheet" -> {
                    if (argList.size() < 2) { logError("createsheet: requires <spreadsheetId> <sheetName>"); System.exit(1); }
                    handleCreateSheet(argList.remove(0), argList.remove(0));
                }
                case "renamesheet" -> {
                    if (argList.size() < 3) { logError("renamesheet: requires <spreadsheetId> <oldName> <newName>"); System.exit(1); }
                    handleRenameSheet(argList.remove(0), argList.remove(0), argList.remove(0));
                }
                case "freeze" -> {
                    if (argList.size() < 2) { logError("freeze: requires <spreadsheetId> <sheetName>"); System.exit(1); }
                    handleFreeze(argList.remove(0), argList.remove(0), argList);
                }
                case "insertcolumn" -> {
                    if (argList.size() < 3) { logError("insertcolumn: requires <spreadsheetId> <sheetName> <colLetter>"); System.exit(1); }
                    handleInsertColumn(argList.remove(0), argList.remove(0), argList.remove(0), argList);
                }
                case "insertrow" -> {
                    if (argList.size() < 3) { logError("insertrow: requires <spreadsheetId> <sheetName> <rowNumber>"); System.exit(1); }
                    handleInsertRow(argList.remove(0), argList.remove(0), argList.remove(0), argList);
                }
                case "deletecolumn" -> {
                    if (argList.size() < 3) { logError("deletecolumn: requires <spreadsheetId> <sheetName> <colLetter>"); System.exit(1); }
                    handleDeleteColumn(argList.remove(0), argList.remove(0), argList.remove(0), argList);
                }
                case "format" -> {
                    if (argList.size() < 3) { logError("format: requires <spreadsheetId> <sheetName> <range>"); System.exit(1); }
                    handleFormat(argList.remove(0), argList.remove(0), argList.remove(0), argList);
                }
                case "numberformat" -> {
                    if (argList.size() < 3) { logError("numberformat: requires <spreadsheetId> <sheetName> <range>"); System.exit(1); }
                    handleNumberFormat(argList.remove(0), argList.remove(0), argList.remove(0), argList);
                }
                case "validate" -> {
                    if (argList.size() < 3) { logError("validate: requires <spreadsheetId> <sheetName> <range>"); System.exit(1); }
                    handleValidate(argList.remove(0), argList.remove(0), argList.remove(0), argList);
                }
                case "highlight" -> {
                    if (argList.size() < 3) { logError("highlight: requires <spreadsheetId> <sheetName> <range>"); System.exit(1); }
                    handleHighlight(argList.remove(0), argList.remove(0), argList.remove(0), argList);
                }
                case "filter" -> {
                    if (argList.size() < 2) { logError("filter: requires <spreadsheetId> <sheetName>"); System.exit(1); }
                    handleFilter(argList.remove(0), argList.remove(0), argList);
                }
                case "describeschema" -> {
                    if (argList.isEmpty()) { logError("describeschema: requires <spreadsheetId>"); System.exit(1); }
                    handleDescribeSchema(argList.remove(0), argList);
                }
                case "grant" -> {
                    if (argList.size() < 2) { logError("grant: requires --read|--write <spreadsheetId> [name]"); System.exit(1); }
                    handleGrant(argList);
                }
                case "revoke" -> {
                    if (argList.isEmpty()) { logError("revoke: requires <spreadsheetId>"); System.exit(1); }
                    handleRevoke(argList.remove(0));
                }
                default -> {
                    logError("Unknown command: " + command);
                    logError("Valid: read inspect query update write append clear listsheet createsheet renamesheet freeze insertcolumn insertrow deletecolumn format numberformat validate highlight filter describeschema grant revoke listperms help");
                    logError("Run 'uskoag-gsheetscli --help' for usage.");
                    System.exit(1);
                }
            }

        } catch (GoogleJsonResponseException gjre) {
            int code = gjre.getStatusCode();
            // Prefer the concise API error message over the status line / full JSON details.
            String msg = (gjre.getDetails() != null && gjre.getDetails().getMessage() != null)
                ? gjre.getDetails().getMessage()
                : gjre.getStatusMessage();
            if (code == 403) logError("PERMISSION DENIED (" + code + "): " + msg);
            else if (code == 404) logError("NOT FOUND (" + code + "): " + msg);
            else if (code == 401) logError("AUTH FAILURE (" + code + "): " + msg);
            else logError("API ERROR " + code + ": " + msg);
            if (verbose) gjre.printStackTrace(System.err);
            System.exit(1);
        } catch (Exception e) {
            String msg = e.getMessage();
            logError(msg != null && !msg.isEmpty() ? msg : e.getClass().getSimpleName());
            if (verbose) e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    // ─── read ────────────────────────────────────────────────────────────────

    private static void handleRead(String spreadsheetId, List<String> argList) throws IOException {
        String format = extractFlagValue(argList, "--format");
        if (format == null) format = "kv";

        boolean formulas = removeFlag(argList, "--formulas");
        boolean both = removeFlag(argList, "--both");
        boolean rich = removeFlag(argList, "--rich");
        String render = extractFlagValue(argList, "--render");
        if ((formulas ? 1 : 0) + (both ? 1 : 0) + (rich ? 1 : 0) + (render != null ? 1 : 0) > 1) {
            logError("read: --formulas, --both, --rich and --render are mutually exclusive");
            System.exit(1);
        }
        String renderOption = formulas ? "FORMULA" : renderOptionOf(render);

        if (argList.size() < 2) {
            logError("read: requires <sheetName> <range>");
            System.exit(1);
        }
        String sheetName = argList.remove(0);
        List<String> ranges = new ArrayList<>(argList);

        if (ranges.isEmpty()) {
            logError("read: requires at least one <range>");
            System.exit(1);
        }

        logInfo("Reading " + sheetName + " ranges=" + ranges + " format=" + format + " render=" + renderOption + " both=" + both + " rich=" + rich);

        if (both) {
            handleReadBoth(spreadsheetId, sheetName, ranges, format);
            return;
        }
        if (rich) {
            handleReadRich(spreadsheetId, sheetName, ranges, format);
            return;
        }

        if (ranges.size() == 1 && CellRange.isSingleCell(ranges.get(0)) && renderOption == null) {
            // Single cell fast path — plain value
            String apiRange = CellRange.a1Ref(sheetName, ranges.get(0));
            ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, apiRange).execute();
            List<List<Object>> values = response.getValues();
            if (values != null && !values.isEmpty() && !values.get(0).isEmpty()) {
                Object v = values.get(0).get(0);
                System.out.println(v != null ? v.toString() : "");
            } else {
                System.out.println("");
            }
            return;
        }

        if (ranges.size() == 1) {
            // Single range
            String apiRange = CellRange.a1Ref(sheetName, ranges.get(0));
            var get = sheetsService.spreadsheets().values().get(spreadsheetId, apiRange);
            if (renderOption != null) get.setValueRenderOption(renderOption);
            ValueRange response = get.execute();
            outputRangeData(ranges.get(0), response.getValues(), format);
        } else {
            // Multiple ranges — batchGet
            List<String> apiRanges = ranges.stream().map(r -> CellRange.a1Ref(sheetName, r)).collect(Collectors.toList());
            var batchGet = sheetsService.spreadsheets().values().batchGet(spreadsheetId).setRanges(apiRanges);
            if (renderOption != null) batchGet.setValueRenderOption(renderOption);
            BatchGetValuesResponse batchResponse = batchGet.execute();
            List<ValueRange> valueRanges = batchResponse.getValueRanges();
            for (int i = 0; i < ranges.size(); i++) {
                System.out.println("# " + sheetName + "!" + ranges.get(i));
                ValueRange vr = (valueRanges != null && i < valueRanges.size()) ? valueRanges.get(i) : null;
                outputRangeData(ranges.get(i), vr != null ? vr.getValues() : null, format);
            }
        }
    }

    /** --render FORMATTED|UNFORMATTED|FORMULA → API valueRenderOption; null passthrough. */
    private static String renderOptionOf(String render) {
        if (render == null) return null;
        return switch (render.toUpperCase()) {
            case "FORMATTED" -> "FORMATTED_VALUE";
            case "UNFORMATTED" -> "UNFORMATTED_VALUE";
            case "FORMULA" -> "FORMULA";
            default -> {
                logError("read: --render must be FORMATTED|UNFORMATTED|FORMULA");
                System.exit(1);
                yield null;
            }
        };
    }

    /**
     * --both: fetch each range twice (plain value + FORMULA) and pair them per cell, so callers
     * never have to choose. json gets real nested {"formula","value"} objects; kv/tsv/csv embed
     * the same pair as a compact JSON string per cell (those formats are flat, single-string-per-cell).
     */
    private static void handleReadBoth(String spreadsheetId, String sheetName, List<String> ranges, String format) throws IOException {
        List<String> apiRanges = ranges.stream().map(r -> CellRange.a1Ref(sheetName, r)).collect(Collectors.toList());

        BatchGetValuesResponse valuesResp = sheetsService.spreadsheets().values()
            .batchGet(spreadsheetId).setRanges(apiRanges).execute();
        BatchGetValuesResponse formulasResp = sheetsService.spreadsheets().values()
            .batchGet(spreadsheetId).setRanges(apiRanges).setValueRenderOption("FORMULA").execute();

        List<ValueRange> valueRanges = valuesResp.getValueRanges();
        List<ValueRange> formulaRanges = formulasResp.getValueRanges();

        for (int i = 0; i < ranges.size(); i++) {
            if (ranges.size() > 1) System.out.println("# " + sheetName + "!" + ranges.get(i));
            ValueRange vr = (valueRanges != null && i < valueRanges.size()) ? valueRanges.get(i) : null;
            ValueRange fr = (formulaRanges != null && i < formulaRanges.size()) ? formulaRanges.get(i) : null;
            outputRangeDataBoth(ranges.get(i), vr != null ? vr.getValues() : null, fr != null ? fr.getValues() : null, format);
        }
    }

    private static void outputRangeDataBoth(String address, List<List<Object>> apiValues, List<List<Object>> apiFormulas, String format) {
        int[] d = readDims(address, apiValues);
        int startCol = d[0], startRow = d[1], numCols = d[2], numRows = d[3];
        List<List<String>> valueGrid = toStringGrid(apiValues, numRows, numCols);
        List<List<String>> formulaGrid = toStringGrid(apiFormulas, numRows, numCols);

        if (format.equalsIgnoreCase("json")) {
            List<List<Map<String, String>>> grid = new ArrayList<>();
            for (int i = 0; i < numRows; i++) {
                List<Map<String, String>> row = new ArrayList<>();
                for (int j = 0; j < numCols; j++) row.add(cellPair(formulaGrid.get(i).get(j), valueGrid.get(i).get(j)));
                grid.add(row);
            }
            System.out.println(GSON.toJson(grid));
            return;
        }

        List<List<String>> grid = new ArrayList<>();
        for (int i = 0; i < numRows; i++) {
            List<String> row = new ArrayList<>();
            for (int j = 0; j < numCols; j++) row.add(GSON.toJson(cellPair(formulaGrid.get(i).get(j), valueGrid.get(i).get(j))));
            grid.add(row);
        }
        printGridKvTsvCsv(startCol, startRow, grid, format);
    }

    private static Map<String, String> cellPair(String formula, String value) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("formula", formula);
        m.put("value", value);
        return m;
    }

    /**
     * --rich: one spreadsheets().get() grid-data call (like `inspect`) returning per-cell
     * {value, formula, note, color, numberFormat} — the details the plain values API can't return.
     */
    private static void handleReadRich(String spreadsheetId, String sheetName, List<String> ranges, String format) throws IOException {
        List<String> apiRanges = ranges.stream().map(r -> CellRange.a1Ref(sheetName, r)).collect(Collectors.toList());

        Spreadsheet ss = sheetsService.spreadsheets().get(spreadsheetId)
            .setRanges(apiRanges)
            .setFields("sheets(properties.title,data(startRow,startColumn,rowData(values("
                     + "userEnteredValue,formattedValue,note,effectiveFormat.backgroundColor,effectiveFormat.numberFormat))))")
            .execute();

        Sheet target = pickSheet(ss, sheetName);
        if (target == null) {
            logError("read: sheet '" + sheetName + "' not found");
            System.exit(1);
            return;
        }

        List<GridData> dataList = target.getData();
        for (int i = 0; i < ranges.size(); i++) {
            if (ranges.size() > 1) System.out.println("# " + sheetName + "!" + ranges.get(i));
            GridData gd = (dataList != null && i < dataList.size()) ? dataList.get(i) : null;
            outputRangeDataRich(ranges.get(i), gd, format);
        }
    }

    private static void outputRangeDataRich(String address, GridData gd, String format) {
        int[] d = readDims(address, shapeOf(gd));
        int startCol = d[0], startRow = d[1], numCols = d[2], numRows = d[3];
        List<List<Map<String, Object>>> grid = richGrid(gd, numRows, numCols);

        if (format.equalsIgnoreCase("json")) {
            System.out.println(GSON.toJson(grid));
            return;
        }

        List<List<String>> strGrid = new ArrayList<>();
        for (List<Map<String, Object>> row : grid) {
            List<String> strRow = new ArrayList<>();
            for (Map<String, Object> cellMap : row) strRow.add(GSON.toJson(cellMap));
            strGrid.add(strRow);
        }
        printGridKvTsvCsv(startCol, startRow, strGrid, format);
    }

    /** Row/col counts only (values irrelevant) so readDims can size an open-ended --rich range. */
    private static List<List<Object>> shapeOf(GridData gd) {
        List<List<Object>> shape = new ArrayList<>();
        if (gd != null && gd.getRowData() != null) {
            for (RowData r : gd.getRowData()) {
                int n = (r.getValues() != null) ? r.getValues().size() : 0;
                List<Object> shapeRow = new ArrayList<>();
                for (int k = 0; k < n; k++) shapeRow.add("");
                shape.add(shapeRow);
            }
        }
        return shape;
    }

    private static List<List<Map<String, Object>>> richGrid(GridData gd, int numRows, int numCols) {
        List<RowData> rows = (gd != null) ? gd.getRowData() : null;
        List<List<Map<String, Object>>> grid = new ArrayList<>();
        for (int i = 0; i < numRows; i++) {
            List<Map<String, Object>> row = new ArrayList<>();
            List<CellData> src = (rows != null && i < rows.size() && rows.get(i).getValues() != null) ? rows.get(i).getValues() : List.of();
            for (int j = 0; j < numCols; j++) {
                CellData cell = (j < src.size()) ? src.get(j) : null;
                row.add(CellRich.cellMap(cell));
            }
            grid.add(row);
        }
        return grid;
    }

    private static void outputRangeData(String address, List<List<Object>> apiValues, String format) {
        int[] d = readDims(address, apiValues);
        int startCol = d[0], startRow = d[1], numCols = d[2], numRows = d[3];
        List<List<String>> grid = toStringGrid(apiValues, numRows, numCols);

        if (format.equalsIgnoreCase("json")) {
            System.out.println(GSON.toJson(grid));
        } else {
            printGridKvTsvCsv(startCol, startRow, grid, format);
        }
    }

    /**
     * {startCol, startRow (1-based), numCols, numRows} for rendering a read result. A bounded range
     * fixes its own extent (so blank cells stay padded positionally); an open-ended range (C2:C, A:A)
     * takes its missing dimension from the data the API actually returned — which is the only place
     * that extent exists for an unbounded range.
     */
    private static int[] readDims(String address, List<List<Object>> data) {
        var parts = address.toUpperCase().split(":", 2);
        var last = parts[parts.length - 1];
        Integer startCol = CellRange.colOrNull(parts[0]);
        Integer startRow = CellRange.rowOrNull(parts[0]);
        Integer endCol = CellRange.colOrNull(last);
        Integer endRow = CellRange.rowOrNull(last);
        int sc = (startCol != null) ? startCol : 1;
        int sr = (startRow != null) ? startRow : 1;
        int dataRows = (data == null) ? 0 : data.size();
        int dataCols = (data == null) ? 0 : data.stream().mapToInt(List::size).max().orElse(0);
        int numCols = (endCol != null) ? endCol - sc + 1 : Math.max(dataCols, 1);
        int numRows = (endRow != null) ? endRow - sr + 1 : Math.max(dataRows, 1);
        return new int[]{ sc, sr, numCols, numRows };
    }

    /** Rectangular String grid, padding short/absent rows with "". */
    private static List<List<String>> toStringGrid(List<List<Object>> apiValues, int numRows, int numCols) {
        List<List<String>> grid = new ArrayList<>();
        for (int i = 0; i < numRows; i++) {
            List<String> row = new ArrayList<>();
            List<Object> src = (apiValues != null && i < apiValues.size()) ? apiValues.get(i) : List.of();
            for (int j = 0; j < numCols; j++) {
                Object cell = (j < src.size()) ? src.get(j) : null;
                row.add(cell != null ? cell.toString() : "");
            }
            grid.add(row);
        }
        return grid;
    }

    private static void printGridKvTsvCsv(int startCol, int startRow, List<List<String>> grid, String format) {
        switch (format.toLowerCase()) {
            case "kv" -> {
                for (int i = 0; i < grid.size(); i++) {
                    for (int j = 0; j < grid.get(i).size(); j++) {
                        String addr = CellRange.colToLetter(startCol + j) + (startRow + i);
                        String val = grid.get(i).get(j);
                        System.out.println("\"" + addr + "\": \"" + escapeKv(val) + "\"");
                    }
                }
            }
            case "tsv" -> {
                for (List<String> row : grid) {
                    System.out.println(row.stream()
                        .map(v -> v.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", ""))
                        .collect(Collectors.joining("\t")));
                }
            }
            case "csv" -> {
                for (List<String> row : grid) {
                    System.out.println(row.stream().map(SpreadsheetCli::csvQuote).collect(Collectors.joining(",")));
                }
            }
            default -> {
                logError("Unknown --format: " + format + ". Use kv|tsv|csv|json");
                System.exit(1);
            }
        }
    }

    // ─── inspect (notes + cell colors) ─────────────────────────────────────────

    /**
     * Show what the values API can't: cell notes and background colors.
     * Both come from one spreadsheets.get with a grid-data field mask.
     * Colors are reported as merged rectangles, not per cell.
     */
    private static void handleInspect(String spreadsheetId, List<String> argList) throws IOException {
        String format = extractFlagValue(argList, "--format");
        if (format == null) format = "text";
        boolean notesOnly  = removeFlag(argList, "--notes-only");
        boolean colorsOnly = removeFlag(argList, "--colors-only");
        if (notesOnly && colorsOnly) {
            logError("inspect: --notes-only and --colors-only are mutually exclusive");
            System.exit(1);
        }
        boolean wantNotes  = !colorsOnly;
        boolean wantColors = !notesOnly;

        if (argList.isEmpty()) {
            logError("inspect: requires <sheetName> [<range>]");
            System.exit(1);
        }
        String sheetName = argList.remove(0);
        String range     = argList.isEmpty() ? null : argList.remove(0);
        String apiRange  = (range == null) ? CellRange.a1Sheet(sheetName) : CellRange.a1Ref(sheetName, range);

        logInfo("Inspecting " + apiRange + " notes=" + wantNotes + " colors=" + wantColors);

        Spreadsheet ss = sheetsService.spreadsheets().get(spreadsheetId)
            .setRanges(List.of(apiRange))
            .setFields("sheets(properties.title,data(startRow,startColumn,"
                     + "rowData(values(note,effectiveFormat.backgroundColor))))")
            .execute();

        Sheet target = pickSheet(ss, sheetName);
        if (target == null) {
            logError("inspect: sheet '" + sheetName + "' not found");
            System.exit(1);
        }

        // notes: {a1, text}, in reading order.  colors: cell→hex plus bounding box.
        List<String[]> notes = new ArrayList<>();
        Map<Long, String> colorAt = new java.util.HashMap<>();
        int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;
        int minC = Integer.MAX_VALUE, maxC = Integer.MIN_VALUE;

        if (target.getData() != null) {
            for (GridData gd : target.getData()) {
                int sr = gd.getStartRow() == null ? 0 : gd.getStartRow();
                int sc = gd.getStartColumn() == null ? 0 : gd.getStartColumn();
                List<RowData> rows = gd.getRowData();
                if (rows == null) continue;
                for (int i = 0; i < rows.size(); i++) {
                    List<CellData> cells = rows.get(i).getValues();
                    if (cells == null) continue;
                    for (int j = 0; j < cells.size(); j++) {
                        CellData cell = cells.get(j);
                        if (cell == null) continue;
                        int row1 = sr + i + 1, col1 = sc + j + 1;

                        if (wantNotes) {
                            String note = cell.getNote();
                            if (note != null && !note.isEmpty()) {
                                notes.add(new String[]{ CellRange.colToLetter(col1) + row1, note });
                            }
                        }
                        if (wantColors && cell.getEffectiveFormat() != null) {
                            String hex = CellInspect.colorToHex(cell.getEffectiveFormat().getBackgroundColor());
                            if (!CellInspect.isWhite(hex)) {
                                colorAt.put(((long) row1 << 20) | col1, hex);
                                minR = Math.min(minR, row1); maxR = Math.max(maxR, row1);
                                minC = Math.min(minC, col1); maxC = Math.max(maxC, col1);
                            }
                        }
                    }
                }
            }
        }

        // Merge colored cells into maximal rectangles, grouped by color (first-seen order).
        Map<String, List<String>> colorRanges = new LinkedHashMap<>();
        if (wantColors && !colorAt.isEmpty()) {
            String[][] grid = new String[maxR - minR + 1][maxC - minC + 1];
            for (Map.Entry<Long, String> e : colorAt.entrySet()) {
                long k = e.getKey();
                int row1 = (int) (k >> 20), col1 = (int) (k & 0xFFFFF);
                grid[row1 - minR][col1 - minC] = e.getValue();
            }
            for (CellInspect.Region rgn : CellInspect.mergeRegions(grid, minR, minC)) {
                colorRanges.computeIfAbsent(rgn.colorHex, x -> new ArrayList<>()).add(rgn.a1());
            }
        }

        if (format.equalsIgnoreCase("json")) {
            outputInspectJson(target.getProperties().getTitle(), apiRange, wantNotes, wantColors, notes, colorRanges);
        } else if (format.equalsIgnoreCase("text")) {
            outputInspectText(target.getProperties().getTitle(), wantNotes, wantColors, notes, colorRanges);
        } else {
            logError("inspect: unknown --format: " + format + ". Use text|json");
            System.exit(1);
        }
    }

    private static Sheet pickSheet(Spreadsheet ss, String sheetName) {
        if (ss.getSheets() == null) return null;
        for (Sheet s : ss.getSheets()) {
            if (s.getProperties() != null && sheetName.equals(s.getProperties().getTitle())) return s;
        }
        // Range-filtered get may return only the matching sheet; fall back to the one carrying data.
        for (Sheet s : ss.getSheets()) {
            if (s.getData() != null && !s.getData().isEmpty()) return s;
        }
        return ss.getSheets().isEmpty() ? null : ss.getSheets().get(0);
    }

    private static void outputInspectText(String sheet, boolean wantNotes, boolean wantColors,
                                          List<String[]> notes, Map<String, List<String>> colorRanges) {
        if (wantNotes) {
            System.out.println("# " + sheet + " notes (" + notes.size() + ")");
            for (String[] n : notes) {
                System.out.println(n[0] + ": " + n[1].replace("\\", "\\\\").replace("\n", "\\n").replace("\r", ""));
            }
        }
        if (wantColors) {
            if (wantNotes) System.out.println();
            int regions = colorRanges.values().stream().mapToInt(List::size).sum();
            System.out.println("# " + sheet + " colors (" + regions + " region(s), " + colorRanges.size() + " color(s))");
            for (Map.Entry<String, List<String>> e : colorRanges.entrySet()) {
                System.out.println(e.getKey() + " " + CellInspect.approxName(e.getKey()) + ": "
                                 + String.join(", ", e.getValue()));
            }
        }
    }

    private static void outputInspectJson(String sheet, String apiRange, boolean wantNotes, boolean wantColors,
                                          List<String[]> notes, Map<String, List<String>> colorRanges) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("sheet", sheet);
        root.put("range", apiRange);
        if (wantNotes) {
            List<Map<String, String>> noteList = new ArrayList<>();
            for (String[] n : notes) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("cell", n[0]);
                m.put("note", n[1]);
                noteList.add(m);
            }
            root.put("notes", noteList);
        }
        if (wantColors) {
            List<Map<String, Object>> colorList = new ArrayList<>();
            for (Map.Entry<String, List<String>> e : colorRanges.entrySet()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("hex", e.getKey());
                m.put("name", CellInspect.approxName(e.getKey()));
                m.put("ranges", e.getValue());
                colorList.add(m);
            }
            root.put("colors", colorList);
        }
        System.out.println(GSON.toJson(root));
    }

    // ─── query (SQL over the sheet) ────────────────────────────────────────────

    /**
     * Read the sheet (whole tab, or --range) into an in-memory H2 table `t` and
     * run the given SQL SELECT. Columns are addressed as A,B,C...; "_row" carries
     * the source sheet row number. Cells are text — CAST for numeric/date compares.
     */
    private static void handleQuery(String spreadsheetId, List<String> argList) throws IOException {
        String format = extractFlagValue(argList, "--format");
        if (format == null) format = "json";
        String range = extractFlagValue(argList, "--range");

        if (argList.size() < 2) {
            logError("query: requires <sheetName> \"<SQL>\"");
            System.exit(1);
        }
        String sheetName = argList.remove(0);
        String sql       = argList.remove(0);

        String apiRange = (range == null) ? CellRange.a1Sheet(sheetName) : CellRange.a1Ref(sheetName, range);
        int firstRow    = (range == null) ? 1 : startRowOf(range);

        logInfo("Querying " + apiRange + " firstRow=" + firstRow + " format=" + format);

        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, apiRange).execute();
        QueryResult result = SheetQuery.run(response.getValues(), firstRow, sql);
        System.out.println(QueryOutput.format(result, format));
        logInfo("Query returned " + result.rows().size() + " row(s)");
    }

    /** First row of an A1 range: "A2:Z" → 2, "B5" → 5, "A:Z" (no rows) → 1. */
    private static int startRowOf(String range) {
        String digits = range.split(":", 2)[0].replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 1 : Integer.parseInt(digits);
    }

    // ─── update (SQL UPDATE + before→after diff, pushes changed cells) ──────────

    /**
     * Read the sheet, run a SQL UPDATE against an in-memory copy, then push ONLY
     * the changed cells back and report each cell's old→new value (so the agent
     * can verify and self-heal). --dry-run computes the diff but writes nothing.
     */
    private static void handleUpdate(String spreadsheetId, List<String> argList) throws IOException {
        boolean dryRun      = removeFlag(argList, "--dry-run");
        boolean userEntered = removeFlag(argList, "--user-entered");
        removeFlag(argList, "--raw"); // RAW is the default; explicit --raw is a no-op
        boolean asDate = removeFlag(argList, "--as-date");
        String dateType = dateTypeOf(extractFlagValue(argList, "--date-type"));
        String dateFormat = extractFlagValue(argList, "--date-format");
        String format = extractFlagValue(argList, "--format");
        if (format == null) format = "json";
        String range = extractFlagValue(argList, "--range");

        if (argList.size() < 2) {
            logError("update: requires <sheetName> \"<SQL UPDATE>\"");
            System.exit(1);
        }
        String sheetName = argList.remove(0);
        String sql       = argList.remove(0);

        String apiRange = (range == null) ? CellRange.a1Sheet(sheetName) : CellRange.a1Ref(sheetName, range);
        int firstRow    = (range == null) ? 1 : startRowOf(range);

        logInfo("Update " + apiRange + " firstRow=" + firstRow + " dryRun=" + dryRun + " asDate=" + asDate);

        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, apiRange).execute();
        UpdateDiff diff = SheetUpdate.run(response.getValues(), firstRow, sql);

        // --as-date: cells whose new value parses as a date get converted to a Sheets serial + a
        // matching number format; a SET touching unrelated non-date columns passes through unchanged
        // (heterogeneous SQL SET, unlike write/append's single homogeneous block, can't fail loud).
        Integer sheetId = null;
        if (asDate && !dryRun && !diff.changes().isEmpty()) {
            Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
            sheetId = CellRange.sheetIdByName(spreadsheet, sheetName);
            if (sheetId == null) {
                logError("Sheet '" + sheetName + "' not found");
                System.exit(1);
            }
        }

        int written = 0;
        if (!dryRun && !diff.changes().isEmpty()) {
            List<ValueRange> data = new ArrayList<>();
            List<Request> formatRequests = new ArrayList<>();
            for (CellChange c : diff.changes()) {
                Object newVal = c.newVal();
                if (asDate) {
                    try {
                        newVal = CellDate.toSerial(c.newVal(), dateType, dateFormat);
                        formatRequests.add(CellNumberFormat.buildRequest(CellRange.toGridRange(sheetId, c.cell()), dateType, dateFormat));
                    } catch (IllegalArgumentException e) {
                        // pass-through: leave newVal as the literal string
                    }
                }
                data.add(new ValueRange()
                    .setRange(CellRange.a1Ref(sheetName, c.cell()))
                    .setValues(List.of(List.of(newVal))));
            }
            sheetsService.spreadsheets().values()
                .batchUpdate(spreadsheetId, new BatchUpdateValuesRequest()
                    .setValueInputOption(userEntered ? "USER_ENTERED" : "RAW")
                    .setData(data))
                .execute();
            written = data.size();

            if (!formatRequests.isEmpty()) {
                sheetsService.spreadsheets().batchUpdate(spreadsheetId,
                    new BatchUpdateSpreadsheetRequest().setRequests(formatRequests)).execute();
            }
        }

        System.out.println(UpdateOutput.format(diff, dryRun, written, format));
        logInfo("Update affected=" + diff.affected() + " changed=" + diff.changes().size() + " written=" + written);
    }

    /** --date-type DATE|TIME|DATE_TIME, defaulting to DATE; validates against CellDate's supported set. */
    private static String dateTypeOf(String raw) {
        var type = raw != null ? raw.toUpperCase() : "DATE";
        if (!Set.of("DATE", "TIME", "DATE_TIME").contains(type)) {
            logError("--date-type must be DATE|TIME|DATE_TIME");
            System.exit(1);
        }
        return type;
    }

    // ─── write ───────────────────────────────────────────────────────────────

    private static void handleWrite(String spreadsheetId, List<String> argList) throws IOException {
        boolean userEntered = removeFlag(argList, "--user-entered");
        removeFlag(argList, "--raw"); // explicit --raw is no-op; RAW is the default
        boolean asDate = removeFlag(argList, "--as-date");
        String dateType = dateTypeOf(extractFlagValue(argList, "--date-type"));
        String dateFormat = extractFlagValue(argList, "--date-format");
        String tsvFile   = extractFlagValue(argList, "--tsv-file");
        String csvFile   = extractFlagValue(argList, "--csv-file");
        String jsonFile  = extractFlagValue(argList, "--json-file");
        String tsvInline = extractFlagValue(argList, "--tsv");
        String jsonInline = extractFlagValue(argList, "--json");

        if (argList.size() < 2) {
            logError("write: requires <sheetName> <address>");
            System.exit(1);
        }
        String sheetName = argList.remove(0);
        String address   = argList.remove(0);
        String inlineValue = argList.isEmpty() ? null : argList.remove(0);

        logInfo("Writing to: " + sheetName + "!" + address);

        String apiRange = CellRange.a1Ref(sheetName, address);
        ValueRange body;
        boolean isBulk;

        if (tsvFile != null || csvFile != null || jsonFile != null || tsvInline != null || jsonInline != null) {
            List<List<Object>> values = loadWriteData(tsvFile, csvFile, jsonFile, tsvInline, jsonInline);
            body = new ValueRange().setValues(values);
            isBulk = true;
        } else if (inlineValue != null) {
            body = new ValueRange().setValues(List.of(List.of(inlineValue)));
            isBulk = false;
        } else {
            logError("write: no value. Provide <value> or --tsv-file/--tsv/--csv-file/--json-file/--json");
            System.exit(1);
            return;
        }

        if (asDate) {
            handleWriteAsDate(spreadsheetId, sheetName, address, body.getValues(), dateType, dateFormat);
            logInfo("Write completed");
            return;
        }

        sheetsService.spreadsheets().values()
            .update(spreadsheetId, apiRange, body)
            .setValueInputOption(userEntered ? "USER_ENTERED" : "RAW")
            .execute();

        if (!quiet) {
            if (isBulk) {
                int rows = body.getValues().size();
                int cols = body.getValues().stream().mapToInt(List::size).max().orElse(0);
                System.out.println("SUCCESS: wrote " + rows + " rows × " + cols + " cols to " + sheetName + "!" + address);
            } else {
                System.out.println("SUCCESS: Written to " + sheetName + "!" + address);
            }
        }
        logInfo("Write completed");
    }

    /**
     * --as-date: bypasses the values API's RAW/USER_ENTERED choice entirely — parses each non-blank
     * string as `dateType`, writes it as a numeric Sheets serial, and applies a matching number
     * format, all in one batchUpdate, so the cell renders as a real date regardless of locale.
     * Fails loudly on the first value that doesn't parse (write is one homogeneous declared block).
     */
    private static void handleWriteAsDate(String spreadsheetId, String sheetName, String address,
                                           List<List<Object>> values, String dateType, String dateFormat) throws IOException {
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        Integer sheetId = CellRange.sheetIdByName(spreadsheet, sheetName);
        if (sheetId == null) {
            logError("Sheet '" + sheetName + "' not found");
            System.exit(1);
            return;
        }

        var startToken = address.toUpperCase().split(":", 2)[0];
        int startCol0 = CellRange.letterToCol(CellRange.colOf(startToken)) - 1;
        int startRow0 = CellRange.rowOf(startToken) - 1;

        var rows = new ArrayList<RowData>();
        for (var row : values) {
            var cells = new ArrayList<CellData>();
            for (var v : row) {
                var s = v == null ? "" : v.toString();
                if (s.isBlank()) { cells.add(new CellData()); continue; }
                double serial;
                try {
                    serial = CellDate.toSerial(s, dateType, dateFormat);
                } catch (IllegalArgumentException e) {
                    logError("write --as-date: " + e.getMessage());
                    System.exit(1);
                    return;
                }
                cells.add(new CellData().setUserEnteredValue(new ExtendedValue().setNumberValue(serial)));
            }
            rows.add(new RowData().setValues(cells));
        }

        int numRows = rows.size();
        int numCols = rows.stream().mapToInt(r -> r.getValues().size()).max().orElse(0);
        var gridRange = new GridRange().setSheetId(sheetId)
            .setStartRowIndex(startRow0).setStartColumnIndex(startCol0)
            .setEndRowIndex(startRow0 + numRows).setEndColumnIndex(startCol0 + numCols);

        var updateCells = new Request().setUpdateCells(new UpdateCellsRequest()
            .setStart(new GridCoordinate().setSheetId(sheetId).setRowIndex(startRow0).setColumnIndex(startCol0))
            .setRows(rows).setFields("userEnteredValue"));
        var formatReq = CellNumberFormat.buildRequest(gridRange, dateType, dateFormat);

        sheetsService.spreadsheets().batchUpdate(spreadsheetId,
            new BatchUpdateSpreadsheetRequest().setRequests(List.of(updateCells, formatReq))).execute();

        if (!quiet) {
            System.out.println("SUCCESS: wrote " + numRows + " rows × " + numCols + " cols as " + dateType
                + " to " + sheetName + "!" + address);
        }
    }

    // ─── append ──────────────────────────────────────────────────────────────

    private static void handleAppend(String spreadsheetId, List<String> argList) throws IOException {
        boolean userEntered = removeFlag(argList, "--user-entered");
        removeFlag(argList, "--raw");
        boolean asDate = removeFlag(argList, "--as-date");
        String dateType = dateTypeOf(extractFlagValue(argList, "--date-type"));
        String dateFormat = extractFlagValue(argList, "--date-format");
        String tsvFile    = extractFlagValue(argList, "--tsv-file");
        String csvFile    = extractFlagValue(argList, "--csv-file");
        String jsonFile   = extractFlagValue(argList, "--json-file");
        String tsvInline  = extractFlagValue(argList, "--tsv");
        String jsonInline = extractFlagValue(argList, "--json");

        if (argList.isEmpty()) {
            logError("append: requires <sheetName>");
            System.exit(1);
        }
        String sheetName  = argList.remove(0);
        String startRange = argList.isEmpty() ? "A1" : argList.remove(0);
        String apiRange   = CellRange.a1Ref(sheetName, startRange);

        List<List<Object>> values = loadWriteData(tsvFile, csvFile, jsonFile, tsvInline, jsonInline);
        if (values == null || values.isEmpty()) {
            logError("append: no data. Use --tsv-file/--tsv/--csv-file/--json-file/--json");
            System.exit(1);
            return;
        }

        if (asDate) {
            values = asDateSerials(values, dateType, dateFormat, "append");
        }

        logInfo("Appending to: " + apiRange);

        AppendValuesResponse result = sheetsService.spreadsheets().values()
            .append(spreadsheetId, apiRange, new ValueRange().setValues(values))
            .setValueInputOption(userEntered ? "USER_ENTERED" : "RAW")
            .setInsertDataOption("INSERT_ROWS")
            .execute();

        if (asDate && result.getUpdates() != null && result.getUpdates().getUpdatedRange() != null) {
            String updatedRange = result.getUpdates().getUpdatedRange();
            Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
            Integer sheetId = CellRange.sheetIdByName(spreadsheet, sheetName);
            if (sheetId != null) {
                var gridRange = CellRange.toGridRange(sheetId, CellRange.rangeOnlyOf(updatedRange));
                sheetsService.spreadsheets().batchUpdate(spreadsheetId, new BatchUpdateSpreadsheetRequest()
                    .setRequests(List.of(CellNumberFormat.buildRequest(gridRange, dateType, dateFormat)))).execute();
            }
        }

        if (!quiet) {
            String written = (result.getUpdates() != null) ? result.getUpdates().getUpdatedRange() : apiRange;
            int rows = values.size();
            int cols = values.stream().mapToInt(List::size).max().orElse(0);
            System.out.println("SUCCESS: appended " + rows + " rows × " + cols + " cols to " + written
                + (asDate ? " (as " + dateType + ")" : ""));
        }
        logInfo("Append completed");
    }

    /** --as-date for write/append: converts every non-blank string in a homogeneous data block to a
     *  Sheets date serial, failing loudly on the first cell that doesn't parse. */
    private static List<List<Object>> asDateSerials(List<List<Object>> values, String dateType, String dateFormat, String cmd) {
        var out = new ArrayList<List<Object>>();
        for (var row : values) {
            var newRow = new ArrayList<Object>();
            for (var v : row) {
                var s = v == null ? "" : v.toString();
                if (s.isBlank()) { newRow.add(""); continue; }
                try {
                    newRow.add(CellDate.toSerial(s, dateType, dateFormat));
                } catch (IllegalArgumentException e) {
                    logError(cmd + " --as-date: " + e.getMessage());
                    System.exit(1);
                }
            }
            out.add(newRow);
        }
        return out;
    }

    // ─── clear ───────────────────────────────────────────────────────────────

    private static void handleClear(String spreadsheetId, String sheetName, String address) throws IOException {
        logInfo("Clearing: " + sheetName + "!" + address);

        sheetsService.spreadsheets().values()
            .clear(spreadsheetId, CellRange.a1Ref(sheetName, address), new ClearValuesRequest())
            .execute();

        if (!quiet) {
            System.out.println("SUCCESS: Cleared " + sheetName + "!" + address);
        }
    }

    // ─── listsheet / createsheet / renamesheet ────────────────────────────────

    private static void handleListSheets(String spreadsheetId) throws IOException {
        logInfo("Listing sheets: " + spreadsheetId);

        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        for (Sheet sheet : spreadsheet.getSheets()) {
            String name = sheet.getProperties().getTitle();
            Integer id   = sheet.getProperties().getSheetId();
            System.out.println(name + " (ID: " + id + ")");
        }
        logInfo("Listed " + spreadsheet.getSheets().size() + " sheet(s)");
    }

    private static void handleCreateSheet(String spreadsheetId, String sheetName) throws IOException {
        logInfo("Creating sheet: " + sheetName);

        BatchUpdateSpreadsheetRequest req = new BatchUpdateSpreadsheetRequest().setRequests(
            List.of(new Request().setAddSheet(
                new AddSheetRequest().setProperties(new SheetProperties().setTitle(sheetName)))));
        sheetsService.spreadsheets().batchUpdate(spreadsheetId, req).execute();

        System.out.println("SUCCESS: Created sheet '" + sheetName + "'");
    }

    private static void handleRenameSheet(String spreadsheetId, String oldName, String newName) throws IOException {
        logInfo("Renaming: '" + oldName + "' → '" + newName + "'");

        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        Integer sheetId = spreadsheet.getSheets().stream()
            .filter(s -> oldName.equals(s.getProperties().getTitle()))
            .map(s -> s.getProperties().getSheetId())
            .findFirst()
            .orElse(null);

        if (sheetId == null) {
            logError("Sheet '" + oldName + "' not found");
            System.exit(1);
        }

        BatchUpdateSpreadsheetRequest req = new BatchUpdateSpreadsheetRequest().setRequests(
            List.of(new Request().setUpdateSheetProperties(
                new UpdateSheetPropertiesRequest()
                    .setProperties(new SheetProperties().setSheetId(sheetId).setTitle(newName))
                    .setFields("title"))));
        sheetsService.spreadsheets().batchUpdate(spreadsheetId, req).execute();

        System.out.println("SUCCESS: Renamed '" + oldName + "' → '" + newName + "'");
    }

    // ─── freeze / format / validate ──────────────────────────────────────────

    private static void handleFreeze(String spreadsheetId, String sheetName, List<String> argList) throws IOException {
        String rowsStr = extractFlagValue(argList, "--rows");
        String colsStr = extractFlagValue(argList, "--cols");
        if (rowsStr == null && colsStr == null) {
            logError("freeze: requires --rows N and/or --cols N");
            System.exit(1);
        }

        logInfo("Freezing " + sheetName + " rows=" + rowsStr + " cols=" + colsStr);

        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        Integer sheetId = CellRange.sheetIdByName(spreadsheet, sheetName);
        if (sheetId == null) {
            logError("Sheet '" + sheetName + "' not found");
            System.exit(1);
        }

        var gridProps = new GridProperties();
        var fields = new ArrayList<String>();
        if (rowsStr != null) { gridProps.setFrozenRowCount(Integer.parseInt(rowsStr)); fields.add("gridProperties.frozenRowCount"); }
        if (colsStr != null) { gridProps.setFrozenColumnCount(Integer.parseInt(colsStr)); fields.add("gridProperties.frozenColumnCount"); }
        var props = new SheetProperties().setSheetId(sheetId).setGridProperties(gridProps);

        var req = new BatchUpdateSpreadsheetRequest().setRequests(
            List.of(new Request().setUpdateSheetProperties(
                new UpdateSheetPropertiesRequest().setProperties(props).setFields(String.join(",", fields)))));
        sheetsService.spreadsheets().batchUpdate(spreadsheetId, req).execute();

        if (!quiet) {
            System.out.println("SUCCESS: froze " + sheetName
                + (rowsStr != null ? " rows=" + rowsStr : "") + (colsStr != null ? " cols=" + colsStr : ""));
        }
    }

    private static void handleInsertColumn(String spreadsheetId, String sheetName, String colLetter, List<String> argList) throws IOException {
        String countStr = extractFlagValue(argList, "--count");
        int count = countStr != null ? Integer.parseInt(countStr) : 1;

        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        Sheet sheet = spreadsheet.getSheets().stream()
            .filter(s -> sheetName.equals(s.getProperties().getTitle()))
            .findFirst().orElse(null);
        if (sheet == null) {
            logError("Sheet '" + sheetName + "' not found");
            System.exit(1);
            return;
        }
        int sheetId = sheet.getProperties().getSheetId();

        // letterToCol is 1-based (documented on CellRange.bounds); GridRange indices are 0-based.
        int startIndex = CellRange.letterToCol(colLetter) - 1;
        var dimRange = new DimensionRange()
            .setSheetId(sheetId)
            .setDimension("COLUMNS")
            .setStartIndex(startIndex)
            .setEndIndex(startIndex + count);

        // InsertDimension shifts existing columns within the CURRENT grid bounds - if the sheet's
        // grid is already sized exactly to the data (no spare columns), the shift has nowhere to go
        // at the far edge. Grow the grid by `count` columns first, in the same batch, so there's
        // always room regardless of how tightly the grid was sized.
        var currentCols = sheet.getProperties().getGridProperties().getColumnCount();
        var growProps = new SheetProperties().setSheetId(sheetId)
            .setGridProperties(new GridProperties().setColumnCount(currentCols + count));

        var req = new BatchUpdateSpreadsheetRequest().setRequests(List.of(
            new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                .setProperties(growProps).setFields("gridProperties.columnCount")),
            new Request().setInsertDimension(
                new InsertDimensionRequest().setRange(dimRange).setInheritFromBefore(false))));
        sheetsService.spreadsheets().batchUpdate(spreadsheetId, req).execute();

        if (!quiet) {
            System.out.println("SUCCESS: inserted " + count + " column(s) at " + sheetName + "!" + colLetter
                + " (everything at/after " + colLetter + " shifted right; existing formulas/validation/formatting shift with it)");
        }
    }

    private static void handleDeleteColumn(String spreadsheetId, String sheetName, String colLetter, List<String> argList) throws IOException {
        String countStr = extractFlagValue(argList, "--count");
        int count = countStr != null ? Integer.parseInt(countStr) : 1;

        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        Integer sheetId = CellRange.sheetIdByName(spreadsheet, sheetName);
        if (sheetId == null) {
            logError("Sheet '" + sheetName + "' not found");
            System.exit(1);
            return;
        }

        // letterToCol is 1-based (documented on CellRange.bounds); GridRange indices are 0-based.
        int startIndex = CellRange.letterToCol(colLetter) - 1;
        var dimRange = new DimensionRange()
            .setSheetId(sheetId)
            .setDimension("COLUMNS")
            .setStartIndex(startIndex)
            .setEndIndex(startIndex + count);

        var req = new BatchUpdateSpreadsheetRequest().setRequests(
            List.of(new Request().setDeleteDimension(new DeleteDimensionRequest().setRange(dimRange))));
        sheetsService.spreadsheets().batchUpdate(spreadsheetId, req).execute();

        if (!quiet) {
            System.out.println("SUCCESS: deleted " + count + " column(s) at " + sheetName + "!" + colLetter
                + " (everything after it shifted left)");
        }
    }

    private static void handleInsertRow(String spreadsheetId, String sheetName, String rowNumStr, List<String> argList) throws IOException {
        String countStr = extractFlagValue(argList, "--count");
        int count = countStr != null ? Integer.parseInt(countStr) : 1;

        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        Sheet sheet = spreadsheet.getSheets().stream()
            .filter(s -> sheetName.equals(s.getProperties().getTitle()))
            .findFirst().orElse(null);
        if (sheet == null) {
            logError("Sheet '" + sheetName + "' not found");
            System.exit(1);
            return;
        }
        int sheetId = sheet.getProperties().getSheetId();

        int startIndex = Integer.parseInt(rowNumStr) - 1;
        var dimRange = new DimensionRange()
            .setSheetId(sheetId)
            .setDimension("ROWS")
            .setStartIndex(startIndex)
            .setEndIndex(startIndex + count);

        var currentRows = sheet.getProperties().getGridProperties().getRowCount();
        var growProps = new SheetProperties().setSheetId(sheetId)
            .setGridProperties(new GridProperties().setRowCount(currentRows + count));

        var req = new BatchUpdateSpreadsheetRequest().setRequests(List.of(
            new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                .setProperties(growProps).setFields("gridProperties.rowCount")),
            new Request().setInsertDimension(
                new InsertDimensionRequest().setRange(dimRange).setInheritFromBefore(false))));
        sheetsService.spreadsheets().batchUpdate(spreadsheetId, req).execute();

        if (!quiet) {
            System.out.println("SUCCESS: inserted " + count + " row(s) at " + sheetName + "!" + rowNumStr
                + " (everything at/after row " + rowNumStr + " shifted down; existing formulas/validation/formatting shift with it)");
        }
    }

    private static void handleFormat(String spreadsheetId, String sheetName, String range, List<String> argList) throws IOException {
        String bg = extractFlagValue(argList, "--bg");
        String text = extractFlagValue(argList, "--text");
        Boolean bold = removeFlag(argList, "--bold") ? Boolean.TRUE : null;
        Boolean italic = removeFlag(argList, "--italic") ? Boolean.TRUE : null;
        String align = extractFlagValue(argList, "--align");
        String valign = extractFlagValue(argList, "--valign");
        String fontSizeStr = extractFlagValue(argList, "--font-size");
        Integer fontSize = fontSizeStr != null ? Integer.parseInt(fontSizeStr) : null;
        boolean wrap = removeFlag(argList, "--wrap");

        logInfo("Formatting " + sheetName + "!" + range);

        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        Integer sheetId = CellRange.sheetIdByName(spreadsheet, sheetName);
        if (sheetId == null) {
            logError("Sheet '" + sheetName + "' not found");
            System.exit(1);
        }

        var gridRange = CellRange.toGridRange(sheetId, range);
        Request req;
        try {
            req = CellStyle.buildRequest(gridRange, bg, text, bold, italic, align, valign, fontSize, wrap);
        } catch (IllegalArgumentException e) {
            logError("format: " + e.getMessage());
            System.exit(1);
            return;
        }

        sheetsService.spreadsheets().batchUpdate(spreadsheetId,
            new BatchUpdateSpreadsheetRequest().setRequests(List.of(req))).execute();

        if (!quiet) System.out.println("SUCCESS: formatted " + sheetName + "!" + range);
    }

    private static void handleNumberFormat(String spreadsheetId, String sheetName, String range, List<String> argList) throws IOException {
        boolean clear = removeFlag(argList, "--clear");
        String type = extractFlagValue(argList, "--type");
        String pattern = extractFlagValue(argList, "--pattern");
        if (!clear && type == null) {
            logError("numberformat: requires --type <TYPE> (or --clear)");
            System.exit(1);
        }
        if (clear && (type != null || pattern != null)) {
            logError("numberformat: --clear can't combine with --type/--pattern");
            System.exit(1);
        }

        logInfo("Number-formatting " + sheetName + "!" + range + " type=" + type + " clear=" + clear);

        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        Integer sheetId = CellRange.sheetIdByName(spreadsheet, sheetName);
        if (sheetId == null) {
            logError("Sheet '" + sheetName + "' not found");
            System.exit(1);
        }

        var gridRange = CellRange.toGridRange(sheetId, range);
        Request req;
        try {
            req = clear ? CellNumberFormat.buildClearRequest(gridRange) : CellNumberFormat.buildRequest(gridRange, type, pattern);
        } catch (IllegalArgumentException e) {
            logError("numberformat: " + e.getMessage());
            System.exit(1);
            return;
        }

        sheetsService.spreadsheets().batchUpdate(spreadsheetId,
            new BatchUpdateSpreadsheetRequest().setRequests(List.of(req))).execute();

        if (!quiet) {
            System.out.println(clear
                ? "SUCCESS: cleared number format on " + sheetName + "!" + range
                : "SUCCESS: set number format (" + type.toUpperCase() + ") on " + sheetName + "!" + range);
        }
    }

    /**
     * Sets up the dropdown; optionally --colors also color-codes the data range and, with
     * --color-legend, the legend range itself — one command that stays in sync when the allowed
     * set changes (re-run it; highlight refreshes rather than stacking duplicates).
     */
    private static void handleValidate(String spreadsheetId, String sheetName, String range, List<String> argList) throws IOException {
        boolean clear = removeFlag(argList, "--clear");
        String fromRange = extractFlagValue(argList, "--from-range");
        String list = extractFlagValue(argList, "--list");
        String perRowFormula = extractFlagValue(argList, "--per-row-formula");
        boolean warn = removeFlag(argList, "--warn");
        removeFlag(argList, "--strict"); // strict is the default; explicit flag is a no-op
        String colorsArg = extractFlagValue(argList, "--colors");
        boolean colorLegend = removeFlag(argList, "--color-legend");

        if (!clear && fromRange == null && list == null && perRowFormula == null) {
            logError("validate: requires --from-range <RangeRef>, --list a,b,c, or --per-row-formula <template> (or --clear)");
            System.exit(1);
        }
        if ((fromRange != null ? 1 : 0) + (list != null ? 1 : 0) + (perRowFormula != null ? 1 : 0) > 1) {
            logError("validate: --from-range, --list and --per-row-formula are mutually exclusive");
            System.exit(1);
        }
        if (clear && (colorsArg != null || colorLegend)) {
            logError("validate: --colors/--color-legend can't combine with --clear (use 'highlight --clear' separately)");
            System.exit(1);
        }
        if (colorLegend && fromRange == null) {
            logError("validate: --color-legend requires --from-range (there's no legend location for --list)");
            System.exit(1);
        }
        if (perRowFormula != null && (colorsArg != null || colorLegend)) {
            logError("validate: --colors/--color-legend aren't supported with --per-row-formula");
            System.exit(1);
        }

        logInfo("Validating " + sheetName + "!" + range + " clear=" + clear);

        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId)
            .setFields("sheets(properties,conditionalFormats)")
            .execute();
        Sheet sheet = spreadsheet.getSheets().stream()
            .filter(s -> sheetName.equals(s.getProperties().getTitle()))
            .findFirst().orElse(null);
        if (sheet == null) {
            logError("Sheet '" + sheetName + "' not found");
            System.exit(1);
            return;
        }
        int sheetId = sheet.getProperties().getSheetId();

        var requests = new ArrayList<Request>();
        if (perRowFormula != null) {
            int[] b;
            try {
                b = CellRange.bounds(range);
            } catch (NumberFormatException e) {
                logError("validate: --per-row-formula needs an explicit start AND end row in <range>, e.g. I2:I501 (open-ended ranges like I2:I can't be expanded per-row)");
                System.exit(1);
                return;
            }
            requests.addAll(CellValidation.buildPerRowRequests(sheetId, b[0], b[2], b[1], b[3], perRowFormula, !warn));
        } else {
            var gridRange = CellRange.toGridRange(sheetId, range);
            requests.add(clear
                ? CellValidation.buildClearRequest(gridRange)
                : CellValidation.buildSetRequest(gridRange, fromRange, list, !warn));

            boolean wantColor = !clear && (colorsArg != null || colorLegend);
            if (wantColor) {
                List<String> values = resolveValues(spreadsheetId, fromRange, list);
                if (values.isEmpty()) {
                    logError("validate: --colors/--color-legend has no values to color");
                    System.exit(1);
                }
                List<String> colors = resolveColors(colorsArg);

                Sheet legendSheet = null;
                GridRange legendGridRange = null;
                if (colorLegend) {
                    String legendSheetName = CellRange.sheetNameOf(fromRange, sheetName);
                    legendSheet = spreadsheet.getSheets().stream()
                        .filter(s -> legendSheetName.equals(s.getProperties().getTitle()))
                        .findFirst().orElse(null);
                    if (legendSheet == null) {
                        logError("validate: legend sheet '" + legendSheetName + "' not found");
                        System.exit(1);
                        return;
                    }
                    legendGridRange = CellRange.toGridRange(legendSheet.getProperties().getSheetId(), CellRange.rangeOnlyOf(fromRange));
                }

                if (legendSheet != null && legendSheet.getProperties().getSheetId() == sheetId) {
                    // Data range and legend share a sheet → one combined refresh so their delete indices
                    // don't corrupt each other mid-batch.
                    requests.addAll(CellHighlight.buildRefreshRequestsMulti(sheet.getConditionalFormats(), sheetId,
                        List.of(gridRange, legendGridRange), List.of(values, values), List.of(colors, colors)));
                } else {
                    requests.addAll(CellHighlight.buildRefreshRequests(sheet.getConditionalFormats(), sheetId, gridRange, values, colors));
                    if (legendSheet != null) {
                        requests.addAll(CellHighlight.buildRefreshRequests(legendSheet.getConditionalFormats(),
                            legendSheet.getProperties().getSheetId(), legendGridRange, values, colors));
                    }
                }
            }
        }

        sheetsService.spreadsheets().batchUpdate(spreadsheetId,
            new BatchUpdateSpreadsheetRequest().setRequests(requests)).execute();

        if (!quiet) {
            var msg = new StringBuilder("SUCCESS: " + (clear ? "cleared validation on " : perRowFormula != null ? "validated (per-row) " : "validated ") + sheetName + "!" + range);
            if (!clear && perRowFormula == null && (colorsArg != null || colorLegend)) {
                msg.append(colorLegend ? " + colored data range + legend" : " + colored data range");
            }
            System.out.println(msg.toString());
        }
    }

    /**
     * Color-code exact-text matches so different enum values (e.g. contacted/replied/bounced)
     * read at a glance — a conditional-format rule per distinct value, background color only.
     * Values/colors come from the same --from-range/--list pattern as `validate` (or, with
     * --colors-from-range, both are read directly off an already-colored legend). Re-running on
     * the same range replaces its rules rather than stacking duplicates, so bumping the value/color
     * set (e.g. a new enum value) is just "run it again" — no manual cleanup needed.
     */
    private static void handleHighlight(String spreadsheetId, String sheetName, String range, List<String> argList) throws IOException {
        boolean clear = removeFlag(argList, "--clear");
        String fromRange = extractFlagValue(argList, "--from-range");
        String list = extractFlagValue(argList, "--list");
        String colorsArg = extractFlagValue(argList, "--colors");
        String colorsFromRange = extractFlagValue(argList, "--colors-from-range");

        int sourceCount = (fromRange != null ? 1 : 0) + (list != null ? 1 : 0) + (colorsFromRange != null ? 1 : 0);
        if (!clear && sourceCount == 0) {
            logError("highlight: requires --from-range, --list, or --colors-from-range (or --clear)");
            System.exit(1);
        }
        if (sourceCount > 1) {
            logError("highlight: --from-range, --list and --colors-from-range are mutually exclusive");
            System.exit(1);
        }
        if (colorsFromRange != null && colorsArg != null) {
            logError("highlight: --colors-from-range already supplies colors; --colors is not needed");
            System.exit(1);
        }

        logInfo("Highlighting " + sheetName + "!" + range + " clear=" + clear);

        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId)
            .setFields("sheets(properties,conditionalFormats)")
            .execute();
        Sheet sheet = spreadsheet.getSheets().stream()
            .filter(s -> sheetName.equals(s.getProperties().getTitle()))
            .findFirst().orElse(null);
        if (sheet == null) {
            logError("Sheet '" + sheetName + "' not found");
            System.exit(1);
            return;
        }
        int sheetId = sheet.getProperties().getSheetId();
        var gridRange = CellRange.toGridRange(sheetId, range);

        if (clear) {
            var indices = CellHighlight.matchingIndices(sheet.getConditionalFormats(), gridRange);
            if (indices.isEmpty()) {
                if (!quiet) System.out.println("SUCCESS: no highlight rules found on " + sheetName + "!" + range);
                return;
            }
            sheetsService.spreadsheets().batchUpdate(spreadsheetId,
                new BatchUpdateSpreadsheetRequest().setRequests(CellHighlight.buildDeleteRequests(sheetId, indices))).execute();
            if (!quiet) System.out.println("SUCCESS: cleared " + indices.size() + " highlight rule(s) from " + sheetName + "!" + range);
            return;
        }

        List<String> values;
        List<String> colors;
        if (colorsFromRange != null) {
            var pairs = valueColorPairs(spreadsheetId, colorsFromRange);
            if (pairs.isEmpty()) {
                logError("highlight: --colors-from-range has no colored data");
                System.exit(1);
            }
            values = pairs.stream().map(p -> p[0]).toList();
            colors = pairs.stream().map(p -> p[1]).toList();
        } else {
            values = resolveValues(spreadsheetId, fromRange, list);
            if (values.isEmpty()) {
                logError("highlight: no values to color (empty --list, or --from-range has no data)");
                System.exit(1);
            }
            colors = resolveColors(colorsArg);
        }

        var requests = CellHighlight.buildRefreshRequests(sheet.getConditionalFormats(), sheetId, gridRange, values, colors);
        sheetsService.spreadsheets().batchUpdate(spreadsheetId,
            new BatchUpdateSpreadsheetRequest().setRequests(requests)).execute();

        if (!quiet) {
            List<String> pairs = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) pairs.add(values.get(i) + "=" + colors.get(i % colors.size()));
            System.out.println("SUCCESS: highlighted " + sheetName + "!" + range + " (" + String.join(", ", pairs) + ")");
        }
    }

    /** Distinct, non-blank cell values from an A1 range reference (e.g. "'Allowed Values'!A2:A"), in sheet order. */
    private static List<String> distinctValues(String spreadsheetId, String rangeRef) throws IOException {
        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, rangeRef).execute();
        List<String> out = new ArrayList<>();
        var seen = new java.util.LinkedHashSet<String>();
        if (response.getValues() != null) {
            for (List<Object> row : response.getValues()) {
                for (Object cell : row) {
                    String s = cell == null ? "" : cell.toString().trim();
                    if (!s.isEmpty() && seen.add(s)) out.add(s);
                }
            }
        }
        return out;
    }

    /** Values from --from-range (deduped, sheet order) or --list — shared by `highlight` and `validate`. */
    private static List<String> resolveValues(String spreadsheetId, String fromRange, String list) throws IOException {
        return (fromRange != null) ? distinctValues(spreadsheetId, fromRange)
            : Stream.of(list.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    /** Colors from --colors, or the built-in default palette (cycled) — shared by `highlight` and `validate`. */
    private static List<String> resolveColors(String colorsArg) {
        return (colorsArg != null) ? Stream.of(colorsArg.split(",")).map(String::trim).toList() : CliColor.DEFAULT_PALETTE;
    }

    /** {value, backgroundColorHex} pairs read directly off a legend range's actual cells (--colors-from-range). */
    private static List<String[]> valueColorPairs(String spreadsheetId, String rangeRef) throws IOException {
        Spreadsheet ss = sheetsService.spreadsheets().get(spreadsheetId)
            .setRanges(List.of(rangeRef))
            .setFields("sheets(data(rowData(values(formattedValue,effectiveFormat.backgroundColor))))")
            .execute();
        var out = new ArrayList<String[]>();
        var seen = new java.util.LinkedHashSet<String>();
        if (ss.getSheets() == null) return out;
        for (Sheet sheet : ss.getSheets()) {
            if (sheet.getData() == null) continue;
            for (GridData gd : sheet.getData()) {
                List<RowData> rows = gd.getRowData();
                if (rows == null) continue;
                for (RowData rd : rows) {
                    List<CellData> cells = rd.getValues();
                    if (cells == null) continue;
                    for (CellData cell : cells) {
                        if (cell == null || cell.getFormattedValue() == null) continue;
                        String value = cell.getFormattedValue().trim();
                        if (value.isEmpty() || !seen.add(value)) continue;
                        String hex = (cell.getEffectiveFormat() != null)
                            ? CellInspect.colorToHex(cell.getEffectiveFormat().getBackgroundColor())
                            : null;
                        out.add(new String[]{ value, hex != null ? hex : "#ffffff" });
                    }
                }
            }
        }
        return out;
    }

    // ─── filter ──────────────────────────────────────────────────────────────

    /**
     * Turn the "Create a filter" funnel buttons on (or off with --clear) over a range — the
     * range's first row becomes the filterable header. No range → the whole sheet (buttons on
     * row 1). A sheet has exactly one basic filter, so re-running just replaces it.
     */
    private static void handleFilter(String spreadsheetId, String sheetName, List<String> argList) throws IOException {
        boolean clear = removeFlag(argList, "--clear");
        String range = argList.isEmpty() ? null : argList.remove(0);

        logInfo("Filter " + sheetName + (range != null ? "!" + range : "") + " clear=" + clear);

        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        Integer sheetId = CellRange.sheetIdByName(spreadsheet, sheetName);
        if (sheetId == null) {
            logError("Sheet '" + sheetName + "' not found");
            System.exit(1);
        }

        Request req = clear
            ? CellFilter.buildClearRequest(sheetId)
            : CellFilter.buildSetRequest(range != null
                ? CellRange.toGridRange(sheetId, range)
                : new GridRange().setSheetId(sheetId));

        sheetsService.spreadsheets().batchUpdate(spreadsheetId,
            new BatchUpdateSpreadsheetRequest().setRequests(List.of(req))).execute();

        if (!quiet) {
            System.out.println("SUCCESS: " + (clear ? "cleared filter on " : "enabled filter on ")
                + sheetName + (range != null && !clear ? "!" + range : ""));
        }
    }

    // ─── describeschema ─────────────────────────────────────────────────────

    /**
     * Ports the design of a separate reference tool (a JavaFX schema-export app, not a dependency):
     * per sheet, the header row (with each cell's note as its "comment") plus a diversified sample
     * of data rows — grouped by a uniqueness-criteria column, one random row per distinct value,
     * topped up randomly if there aren't enough distinct values — so an AI agent consuming the
     * export sees varied real data rather than the first N rows. 3 API calls regardless of sheet count.
     */
    private static void handleDescribeSchema(String spreadsheetId, List<String> argList) throws IOException {
        String sheetsArg = extractFlagValue(argList, "--sheets");
        String sampleRowsStr = extractFlagValue(argList, "--sample-rows");
        String sampleSizeStr = extractFlagValue(argList, "--sample-size");
        String uniquenessCol = extractFlagValue(argList, "--uniqueness-col");
        String truncateStr = extractFlagValue(argList, "--truncate");
        String headerRowStr = extractFlagValue(argList, "--header-row");
        String dataStartRowStr = extractFlagValue(argList, "--data-start-row");
        String colStartStr = extractFlagValue(argList, "--col-start");
        String colEndStr = extractFlagValue(argList, "--col-end");
        String notes = extractFlagValue(argList, "--notes");
        String format = extractFlagValue(argList, "--format");
        if (format == null) format = "json";

        int sampleRowsAnalyzed = sampleRowsStr != null ? Integer.parseInt(sampleRowsStr) : 100;
        int sampleSize = sampleSizeStr != null ? Integer.parseInt(sampleSizeStr) : 5;
        if (uniquenessCol == null) uniquenessCol = "A";
        int truncateLen = truncateStr != null ? Integer.parseInt(truncateStr) : 1024;
        int headerRowNum = headerRowStr != null ? Integer.parseInt(headerRowStr) : 1;
        int dataStartRow = dataStartRowStr != null ? Integer.parseInt(dataStartRowStr) : headerRowNum + 1;
        int colStart = colStartStr != null ? CellRange.letterToCol(colStartStr.toUpperCase()) : 1;
        Integer colEndOverride = colEndStr != null ? CellRange.letterToCol(colEndStr.toUpperCase()) : null;

        List<String> requestedSheets = sheetsArg != null
            ? Stream.of(sheetsArg.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
            : List.of();

        boolean perSheetOverride = headerRowStr != null || dataStartRowStr != null || colStartStr != null || colEndStr != null;
        if (perSheetOverride && requestedSheets.size() != 1) {
            logError("describeschema: --header-row/--data-start-row/--col-start/--col-end require --sheets naming exactly one sheet");
            System.exit(1);
        }

        logInfo("describeschema " + spreadsheetId + " sheets=" + requestedSheets + " sampleRows=" + sampleRowsAnalyzed
            + " sampleSize=" + sampleSize + " uniquenessCol=" + uniquenessCol);

        Spreadsheet meta = sheetsService.spreadsheets().get(spreadsheetId)
            .setFields("properties.title,sheets.properties").execute();

        List<Sheet> targetSheets = new ArrayList<>();
        for (Sheet s : meta.getSheets()) {
            String title = s.getProperties().getTitle();
            if (requestedSheets.isEmpty() || requestedSheets.contains(title)) targetSheets.add(s);
        }
        if (!requestedSheets.isEmpty() && targetSheets.size() != requestedSheets.size()) {
            var found = targetSheets.stream().map(s -> s.getProperties().getTitle()).toList();
            var missing = requestedSheets.stream().filter(n -> !found.contains(n)).toList();
            logError("describeschema: sheet(s) not found: " + missing);
            System.exit(1);
        }
        if (targetSheets.isEmpty()) {
            logError("describeschema: no sheets to describe");
            System.exit(1);
            return;
        }

        // Phase A: header row (+ notes) for every target sheet, one combined call — auto-detects
        // each sheet's dataColumnEnd from its last non-empty header cell (unless --col-end given).
        List<String> apiRangesA = targetSheets.stream()
            .map(s -> CellRange.a1Ref(s.getProperties().getTitle(), headerRowNum + ":" + headerRowNum))
            .toList();
        Spreadsheet ssA = sheetsService.spreadsheets().get(spreadsheetId)
            .setRanges(apiRangesA)
            .setFields("sheets(properties.title,data(rowData(values(formattedValue,note))))")
            .execute();

        Map<String, List<CellData>> headerCellsBySheet = new LinkedHashMap<>();
        Map<String, Integer> colEndBySheet = new LinkedHashMap<>();
        for (Sheet s : targetSheets) {
            String name = s.getProperties().getTitle();
            Sheet found = pickSheet(ssA, name);
            List<GridData> dataList = (found != null) ? found.getData() : null;
            GridData gd = (dataList != null && !dataList.isEmpty()) ? dataList.get(0) : null;
            List<RowData> rowData = (gd != null) ? gd.getRowData() : null;
            List<CellData> headerCells = (rowData != null && !rowData.isEmpty() && rowData.get(0).getValues() != null)
                ? rowData.get(0).getValues() : List.of();
            headerCellsBySheet.put(name, headerCells);
            colEndBySheet.put(name, colEndOverride != null ? colEndOverride : SchemaDescriptor.detectColumnEnd(headerCells, colStart));
        }

        // Phase B: bounded data rows (colStart..colEnd, dataStartRow..+sampleRowsAnalyzed) for every
        // target sheet, one combined call — never fetches a whole large sheet.
        List<String> apiRangesB = targetSheets.stream().map(s -> {
            String name = s.getProperties().getTitle();
            int ce = colEndBySheet.get(name);
            return CellRange.a1Ref(name, CellRange.colToLetter(colStart) + dataStartRow + ":"
                + CellRange.colToLetter(ce) + (dataStartRow + sampleRowsAnalyzed - 1));
        }).toList();
        Spreadsheet ssB = sheetsService.spreadsheets().get(spreadsheetId)
            .setRanges(apiRangesB)
            .setFields("sheets(properties.title,data(rowData(values(formattedValue))))")
            .execute();

        List<SheetSchema> sheetSchemas = new ArrayList<>();
        for (Sheet s : targetSheets) {
            String name = s.getProperties().getTitle();
            int sheetId = s.getProperties().getSheetId();
            Sheet found = pickSheet(ssB, name);
            List<GridData> dataList = (found != null) ? found.getData() : null;
            GridData gd = (dataList != null && !dataList.isEmpty()) ? dataList.get(0) : null;
            List<RowData> dataRows = (gd != null && gd.getRowData() != null) ? gd.getRowData() : List.of();

            sheetSchemas.add(SchemaDescriptor.describe(name, sheetId, headerRowNum, dataStartRow,
                colStart, colEndOverride, headerCellsBySheet.get(name), dataRows,
                sampleRowsAnalyzed, sampleSize, uniquenessCol, truncateLen));
        }

        var schema = new SpreadsheetSchema(meta.getProperties().getTitle(), spreadsheetId,
            notes != null ? notes : "", sheetSchemas);

        if (format.equalsIgnoreCase("json")) {
            System.out.println(GSON.toJson(schema));
        } else if (format.equalsIgnoreCase("md") || format.equalsIgnoreCase("text")) {
            System.out.println(SchemaOutput.render(schema, format));
        } else {
            logError("describeschema: unknown --format: " + format + ". Use json|md|text");
            System.exit(1);
        }
    }

    // ─── grant / revoke / listperms ──────────────────────────────────────────

    /**
     * These three used to edit this tool's own {@code SpreadsheetCli.xml}. They now redirect, rather than
     * being deleted outright: a verb that vanishes gives a script an unrecognised-command error, while one
     * that prints the replacement command turns the same failure into an instruction.
     *
     * <p>They are not proxied through to the wallet either. Granting has to be a decision made at the
     * wallet, in a dialog naming the document, and a tool that could obtain a permission by asking on its
     * own behalf is the thing the whole arrangement exists to prevent.
     */
    private static void handleGrant(List<String> argList) {
        var id = argList.isEmpty() ? "<spreadsheetId>" : argList.getFirst();
        var write = argList.contains("--write");
        redirect("grant", "uskoag-walletcli policy allow --api sheets --resource " + id
                + " --tier " + (write ? "mutate" : "read") + " --account " + (email == null ? "<email>" : email));
    }

    private static void handleRevoke(String spreadsheetId) {
        redirect("revoke", "uskoag-walletcli policy list        (find the rule id, then)\n"
                + "  uskoag-walletcli policy revoke <ruleId>");
    }

    private static void handleListPerms() {
        redirect("listperms", "uskoag-walletcli policy list");
    }

    private static void redirect(String verb, String instead) {
        logError(verb + " has moved to the wallet, which is now the only thing that decides what this tool"
                + " may touch — for every tool, with expiry and an audit, instead of one XML file per tool.");
        logError("Run instead:");
        logError("  " + instead);
        logError("Permissions are also created just by using a document: the first touch asks once.");
        System.exit(2);
    }

    // ─── data loading helpers ────────────────────────────────────────────────

    private static List<List<Object>> loadWriteData(
            String tsvFile, String csvFile, String jsonFile,
            String tsvInline, String jsonInline) throws IOException {

        if (tsvFile != null) {
            String content = Files.readString(Path.of(tsvFile), StandardCharsets.UTF_8);
            return parseTsv(content);
        }
        if (csvFile != null) {
            String content = Files.readString(Path.of(csvFile), StandardCharsets.UTF_8);
            return parseCsv(content);
        }
        if (jsonFile != null) {
            String content = Files.readString(Path.of(jsonFile), StandardCharsets.UTF_8);
            return parseJson(content);
        }
        if (tsvInline != null) {
            return parseTsv(tsvInline);
        }
        if (jsonInline != null) {
            return parseJson(jsonInline);
        }
        return null;
    }

    /** Parse tab-separated values (tab = column separator, newline = row separator). */
    private static List<List<Object>> parseTsv(String tsv) {
        List<List<Object>> rows = new ArrayList<>();
        String[] lines = tsv.split("\n", -1);
        for (String line : lines) {
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            String[] cells = line.split("\t", -1);
            List<Object> row = new ArrayList<>();
            for (String cell : cells) row.add(cell);
            rows.add(row);
        }
        // Trim trailing all-empty rows
        while (!rows.isEmpty() && rows.get(rows.size() - 1).stream().allMatch(o -> o.toString().isEmpty())) {
            rows.remove(rows.size() - 1);
        }
        return rows;
    }

    /** Parse RFC-4180 CSV. */
    private static List<List<Object>> parseCsv(String csv) {
        List<List<Object>> rows = new ArrayList<>();
        List<Object> currentRow = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    currentRow.add(field.toString());
                    field.setLength(0);
                } else if (c == '\n') {
                    currentRow.add(field.toString());
                    field.setLength(0);
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                } else if (c != '\r') {
                    field.append(c);
                }
            }
        }
        // Flush last field / row
        currentRow.add(field.toString());
        if (!(currentRow.size() == 1 && currentRow.get(0).toString().isEmpty())) {
            rows.add(currentRow);
        }
        return rows;
    }

    /** Parse JSON 2-D array: [["a","b"],["c","d"]] */
    private static List<List<Object>> parseJson(String json) {
        Type type = new TypeToken<List<List<Object>>>(){}.getType();
        return GSON.fromJson(json, type);
    }

    // ─── output formatting helpers ───────────────────────────────────────────

    private static String escapeKv(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String csvQuote(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ─── initialization ──────────────────────────────────────────────────────

    private static void initializeSheetsService() throws IOException, GeneralSecurityException {
        logInfo("Initializing Google Sheets service...");
        var spec = AccessSpec.of("sheets", PROFILE, APP_NAME, email,
                        List.of(SheetsScopes.SPREADSHEETS, SheetsScopes.DRIVE))
                .legacyRoot(CONFIG_DIR.toString());
        var access = Credentials.access(spec, () -> AppKeyPrompt.ask(LEGACY_APP_KEY_HINT));
        walletBrokered = "wallet".equals(access.sourceName());
        sheetsService = SheetsService.sheets(access, APP_NAME);
        logInfo("Sheets service initialized via " + access.sourceName()
                + (access.account() == null ? "" : " for " + access.account()));
        if (!walletBrokered) {
            // Said out loud, every time, because it is a real reduction and a silent one would be worse.
            // The old XML allow-list is gone, so on this route there is nothing narrowing the account:
            // whatever the token can reach, this tool can reach.
            logError("NOTE: no wallet is brokering, so there is no per-document permission on this route."
                    + " Start uskoag-wallet to get the approved list, expiry and the audit back.");
        }
    }

    // ─── arg parsing helpers ─────────────────────────────────────────────────

    /** Remove all occurrences of any of the given flag names; return true if any was found. */
    private static boolean removeFlag(List<String> args, String... flags) {
        boolean found = false;
        for (String flag : flags) {
            while (args.remove(flag)) found = true;
        }
        return found;
    }

    /** Remove `--flag value` pair and return the value, or null if not present. */
    private static String extractFlagValue(List<String> args, String... flags) {
        for (String flag : flags) {
            int idx = args.indexOf(flag);
            if (idx >= 0 && idx + 1 < args.size()) {
                args.remove(idx);
                return args.remove(idx);
            }
        }
        return null;
    }

    // ─── logging ─────────────────────────────────────────────────────────────

    private static void logInfo(String message) {
        if (verbose) System.err.println("[INFO] " + message);
    }

    private static void logError(String message) {
        System.err.println("[ERROR] " + message);
    }

    private static void printHelp(java.io.PrintStream out) {
        out.println("uskoag-gsheetscli - Google Sheets from the command line");
        out.println();
        out.println("USAGE");
        out.println("  uskoag-gsheetscli [-v|--verbose] [--quiet] <command> [args...]");
        out.println();
        out.println("COMMANDS");
        out.println("  read    <id> <sheet> <range> [<range2> ...]   Read a cell / range / several ranges");
        out.println("  query   <id> <sheet> \"<SQL>\"                  Run SQL over the sheet (columns A,B,C... + _row)");
        out.println("  update  <id> <sheet> \"<SQL UPDATE>\"           Conditional UPDATE; writes changed cells, returns old->new diff");
        out.println("  inspect <id> <sheet> [<range>]                Show cell notes and background colors (range optional)");
        out.println("  write   <id> <sheet> <addr> [<value>]         Write one cell, or a 2-D block (with a data flag)");
        out.println("  append  <id> <sheet> [<startRange>]           Append rows to the end of a sheet (needs a data flag)");
        out.println("  clear   <id> <sheet> <range>                  Empty a range");
        out.println("  listsheet   <id>                              List sheet tabs and their IDs");
        out.println("  createsheet <id> <name>                       Add a sheet tab");
        out.println("  renamesheet <id> <oldName> <newName>          Rename a sheet tab");
        out.println("  freeze  <id> <sheet>                          Freeze header rows/columns (--rows N and/or --cols N)");
        out.println("  insertcolumn <id> <sheet> <colLetter>         Insert blank column(s) at colLetter, shifting everything at/after it right (--count N, default 1)");
        out.println("  insertrow <id> <sheet> <rowNumber>            Insert blank row(s) at rowNumber, shifting everything at/after it down (--count N, default 1)");
        out.println("  deletecolumn <id> <sheet> <colLetter>         Delete column(s) at colLetter, shifting everything after it left (--count N, default 1)");
        out.println("  format  <id> <sheet> <range>                  Color/style cells (bg, text color, bold, italic, align, wrap)");
        out.println("  numberformat <id> <sheet> <range>             Set the number/date rendering format (--type + optional --pattern, or --clear)");
        out.println("  validate <id> <sheet> <range>                 Dropdown / data validation (from a range or an inline list)");
        out.println("  highlight <id> <sheet> <range>                Color-code each allowed value (conditional format, one rule per value)");
        out.println("  filter  <id> <sheet> [<range>]                Turn on the filter funnel buttons (--clear to remove; range optional -> whole sheet)");
        out.println("  describeschema <id>                           Export a rich per-sheet schema (headers, notes, diversified data samples)");
        out.println("  grant | revoke | listperms                    MOVED - see PERMISSIONS below");
        out.println("  help                                          Show this help");
        out.println();
        out.println("GLOBAL OPTIONS");
        out.println("  -v, --verbose   Log progress to stderr ([INFO] lines, full stack traces on error)");
        out.println("  --quiet         Suppress the 'SUCCESS:' line (exit code still signals success)");
        out.println("  -h, --help      Show this help");
        out.println();
        out.println("VALUE INPUT  (write / append)");
        out.println("  Default is RAW: values are stored literally - phone numbers, leading +/=/0,");
        out.println("  and leading zeros are preserved (no #ERROR!, no 'tel:' workaround).");
        out.println("  --user-entered  Opt into formula/auto-typing (=1+1 -> 2, 50% -> 0.5, dates parsed)");
        out.println("  --raw           Explicit RAW (already the default; accepted as a no-op)");
        out.println("  --as-date       Parse each value as a date (default) / --date-type TIME|DATE_TIME, write it");
        out.println("                  as a Sheets serial number, and apply a matching number format in the same");
        out.println("                  call -- guaranteed to render as a real date/time regardless of locale, unlike");
        out.println("                  --user-entered's own auto-parsing. --date-format <pattern> overrides the ISO");
        out.println("                  default (java.time DateTimeFormatter, e.g. 'dd/MM/yyyy'). Ignores --raw/--user-entered.");
        out.println("                  write/append fail loudly on the first value that doesn't parse (blank cells are");
        out.println("                  skipped, not an error); update's SQL SET can touch unrelated non-date columns in");
        out.println("                  the same statement, so a value that doesn't parse as the date passes through unchanged.");
        out.println();
        out.println("BULK DATA INPUT  (write / append) - one of:");
        out.println("  --tsv-file <path>    Tab-separated file  (tab = column, newline = row)");
        out.println("  --csv-file <path>    RFC-4180 CSV file    (quotes / commas / newlines preserved)");
        out.println("  --json-file <path>   JSON 2-D array file  ([[\"a\",\"b\"],[\"c\",\"d\"]])");
        out.println("  --tsv  <data>        Inline TSV, e.g.  $'a\\tb\\nc\\td'");
        out.println("  --json <data>        Inline JSON 2-D array");
        out.println("  The block is written starting at the top-left of <addr>.");
        out.println();
        out.println("READ OUTPUT  (--format, default kv)");
        out.println("  kv     One line per cell:  \"A1\": \"value\"   (quotes/backslashes escaped)");
        out.println("  tsv    Rectangular grid, tab-separated      (cheapest to parse)");
        out.println("  csv    RFC-4180 CSV");
        out.println("  json   2-D array of strings");
        out.println("  A single-cell read prints the bare value. Multiple ranges use batchGet and are");
        out.println("  delimited by a '# <sheet>!<range>' header line. Blank cells are kept positionally.");
        out.println("  Columns past Z (AA, AB, ...) are handled correctly.");
        out.println("  --formulas      Return the FORMULA behind each cell instead of its computed value");
        out.println("                  (a cell with no formula still returns its literal value)");
        out.println("  --render R      Superset of --formulas: FORMATTED (default) | UNFORMATTED | FORMULA");
        out.println("  --both          Return BOTH the formula and the value for each cell (2 API calls) --");
        out.println("                  never choose up front. --format json: real nested {\"formula\",\"value\"}");
        out.println("                  objects. kv/tsv/csv: each cell's text is that same pair as compact JSON,");
        out.println("                  e.g. {\"formula\":\"=SUM(A1:A9)\",\"value\":\"30\"} -- works in every format,");
        out.println("                  including tsv (no embedded tabs/newlines to worry about).");
        out.println("                  Mutually exclusive with --formulas/--render.");
        out.println("  --rich          Per cell: {value, formula, note, color, numberFormat} -- everything 'inspect'");
        out.println("                  shows plus value/formula/numberFormat, in one spreadsheets().get() call.");
        out.println("                  --format json: real nested objects. kv/tsv/csv: each cell's text is that");
        out.println("                  same object as compact JSON, e.g. {\"value\":\"2026-07-24\",\"color\":\"#fff2cc\",");
        out.println("                  \"numberFormat\":{\"type\":\"DATE\",\"pattern\":\"yyyy-mm-dd\"}} -- fields with no");
        out.println("                  data (no note, white/no-fill background) are simply omitted from the object.");
        out.println("                  Mutually exclusive with --formulas/--render/--both.");
        out.println();
        out.println("QUERY  (SQL over the sheet, in-memory -- read-only, never writes back)");
        out.println("  The whole tab (or --range) is loaded into a throwaway table `t`, then your");
        out.println("  SQL SELECT runs against it. Columns are addressed by their sheet letter:");
        out.println("  A, B, C, ... (quote two-letter names that clash with SQL keywords, e.g. \"AS\").");
        out.println("  \"_row\" is an extra column holding the source sheet row number (1-based).");
        out.println("  Every cell is text (stored literally); CAST when you need a numeric/date compare:");
        out.println("  e.g.  WHERE CAST(E AS INT) > 100.  Row 1 (header) is loaded as data too, so");
        out.println("  add  WHERE _row > 1  to skip it. GROUP BY / COUNT / DISTINCT / joins all work.");
        out.println("  --range <A1>    Restrict the loaded rows (e.g. A2:H  keeps _row aligned to the sheet)");
        out.println("  --format json (default) | tsv | csv");
        out.println("    json = array of objects keyed by result column (self-describing, carries _row).");
        out.println("    tsv/csv = header row of column names, then rows (cheapest to parse).");
        out.println();
        out.println("UPDATE  (conditional SQL UPDATE with an old->new safety diff)");
        out.println("  Like 'query', the tab (or --range) is loaded into an in-memory table `t` (cols");
        out.println("  A,B,C... + _row). You run a SQL UPDATE; ONLY the cells whose value actually");
        out.println("  changed are written back to the sheet. The WHERE clause is your safety gate:");
        out.println("  no match -> nothing written. Same column rules as query (text cells; CAST for");
        out.println("  numeric compares; row 1 is data, so add  WHERE _row>1  or target  WHERE _row=N).");
        out.println("  Only UPDATE is allowed (use append to add rows, clear/write for the rest).");
        out.println("  --dry-run       Compute + print the diff but write NOTHING (preview first, then re-run to commit)");
        out.println("  --user-entered  Push changed cells as USER_ENTERED (formulas/typing) instead of RAW (default)");
        out.println("  --format json (default) | tsv");
        out.println("  Output reports  affected (rows matched), changedCells, written (0 on dry-run), dryRun,");
        out.println("  and a changes[] list of {row, cell, col, old, new}. To self-heal / undo, run the");
        out.println("  reverse: for each change,  update ID Sheet \"UPDATE t SET <col>='<old>' WHERE _row=<row>\".");
        out.println();
        out.println("INSPECT  (notes + background colors  --  things 'read' cannot see)");
        out.println("  inspect ID Sheet1            Whole sheet: notes + colors");
        out.println("  inspect ID Sheet1 A1:Q240    Restrict to a range");
        out.println("  --notes-only / --colors-only  Show just one section");
        out.println("  --format text (default) | json");
        out.println("  Notes are listed one per cell (\"O120: <text>\"). Background colors are merged into");
        out.println("  maximal rectangles and grouped by color, e.g.  #fff2cc light yellow: A2:Q40");
        out.println("  The default white (unfilled) background is omitted.");
        out.println();
        out.println("FREEZE  (pin header rows/columns so they stay visible while scrolling)");
        out.println("  --rows N   Freeze the first N rows (0 unfreezes)");
        out.println("  --cols N   Freeze the first N columns (0 unfreezes)");
        out.println("  At least one of --rows/--cols is required. Combine with format for a 'stylized");
        out.println("  header row': format the row first, then freeze --rows 1.");
        out.println();
        out.println("FORMAT  (cell background/text color and basic text style)");
        out.println("  Only the attributes you pass are touched (fields mask); everything else is left alone.");
        out.println("  --bg #hex           Background color");
        out.println("  --text #hex         Text (foreground) color");
        out.println("  --bold / --italic   Toggle on (there is no --no-bold; re-run to change again)");
        out.println("  --align left|center|right");
        out.println("  --valign top|middle|bottom");
        out.println("  --font-size N       Point size");
        out.println("  --wrap              Wrap text within the cell");
        out.println();
        out.println("NUMBERFORMAT  (how a cell/range's underlying value RENDERS -- date, currency, percent, ...)");
        out.println("  --type DATE|TIME|DATE_TIME|NUMBER|PERCENT|CURRENCY|SCIENTIFIC|TEXT   Required (or --clear)");
        out.println("  --pattern <custom>   Override the built-in default pattern for --type (e.g. 'yyyy-mm-dd',");
        out.println("                       '$#,##0.00'). Sets rendering only -- it does NOT convert a literal");
        out.println("                       string into a real date/number; the underlying value must already be");
        out.println("                       numeric (see 'write --as-date' for writing dates as real values).");
        out.println("  --clear              Remove the number format from the range");
        out.println();
        out.println("VALIDATE  (dropdown / data validation -- constrain a range to an allowed set)");
        out.println("  --from-range 'Allowed Values'!A2:A   Allowed values come from another range/sheet");
        out.println("                                        (edit the vocabulary in one place, not per cell)");
        out.println("  --list a,b,c                          Allowed values inline (secondary convenience)");
        out.println("  --warn                                Flag invalid input instead of rejecting it (default: reject)");
        out.println("  --clear                                Remove the validation rule from the range");
        out.println("  Shows a dropdown (showCustomUi) in Sheets either way.");
        out.println("  --colors #h1,#h2,...   Also color-code the data range (like 'highlight', bundled in) --");
        out.println("                         one color per value, in order (cycled if fewer colors than values;");
        out.println("                         omit --colors' value list and just pass --color-legend for the");
        out.println("                         default palette). Not allowed with --clear.");
        out.println("  --color-legend         Also color-code the --from-range legend cells with the SAME");
        out.println("                         mapping, so the legend visually declares \"this text = this color\"");
        out.println("                         (requires --from-range; no legend location for --list).");
        out.println("  Both re-run safely: bump the allowed set (add a row to the legend, or extend --list),");
        out.println("  run the SAME validate command again, and the color rules refresh (old ones for this");
        out.println("  range are replaced, not stacked) while the dropdown itself needs no update at all when");
        out.println("  using --from-range (it re-reads the legend live, every time).");
        out.println();
        out.println("  --per-row-formula '<template with {ROW}>'   Cascading/dependent dropdown: <range> MUST");
        out.println("                         give an explicit start AND end row (e.g. I2:I501, not I2:I) --");
        out.println("                         one ONE_OF_RANGE rule is generated PER ROW, with {ROW} replaced");
        out.println("                         by that row's own number, e.g.:");
        out.println("                           --per-row-formula 'INDIRECT(\"Lists!\"&VLOOKUP($H{ROW},Lists!$K$2:$L$7,2,FALSE)&\"2:\"&VLOOKUP($H{ROW},Lists!$K$2:$L$7,2,FALSE))'");
        out.println("                         Needed because a single data-validation rule applied over a multi-row");
        out.println("                         range does NOT auto-shift its formula's relative refs per row the way");
        out.println("                         conditional formatting does -- every row would otherwise consult the");
        out.println("                         same anchor cell. Mutually exclusive with --from-range/--list/--colors*.");
        out.println("                         Re-running replaces those rows' rules (SetDataValidation always replaces,");
        out.println("                         never stacks); growing past the row count needs a re-run with a wider range.");
        out.println();
        out.println("HIGHLIGHT  (conditional format -- color-code each allowed value at a glance)");
        out.println("  Adds one conditional-format rule per distinct value (exact text match, background");
        out.println("  color only) -- pairs naturally with 'validate' on the same range/values, but works");
        out.println("  standalone too. Value + color source -- pick one:");
        out.println("  --from-range 'Allowed Values'!A2:A   Values from another range/sheet (deduped, in order);");
        out.println("                                        --colors optional (else auto palette)");
        out.println("  --list a,b,c                          Values inline; --colors optional (else auto palette)");
        out.println("  --colors-from-range 'Allowed Values'!A2:A");
        out.println("                                        Values AND colors both read directly off that range's");
        out.println("                                        OWN current cell colors -- color the legend once (by");
        out.println("                                        hand, or with --color-legend/highlight above), then");
        out.println("                                        mirror that exact mapping onto any data range, no");
        out.println("                                        --colors list to keep in sync by hand");
        out.println("  --clear                                Remove highlight rule(s) previously added on this exact range");
        out.println("  Re-running on the SAME range replaces its rules rather than stacking duplicates -- so");
        out.println("  after a revision (new value added, colors changed), just run it again. New rules are");
        out.println("  appended after any OTHER pre-existing conditional formatting on the sheet, untouched.");
        out.println("  Prints value=color pairs actually used, so an auto-assigned palette is visible.");
        out.println();
        out.println("FILTER  (turn on the 'Create a filter' funnel buttons on the header row)");
        out.println("  filter ID Sheet1              Enable the filter over the whole sheet (buttons on row 1)");
        out.println("  filter ID Sheet1 A1:H        Enable it over a range; the range's first row is the header");
        out.println("  filter ID Sheet1 --clear     Remove the filter");
        out.println("  Enables the filter option only (no specific column criteria set). A sheet holds exactly");
        out.println("  one basic filter, so re-running replaces it -- it never stacks. Pair with 'freeze --rows 1'");
        out.println("  for a header row that both filters and stays pinned while scrolling.");
        out.println();
        out.println("DESCRIBESCHEMA  (rich per-sheet schema export, for feeding a spreadsheet's shape to an AI agent)");
        out.println("  describeschema ID                     Every sheet: header row + a diversified data sample");
        out.println("  --sheets name1,name2,...              Only these sheets (default: all, in tab order)");
        out.println("  --sample-rows N       (default 100)   How many data rows to analyze for sampling");
        out.println("  --sample-size N       (default 5)     How many rows to actually include in the sample");
        out.println("  --uniqueness-col A    (default A)     Column used to diversify the sample: one random row");
        out.println("                                         per distinct value in this column, topped up with");
        out.println("                                         random extra rows if there aren't enough distinct");
        out.println("                                         values -- never a mechanical first-N-rows sample");
        out.println("  --truncate N          (default 1024)  Max chars per sampled cell value before truncating");
        out.println("                                         (keeps both the head and tail of the value, with a");
        out.println("                                         '... (sample truncated, total length N characters)'");
        out.println("                                         marker in between)");
        out.println("  --notes \"...\"                          Free-text note about the spreadsheet, carried into the export");
        out.println("  --format json (default) | md | text");
        out.println("  Per-sheet-only overrides (require --sheets naming exactly ONE sheet):");
        out.println("    --header-row N       (default 1)      Which row holds column headers");
        out.println("    --data-start-row N   (default header-row + 1)");
        out.println("    --col-start A / --col-end Z           Column range (default: A .. auto-detected from");
        out.println("                                           the last non-empty header cell)");
        out.println("  A header cell's Sheets NOTE (if any) is carried as its \"comment\" in the export.");
        out.println("  3 API calls total, regardless of how many sheets are described.");
        out.println();
        out.println("EXAMPLES");
        out.println("  uskoag-gsheetscli read  ID Sheet1 A1");
        out.println("  uskoag-gsheetscli read  ID Sheet1 A1:H20 --format tsv");
        out.println("  uskoag-gsheetscli read  ID Sheet1 A1:C3 E1:F2 --format csv");
        out.println("  uskoag-gsheetscli read  ID Sheet1 C1 --formulas                # \"=SUM(A1:A9)\" instead of its value");
        out.println("  uskoag-gsheetscli read  ID Sheet1 A1:C3 --both --format tsv    # {\"formula\":..,\"value\":..} per cell");
        out.println("  uskoag-gsheetscli read  ID Sheet1 A1:C3 --rich --format json    # value+formula+note+color+numberFormat");
        out.println("  uskoag-gsheetscli query ID Contacts \"SELECT _row, C, D FROM t WHERE _row>1 AND D=''\"");
        out.println("  uskoag-gsheetscli query ID Contacts \"SELECT C, COUNT(*) n FROM t GROUP BY C HAVING COUNT(*)>1\"  # dupes");
        out.println("  uskoag-gsheetscli query ID Contacts \"SELECT DISTINCT C FROM t WHERE _row>1\" --format tsv");
        out.println("  uskoag-gsheetscli update ID Contacts \"UPDATE t SET D='yes' WHERE C='a@x.com'\" --dry-run  # preview");
        out.println("  uskoag-gsheetscli update ID Contacts \"UPDATE t SET D='yes',E='2026-07-15' WHERE _row=5\"   # commit");
        out.println("  uskoag-gsheetscli inspect ID Sheet1 A1:Q240                    # notes + color regions");
        out.println("  uskoag-gsheetscli inspect ID Sheet1 --colors-only --format json");
        out.println("  uskoag-gsheetscli write ID Sheet1 B2 \"+91 80 4656 3000\"        # stored literally (RAW)");
        out.println("  uskoag-gsheetscli write ID Sheet1 C1 \"=SUM(A1:A9)\" --user-entered");
        out.println("  uskoag-gsheetscli write ID Sheet1 A1 --tsv-file grid.tsv        # bulk 2-D write");
        out.println("  uskoag-gsheetscli write ID Sheet1 B2 \"2026-07-24\" --as-date     # real date, not a string");
        out.println("  uskoag-gsheetscli write ID Sheet1 C2 \"07/24/2026\" --as-date --date-format MM/dd/yyyy");
        out.println("  uskoag-gsheetscli append ID Sheet1 --csv-file newrows.csv");
        out.println("  uskoag-gsheetscli append ID Sheet1 --tsv $'2026-07-24' --as-date");
        out.println("  uskoag-gsheetscli clear ID Sheet1 A1:C3");
        out.println("  uskoag-gsheetscli format ID Sheet1 A1:Z1 --bg #4a86e8 --text #ffffff --bold  # stylized header");
        out.println("  uskoag-gsheetscli numberformat ID Sheet1 B2:B --type DATE                     # yyyy-mm-dd");
        out.println("  uskoag-gsheetscli numberformat ID Sheet1 D2:D --type CURRENCY --pattern \"$#,##0.00\"");
        out.println("  uskoag-gsheetscli numberformat ID Sheet1 B2:B --clear");
        out.println("  uskoag-gsheetscli freeze ID Sheet1 --rows 1");
        out.println("  uskoag-gsheetscli insertrow ID Sheet1 3                          # shift row 3+ down by one");
        out.println("  uskoag-gsheetscli validate ID Sheet1 C2:C --from-range \"'Allowed Values'!A2:A\"    # dropdown from another sheet");
        out.println("  uskoag-gsheetscli validate ID Sheet1 D2:D --list contacted,replied,bounced --warn");
        out.println("  uskoag-gsheetscli validate ID Sheet1 C2:C --clear");
        out.println("  uskoag-gsheetscli validate ID Sheet1 I2:I501 --per-row-formula 'INDIRECT(\"Lists!\"&VLOOKUP($H{ROW},Lists!$K$2:$L$7,2,FALSE)&\"2:\"&VLOOKUP($H{ROW},Lists!$K$2:$L$7,2,FALSE))'  # cascading dropdown, keyed on each row's own H cell");
        out.println("  uskoag-gsheetscli validate ID Sheet1 C2:C --from-range \"'Allowed Values'!A2:A\" \\");
        out.println("      --colors \"#d9ead3,#fff2cc,#f4cccc\" --color-legend   # dropdown + data colored + legend colored, one call");
        out.println("  uskoag-gsheetscli highlight ID Sheet1 C2:C --from-range \"'Allowed Values'!A2:A\"   # auto palette");
        out.println("  uskoag-gsheetscli highlight ID Sheet1 C2:C --list contacted,replied,bounced --colors \"#d9ead3,#fff2cc,#f4cccc\"");
        out.println("  uskoag-gsheetscli highlight ID Sheet1 C2:C --colors-from-range \"'Allowed Values'!A2:A\"   # mirror the legend's own colors");
        out.println("  uskoag-gsheetscli highlight ID Sheet1 C2:C --clear");
        out.println("  uskoag-gsheetscli filter ID Sheet1                             # filter funnels on the header row");
        out.println("  uskoag-gsheetscli filter ID Sheet1 A1:H --clear");
        out.println("  uskoag-gsheetscli describeschema ID                            # every sheet, default sampling");
        out.println("  uskoag-gsheetscli describeschema ID --sheets Contacts --sample-size 8 --format md");
        out.println("  uskoag-gsheetscli describeschema ID --sheets Contacts --header-row 2 --col-end BQ");
        out.println();
        out.println("OUTPUT CONTRACT");
        out.println("  stdout = command data only (values / sheet list / a single SUCCESS: line).");
        out.println("  stderr = [ERROR] always, [INFO] only with -v. No JVM/AOT noise on warm runs.");
        out.println();
        out.println("PERMISSIONS");
        out.println("  The wallet decides, and it is the only thing that does. The per-spreadsheet XML");
        out.println("  allowlist this tool used to keep is gone: every tool grew its own half of a policy");
        out.println("  engine, and two that can disagree are worse than either.");
        out.println();
        out.println("  Nothing to set up for one spreadsheet. The first time a document is touched the");
        out.println("  wallet asks once, naming it, and remembers the answer until it expires - a week to");
        out.println("  read, a day to change, an hour for anything irreversible. That rule DOES carry");
        out.println("  across separate invocations, so a second command on the same sheet is silent.");
        out.println();
        out.println("  FOR A BATCH, set it up first rather than answering your way through it:");
        out.println();
        out.println("    uskoag-walletcli policy quiet --tier write        stop asking me, for everything,");
        out.println("                                                     for a day (--tier read = a week,");
        out.println("                                                     and write covers read too)");
        out.println("    uskoag-walletcli policy allow --api sheets --resource <id> --tier write");
        out.println("                                                     pre-approve one known document");
        out.println("    uskoag-walletcli policy check --api sheets --resource <id> --tier write");
        out.println("                                                     will this be allowed? ask BEFORE");
        out.println("                                                     starting, not halfway through");
        out.println("    uskoag-walletcli policy list                     what stands right now");
        out.println("    uskoag-walletcli policy extend <ruleId> --by 1w  push one out");
        out.println("    uskoag-walletcli policy revoke <ruleId>          drop one");
        out.println("    uskoag-walletcli audit                          what was actually attempted");
        out.println("    uskoag-walletcli session                         why am I being asked twice?");
        out.println();
        out.println("  The approval dialog's \"apply to every item this command touches\" checkbox does NOT");
        out.println("  stop the asking across commands - it is bound to the session, so it covers one");
        out.println("  command however wide it is. 'policy quiet' is the thing that spans commands.");
        out.println();
        out.println("  --email is required whenever the wallet holds more than one account. It refuses");
        out.println("  rather than guessing, because acting as the wrong account is worse than failing.");
        out.println();
        out.println("  If the wallet is not running this starts it, and if it is locked the command fails");
        out.println("  saying so - unlock it and run the same thing again. It no longer falls back to an");
        out.println("  app-key prompt: a control you can remove by stopping a process is not a control.");
    }
}
