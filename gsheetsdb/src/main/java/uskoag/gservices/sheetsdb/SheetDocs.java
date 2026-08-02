package uskoag.gservices.sheetsdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import xyz.jphil.datahelper.DataHelper_I;

/**
 * The documents of one type: their mirror in H2, and the record of what has changed since they were
 * loaded.
 *
 * <p>Internal. Callers reach documents through {@link Query#query} and {@link Upsert#upsert}, which
 * is the ArcadeDB shape — {@code query(db, Treaty.DEF)} rather than a handle object that has to be
 * named after a grid.
 *
 * @param <E> the document type
 */
final class SheetDocs<E extends DataHelper_I<E>> {

    /** The sheet row a document came from. Not a field of the model — the model never sees it. */
    static final String ROW = "_ROW";

    private final GSheetDb db;
    private final TypeDef<E> def;
    private final Connection conn;

    /** Numeric sheetId, needed to delete a row and to add a conditional format. */
    private Integer sheetId;

    /** Declared field -&gt; 1-based sheet column, for those the header actually has. */
    private final Map<String, Integer> columnOf = new LinkedHashMap<>();

    private List<String> missingFields = List.of();

    /** The loaded grid, kept so a save can carry through cells no field owns. Row-major, 0-based. */
    private List<List<Object>> grid = List.of();

    /** Key value -&gt; the sheet rows carrying it, for keys that appear more than once. */
    private Map<Object, List<Integer>> duplicateKeys = Map.of();

    /** The header row exactly as loaded, for the setup pass to find or place its own columns. */
    private List<Object> headerCells = List.of();

    private int firstDataRow = 2;
    private int nextFreeRow = 2;

    /**
     * The rightmost column any declared field occupies — the extent a save writes.
     *
     * <p>Deliberately not the header width. Anything beyond the last declared field belongs to
     * somebody else: a note, a formula, the duplicate-report column this library installs itself.
     * Writing to the header width would blank all of it on every save. Undeclared columns that fall
     * <em>between</em> declared ones are still carried through untouched by {@code buildRow}.
     */
    private int lastDeclaredColumn = 1;

    /** How wide the header row is, which is what the formatting pass styles. */
    private int headerWidth = 1;

    /**
     * The rightmost column with anything in it <b>anywhere</b> in the sheet, not just in the header.
     *
     * <p>The distinction is what stops this library dropping a new column on top of somebody's data.
     * A column can hold values under a blank header — a working note, a scratch calculation, a
     * half-finished field nobody has labelled yet — and the header row alone cannot see it. Placing
     * anything at {@code headerWidth + 1} would land squarely on it.
     */
    private int usedWidth = 1;

    private final Set<Integer> dirtyRows = new TreeSet<>();

    /** Of {@link #dirtyRows}, the ones this session created rather than found. */
    private final Set<Integer> appendedRows = new TreeSet<>();

    /** Pending deletions, keyed — a row number alone stops identifying anything once rows move. */
    private final Map<Object, Integer> deleted = new LinkedHashMap<>();

    /** Mirror row -&gt; the sheet row to write it to, as re-resolved by {@link #relocate}. */
    private final Map<Integer, Integer> rowRemap = new LinkedHashMap<>();

    SheetDocs(GSheetDb db, TypeDef<E> def, Connection conn) {
        this.db = db;
        this.def = def;
        this.conn = conn;
    }

    TypeDef<E> def() {
        return def;
    }

    List<String> missingFields() {
        return missingFields;
    }

    Map<Object, List<Integer>> duplicateKeys() {
        return duplicateKeys;
    }

    Integer sheetId() {
        return sheetId;
    }

    int firstDataRow() {
        return firstDataRow;
    }

    /** 1-based sheet column of a field, or {@code null} if the header does not have it. */
    Integer columnOf(String field) {
        return columnOf.get(field);
    }

    int headerWidth() {
        return headerWidth;
    }

    int usedWidth() {
        return usedWidth;
    }

    int lastDeclaredColumn() {
        return lastDeclaredColumn;
    }

    /** 1-based column whose header cell reads {@code text}, or {@code null}. */
    Integer columnOfHeader(String text) {
        int at = indexOfHeader(headerCells, text);
        return at < 0 ? null : at + 1;
    }

