package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Stages A and B: the text and the empty tables, then the content of the cells, one round per nesting depth.
 * Everything goes in at ONE fixed index, last element first, so each insertion pushes the earlier ones down and no
 * index has to be computed. A chunk of paragraphs ahead of a table (or at the end) takes no final newline: it goes
 * into the empty paragraph that insertTable leaves ahead of the table, and into the one that already ends the region.
 */
final class ImportInsert {

    private ImportInsert() {}

    static List<Request> structure(List<Unit> units, String tabId, int p) {
        var out = new ArrayList<Request>();
        var loc = new Location().setTabId(tabId).setIndex(p);
        var chunk = new ArrayList<String>();
        var chunks = new ArrayList<Object>();
        for (var u : units) {
            if (u instanceof SrcPara sp) { if (!sp.breakOnly) chunk.add(sp.line()); }
            else { chunks.add(List.copyOf(chunk)); chunk.clear(); chunks.add(u); }
        }
        chunks.add(List.copyOf(chunk));
        for (var i = chunks.size() - 1; i >= 0; i--) {
            if (chunks.get(i) instanceof SrcTable t) out.add(new Request().setInsertTable(new InsertTableRequest().setRows(t.rows).setColumns(t.cols).setLocation(loc)));
            else {
                var text = String.join("\n", ((List<?>) chunks.get(i)).stream().map(Object::toString).toList());
                if (!text.isEmpty()) out.add(new Request().setInsertText(new InsertTextRequest().setText(text).setLocation(loc)));
            }
        }
        return out;
    }

    /** The cells of every table at this depth, last cell of the last table first, so nothing shifts under the cells still to come. */
    static List<Request> cells(List<ImportTables.Pair> pairs, int depth, String tabId) {
        var out = new ArrayList<Request>();
        for (var t = pairs.size() - 1; t >= 0; t--) {
            var pr = pairs.get(t);
            if (pr.depth() != depth) continue;
            var rows = pr.dest().getTable().getTableRows();
            for (var r = pr.src().rows - 1; r >= 0; r--) for (var c = pr.src().cols - 1; c >= 0; c--) {
                var cell = pr.src().cell(r, c);
                if (cell.units.size() == 1 && cell.units.get(0) instanceof SrcPara sp && sp.line().isEmpty()) continue;
                out.addAll(structure(cell.units, tabId, Para.idx(rows.get(r).getTableCells().get(c).getContent().get(0).getStartIndex())));
            }
        }
        return out;
    }
}
