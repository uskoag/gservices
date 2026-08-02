package uskoag.gservices.sheetsdb;

import com.google.api.services.sheets.v4.model.AddConditionalFormatRuleRequest;
import com.google.api.services.sheets.v4.model.AddProtectedRangeRequest;
import com.google.api.services.sheets.v4.model.AutoResizeDimensionsRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BooleanCondition;
import com.google.api.services.sheets.v4.model.BooleanRule;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.CellFormat;
import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.ConditionValue;
import com.google.api.services.sheets.v4.model.ConditionalFormatRule;
import com.google.api.services.sheets.v4.model.DataValidationRule;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.GridProperties;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.ProtectedRange;
import com.google.api.services.sheets.v4.model.RepeatCellRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SetDataValidationRequest;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.TextFormat;
import com.google.api.services.sheets.v4.model.UpdateDimensionPropertiesRequest;
import com.google.api.services.sheets.v4.model.UpdateSheetPropertiesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything this library can do <em>to</em> a sheet rather than with its contents: make duplicate
 * keys impossible to miss, freeze the header, and make the thing legible.
 *
 * <pre>
 * db.setup(Documents.DEF)
 *   .keyGuard()          // duplicate column + whole-row highlight + input validation
 *   .freezeHeader()
 *   .headerStyle()
 *   .apply();
 * </pre>
 *
 * <p>Or {@code db.prepare(Documents.DEF)} for exactly that set. Everything is idempotent: existing
 * rules are read once and identical ones are not added again, so a build can call it every run.
 *
 * <h3>On enforcing uniqueness — the honest answer is that you cannot</h3>
 *
 * <p>A spreadsheet has no unique constraint and {@link #validation(boolean) data validation} is not
 * one either, however much it looks like one. A {@code CUSTOM_FORMULA} rule with reject-on-invalid
 * stops somebody <em>typing</em> a duplicate id, and that is worth having. It does not stop:
 *
 * <ul>
 *   <li><b>The API.</b> Validation is a UI-layer check. Anything written through
 *       {@code spreadsheets.values} — including this library's own {@link GSheetDb#flush()} — goes
 *       straight past it.
 *   <li><b>Paste.</b> Pasting cells carries the source's validation rules with it, so a paste can
 *       overwrite the guard along with the value that violates it.
 *   <li><b>Anyone who opens Data &gt; Data validation and deletes the rule.</b>
 * </ul>
 *
 * <p>So this class layers three things that between them make a duplicate loud rather than
 * impossible: validation catches it at the keyboard, the {@linkplain #keyGuard() duplicate column and
 * row highlight} make it visible from across the room, and {@link GSheetDb#duplicateKeys} refuses to
 * read or write the ambiguous key at all. The last of those is the one that actually holds, because
 * it is the only one on this side of the network.
 */
public final class SheetSetup {

    /** What the duplicate column should say. */
    public enum Report {
        /**
         * {@code rows 14, 37} — every sheet row sharing that key. What you want: an error message the
         * spreadsheet generates for itself, naming exactly where to look.
         *
         * <p>Uses {@code MAP}/{@code LAMBDA}/{@code FILTER}, so it is per-row work over the whole key
         * column. Fine to a few thousand documents; past that prefer {@link #COUNT}.
         */
        ROWS,
        /** {@code 2} — how many rows share the key. Cheaper: one native {@code ARRAYFORMULA}. */
        COUNT,
        /** No column; the highlight and validation only. */
        NONE
    }

    private static final Color HEADER_BG = new Color().setRed(0.17f).setGreen(0.22f).setBlue(0.28f);
    private static final Color HEADER_FG = new Color().setRed(1f).setGreen(1f).setBlue(1f);
    private static final Color DUPLICATE_BG = new Color().setRed(0.98f).setGreen(0.80f).setBlue(0.80f);

    private final GSheetDb db;
    private final TypeDef<?> def;
    private final SheetDocs<?> docs;

    private boolean keyGuard;
    private Report report = Report.ROWS;
    private boolean rowHighlight = true;
    private boolean validation = true;

    private Integer freezeRows;
    private Integer freezeColumns;
    private boolean headerStyle;
    private boolean autoResize;
    private boolean protectHeader;
    private boolean protectWarningOnly = true;

    SheetSetup(GSheetDb db, TypeDef<?> def, SheetDocs<?> docs) {
        this.db = db;
        this.def = def;
        this.docs = docs;
    }

    // ---- what to do ------------------------------------------------------------

    /** Duplicate-report column, whole-row highlight and input validation, all three. */
    public SheetSetup keyGuard() {
        this.keyGuard = true;
        return this;
    }

    /** The same, choosing which parts. */
    public SheetSetup keyGuard(Report report, boolean rowHighlight, boolean validation) {
        this.keyGuard = true;
        this.report = report == null ? Report.NONE : report;
        this.rowHighlight = rowHighlight;
        this.validation = validation;
        return this;
    }

    /** What the duplicate column reports. Defaults to {@link Report#ROWS}. */
    public SheetSetup report(Report report) {
        this.report = report == null ? Report.NONE : report;
        return this;
    }

    /**
     * Add the reject-on-duplicate input rule, or not. On by default with {@link #keyGuard()}.
     *
     * <p>Read the class note before treating it as a constraint: it stops typing and nothing else.
     */
    public SheetSetup validation(boolean on) {
        this.validation = on;
        return this;
    }

    /** Freeze the header rows so they stay put when scrolling. */
    public SheetSetup freezeHeader() {
        return freeze(def.headerRow(), 0);
    }

    /**
     * Freeze this many rows and columns.
     *
     * <p>Freezing the key column as well as the header is usually right for a wide catalogue — you
     * can scroll to the far fields and still see which document you are on.
     */
    public SheetSetup freeze(int rows, int columns) {
        this.freezeRows = Math.max(0, rows);
        this.freezeColumns = Math.max(0, columns);
        return this;
    }

    /** Bold white-on-slate header row, clipped rather than overflowing. */
    public SheetSetup headerStyle() {
        this.headerStyle = true;
        return this;
    }

    /**
     * Protect the header row, so its columns cannot be reordered or renamed by accident.
     *
     * <p>The one thing in this whole class that Google actually <em>enforces</em>. Data validation is
     * advisory and a conditional format is decoration, but a protected range is real: with
     * {@code warningOnly} the editor gets "you're trying to edit part of this sheet that shouldn't be
     * changed accidentally" and has to confirm; with it off, only the listed editors can touch it at
     * all.
     *
     * <p>It matters more than it looks. Every field this library writes is addressed by the column
     * its name sits in, so reordering the header is the one edit that can put values in the wrong
     * column. The flush re-reads the header to catch that having happened; this stops it happening.
     *
     * @param warningOnly true to warn and allow, false to restrict to the current editors
     */
    public SheetSetup protectHeader(boolean warningOnly) {
        this.protectHeader = true;
        this.protectWarningOnly = warningOnly;
        return this;
    }

    /** {@link #protectHeader(boolean)} in its proportionate form: warn, do not lock anybody out. */
    public SheetSetup protectHeader() {
        return protectHeader(true);
    }

    /**
     * Size every column to its widest cell.
     *
     * <p>Opt-in, not part of {@link GSheetDb#prepare}, because it is only an improvement when no
     * field holds long prose. One 400-character {@code summary} and auto-resize gives you a single
     * column wider than the screen, which is worse than the default.
     */
    public SheetSetup autoResizeColumns() {
        this.autoResize = true;
        return this;
    }

    // ---- do it -----------------------------------------------------------------

    /**
     * Apply everything selected, in as few API calls as it takes: one {@code batchUpdate} for the
     * structural changes, and one {@code values.update} if a duplicate column is being written.
     *
     * @return one line per change actually made — suitable for a build log. Empty means everything
     *         asked for was already in place.
     */
    public List<String> apply() {
        db.requireLoaded();
        var done = new ArrayList<String>();
        var requests = new ArrayList<Request>();
        Integer sheetId = docs.sheetId();

        String duplicateFormula = null;
        int duplicateColumn = -1;

        if (keyGuard) {
            requireKey();
            int keyColumn = keyColumn();
            String keyCol = A1.col(keyColumn);
            int firstRow = docs.firstDataRow();

            // "is this key value on more than one row", written once and reused by all three parts.
            String isDuplicate = "COUNTIF($" + keyCol + "$" + firstRow + ":$" + keyCol
                    + ", $" + keyCol + firstRow + ")>1";

            if (report != Report.NONE) {
                duplicateColumn = duplicateColumn();
                requireColumnFree(duplicateColumn);
                duplicateFormula = duplicateFormula(keyCol, firstRow);
            }

            if (rowHighlight) {
                int width = Math.max(docs.usedWidth(),
                        duplicateColumn > 0 ? duplicateColumn : docs.lastDeclaredColumn());
                String formula = "=AND($" + keyCol + firstRow + "<>\"\", " + isDuplicate + ")";
                if (addConditionalFormat(sheetId, formula, firstRow, width, requests)) {
                    done.add("added the duplicate-key row highlight");
                }
            }

            if (validation) {
                requests.add(new Request().setSetDataValidation(new SetDataValidationRequest()
                        .setRange(new GridRange()
                                .setSheetId(sheetId)
                                .setStartRowIndex(firstRow - 1)
                                .setStartColumnIndex(keyColumn - 1)
                                .setEndColumnIndex(keyColumn))
                        .setRule(new DataValidationRule()
                                .setCondition(new BooleanCondition()
                                        .setType("CUSTOM_FORMULA")
                                        .setValues(List.of(new ConditionValue().setUserEnteredValue(
                                                "=NOT(" + isDuplicate + ")"))))
                                .setInputMessage("Must be unique — this " + def.keyField()
                                        + " already appears elsewhere in the column.")
                                .setStrict(true))));
                done.add("set input validation on " + def.keyField());
            }
        }

        if (freezeRows != null) {
            requests.add(new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                    .setProperties(new SheetProperties()
                            .setSheetId(sheetId)
                            .setGridProperties(new GridProperties()
                                    .setFrozenRowCount(freezeRows)
                                    .setFrozenColumnCount(freezeColumns)))
                    .setFields("gridProperties.frozenRowCount,gridProperties.frozenColumnCount")));
            done.add("froze " + freezeRows + " row(s) and " + freezeColumns + " column(s)");
        }

        if (protectHeader && addHeaderProtection(sheetId, requests)) {
            done.add("protected the header row"
                    + (protectWarningOnly ? " (warn on edit)" : " (editors only)"));
        }

        if (headerStyle) {
            requests.add(new Request().setRepeatCell(new RepeatCellRequest()
                    .setRange(new GridRange()
                            .setSheetId(sheetId)
                            .setStartRowIndex(def.headerRow() - 1)
                            .setEndRowIndex(def.headerRow())
                            .setStartColumnIndex(0)
                            .setEndColumnIndex(Math.max(docs.usedWidth(),
                                    duplicateColumn > 0 ? duplicateColumn : 1)))
                    .setCell(new CellData().setUserEnteredFormat(new CellFormat()
                            .setBackgroundColor(HEADER_BG)
                            .setTextFormat(new TextFormat()
                                    .setBold(true)
                                    .setForegroundColor(HEADER_FG))
                            .setVerticalAlignment("MIDDLE")
                            .setWrapStrategy("CLIP")))
                    .setFields("userEnteredFormat(backgroundColor,textFormat,verticalAlignment,"
                            + "wrapStrategy)")));
            done.add("styled the header row");
        }

        if (autoResize) {
            requests.add(new Request().setAutoResizeDimensions(new AutoResizeDimensionsRequest()
                    .setDimensions(new DimensionRange()
                            .setSheetId(sheetId)
                            .setDimension("COLUMNS")
                            .setStartIndex(0)
                            .setEndIndex(Math.max(docs.usedWidth(),
                                    duplicateColumn > 0 ? duplicateColumn : 1)))));
            done.add("auto-sized the columns");
        } else if (duplicateColumn > 0) {
            // The report column holds "rows 14, 37" and nothing else ever looks at it; a fixed
            // sensible width beats both the default and whatever auto-resize would decide.
            requests.add(new Request().setUpdateDimensionProperties(
                    new UpdateDimensionPropertiesRequest()
                            .setRange(new DimensionRange()
                                    .setSheetId(sheetId)
                                    .setDimension("COLUMNS")
                                    .setStartIndex(duplicateColumn - 1)
                                    .setEndIndex(duplicateColumn))
                            .setProperties(new com.google.api.services.sheets.v4.model.DimensionProperties()
                                    .setPixelSize(180))
                            .setFields("pixelSize")));
        }

        try {
            if (!requests.isEmpty()) {
                db.sheets().spreadsheets().batchUpdate(db.spreadsheetId(),
                        new BatchUpdateSpreadsheetRequest().setRequests(requests)).execute();
            }
            if (duplicateFormula != null) {
                // USER_ENTERED, not RAW: RAW would store the formula as its own text. This is the one
                // write in the library that is not RAW, and it is a formula, which is the difference.
                db.sheets().spreadsheets().values()
                        .update(db.spreadsheetId(),
                                A1.sheet(def.name()) + "!" + A1.col(duplicateColumn) + def.headerRow(),
                                new ValueRange().setValues(List.of(List.of(duplicateFormula))))
                        .setValueInputOption("USER_ENTERED")
                        .execute();
                done.add("wrote the " + duplicateHeader() + " column at "
                        + A1.col(duplicateColumn) + def.headerRow());
            }
        } catch (IOException e) {
            throw new GSheetDbException("could not set up sheet '" + def.name() + "'", e);
        }
        return done;
    }

    // ---- internals -------------------------------------------------------------

    private String duplicateHeader() {
        return def.keyField() + "__duplicates";
    }

    /**
     * Where the report column goes: reuse it if it is already there, otherwise the first column past
     * everything the sheet is using.
     *
     * <p>Appended rather than inserted next to the key. Inserting shifts every column to its right,
     * which silently invalidates every A1 reference anybody has written into the sheet — a formula,
     * a named range, a chart, another tool's hard-coded address. Appending disturbs nothing, and a
     * save never reaches it because a save writes only the cells its own fields own.
     *
     * <p>Past {@code usedWidth}, not {@code headerWidth}: a column can hold data under a blank
     * header, and the header row alone cannot see it.
     */
    private int duplicateColumn() {
        Integer existing = docs.columnOfHeader(duplicateHeader());
        if (existing != null) return existing;
        if (def.fieldType(duplicateHeader()) != null) {
            throw new IllegalStateException(def.name() + " declares a field called "
                    + duplicateHeader() + ", which is the name this library needs for the duplicate "
                    + "report column. Rename the field.");
        }
        return docs.usedWidth() + 1;
    }

    /**
     * Check the target column is genuinely free, against the sheet as it is <em>now</em>.
     *
     * <p>The width this decision rests on was measured at {@link GSheetDb#load()}, and a spreadsheet
     * has other people in it: a column added in between would be invisible to that measurement and
     * would be written over. So the column is re-read immediately before it is claimed, and anything
     * unexpected is refused rather than overwritten.
     *
     * <p>Refusing is the right failure. A duplicate-report column is a convenience; somebody's data
     * is not.
     */
    private void requireColumnFree(int column) {
        String range = A1.column(def.name(), column, def.headerRow());
        try {
            var response = db.sheets().spreadsheets().values()
                    .get(db.spreadsheetId(), range)
                    .setValueRenderOption("UNFORMATTED_VALUE")
                    .execute();
            var values = response.getValues();
            if (values == null) return;
            for (int i = 0; i < values.size(); i++) {
                var row = values.get(i);
                if (row.isEmpty()) continue;
                Object cell = row.get(0);
                if (cell == null || cell.toString().isEmpty()) continue;
                // Our own previous run is the one thing allowed to be there.
                if (i == 0 && duplicateHeader().equals(cell.toString().trim())) return;
                throw new GSheetDbException("Will not write the duplicate report into column "
                        + A1.col(column) + " of '" + def.name() + "': row "
                        + (def.headerRow() + i) + " of it already holds " + cell + ". Something was "
                        + "added to the sheet after this session loaded it. Reload and try again, or "
                        + "clear that column first — this library will overwrite nobody's data to "
                        + "make room for a convenience.");
            }
        } catch (IOException e) {
            throw new GSheetDbException("could not check whether column " + A1.col(column)
                    + " of '" + def.name() + "' is free", e);
        }
    }

    /**
     * The whole report column as one formula in the header cell.
     *
     * <p>{@code ={"header"; MAP(...)}} — the vertical-bar array literal writes the header and every
     * value below it from a single cell, so there is exactly one formula to maintain and no fill-down
     * to go stale when rows are added.
     */
    private String duplicateFormula(String keyCol, int firstRow) {
        String range = "$" + keyCol + "$" + firstRow + ":$" + keyCol;
        return switch (report) {
            case ROWS -> "={\"" + duplicateHeader() + "\";MAP(" + range + ",LAMBDA(v,IF(v=\"\",\"\","
                    + "IF(COUNTIF(" + range + ",v)>1,\"rows \"&TEXTJOIN(\", \",TRUE,"
                    + "FILTER(ROW(" + range + ")," + range + "=v)),\"\"))))}";
            case COUNT -> "={\"" + duplicateHeader() + "\";ARRAYFORMULA(IF(" + range + "=\"\",\"\","
                    + "IF(COUNTIF(" + range + "," + range + ")>1,COUNTIF(" + range + ","
                    + range + "),\"\")))}";
            case NONE -> null;
        };
    }

    /** Protect the header row unless it already is. */
    private boolean addHeaderProtection(Integer sheetId, List<Request> requests) {
        String description = "gsheetsdb: " + def.name()
                + " field names — reordering or renaming these moves where data is written";
        try {
            var existing = db.sheets().spreadsheets().get(db.spreadsheetId())
                    .setFields("sheets(properties.sheetId,protectedRanges(description))")
                    .execute();
            if (existing.getSheets() != null) {
                for (var s : existing.getSheets()) {
                    if (!sheetId.equals(s.getProperties().getSheetId())) continue;
                    if (s.getProtectedRanges() == null) continue;
                    for (var range : s.getProtectedRanges()) {
                        if (description.equals(range.getDescription())) return false;
                    }
                }
            }
        } catch (IOException e) {
            throw new GSheetDbException("could not read the existing protections of '"
                    + def.name() + "'", e);
        }

        requests.add(new Request().setAddProtectedRange(new AddProtectedRangeRequest()
                .setProtectedRange(new ProtectedRange()
                        .setRange(new GridRange()
                                .setSheetId(sheetId)
                                .setStartRowIndex(def.headerRow() - 1)
                                .setEndRowIndex(def.headerRow()))
                        .setDescription(description)
                        .setWarningOnly(protectWarningOnly))));
        return true;
    }

    /** Add a conditional format unless an identical one is already on the sheet. */
    private boolean addConditionalFormat(Integer sheetId, String formula, int firstRow, int width,
            List<Request> requests) {
        try {
            var existing = db.sheets().spreadsheets().get(db.spreadsheetId())
                    .setFields("sheets(properties.sheetId,conditionalFormats)")
                    .execute();
            if (existing.getSheets() != null) {
                for (var s : existing.getSheets()) {
                    if (!sheetId.equals(s.getProperties().getSheetId())) continue;
                    if (s.getConditionalFormats() == null) continue;
                    for (var rule : s.getConditionalFormats()) {
                        var b = rule.getBooleanRule();
                        if (b == null || b.getCondition() == null) continue;
                        var values = b.getCondition().getValues();
                        if (values != null && !values.isEmpty()
                                && formula.equals(values.get(0).getUserEnteredValue())) {
                            return false;
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new GSheetDbException("could not read the existing formatting of '"
                    + def.name() + "'", e);
        }

        requests.add(new Request().setAddConditionalFormatRule(new AddConditionalFormatRuleRequest()
                .setIndex(0)
                .setRule(new ConditionalFormatRule()
                        .setRanges(List.of(new GridRange()
                                .setSheetId(sheetId)
                                .setStartRowIndex(firstRow - 1)
                                .setStartColumnIndex(0)
                                .setEndColumnIndex(width)))
                        .setBooleanRule(new BooleanRule()
                                .setCondition(new BooleanCondition()
                                        .setType("CUSTOM_FORMULA")
                                        .setValues(List.of(new ConditionValue()
                                                .setUserEnteredValue(formula))))
                                .setFormat(new CellFormat().setBackgroundColor(DUPLICATE_BG))))));
        return true;
    }

    private int keyColumn() {
        Integer column = docs.columnOf(def.keyField());
        if (column == null) {
            throw new IllegalStateException("Sheet '" + def.name() + "' has no header for its key "
                    + "field '" + def.keyField() + "', so there is no column to guard.");
        }
        return column;
    }

    private void requireKey() {
        if (def.keyField() == null) {
            throw new UnsupportedOperationException(def.name() + " declares no key field, so there "
                    + "is nothing for keyGuard() to guard. Add .key($someField) to its typeDef(...) "
                    + "chain.");
        }
    }
}