    // ---- loading ---------------------------------------------------------------

    void load(Integer sheetId, List<List<Object>> values) throws SQLException {
        this.sheetId = sheetId;
        this.grid = values == null ? new ArrayList<>() : new ArrayList<>(values);
        this.dirtyRows.clear();
        this.deleted.clear();
        this.appendedRows.clear();
        this.rowRemap.clear();
        this.columnOf.clear();

        int header = def.headerRow();
        this.firstDataRow = header + 1;

        this.headerCells = grid.size() >= header ? List.copyOf(grid.get(header - 1)) : List.of();
        var missing = new ArrayList<String>();
        for (String field : def.fields()) {
            int at = indexOfHeader(headerCells, field);
            if (at < 0) missing.add(field);
            else columnOf.put(field, at + 1);
        }
        this.missingFields = List.copyOf(missing);

        int declared = 0;
        for (Integer i : columnOf.values()) declared = Math.max(declared, i);
        this.lastDeclaredColumn = Math.max(1, declared);
        this.headerWidth = Math.max(1, headerCells.size());

        int used = headerCells.size();
        for (List<Object> r : grid) {
            for (int c = r.size(); c > used; c--) {
                if (r.get(c - 1) != null && !r.get(c - 1).toString().isEmpty()) {
                    used = c;
                    break;
                }
            }
        }
        this.usedWidth = Math.max(1, used);

        // The last row with something in it, NOT the height of the grid.
        //
        // Those differ the moment a whole-column array formula is on the sheet — the duplicate
        // report this library installs is exactly one. Such a formula evaluates to "" for every row
        // to the bottom of the sheet, and the API returns those as real cells, so the grid comes back
        // a thousand rows tall with three documents in it. Appending at grid height would drop the
        // next document at row 1001, below a wall of blanks, and every one after it.
        int lastUsed = def.headerRow();
        for (int r = firstDataRow; r <= grid.size(); r++) {
            if (!isBlank(grid.get(r - 1))) lastUsed = r;
        }
        this.nextFreeRow = Math.max(firstDataRow, lastUsed + 1);

        createMirror();
        insertLoaded();
        findDuplicateKeys();
    }

    private static int indexOfHeader(List<Object> headerCells, String field) {
        for (int i = 0; i < headerCells.size(); i++) {
            Object cell = headerCells.get(i);
            if (cell != null && field.equals(cell.toString().trim())) return i;
        }
        return -1;
    }

