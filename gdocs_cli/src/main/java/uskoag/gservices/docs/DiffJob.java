package uskoag.gservices.docs;

import java.util.*;
import uskoag.gservices.docs.Blocks.Block;
import uskoag.gservices.docs.DocDiff.Change;

/**
 * Runs one comparison or a walk. A walk is read as people's decisions, not keystrokes: consecutive
 * revisions by the same author are clubbed into one step and shown as their net effect (--each shows
 * every revision instead).
 */
final class DiffJob {

    private final String id, fmt, tabSel;
    private final List<String> titles;
    private final boolean json, stat;
    private final Authors authors;
    private final List<Map<String, Object>> steps = new ArrayList<>();
    private int quiet;

    DiffJob(String id, String fmt, List<String> titles, String tabSel, boolean json, boolean stat) throws Exception {
        this.id = id; this.fmt = fmt; this.titles = titles; this.tabSel = tabSel; this.json = json; this.stat = stat;
        this.authors = new Authors(id);
    }

    private List<Block> blocks(RevSpec r) throws Exception {
        return Blocks.of(RevExport.text(id, r.n(), fmt), new TabSplit(titles));
    }

    void pair(RevSpec f, RevSpec t) throws Exception {
        var cs = filter(DocDiff.of(blocks(f), blocks(t)));
        var who = authors.within(f.n(), t.n());
        if (json) { steps.add(step(f, t, cs, null, 0, who)); return; }
        Out.data("diff " + f.label() + " → " + t.label() + "   document " + id);
        Out.data("  from " + f.label() + "  " + f.when() + "  " + f.who());
        Out.data("  to   " + t.label() + "  " + t.when() + "  " + t.who());
        Out.data("  edited in between by: " + (who.isEmpty() ? "(nobody Drive or our journal names; see --steps)" : String.join("; ", who)));
        Out.data("  " + (titles.isEmpty() ? "tabs: not separated (one tab, or --no-tabs)" : "tabs: " + String.join(", ", titles)
                + " (located by their title lines in Drive's export)"));
        Out.data("  who changed which line: diff --steps (edits clubbed per author)");
        Out.data("");
        if (!stat) DiffOut.text(cs, "");
        Out.data((stat ? "" : "\n") + DiffOut.stat(cs));
    }

    /** Club runs of the same author, then diff each run's first version against its last. */
    void walk(List<RevSpec> revs, String by, boolean each) throws Exception {
        var k = 1;
        while (k < revs.size()) {
            var who = authors.of(revs.get(k).n());
            var end = k;
            while (!each && end + 1 < revs.size() && authors.of(revs.get(end + 1).n()).key().equals(who.key())) end++;
            if (by == null || who.name().toLowerCase().contains(by.toLowerCase()))
                emit(revs.get(k - 1), revs.get(end), end - k + 1, who);
            k = end + 1;
        }
    }

    private void emit(RevSpec f, RevSpec t, int count, Authors.Who who) throws Exception {
        var cs = filter(DocDiff.of(blocks(f), blocks(t)));
        if (cs.isEmpty()) { quiet++; return; }
        if (json) { steps.add(step(f, t, cs, who, count, null)); return; }
        Out.data("── " + f.label() + " → " + t.label() + "  (" + count + " revision" + (count == 1 ? "" : "s") + ")  by " + who.show());
        Out.data("   " + t.when() + "  · " + DiffOut.stat(cs));
        if (!stat) { DiffOut.text(cs, "   "); Out.data(""); }
    }

    private Map<String, Object> step(RevSpec f, RevSpec t, List<Change> cs, Authors.Who who, int count, List<String> within) {
        var m = new LinkedHashMap<String, Object>();
        m.put("from", DiffOut.rev(f));
        m.put("to", DiffOut.rev(t));
        if (who != null) {
            m.put("revisions", count);
            m.put("by", who.name());
            m.put("confidence", who.confidence());
            m.put("attribution", who.source());
        }
        if (within != null) m.put("editedBy", within);
        m.put("summary", DiffOut.stat(cs));
        if (!stat) m.put("changes", DiffOut.json(cs));
        return m;
    }

    private List<Change> filter(List<Change> cs) {
        if (tabSel == null) return cs;
        var want = Arrays.stream(tabSel.split(",")).map(s -> s.strip().toLowerCase()).toList();
        return cs.stream().filter(c -> want.contains(c.at().tab().toLowerCase())).toList();
    }

    void finish(boolean single) {
        if (json) { Out.json(single ? steps.getFirst() : steps); return; }
        if (quiet > 0 && !Out.quiet) System.err.println(quiet + " step(s) with no net text change not shown (comments, suggestions, colour/size restyles and edits undone within the run do not change the export)");
    }
}
