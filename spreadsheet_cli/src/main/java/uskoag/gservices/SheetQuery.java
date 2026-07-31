package uskoag.gservices;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads sheet values into a throwaway in-memory H2 table `t` and runs a user
 * SQL SELECT against it. Columns are exposed as "A","B","C"... by position;
 * "_ROW" carries the 1-based source sheet row number. Every cell is VARCHAR
 * (kept literally, as stored) so the caller CASTs when a numeric/date compare
 * is wanted. The table lives only for the connection, so no query can touch
 * the real spreadsheet — even an UPDATE/DROP hits the discarded copy.
 */
public class SheetQuery {

    public static QueryResult run(List<List<Object>> values, int firstRow, String sql) {
        var rows = (values == null) ? List.<List<Object>>of() : values;
        var cols = rows.stream().mapToInt(List::size).max().orElse(0);
        try {
            Class.forName("org.h2.Driver");
            try (var conn = DriverManager.getConnection("jdbc:h2:mem:")) {
                createTable(conn, cols);
                insertRows(conn, rows, firstRow, cols);
                return select(conn, sql);
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("H2 driver not found on classpath", e);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static void createTable(Connection conn, int cols) throws SQLException {
        var sb = new StringBuilder("CREATE TABLE t (\"_ROW\" INT");
        for (var j = 1; j <= cols; j++)
            sb.append(", \"").append(CellRange.colToLetter(j)).append("\" VARCHAR");
        sb.append(")");
        try (var st = conn.createStatement()) { st.execute(sb.toString()); }
    }

    private static void insertRows(Connection conn, List<List<Object>> rows, int firstRow, int cols) throws SQLException {
        if (cols == 0) return;
        var sb = new StringBuilder("INSERT INTO t VALUES (?");
        for (var j = 0; j < cols; j++) sb.append(",?");
        sb.append(")");
        try (var ps = conn.prepareStatement(sb.toString())) {
            for (var i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                ps.setInt(1, firstRow + i);
                for (var j = 0; j < cols; j++) {
                    var cell = (j < row.size()) ? row.get(j) : null;
                    ps.setString(j + 2, cell == null ? "" : cell.toString());
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static QueryResult select(Connection conn, String sql) throws SQLException {
        try (var st = conn.createStatement(); var rs = st.executeQuery(sql)) {
            var md = rs.getMetaData();
            var n = md.getColumnCount();
            var columns = new ArrayList<String>(n);
            for (var c = 1; c <= n; c++) {
                var lbl = md.getColumnLabel(c);
                columns.add("_ROW".equals(lbl) ? "_row" : lbl);
            }
            var out = new ArrayList<List<Object>>();
            while (rs.next()) {
                var row = new ArrayList<Object>(n);
                for (var c = 1; c <= n; c++) {
                    var v = rs.getObject(c);
                    if (rs.wasNull()) v = null;
                    else if (!(v instanceof Number || v instanceof Boolean)) v = rs.getString(c);
                    row.add(v);
                }
                out.add(row);
            }
            return new QueryResult(columns, out);
        }
    }
}
