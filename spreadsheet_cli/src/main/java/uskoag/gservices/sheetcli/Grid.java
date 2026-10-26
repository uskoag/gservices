package uskoag.gservices.sheetcli;

import java.util.List;

/**
 * A whole spreadsheet at one moment: every tab's displayed values and formulas, as rows of strings.
 * This is also the on-disk shape of a snapshot, so it stays plain.
 *
 * @param time   when it was read (epoch millis), which is what attribution windows are built from; 0 when unknown
 * @param source what it was read from, as the report should name it
 */
record Grid(String spreadsheetId, String title, long time, String source, List<Tab> tabs) {

    record Tab(int sheetId, String title, List<List<String>> values, List<List<String>> formulas) {

        int rows() { return Math.max(values.size(), formulas.size()); }

        int cols() {
            var c = 0;
            for (var r : values) c = Math.max(c, r.size());
            for (var r : formulas) c = Math.max(c, r.size());
            return c;
        }

        String value(int r, int c) { return at(values, r, c); }

        /** The formula when the cell holds one, else "". */
        String formula(int r, int c) {
            var f = at(formulas, r, c);
            return f.startsWith("=") ? f : "";
        }

        private static String at(List<List<String>> g, int r, int c) {
            if (r >= g.size()) return "";
            var row = g.get(r);
            return c >= row.size() || row.get(c) == null ? "" : row.get(c);
        }
    }

    Tab tab(int sheetId) {
        for (var t : tabs) if (t.sheetId() == sheetId) return t;
        return null;
    }

    Tab tab(String title) {
        for (var t : tabs) if (t.title().equals(title)) return t;
        return null;
    }
}