    private void createMirror() throws SQLException {
        var sql = new StringBuilder("CREATE TABLE ").append(Sql.quote(def.name()))
                .append(" (").append(Sql.quote(ROW)).append(" INT PRIMARY KEY");
        for (String field : def.fields()) {
            sql.append(", ").append(Sql.quote(field)).append(' ')
               .append(SheetValues.h2Type(def.fieldType(field)));
        }
        sql.append(')');
        try (var st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + Sql.quote(def.name()));
            st.execute(sql.toString());
        }
    }

    private void insertLoaded() throws SQLException {
        if (grid.size() < firstDataRow) return;

        try (var ps = conn.prepareStatement(insertSql())) {
            int batched = 0;
            for (int r = firstDataRow; r <= grid.size(); r++) {
                List<Object> cells = grid.get(r - 1);
                if (isBlank(cells)) continue;               // a spacer row is not a document

                ps.setInt(1, r);
                int p = 2;
                for (String field : def.fields()) {
                    ps.setObject(p++, SheetValues.toJava(cellAt(cells, field), def.fieldType(field)));
                }
                ps.addBatch();
                batched++;
            }
            if (batched > 0) ps.executeBatch();
        }
    }

    /**
     * Find key values that appear on more than one row.
     *
     * <p>A spreadsheet cannot enforce uniqueness, so this is the only place it can be established.
     * Found here at load, once, rather than discovered later by a save that silently updated the
     * wrong one of two identical keys.
     */
    private void findDuplicateKeys() throws SQLException {
        this.duplicateKeys = Map.of();
        if (def.keyField() == null) return;

        var byKey = new LinkedHashMap<Object, List<Integer>>();
        var sql = "SELECT " + Sql.quote(def.keyField()) + ", " + Sql.quote(ROW)
                + " FROM " + Sql.quote(def.name()) + " ORDER BY " + Sql.quote(ROW);
        try (var st = conn.createStatement(); var rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Object key = rs.getObject(1);
                if (key == null) continue;
                if (key instanceof String s && s.isBlank()) continue;
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(rs.getInt(2));
            }
        }
        var dups = new LinkedHashMap<Object, List<Integer>>();
        byKey.forEach((k, rows) -> {
            if (rows.size() > 1) dups.put(k, List.copyOf(rows));
        });
        this.duplicateKeys = Map.copyOf(dups);
    }

    private Object cellAt(List<Object> cells, String field) {
        Integer at = columnOf.get(field);
        if (at == null || at > cells.size()) return null;
        return cells.get(at - 1);
    }

    private static boolean isBlank(List<Object> cells) {
        if (cells == null || cells.isEmpty()) return true;
        for (Object c : cells) {
            if (c != null && !c.toString().isBlank()) return false;
        }
        return true;
    }

    // ---- reads -----------------------------------------------------------------

    /** The document whose key field equals {@code key}, or {@code null}. */
    E byKey(Object key) {
        requireKey();
        rejectDuplicate(key);
        var sql = "SELECT * FROM " + Sql.quote(def.name())
                + " WHERE " + Sql.quote(def.keyField()) + " = ?";
        var found = query(sql, List.of(Sql.param(key)));
        return found.isEmpty() ? null : found.get(0);
    }

    // ---- writes ----------------------------------------------------------------

    void save(E document) {
        requireKey();
        requireWritable();
        if (document == null) throw new IllegalArgumentException("document is required");

        Object key = document.getPropertyByName(def.keyField());
        if (key == null || (key instanceof String s && s.isBlank())) {
            throw new IllegalArgumentException(def.name() + ": the key field '" + def.keyField()
                    + "' is empty, so this document cannot be placed in the sheet.");
        }
        rejectDuplicate(key);

        Integer row = rowOfKey(key);
        if (row == null) {
            row = nextFreeRow++;
            insert(row, document);
            appendedRows.add(row);
        } else {
            update(row, document);
        }
        dirtyRows.add(row);
        db.markDirty();
    }

    void delete(E document) {
        requireKey();
        requireWritable();
        if (document == null) throw new IllegalArgumentException("document is required");

        Object key = document.getPropertyByName(def.keyField());
        if (key == null) return;
        rejectDuplicate(key);

        Integer row = rowOfKey(key);
        if (row == null) return;                            // already absent; nothing to do

        exec("DELETE FROM " + Sql.quote(def.name())
                + " WHERE " + Sql.quote(def.keyField()) + " = ?", List.of(Sql.param(key)));
        // Keyed, not just the row number: the row can move before the flush, and the key is the only
        // thing that still identifies which one to remove.
        deleted.put(key, row);
        dirtyRows.remove(row);
        appendedRows.remove(row);
        db.markDirty();
    }

    boolean isDirty() {
        return !dirtyRows.isEmpty() || !deleted.isEmpty();
    }

    // ---- re-addressing, immediately before a write -----------------------------

    /**
     * Rebuild the field-to-column map from the sheet's header as it is <em>now</em>.
     *
     * <p>Called immediately before every flush. Everything this library holds about where things are
     * was measured at {@link #load()}, and a spreadsheet has other people in it: insert one column
     * and every held index is off by one. Writing then puts each value one column to the left of
     * where it belongs — silently, in every dirty row. That is far worse than a lost update, and it
     * is not something a lock could prevent, because the person inserting the column is in a browser
     * and will never consult one.
     *
     * <p>Re-reading costs one small request and makes the whole class of problem go away: the write
     * is addressed by field <em>name</em> against a header read seconds earlier.
     *
     * @return the key field's column as it now stands
     */
    int reheader(List<Object> freshHeader) {
        var cells = freshHeader == null ? List.of() : freshHeader;
        var found = new LinkedHashMap<String, Integer>();
        var missing = new ArrayList<String>();
        for (String field : def.fields()) {
            int at = indexOfHeader(cells, field);
            if (at < 0) missing.add(field);
            else found.put(field, at + 1);
        }
        if (!missing.isEmpty()) {
            throw new GSheetDbException("Sheet '" + def.name() + "' no longer has a header for "
                    + missing + ". Someone changed the header row after this session loaded it, so "
                    + "there is nowhere safe to write. Nothing has been written. Reload and retry.");
        }
        columnOf.clear();
        columnOf.putAll(found);
        return columnOf.get(def.keyField());
    }

    /**
     * Re-locate every pending document by key against the key column as it is now.
     *
     * <p>Row numbers are not identity — insert a row above and every one below it moves. So the rows
     * a flush is about to write are resolved again, by the one thing that does identify a document.
     *
     * @param keyColumn the key column from {@code firstDataRow} down, one cell per row
     */
    void relocate(List<List<Object>> keyColumn) {
        rowRemap.clear();
        if (dirtyRows.isEmpty() && deleted.isEmpty()) return;

        var rowOfKey = new LinkedHashMap<Object, Integer>();
        int lastUsed = def.headerRow();
        if (keyColumn != null) {
            for (int i = 0; i < keyColumn.size(); i++) {
                var cells = keyColumn.get(i);
                if (cells.isEmpty()) continue;
                Object value = SheetValues.toJava(cells.get(0), def.fieldType(def.keyField()));
                if (value == null || (value instanceof String s && s.isBlank())) continue;
                rowOfKey.putIfAbsent(value, firstDataRow + i);
                lastUsed = firstDataRow + i;
            }
        }

        var vanished = new ArrayList<Object>();
        int append = Math.max(firstDataRow, lastUsed + 1);

        for (int row : dirtyRows) {
            Object key = keyAtRow(row);
            if (key == null) continue;
            Integer now = rowOfKey.get(key);
            if (now != null) {
                rowRemap.put(row, now);                 // moved, or never moved — either way, correct
            } else if (appendedRows.contains(row)) {
                rowRemap.put(row, append++);            // new document; land after everything there
            } else {
                vanished.add(key);                      // its row was deleted while we worked
            }
        }
        if (!vanished.isEmpty()) {
            throw new GSheetDbException("Sheet '" + def.name() + "': " + vanished + " no longer "
                    + (vanished.size() == 1 ? "has a row" : "have rows")
                    + " — deleted after this session loaded. Nothing has been written; the rest of "
                    + "the flush was abandoned with it. Reload and retry.");
        }

        // Deletions likewise: by key, and silently dropped if somebody already removed the row.
        var stillThere = new LinkedHashMap<Object, Integer>();
        deleted.forEach((key, was) -> {
            Integer now = rowOfKey.get(key);
            if (now != null) stillThere.put(key, now);
        });
        deleted.clear();
        deleted.putAll(stillThere);
    }

    /** Whether the last {@link #relocate} moved anything, so the mirror's row numbers are stale. */
    boolean wasRemapped() {
        return rowRemap.entrySet().stream().anyMatch(e -> !e.getKey().equals(e.getValue()));
    }

    private Object keyAtRow(int row) {
        var sql = "SELECT " + Sql.quote(def.keyField()) + " FROM " + Sql.quote(def.name())
                + " WHERE " + Sql.quote(ROW) + " = ?";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setInt(1, row);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1) : null;
            }
        } catch (SQLException e) {
            throw new GSheetDbException("could not read the key of row " + row + " of "
                    + def.name(), e);
        }
    }

    // ---- flushing --------------------------------------------------------------

    boolean hasDeletions() {
        return !deleted.isEmpty();
    }

    /** Sheet rows to delete, highest first — the order they must be applied in to stay valid. */
    List<Integer> deletionsDescending() {
        var rows = new ArrayList<>(deleted.values());
        rows.sort((a, b) -> Integer.compare(b, a));
        return rows;
    }

    /**
     * Pending writes, as the smallest set of rectangles that covers <b>only cells this type owns</b>.
     *
     * <p>This is the whole answer to "how do you avoid destroying somebody else's work". The payload
     * never contains a cell belonging to an undeclared column, so there is nothing to carry through
     * and nothing to get stale. A row is written as one rectangle per run of consecutive declared
     * fields: for a sheet whose columns go {@code docId title notes year ratified}, a save sends
     * {@code A2:B2} and {@code D2:E2}, and column C is not in the request at all. Whoever is typing
     * in it cannot lose that edit to this library, whatever the timing.
     *
     * <p>The earlier design read the whole row at load, overwrote the declared cells and wrote the
     * lot back. That preserved the neighbouring column only as it stood at load time — so an edit
     * made in the browser after the load and before the flush was silently reverted. On a sheet with
     * independent human collaboration that is a lost update, and it is not fixable by re-reading
     * more often; it is only fixable by not writing the cell.
     *
     * <p>Rows still coalesce, so saving a thousand consecutive documents is a handful of ranges
     * rather than a thousand.
     */
    List<WriteBlock> pendingBlocks() throws SQLException {
        if (dirtyRows.isEmpty()) return List.of();
        requireWritable();

        var segments = declaredSegments();
        var blocks = new ArrayList<WriteBlock>();

        // Ordered by where each document is going, which is not necessarily where it came from:
        // relocate() may have moved it. Coalescing has to follow the destination.
        var targets = new ArrayList<int[]>();               // {targetRow, mirrorRow}
        for (int row : dirtyRows) targets.add(new int[]{rowRemap.getOrDefault(row, row), row});
        targets.sort((a, b) -> Integer.compare(a[0], b[0]));

        int runStart = -1;
        int previous = -1;
        var run = new ArrayList<Integer>();

        for (int[] target : targets) {
            if (runStart < 0) {
                runStart = target[0];
            } else if (target[0] != previous + 1) {
                emit(blocks, segments, runStart, run);
                run = new ArrayList<>();
                runStart = target[0];
            }
            run.add(target[1]);
            previous = target[0];
        }
        if (runStart >= 0) emit(blocks, segments, runStart, run);
        return blocks;
    }

    private void emit(List<WriteBlock> blocks, List<int[]> segments, int firstRow,
            List<Integer> mirrorRows) throws SQLException {
        for (int[] segment : segments) {
            var values = new ArrayList<List<Object>>(mirrorRows.size());
            for (int row : mirrorRows) {
                var cells = cellsOf(row);
                var slice = new ArrayList<Object>(segment[1] - segment[0] + 1);
                for (int c = segment[0]; c <= segment[1]; c++) {
                    slice.add(cells.getOrDefault(c, ""));
                }
                values.add(slice);
            }
            blocks.add(new WriteBlock(
                    A1.rect(def.name(), firstRow, segment[0],
                            firstRow + mirrorRows.size() - 1, segment[1]),
                    List.copyOf(values)));
        }
    }

    /** Runs of consecutive sheet columns that declared fields occupy, ascending. */
    private List<int[]> declaredSegments() {
        var columns = new ArrayList<>(new TreeSet<>(columnOf.values()));
        var segments = new ArrayList<int[]>();
        int start = -1;
        int previous = -1;
        for (int c : columns) {
            if (start < 0) {
                start = c;
            } else if (c != previous + 1) {
                segments.add(new int[]{start, previous});
                start = c;
            }
            previous = c;
        }
        if (start >= 0) segments.add(new int[]{start, previous});
        return segments;
    }

    /** Sheet column -&gt; the cell to write, for one document. Declared fields only. */
    private Map<Integer, Object> cellsOf(int row) throws SQLException {
        var cells = new LinkedHashMap<Integer, Object>();
        var sql = "SELECT * FROM " + Sql.quote(def.name())
                + " WHERE " + Sql.quote(ROW) + " = ?";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setInt(1, row);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    for (String field : def.fields()) {
                        Object value = SheetValues.fromSql(rs.getObject(field), def.fieldType(field));
                        cells.put(columnOf.get(field), SheetValues.toCell(value));
                    }
                }
            }
        }
        return cells;
    }

    void clearPending() {
        dirtyRows.clear();
        deleted.clear();
        appendedRows.clear();
        rowRemap.clear();
    }

    /** One rectangle of cells to write, already in A1 notation. Covers declared fields only. */
    record WriteBlock(String range, List<List<Object>> rows) {}

    // ---- SQL plumbing used by Query ---------------------------------------------

    List<E> query(String sql, List<Object> params) {
        try (var ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (var rs = ps.executeQuery()) {
                var out = new ArrayList<E>();
                while (rs.next()) out.add(materialize(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new GSheetDbException("query failed on " + def.name() + ": " + sql, e);
        }
    }

    long scalarLong(String sql, List<Object> params) {
        try (var ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new GSheetDbException("query failed on " + def.name() + ": " + sql, e);
        }
    }

    private E materialize(ResultSet rs) throws SQLException {
        E document = def.factory().get();
        for (String field : def.fields()) {
            Object value = SheetValues.fromSql(rs.getObject(field), def.fieldType(field));
            if (value != null) document.setPropertyByName(field, value);
        }
        return document;
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
    }

    // ---- internals -------------------------------------------------------------

    private Integer rowOfKey(Object key) {
        var sql = "SELECT " + Sql.quote(ROW) + " FROM " + Sql.quote(def.name())
                + " WHERE " + Sql.quote(def.keyField()) + " = ?";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setObject(1, Sql.param(key));
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        } catch (SQLException e) {
            throw new GSheetDbException("key lookup failed on " + def.name(), e);
        }
    }

    private String insertSql() {
        var sql = new StringBuilder("INSERT INTO ").append(Sql.quote(def.name()))
                .append(" (").append(Sql.quote(ROW));
        for (String field : def.fields()) sql.append(", ").append(Sql.quote(field));
        sql.append(") VALUES (?");
        for (int i = 0; i < def.fields().size(); i++) sql.append(", ?");
        return sql.append(')').toString();
    }

    private void insert(int row, E document) {
        try (var ps = conn.prepareStatement(insertSql())) {
            ps.setInt(1, row);
            int p = 2;
            for (String field : def.fields()) {
                ps.setObject(p++, Sql.param(document.getPropertyByName(field)));
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new GSheetDbException("insert failed on " + def.name(), e);
        }
    }

    private void update(int row, E document) {
        var sql = new StringBuilder("UPDATE ").append(Sql.quote(def.name())).append(" SET ");
        boolean first = true;
        for (String field : def.fields()) {
            if (!first) sql.append(", ");
            first = false;
            sql.append(Sql.quote(field)).append(" = ?");
        }
        sql.append(" WHERE ").append(Sql.quote(ROW)).append(" = ?");

        try (var ps = conn.prepareStatement(sql.toString())) {
            int p = 1;
            for (String field : def.fields()) {
                ps.setObject(p++, Sql.param(document.getPropertyByName(field)));
            }
            ps.setInt(p, row);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new GSheetDbException("update failed on " + def.name(), e);
        }
    }

    private void exec(String sql, List<Object> params) {
        try (var ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new GSheetDbException("statement failed on " + def.name() + ": " + sql, e);
        }
    }

    /**
     * Refuse to touch a key the sheet carries twice.
     *
     * <p>Two documents with one identity is corruption, not a query result. Reading one of them would
     * be a coin toss and writing one of them would silently discard the other, so both are refused
     * and the offending sheet rows are named. {@code GSheetDb.installKeyGuard} makes the same rows
     * visible to whoever is editing the sheet.
     */
    private void rejectDuplicate(Object key) {
        var rows = duplicateKeys.get(key);
        if (rows != null) {
            throw new GSheetDbException(def.name() + ": the key '" + key + "' appears on sheet rows "
                    + rows + ". Two documents cannot share one identity — reading would pick one at "
                    + "random and writing would discard the other. Fix the sheet: delete or re-key "
                    + "the duplicate rows. Run GSheetDb.installKeyGuard(" + def.name()
                    + ".DEF) to have the spreadsheet highlight them as they are typed.");
        }
    }

    private void requireKey() {
        if (def.keyField() == null) {
            throw new UnsupportedOperationException(def.name()
                    + " declares no key field, so it is read-only. Add .key($someField) to its "
                    + "typeDef(...) chain to make its documents addressable.");
        }
    }

    private void requireWritable() {
        if (!missingFields.isEmpty()) {
            throw new IllegalStateException("Sheet '" + def.name() + "' has no header for "
                    + missingFields + ", so there is nowhere to write "
                    + (missingFields.size() == 1 ? "that field" : "those fields")
                    + ". Add the header cell(s) to row " + def.headerRow() + " and reload.");
        }
    }
}
