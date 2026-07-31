package uskoag.gservices;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a user SQL UPDATE against an in-memory H2 copy of the sheet, then reports
 * the per-cell before→after diff so the caller can push only the changed cells
 * back — and reverse them to self-heal. Rows are keyed by a hidden "_RID"
 * (original 0-based position) so the map back to sheet rows is stable whatever
 * the UPDATE does; "_row" (sheet row number) and letter columns A,B,C... are
 * available in WHERE/SET. Only UPDATE is accepted (append/clear cover the rest).
 */
public class SheetUpdate {

    public static UpdateDiff run(List<List<Object>> values, int firstRow, String sql) {
        if (!sql.strip().regionMatches(true, 0, "UPDATE", 0, 6))
            throw new IllegalArgumentException(
                "update: only UPDATE statements are supported (use append to add rows, clear/write for the rest)");

        var rows = (values == null) ? List.<List<Object>>of() : values;
        var cols = rows.stream().mapToInt(List::size).max().orElse(0);
        var before = snapshot(rows, cols);
        try {
            Class.forName("org.h2.Driver");
            try (var conn = DriverManager.getConnection("jdbc:h2:mem:")) {
                createTable(conn, cols);
                insertRows(conn, before, firstRow);
                int affected;
                try (var st = conn.createStatement()) { affected = st.executeUpdate(sql); }
                return new UpdateDiff(affected, diff(before, readBack(conn, cols, before.size()), firstRow, cols));
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("H2 driver not found on classpath", e);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /** Original letter-column cells as strings, padded to width, null→"". */
    private static List<List<String>> snapshot(List<List<Object>> rows, int cols) {
        var out = new ArrayList<List<String>>(rows.size());
        for (var row : rows) {
            var r = new ArrayList<String>(cols);
            for (var j = 0; j < cols; j++) {
                var cell = (j < row.size()) ? row.get(j) : null;
                r.add(cell == null ? "" : cell.toString());
            }
            out.add(r);
        }
        return out;
    }

    private static void createTable(Connection conn, int cols) throws SQLException {
        var sb = new StringBuilder("CREATE TABLE t (\"_RID\" INT PRIMARY KEY, \"_ROW\" INT");
        for (var j = 1; j <= cols; j++)
            sb.append(", \"").append(CellRange.colToLetter(j)).append("\" VARCHAR");
        sb.append(")");
        try (var st = conn.createStatement()) { st.execute(sb.toString()); }
    }

    private static void insertRows(Connection conn, List<List<String>> before, int firstRow) throws SQLException {
        if (before.isEmpty()) return;
        var cols = before.get(0).size();
        var sb = new StringBuilder("INSERT INTO t VALUES (?,?");
        for (var j = 0; j < cols; j++) sb.append(",?");
        sb.append(")");
        try (var ps = conn.prepareStatement(sb.toString())) {
            for (var i = 0; i < before.size(); i++) {
                ps.setInt(1, i);
                ps.setInt(2, firstRow + i);
                var r = before.get(i);
                for (var j = 0; j < cols; j++) ps.setString(j + 3, r.get(j));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static List<List<String>> readBack(Connection conn, int cols, int n) throws SQLException {
        var after = new ArrayList<List<String>>(n);
        if (cols == 0) return after;
        var sb = new StringBuilder("SELECT ");
        for (var j = 1; j <= cols; j++) {
            if (j > 1) sb.append(",");
            sb.append("\"").append(CellRange.colToLetter(j)).append("\"");
        }
        sb.append(" FROM t ORDER BY \"_RID\"");
        try (var st = conn.createStatement(); var rs = st.executeQuery(sb.toString())) {
            while (rs.next()) {
                var r = new ArrayList<String>(cols);
                for (var j = 1; j <= cols; j++) { var v = rs.getString(j); r.add(v == null ? "" : v); }
                after.add(r);
            }
        }
        return after;
    }

    private static List<CellChange> diff(List<List<String>> before, List<List<String>> after, int firstRow, int cols) {
        var changes = new ArrayList<CellChange>();
        for (var i = 0; i < before.size() && i < after.size(); i++) {
            var b = before.get(i);
            var a = after.get(i);
            for (var j = 0; j < cols; j++) {
                var ov = j < b.size() ? b.get(j) : "";
                var nv = j < a.size() ? a.get(j) : "";
                if (!ov.equals(nv)) {
                    var col = CellRange.colToLetter(j + 1);
                    changes.add(new CellChange(firstRow + i, col + (firstRow + i), col, ov, nv));
                }
            }
        }
        return changes;
    }
}
