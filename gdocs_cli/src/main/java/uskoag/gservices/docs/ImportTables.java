package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.StructuralElement;
import java.util.ArrayList;
import java.util.List;

/** Source tables matched to the destination's, nested ones included, in document order. */
final class ImportTables {

    record Pair(SrcTable src, StructuralElement dest, int depth) {}

    private ImportTables() {}

    static List<Pair> of(List<SrcTable> top, List<StructuralElement> destTop) {
        var out = new ArrayList<Pair>();
        walk(top, destTop, 0, out);
        return out;
    }

    private static void walk(List<SrcTable> src, List<StructuralElement> dest, int depth, List<Pair> out) {
        if (src.size() != dest.size()) throw new IllegalStateException("stage tables: " + dest.size() + " table(s) found at depth " + depth + ", the source has " + src.size());
        for (var i = 0; i < src.size(); i++) {
            var st = src.get(i);
            var rows = dest.get(i).getTable().getTableRows();
            if (rows.size() != st.rows || rows.get(0).getTableCells().size() != st.cols)
                throw new IllegalStateException("stage tables: a table came back " + rows.size() + " rows, expected " + st.rows + "x" + st.cols);
            out.add(new Pair(st, dest.get(i), depth));
            for (var c : st.cells) {
                var nested = c.units.stream().filter(u -> u instanceof SrcTable).map(u -> (SrcTable) u).toList();
                var found = rows.get(c.row).getTableCells().get(c.col).getContent().stream().filter(e -> e.getTable() != null).toList();
                if (!nested.isEmpty() && !found.isEmpty()) walk(nested, found, depth + 1, out);
            }
        }
    }

    static int count(List<SrcTable> ts) {
        var n = 0;
        for (var t : ts) { n++; for (var c : t.cells) n += count(c.units.stream().filter(u -> u instanceof SrcTable).map(u -> (SrcTable) u).toList()); }
        return n;
    }
}
