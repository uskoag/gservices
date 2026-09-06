package uskoag.gservices;

import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Spreadsheet;

public class CellRange {

    /** 1-based column index → letter string.  1→A, 26→Z, 27→AA, 702→ZZ, … */
    public static String colToLetter(int col) {
        StringBuilder sb = new StringBuilder();
        while (col > 0) {
            int rem = (col - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            col = (col - 1) / 26;
        }
        return sb.toString();
    }

    /** Column letter string → 1-based index.  A→1, Z→26, AA→27, … */
    public static int letterToCol(String letters) {
        int result = 0;
        for (char c : letters.toUpperCase().toCharArray()) {
            result = result * 26 + (c - 'A' + 1);
        }
        return result;
    }

    /** Extract column letters from a cell address.  "AB23" → "AB" */
    public static String colOf(String cellAddr) {
        return cellAddr.replaceAll("[0-9]+$", "").toUpperCase();
    }

    /** Extract row number from a cell address.  "AB23" → 23 */
    public static int rowOf(String cellAddr) {
        return Integer.parseInt(cellAddr.replaceAll("^[A-Za-z]+", ""));
    }

    /** True if address has no colon (single cell, not a range). */
    public static boolean isSingleCell(String address) {
        return !address.contains(":");
    }

    /**
     * Parse an A1-notation range into its four boundary coordinates.
     * Returns int[4]: { startColIdx(1-based), startRow, endColIdx(1-based), endRow }
     */
    public static int[] bounds(String address) {
        String upper = address.toUpperCase();
        String[] parts = upper.split(":", 2);
        int sc = letterToCol(colOf(parts[0]));
        int sr = rowOf(parts[0]);
        if (parts.length == 1) return new int[]{sc, sr, sc, sr};
        int ec = letterToCol(colOf(parts[1]));
        int er = rowOf(parts[1]);
        return new int[]{sc, sr, ec, er};
    }

    /** Numeric sheetId for a tab name, or null if no such tab exists. */
    public static Integer sheetIdByName(Spreadsheet ss, String sheetName) {
        return ss.getSheets().stream()
            .filter(s -> sheetName.equals(s.getProperties().getTitle()))
            .map(s -> s.getProperties().getSheetId())
            .findFirst().orElse(null);
    }

    /** 1-based column index of an A1 token, or null if it carries no column letters. "C2"→3, "C"→3, "2"→null */
    public static Integer colOrNull(String token) {
        var letters = token.replaceAll("[0-9]+$", "").toUpperCase();
        return letters.isEmpty() ? null : letterToCol(letters);
    }

    /** 1-based row index of an A1 token, or null if it carries no row digits. "C2"→2, "2"→2, "C"→null */
    public static Integer rowOrNull(String token) {
        var digits = token.replaceAll("^[A-Za-z]+", "");
        return digits.isEmpty() ? null : Integer.parseInt(digits);
    }

    /**
     * A1-notation range (or single cell) + sheetId → GridRange (0-based, end-exclusive).
     * Open-ended ranges are supported: a missing row bound ("C2:C", "A2:A") or column bound
     * ("2:5") leaves the corresponding GridRange index unset, which the API reads as "unbounded
     * to the sheet edge" — the natural meaning of the open A1 form.
     */
    public static GridRange toGridRange(int sheetId, String address) {
        var parts = address.toUpperCase().split(":", 2);
        var last = parts[parts.length - 1];
        var startCol = colOrNull(parts[0]);
        var startRow = rowOrNull(parts[0]);
        var endCol = colOrNull(last);   // 1-based inclusive == 0-based exclusive, so no +1 needed
        var endRow = rowOrNull(last);
        var gr = new GridRange().setSheetId(sheetId);
        if (startCol != null) gr.setStartColumnIndex(startCol - 1);
        if (startRow != null) gr.setStartRowIndex(startRow - 1);
        if (endCol != null) gr.setEndColumnIndex(endCol);
        if (endRow != null) gr.setEndRowIndex(endRow);
        return gr;
    }

    /** Sheet-name portion of a full A1 reference ("'Allowed Values'!A2:A" → "Allowed Values"), or
     *  `defaultSheet` if the reference has no "!" (bare range, implicitly on the caller's own sheet). */
    public static String sheetNameOf(String rangeRef, String defaultSheet) {
        int bang = rangeRef.lastIndexOf('!');
        if (bang < 0) return defaultSheet;
        var name = rangeRef.substring(0, bang);
        if (name.length() >= 2 && name.startsWith("'") && name.endsWith("'")) {
            name = name.substring(1, name.length() - 1).replace("''", "'");
        }
        return name;
    }

    /** Bare A1 range portion (after the last "!") of a full reference; unchanged if there's no "!". */
    public static String rangeOnlyOf(String rangeRef) {
        int bang = rangeRef.lastIndexOf('!');
        return bang < 0 ? rangeRef : rangeRef.substring(bang + 1);
    }

    /**
     * A tab title as it must appear to the left of "!" in an A1 reference. Everywhere else in this
     * tool a sheet name is the literal title — it is matched against `properties.title` — so it is
     * this one step, building the reference, that has to add the quoting A1 notation wants.
     *
     * <p>Google accepts a bare title only when it reads as an identifier; a space, a hyphen, a plus,
     * an apostrophe or a leading digit each need the quoted form, and an apostrophe inside the title
     * is doubled within it. Quoting is always legal, so this quotes unconditionally rather than try
     * to guess which titles are safe bare — "'Sheet1'!A1" and "Sheet1!A1" mean the same thing.
     *
     * <p>A title that arrives already wrapped in quotes is passed through untouched, so a caller
     * who quotes on the command line (which worked before this method existed) is not quoted twice.
     */
    public static String a1Sheet(String title) {
        if (title.length() >= 2 && title.startsWith("'") && title.endsWith("'")) return title;
        return "'" + title.replace("'", "''") + "'";
    }

    /** Full A1 reference for a range on a named tab: ("Q1 2026", "A1:C3") → "'Q1 2026'!A1:C3". */
    public static String a1Ref(String title, String range) {
        return a1Sheet(title) + "!" + range;
    }
}
