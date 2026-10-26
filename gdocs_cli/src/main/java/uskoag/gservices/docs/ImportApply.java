package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The replay, stage by stage, each followed by a fresh read of the destination so no index is ever worked out
 * by hand: A text and empty tables, B cell text, S1 resets and lists, C styles, E pictures and page breaks,
 * M merged cells, then a full comparison with the source. A failure names the stage and where the partial import sits.
 */
final class ImportApply {

    private ImportApply() {}

    static List<String> run(String id, Document doc, Tab tab, String after, ImportPlan plan, DocumentTab srcTab, java.util.concurrent.Callable<Map<String, String>> pictures) throws Exception {
        var tabId = Tabs.id(tab);
        var bodyEnd = ImportDest.bodyEnd(tab);
        var last = tab.getDocumentTab().getBody().getContent().getLast();
        var anchor = after == null ? null : new Scope(tab, Para.of(tab.getDocumentTab())).anchor("import --after", after);
        var pre = new ArrayList<Request>();
        int p, tail;
        if (anchor == null || anchor.end() == bodyEnd) {
            var s = Para.idx(last.getStartIndex());
            if (bodyEnd - s == 1) { p = s; tail = 0; }
            else { pre.add(split(tabId, bodyEnd - 1)); p = bodyEnd; tail = 0; }
        } else { pre.add(split(tabId, anchor.end() - 1)); p = anchor.end(); tail = bodyEnd - p; }
        try {
            return stages(id, doc, tabId, p, tail, pre, plan, srcTab, pictures);
        } catch (Exception e) {
            var where = "stopped in stage " + ImportSend.stage + "; whatever was imported sits in tab " + Tabs.title(tab) + " from index " + p + " on";
            throw new IllegalStateException((e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "\n  " + where, e);
        }
    }

    private static List<String> stages(String id, Document doc, String tabId, int p, int tail, List<Request> pre, ImportPlan plan, DocumentTab srcTab, java.util.concurrent.Callable<Map<String, String>> pictures) throws Exception {
        var a = new ArrayList<>(pre);
        a.addAll(ImportInsert.structure(plan.units, tabId, p));
        ImportSend.send(id, "A text and tables", a, doc.getRevisionId());
        var d = ImportDest.read(id, tabId, p, tail);
        for (var depth = 0; ; depth++) {
            final var dep = depth;
            var pairs = ImportTables.of(plan.tables, d.tables());
            if (pairs.stream().noneMatch(x -> x.depth() == dep)) break;
            var cells = ImportInsert.cells(pairs, depth, tabId);
            if (!cells.isEmpty()) ImportSend.send(id, "B cell content, depth " + depth, cells, null);
            d = ImportDest.read(id, tabId, p, tail);
        }
        var dp = d.paras();
        ImportVerify.aligned(plan, dp, d, true);
        var report = new ArrayList<String>();
        ImportSend.send(id, "S1 lists", ImportLists.make(plan, d, dp, srcTab, report), null);
        d = ImportDest.read(id, tabId, p, tail);
        dp = d.paras();
        ImportVerify.aligned(plan, dp, d, false);
        ImportSend.send(id, "C paragraph and table styles", ImportStyle.make(plan, d, dp), null);
        d = ImportDest.read(id, tabId, p, tail);
        dp = d.paras();
        var links = ImportLinks.make(plan, d, dp);
        if (!links.isEmpty()) ImportSend.send(id, "L heading links", links, null);
        ImportSend.send(id, "T text styles", ImportStyle.texts(plan, d, dp), null);
        var ats = ImportObjects.make(plan, d, dp, pictures.call());
        var fids = new java.util.LinkedHashMap<String, String>();
        if (!ats.isEmpty()) {
            var replies = ImportSend.sendReplies(id, "E pictures, page breaks and footnote marks", ats.stream().map(ImportObjects.At::req).toList());
            for (var i = 0; i < ats.size(); i++) if (ats.get(i).footnote() != null) fids.put(ats.get(i).footnote(), replies.get(i).getCreateFootnote().getFootnoteId());
        }
        d = ImportDest.read(id, tabId, p, tail);
        if (!fids.isEmpty()) {
            ImportSend.send(id, "F footnote text", ImportFootnotes.make(plan, d, fids), null);
            d = ImportDest.read(id, tabId, p, tail);
        }
        var merges = ImportObjects.merges(plan, d);
        if (!merges.isEmpty()) ImportSend.send(id, "M merged cells", merges, null);
        ImportSend.stage = "verify";
        var out = new ArrayList<>(report.stream().distinct().toList());
        var last = ImportDest.read(id, tabId, p, tail);
        out.addAll(ImportVerify.finish(plan, last));
        out.addAll(ImportFootnotes.verify(plan, last, fids));
        return out;
    }

    private static Request split(String tabId, int at) {
        return new Request().setInsertText(new InsertTextRequest().setText("\n").setLocation(new Location().setTabId(tabId).setIndex(at)));
    }
}
