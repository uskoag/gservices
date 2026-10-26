package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Setting a nesting level inside a list that already exists. The API has no request for a paragraph's level, and a
 * paragraph bulleted next to a list of the same preset joins it at its neighbour's level whatever tabs it carries
 * (measured 2026-10-21, PRP 26). So the whole list is bulleted again in ONE createParagraphBullets, each item with
 * tabs for its level. Paragraphs between items are bulleted with them and taken out again with their own indents
 * restored, which keeps the list one list and its numbering continuous across them.
 * Positions are as they stand when these requests run: earlier requests in the same batch already applied.
 */
final class Relist {

    record Item(int start, int end, Paragraph p, Integer level, boolean member) {}

    private Relist() {}

    /** Every paragraph from the list's first item to its last, as read; others' list or a table inside it stops the run. */
    static List<Para> span(String label, Scope s, String listId) {
        var ps = s.paras();
        var first = -1;
        var last = -1;
        for (var i = 0; i < ps.size(); i++) if (listId.equals(listId(ps.get(i)))) { if (first < 0) first = i; last = i; }
        var span = ps.subList(first, last + 1);
        for (var p : span) {
            var id = listId(p);
            if (id != null && !id.equals(listId)) Out.die(label + ": another list (" + id + ") sits inside list " + listId + " at \"" + p.preview() + "\"; its level cannot be set without merging the two");
        }
        var body = s.tab().getDocumentTab().getBody().getContent();
        for (var e : body)
            if (e.getTable() != null && Para.idx(e.getStartIndex()) > span.getFirst().start() && Para.idx(e.getStartIndex()) < span.getLast().end())
                Out.die(label + ": list " + listId + " continues after a table; setting a level would re-list across the table, which the API cannot do");
        return span;
    }

    static String listId(Para p) { return p.p().getBullet() == null ? null : p.p().getBullet().getListId(); }

    /** An insert after a list item that asks for a level other than the item's own: only then is the list re-listed. */
    static boolean deeper(Edit.Insert ins, Para anchor, DocumentTab dt) {
        if (anchor.p().getBullet() == null) return false;
        var lvl = Styles.level(anchor.p());
        if (ins.paras().stream().allMatch(np -> np.level() == null || np.level() == lvl)) return false;
        for (var np : ins.paras())
            if (np.style() != null || np.preset() != null || np.like() != null || np.list() != null && "number".equals(np.list()) != Styles.ordered(anchor.p(), dt))
                Out.die(ins.label() + ": a new level inside the anchor's list takes text, level and a matching list kind only; "
                        + "\"like\", \"style\", \"preset\" or a list of the other kind need an insert of their own");
        return true;
    }

    /** The new paragraphs (already inserted after the anchor, each {@code plains[i]} long plus its mark) join the anchor's list at their levels. */
    static List<Request> forInsert(Edit.Insert ins, Scope s, Para anchor, List<String> plains, List<String> report, List<Expect.Bullet> expects) {
        var id = listId(anchor);
        var dt = s.tab().getDocumentTab();
        var shift = plains.stream().mapToInt(t -> t.length() + 1).sum();
        var items = new ArrayList<Item>();
        var members = 0;
        for (var p : span(ins.label(), s, id)) {
            var off = p.start() >= anchor.end() ? shift : 0;
            var member = id.equals(listId(p));
            items.add(new Item(p.start() + off, p.end() + off, p.p(), Styles.level(p.p()), member));
            if (member) { members++; expects.add(new Expect.Bullet(s.tabId(), p.plain(), true, Styles.level(p.p()), null)); }
            if (p != anchor) continue;
            var at = anchor.end();
            for (var i = 0; i < plains.size(); i++) {
                var np = ins.paras().get(i);
                items.add(new Item(at, at + plains.get(i).length() + 1, null, np.level() == null ? Styles.level(anchor.p()) : np.level(), true));
                at += plains.get(i).length() + 1;
            }
        }
        var preset = ListPresets.choose(null, null, anchor, dt);
        report.add("  list " + id + " is bulleted again as a whole (" + (members + plains.size()) + " items, " + preset
                + ") so the new level can be set; " + (items.size() - members - plains.size()) + " paragraph(s) between its items keep their indents");
        return requests(s.tabId(), items, preset);
    }

    static List<Request> requests(String tabId, List<Item> span, String preset) {
        var from = span.getFirst().start();
        var to = span.getLast().end();
        var out = new ArrayList<Request>();
        out.add(new Request().setDeleteParagraphBullets(new DeleteParagraphBulletsRequest().setRange(rng(tabId, from, to))));
        out.add(ListRuns.resetIndent(tabId, from, to));
        var tabs = 0;
        for (var it : span.reversed()) {
            var n = it.member() ? it.level() : 0;
            if (n > 0) out.add(new Request().setInsertText(new InsertTextRequest().setText("\t".repeat(n)).setLocation(new Location().setIndex(it.start()).setTabId(tabId))));
            tabs += n;
        }
        out.add(new Request().setCreateParagraphBullets(new CreateParagraphBulletsRequest().setRange(rng(tabId, from, to + tabs)).setBulletPreset(preset)));
        for (var it : span) if (!it.member()) {
            out.add(new Request().setDeleteParagraphBullets(new DeleteParagraphBulletsRequest().setRange(rng(tabId, it.start(), it.end()))));
            out.add(restoreIndent(tabId, it));
        }
        return out;
    }

    private static Request restoreIndent(String tabId, Item it) {
        var src = it.p().getParagraphStyle() == null ? new ParagraphStyle() : it.p().getParagraphStyle();
        return new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest()
                .setParagraphStyle(new ParagraphStyle().setIndentStart(src.getIndentStart()).setIndentFirstLine(src.getIndentFirstLine()).setIndentEnd(src.getIndentEnd()))
                .setRange(rng(tabId, it.start(), it.end())).setFields("indentStart,indentFirstLine,indentEnd"));
    }

    private static Range rng(String tabId, int from, int to) { return new Range().setTabId(tabId).setStartIndex(from).setEndIndex(to); }
}
