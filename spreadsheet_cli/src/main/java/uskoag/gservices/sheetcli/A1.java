package uskoag.gservices.sheetcli;

import com.google.api.services.sheets.v4.model.GridRange;
import uskoag.gservices.CellRange;

/** GridRange (0-based, end-exclusive, open where unset) back to the A1 a person would type. */
final class A1 {

    private A1() {}

    static String of(GridRange g) {
        if (wholeTab(g)) return "(whole tab)";
        var sc = g.getStartColumnIndex();
        var ec = g.getEndColumnIndex();
        var sr = g.getStartRowIndex();
        var er = g.getEndRowIndex();
        if (sc == null && ec == null) return ((sr == null ? 0 : sr) + 1) + ":" + (er == null ? "" : er);
        var left = col(sc == null ? 0 : sc) + (sr == null ? "" : String.valueOf(sr + 1));
        var right = (ec == null ? "" : col(ec - 1)) + (er == null ? "" : String.valueOf(er));
        return left.equals(right) ? left : left + ":" + right;
    }

    static String col(int zeroBased) { return CellRange.colToLetter(zeroBased + 1); }

    static int sheetId(GridRange g) { return g == null || g.getSheetId() == null ? 0 : g.getSheetId(); }

    /** Same cells, whatever Google chose to leave unset (sheetId 0 is never serialized). */
    static boolean same(GridRange a, GridRange b) { return sheetId(a) == sheetId(b) && key(a).equals(key(b)); }

    static String key(GridRange g) {
        return g == null ? "null,null,null,null" : g.getStartRowIndex() + "," + g.getEndRowIndex() + "," + g.getStartColumnIndex() + "," + g.getEndColumnIndex();
    }

    static boolean wholeTab(GridRange g) { return key(g).equals("null,null,null,null"); }
}
