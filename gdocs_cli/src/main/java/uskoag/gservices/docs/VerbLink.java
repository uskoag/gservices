package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;

/**
 * link — put a hyperlink on text already in a tab, or take it off. The text is found the way `replace` finds
 * it: exactly once in the tab or in the --in paragraph, or every occurrence with --all. Only the link
 * field is written, so bold, colour and the rest stay as they are. Dry run unless --apply, then read back.
 */
final class VerbLink {

    private VerbLink() {}

    static void run(List<String> a) throws Exception {
        var apply = Args.flag(a, "--apply");
        var showRequests = Args.flag(a, "--requests");
        var every = Args.flag(a, "--all");
        var none = Args.flag(a, "--none");
        var tabSel = Args.val(a, "--tab", "-t");
        var find = Args.val(a, "--find");
        var in = Args.val(a, "--in");
        var url = Args.val(a, "--url");
        var toTab = Args.val(a, "--to-tab");
        var toHeading = Args.val(a, "--to-heading");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "link");
        if ((url != null ? 1 : 0) + (toTab != null ? 1 : 0) + (toHeading != null ? 1 : 0) + (none ? 1 : 0) != 1) Out.die("link needs exactly one of --url <url>, --to-tab <sel>, --to-heading <heading start> or --none");
        if (find == null || find.isEmpty()) Out.die("link needs --find \"<exact text>\"");
        if (tabSel == null) Out.die("link needs --tab <sel>: the tab holding the text");

        var doc = DocRef.get(id);
        Links.tabs = Tabs.flat(doc);
        var s = scope(tabSel);
        var within = in == null ? null : s.anchor("link --in", in);
        var hits = s.occurrences(find, within);
        var where = within == null ? "tab " + s.name() : "paragraph \"" + within.preview() + "\"";
        if (hits.isEmpty()) Out.die("\"" + find + "\" does not occur in " + where);
        if (hits.size() > 1 && !every) Out.die("\"" + find + "\" occurs " + hits.size() + " times in " + where + "; narrow it with --in, or pass --all");
        var link = none ? null : url != null ? Links.resolve(url) : toHeading != null ? Links.resolve("heading:" + toHeading) : Links.resolve("tab:" + toTab);

        var reqs = new ArrayList<Request>();
        for (var at : hits) {
            var p = s.paraAt(at);
            var off = at - p.start();
            Out.data((none ? "unlink " : "link ") + BuildReplace.around(p.plain(), off, off + find.length()) + (none ? "" : "  → " + Links.describe(link)));
            reqs.add(new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setTextStyle(new TextStyle().setLink(link)).setFields("link")
                    .setRange(new Range().setStartIndex(at).setEndIndex(at + find.length()).setTabId(s.tabId()))));
        }
        var batch = new BatchUpdateDocumentRequest().setRequests(reqs).setWriteControl(new WriteControl().setRequiredRevisionId(doc.getRevisionId()));
        if (showRequests) Out.data(com.google.api.client.json.gson.GsonFactory.getDefaultInstance().toPrettyString(batch));
        if (!apply) {
            Out.data("DRY RUN — " + hits.size() + " span(s) in " + s.name() + " against revision " + doc.getRevisionId() + ". Nothing sent; re-run with --apply to commit.");
            return;
        }
        var what = (none ? "unlink \"" : "link \"") + find + "\" in " + s.name() + (none ? "" : " → " + Links.describe(link));
        Journal.around(id, "link", what, () -> Auth.docs().documents().batchUpdate(id, batch).execute());
        Out.success(hits.size() + " span(s) " + (none ? "unlinked" : "linked"));
        verify(id, s.tabId(), hits, find.length(), link);
    }

    private static Scope scope(String tabSel) {
        var tabs = Tabs.select(Links.tabs, tabSel);
        if (tabs.size() != 1) Out.die("--tab must name exactly one tab");
        return new Scope(tabs.get(0), Para.of(tabs.get(0).getDocumentTab()));
    }

    /** Every character of every span must now carry the asked-for link, and nothing else. */
    private static void verify(String id, String tabId, List<Integer> hits, int len, Link want) throws Exception {
        Links.tabs = Tabs.flat(DocRef.get(id));
        var s = scope(tabId);
        var expected = Styles.link(new TextStyle().setLink(want));
        var failed = 0;
        for (var at : hits) {
            var got = new LinkedHashSet<String>();
            var p = s.paraAt(at);
            for (var i = at; i < at + len; i++) {
                var run = p == null ? null : p.runAt(i);
                got.add(String.valueOf(run == null ? null : Styles.link(run.getTextStyle())));
            }
            var ok = got.size() == 1 && got.contains(String.valueOf(expected));
            if (!ok) failed++;
            Out.data((ok ? "VERIFIED\t@" : "VERIFY FAILED\t@") + at + "\t" + (ok ? String.valueOf(expected) : "expected " + expected + ", found " + got));
        }
        if (failed > 0) System.exit(2);
    }
}
