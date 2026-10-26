package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * toc — list the tables of contents of a tab, or delete one. Google's native TOC copies full heading text and its
 * levels cannot be set through the API, so a shorter overview is ordinary linked paragraphs and the native one goes.
 * Deleting is one deleteContentRange over the element's range, read back to confirm it is gone. Dry run unless --apply.
 */
final class VerbToc {

    private VerbToc() {}

    record Toc(int start, int end, List<Para> entries) {}

    static void run(List<String> a) throws Exception {
        var delete = !a.isEmpty() && a.get(0).equalsIgnoreCase("delete");
        if (delete || !a.isEmpty() && a.get(0).equalsIgnoreCase("list")) a.remove(0);
        var apply = Args.flag(a, "--apply");
        var showRequests = Args.flag(a, "--requests");
        var tabSel = Args.val(a, "--tab", "-t");
        var index = Args.intVal(a, "--index");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "toc");

        var doc = DocRef.get(id);
        var all = Tabs.flat(doc);
        if (tabSel == null && all.size() > 1) Out.die("the document has " + all.size() + " tabs; name one with --tab");
        var tabs = tabSel == null ? all : Tabs.select(all, tabSel);
        if (tabs.size() != 1) Out.die("--tab must name exactly one tab");
        var tab = tabs.get(0);
        var tocs = of(tab);
        Out.data(tocs.size() + " table(s) of contents in " + Tabs.title(tab) + " (" + Tabs.id(tab) + ")");
        for (var i = 0; i < tocs.size(); i++) {
            var t = tocs.get(i);
            Out.data("#" + (i + 1) + " @" + t.start() + "-" + t.end() + " " + t.entries().size() + " entries");
            t.entries().stream().limit(5).forEach(p -> Out.data("    \"" + p.preview() + "\""));
        }
        if (!delete) return;
        if (tocs.isEmpty()) Out.die("no table of contents in this tab");
        if (tocs.size() > 1 && index == null) Out.die("the tab has " + tocs.size() + " tables of contents; pick one with --index N (1-based)");
        var n = index == null ? 1 : index;
        if (n < 1 || n > tocs.size()) Out.die("--index " + n + " is out of range 1-" + tocs.size());
        var t = tocs.get(n - 1);
        var req = new Request().setDeleteContentRange(new DeleteContentRangeRequest()
                .setRange(new Range().setStartIndex(t.start()).setEndIndex(t.end()).setTabId(Tabs.id(tab))));
        var batch = new BatchUpdateDocumentRequest().setRequests(List.of(req)).setWriteControl(new WriteControl().setRequiredRevisionId(doc.getRevisionId()));
        if (showRequests) Out.data(com.google.api.client.json.gson.GsonFactory.getDefaultInstance().toPrettyString(batch));
        Out.data("delete table of contents #" + n + " @" + t.start() + "-" + t.end());
        if (!apply) { Out.data("DRY RUN — nothing sent; re-run with --apply."); return; }
        Journal.around(id, "toc", "delete table of contents #" + n + " in " + Tabs.title(tab), () -> Auth.docs().documents().batchUpdate(id, batch).execute());
        Out.success("table of contents #" + n + " deleted");
        var left = of(Tabs.flat(DocRef.get(id)).stream().filter(x -> Tabs.id(x).equals(Tabs.id(tab))).findFirst().get()).size();
        if (left != tocs.size() - 1) { Out.data("VERIFY FAILED: " + left + " table(s) of contents remain, expected " + (tocs.size() - 1)); System.exit(2); }
        Out.data("VERIFIED: " + left + " table(s) of contents remain");
    }

    static List<Toc> of(Tab tab) {
        var out = new ArrayList<Toc>();
        var body = tab.getDocumentTab() == null || tab.getDocumentTab().getBody() == null ? null : tab.getDocumentTab().getBody().getContent();
        if (body != null) for (var e : body) if (e.getTableOfContents() != null)
            out.add(new Toc(Para.idx(e.getStartIndex()), Para.idx(e.getEndIndex()), Para.of(e.getTableOfContents().getContent())));
        return out;
    }
}
