package uskoag.gservices.sheetcli;

import java.util.*;

/**
 * Which old column is which new column, and which old row is which new row. Columns are keyed by their
 * row-1 header (or, headerless, by their content); rows by their key column, which is the old column A and
 * wherever it went. Same-sized unmatched runs in one gap are paired when their contents mostly agree,
 * which is what a typo fixed in a header or a relabelled row looks like.
 */
final class TabAlign {

    final Grid.Tab a, b;
    final int[] colMap, rowMap;
    final Set<Integer> colRekeyed, rowRekeyed;
    final Map<Integer, Integer> moved = new LinkedHashMap<>();

    TabAlign(Grid.Tab a, Grid.Tab b) {
        this.a = a;
        this.b = b;
        colMap = Align.match(colKeys(a), colKeys(b));
        var keyB = colMap.length > 0 ? colMap[0] : -1;
        rowMap = Align.match(rowKeys(a, 0, true), rowKeys(b, keyB, false));
        rowRekeyed = Align.pairGaps(rowMap, b.rows(), (i, j) -> rowAlike(i, j));
        findMoves();
        colRekeyed = Align.pairGaps(colMap, b.cols(), (i, j) -> colAlike(i, j));
    }

    private static List<String> colKeys(Grid.Tab t) {
        var keys = new ArrayList<String>();
        for (var c = 0; c < t.cols(); c++) {
            var h = t.value(0, c).strip().toLowerCase();
            if (h.isEmpty()) {
                var sb = new StringBuilder("#");
                for (var r = 1; r < Math.min(t.rows(), 60); r++) sb.append(t.value(r, c)).append('\u0001');
                h = sb.toString();
            }
            keys.add(h);
        }
        return keys;
    }

    private List<String> rowKeys(Grid.Tab t, int keyCol, boolean old) {
        var keys = new ArrayList<String>();
        for (var r = 0; r < t.rows(); r++) {
            var k = keyCol < 0 ? "" : t.value(r, keyCol).strip();
            keys.add(k.isEmpty() ? "#" + rowContent(t, r, old) : k);
        }
        return keys;
    }

    /** A row's values over the columns both versions share, so an inserted column does not change it. */
    String rowContent(Grid.Tab t, int r, boolean old) {
        var sb = new StringBuilder();
        for (var c = 0; c < colMap.length; c++) {
            if (colMap[c] < 0) continue;
            sb.append(t.value(r, old ? c : colMap[c])).append('\u0001');
        }
        return sb.toString();
    }

    /** Compared along the row alignment, so a row inserted above does not make a renamed column look new. */
    private boolean colAlike(int i, int j) {
        int same = 0, seen = 0;
        for (var r = 1; r < rowMap.length; r++) {
            if (rowMap[r] < 0) continue;
            var x = a.value(r, i);
            var y = b.value(rowMap[r], j);
            if (x.isEmpty() && y.isEmpty()) continue;
            seen++;
            if (x.equals(y)) same++;
        }
        return seen == 0 || same * 2 >= seen;
    }

    private boolean rowAlike(int i, int j) {
        int same = 0, seen = 0;
        for (var c = 0; c < colMap.length; c++) {
            if (colMap[c] < 0) continue;
            var x = a.value(i, c);
            var y = b.value(j, colMap[c]);
            if (x.isEmpty() && y.isEmpty()) continue;
            seen++;
            if (x.equals(y)) same++;
        }
        return seen == 0 || same * 2 >= seen;
    }

    /**
     * An old row nobody matched that reappears among the new rows nobody matched was moved, not deleted and
     * retyped: first by identical content, then by a column-A label that is unique on both sides (a TOTAL
     * row moved and edited on the way).
     */
    private void findMoves() {
        pairUnmatched((t, r, old) -> rowContent(t, r, old), false);
        var keyB = colMap.length > 0 ? colMap[0] : -1;
        if (keyB >= 0) pairUnmatched((t, r, old) -> t.value(r, old ? 0 : keyB).strip(), true);
    }

    interface RowKey { String of(Grid.Tab t, int r, boolean old); }

    private void pairUnmatched(RowKey key, boolean uniqueOnly) {
        var usedB = new boolean[b.rows()];
        for (var x : rowMap) if (x >= 0) usedB[x] = true;
        var freeB = new HashMap<String, Deque<Integer>>();
        for (var j = 0; j < b.rows(); j++) {
            var k = key.of(b, j, false);
            if (!usedB[j] && !k.replace("\u0001", "").isBlank()) freeB.computeIfAbsent(k, x -> new ArrayDeque<>()).add(j);
        }
        var countA = new HashMap<String, Integer>();
        for (var i = 0; i < rowMap.length; i++) if (rowMap[i] < 0) countA.merge(key.of(a, i, true), 1, Integer::sum);
        for (var i = 0; i < rowMap.length; i++) {
            if (rowMap[i] >= 0) continue;
            var k = key.of(a, i, true);
            var q = freeB.get(k);
            if (q == null || q.isEmpty() || uniqueOnly && (q.size() > 1 || countA.get(k) > 1)) continue;
            var j = q.poll();
            rowMap[i] = j;
            moved.put(i, j);
        }
    }

    int[] unmatchedB(int[] map, int size) {
        var used = new boolean[size];
        for (var x : map) if (x >= 0) used[x] = true;
        return java.util.stream.IntStream.range(0, size).filter(j -> !used[j]).toArray();
    }
}
