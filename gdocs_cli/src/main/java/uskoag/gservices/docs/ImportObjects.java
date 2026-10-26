package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;

/**
 * Stage E: pictures and page breaks, the last to go in because each is one character that shifts everything after it,
 * so they go in from the end of the region backwards. A page break on a paragraph of its own is put in at the start of
 * the paragraph that follows it: insertPageBreak adds a newline after the break, which is exactly that paragraph's end.
 */
final class ImportObjects {

    record At(int index, int seq, Request req, String footnote) {}

    private ImportObjects() {}

    static List<At> make(ImportPlan plan, ImportDest d, List<Para> dp, Map<String, String> pics) {
        var at = new ArrayList<At>();
        for (var sp : plan.all) {
            if (sp.breakOnly) {
                var pos = dp.get(Math.min(sp.beforeFlat, dp.size() - 1)).start();
                at.add(new At(pos, sp.seq * 1000, new Request().setInsertPageBreak(new InsertPageBreakRequest().setLocation(d.loc(pos))), null));
                continue;
            }
            var start = dp.get(plan.flat.indexOf(sp)).start();
            for (var k = 0; k < sp.objs.size(); k++) {
                var o = sp.objs.get(k);
                var idx = start + o.at();
                if (o.kind().equals("pagebreak")) { plan.note("page break inside a paragraph of text (not carried)"); continue; }
                if (o.kind().equals("footnote")) { at.add(new At(idx, sp.seq * 1000 + k + 1, new Request().setCreateFootnote(new CreateFootnoteRequest().setLocation(d.loc(idx))), o.ref())); continue; }
                var eo = plan.images.get(o.ref());
                var size = eo.getSize() == null ? null : new Size().setWidth(eo.getSize().getWidth()).setHeight(eo.getSize().getHeight());
                var req = new InsertInlineImageRequest().setUri(pics.get(o.ref())).setLocation(d.loc(idx));
                if (size != null) req.setObjectSize(size);
                at.add(new At(idx, sp.seq * 1000 + k + 1, new Request().setInsertInlineImage(req), null));
            }
        }
        at.sort(Comparator.comparingInt(At::index).thenComparingInt(At::seq).reversed());
        return at;
    }

    /** Merges last: they change the table's inner indexes, which nothing after them uses except a fresh read. */
    static List<Request> merges(ImportPlan plan, ImportDest d) {
        var out = new ArrayList<Request>();
        var pairs = ImportTables.of(plan.tables, d.tables());
        for (var t = pairs.size() - 1; t >= 0; t--) {
            var at = new Location().setTabId(d.tabId).setIndex(Para.idx(pairs.get(t).dest().getStartIndex()));
            for (var c : pairs.get(t).src().cells) if (c.colSpan > 1 || c.rowSpan > 1)
                out.add(new Request().setMergeTableCells(new MergeTableCellsRequest().setTableRange(new TableRange().setRowSpan(c.rowSpan).setColumnSpan(c.colSpan)
                        .setTableCellLocation(new TableCellLocation().setTableStartLocation(at).setRowIndex(c.row).setColumnIndex(c.col)))));
        }
        return out;
    }
}
