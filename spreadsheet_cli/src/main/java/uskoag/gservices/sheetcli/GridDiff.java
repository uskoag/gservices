package uskoag.gservices.sheetcli;

import java.util.*;

/**
 * Two whole spreadsheets compared. Tabs are paired by their sheetId when both sides are the same
 * spreadsheet (that id survives a rename), then by title; what is left over was added or removed.
 */
record GridDiff(Grid from, Grid to, List<StructChange> struct, List<CellDiff> cells) {

    static GridDiff of(Grid a, Grid b, String onlyTab, boolean formulas) {
        var struct = new ArrayList<StructChange>();
        var cells = new ArrayList<CellDiff>();
        var sameFile = a.spreadsheetId().equals(b.spreadsheetId());
        var pairedB = new HashSet<Grid.Tab>();
        for (var ta : a.tabs()) {
            var tb = sameFile ? b.tab(ta.sheetId()) : null;
            if (tb == null) tb = b.tab(ta.title());
            if (tb == null || pairedB.contains(tb)) {
                if (onlyTab == null || onlyTab.equals(ta.title()))
                    struct.add(new StructChange(ta.title(), "tab-removed", "tab removed (it had " + ta.rows() + " row(s), " + ta.cols() + " column(s))"));
                continue;
            }
            pairedB.add(tb);
            if (onlyTab != null && !onlyTab.equals(tb.title()) && !onlyTab.equals(ta.title())) continue;
            if (!ta.title().equals(tb.title()))
                struct.add(new StructChange(tb.title(), "tab-renamed", "tab renamed: \"" + ta.title() + "\" -> \"" + tb.title() + "\""));
            TabDiff.of(ta, tb, formulas, struct, cells);
        }
        for (var tb : b.tabs())
            if (!pairedB.contains(tb) && (onlyTab == null || onlyTab.equals(tb.title())))
                struct.add(new StructChange(tb.title(), "tab-added", "tab added (" + tb.rows() + " row(s), " + tb.cols() + " column(s))"));
        return new GridDiff(a, b, struct, cells);
    }
}
