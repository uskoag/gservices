package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;

/**
 * footnote add / edit / delete. `add` is the one verb in this tool that is NOT one atomic batch: the
 * Docs API only hands back a new footnote's id from a CreateFootnoteRequest's own response, so the
 * reference is created in one batchUpdate, then the body text is written into that id in a second one,
 * each pinned to the revision the previous call left the document at. `edit` and `delete` act on a
 * footnote that already exists, so both stay one atomic batch like every other edit verb here.
 */
final class VerbFootnote {

    private VerbFootnote() {}

    static void run(List<String> a) throws Exception {
        if (a.isEmpty()) Out.die("footnote needs a sub-verb: add, edit or delete");
        var sub = a.remove(0).toLowerCase();
        switch (sub) {
            case "add" -> add(a);
            case "edit" -> edit(a);
            case "delete" -> delete(a);
            default -> Out.die("unknown footnote sub-verb: " + sub + " (add, edit, delete)");
        }
    }

    private static void add(List<String> a) throws Exception {
        var apply = Args.flag(a, "--apply");
        var showRequests = Args.flag(a, "--requests");
        var tabSel = Args.val(a, "--tab", "-t");
        var find = Args.val(a, "--find");
        var in = Args.val(a, "--in");
        var text = Args.val(a, "--text");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "footnote add");
        if (find == null || find.isEmpty()) Out.die("footnote add needs --find \"<exact text>\" (the footnote goes right after it)");
        if (text == null || text.isEmpty()) Out.die("footnote add needs --text \"<md>\" (the new footnote's body)");

        var doc = DocRef.get(id);
        var all = Tabs.flat(doc);
        Links.tabs = all;
        var tab = oneTab(all, tabSel);
        var scope = new Scope(tab, Para.of(tab.getDocumentTab()));
        var within = in == null ? null : scope.anchor("footnote add --in", in);
        var hits = scope.occurrences(find, within);
        var where = within == null ? "tab " + scope.name() : "paragraph \"" + within.preview() + "\"";
        if (hits.isEmpty()) Out.die("footnote add: \"" + find + "\" does not occur in " + where);
        if (hits.size() > 1) Out.die("footnote add: \"" + find + "\" occurs " + hits.size() + " times in " + where + "; narrow it with --in");
        var at = hits.get(0);
        var insertAt = at + find.length();
        var segs = Segments.parse(text);
        var p = scope.paraAt(at);
        var off = at - p.start();

        Out.data("footnote add in " + scope.name() + ", right after: " + BuildReplace.around(p.plain(), off, off + find.length()));
        Out.data("  body: " + text);
        if (!apply) {
            Out.data("DRY RUN — against revision " + doc.getRevisionId() + ". Nothing sent; re-run with --apply to commit."
                    + " Two API calls are made when applied: create the footnote, then write its body — see the verb's own doc comment.");
            return;
        }

        var batch1 = new BatchUpdateDocumentRequest()
                .setRequests(List.of(new Request().setCreateFootnote(new CreateFootnoteRequest()
                        .setLocation(new Location().setTabId(scope.tabId()).setIndex(insertAt)))))
                .setWriteControl(new WriteControl().setRequiredRevisionId(doc.getRevisionId()));
        if (showRequests) Out.data(com.google.api.client.json.gson.GsonFactory.getDefaultInstance().toPrettyString(batch1));
        var res1 = Journal.around(id, "footnote add (create)", "create footnote after \"" + find + "\" in " + scope.name(),
                () -> Auth.docs().documents().batchUpdate(id, batch1).execute());
        var footnoteId = res1.getReplies().get(0).getCreateFootnote().getFootnoteId();
        Out.data("footnote created: id " + footnoteId + "; writing its body...");

        try {
            var doc2 = DocRef.get(id);
            var tab2 = Tabs.flat(doc2).stream().filter(t -> Tabs.id(t).equals(scope.tabId())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("tab " + scope.tabId() + " vanished between the two calls"));
            var fn2 = footnoteOf(tab2, footnoteId);
            var reqs2 = replaceBody(scope.tabId(), footnoteId, fn2.getContent(), segs);
            var batch2 = new BatchUpdateDocumentRequest().setRequests(reqs2)
                    .setWriteControl(new WriteControl().setRequiredRevisionId(doc2.getRevisionId()));
            if (showRequests) Out.data(com.google.api.client.json.gson.GsonFactory.getDefaultInstance().toPrettyString(batch2));
            Journal.around(id, "footnote add (text)", "footnote " + footnoteId + " body: " + text,
                    () -> Auth.docs().documents().batchUpdate(id, batch2).execute());
        } catch (Exception e) {
            Out.error("footnote " + footnoteId + " was created EMPTY; writing its body failed: " + e.getMessage()
                    + " — retry with: footnote edit " + id + " --tab " + scope.tabId() + " --footnote " + footnoteId + " --text \"...\" --apply");
            throw e;
        }
        Out.success("footnote added");
        verifyAdd(id, scope.tabId(), footnoteId, segs.plain());
    }

    private static void edit(List<String> a) throws Exception {
        var apply = Args.flag(a, "--apply");
        var showRequests = Args.flag(a, "--requests");
        var tabSel = Args.val(a, "--tab", "-t");
        var footnoteSel = Args.val(a, "--footnote");
        var text = Args.val(a, "--text");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "footnote edit");
        if (text == null || text.isEmpty()) Out.die("footnote edit needs --text \"<md>\" (replaces the whole footnote body)");

        var doc = DocRef.get(id);
        var all = Tabs.flat(doc);
        Links.tabs = all;
        var tab = oneTab(all, tabSel);
        var ref = Footnotes.select(tab, footnoteSel);
        var fn = footnoteOf(tab, ref.footnoteId());
        var content = fn.getContent();
        var oldPlain = String.join(" ", Para.of(content).stream().map(Para::plain).toList());
        var segs = Segments.parse(text);
        var reqs = replaceBody(Tabs.id(tab), ref.footnoteId(), content, segs);

        Out.data("footnote edit [^" + ref.number() + "] (" + ref.footnoteId() + ") in tab " + Tabs.title(tab) + ":");
        Out.data("  - " + oldPlain);
        Out.data("  + " + segs.plain());
        var batch = new BatchUpdateDocumentRequest().setRequests(reqs)
                .setWriteControl(new WriteControl().setRequiredRevisionId(doc.getRevisionId()));
        if (showRequests) Out.data(com.google.api.client.json.gson.GsonFactory.getDefaultInstance().toPrettyString(batch));
        if (!apply) {
            Out.data("DRY RUN — against revision " + doc.getRevisionId() + ". Nothing sent; re-run with --apply to commit.");
            return;
        }
        Journal.around(id, "footnote edit", "footnote " + ref.footnoteId() + " body: " + text,
                () -> Auth.docs().documents().batchUpdate(id, batch).execute());
        Out.success("footnote edited");
        verifyEdit(id, Tabs.id(tab), ref.footnoteId(), ref.number(), segs.plain());
    }

    private static void delete(List<String> a) throws Exception {
        var apply = Args.flag(a, "--apply");
        var showRequests = Args.flag(a, "--requests");
        var tabSel = Args.val(a, "--tab", "-t");
        var footnoteSel = Args.val(a, "--footnote");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "footnote delete");

        var doc = DocRef.get(id);
        var all = Tabs.flat(doc);
        Links.tabs = all;
        var tab = oneTab(all, tabSel);
        var ref = Footnotes.select(tab, footnoteSel);
        var fn = footnoteOf(tab, ref.footnoteId());
        var body = String.join(" ", Para.of(fn.getContent()).stream().map(Para::plain).toList());

        Out.data("delete footnote [^" + ref.number() + "] (" + ref.footnoteId() + ") in tab " + Tabs.title(tab)
                + ", reference @" + ref.start() + " — body currently reads: \"" + body + "\"");
        var reqs = List.of(new Request().setDeleteContentRange(new DeleteContentRangeRequest()
                .setRange(new Range().setStartIndex(ref.start()).setEndIndex(ref.end()).setTabId(Tabs.id(tab)))));
        var batch = new BatchUpdateDocumentRequest().setRequests(reqs)
                .setWriteControl(new WriteControl().setRequiredRevisionId(doc.getRevisionId()));
        if (showRequests) Out.data(com.google.api.client.json.gson.GsonFactory.getDefaultInstance().toPrettyString(batch));
        if (!apply) {
            Out.data("DRY RUN — against revision " + doc.getRevisionId() + ". Nothing sent; re-run with --apply to commit.");
            return;
        }
        Journal.around(id, "footnote delete", "delete footnote " + ref.footnoteId() + " (was: " + body + ")",
                () -> Auth.docs().documents().batchUpdate(id, batch).execute());
        Out.success("footnote deleted");
        verifyDelete(id, Tabs.id(tab), ref.footnoteId());
    }

    /**
     * Empty the footnote's own segment and write {@code segs} into it. A brand-new footnote is not
     * actually empty — Google seeds it with a placeholder character or two before its paragraph mark
     * (measured: a leading space) — so the same delete-then-insert-at-0 that replaces an EXISTING
     * footnote's whole body also correctly clears a just-created one; {@code add} and {@code edit}
     * share this rather than either one guessing how many characters are already there.
     */
    private static List<Request> replaceBody(String tabId, String footnoteId, List<StructuralElement> content, Segments segs) {
        var reqs = new ArrayList<Request>();
        var lastEnd = Para.idx(content.get(content.size() - 1).getEndIndex());
        if (lastEnd > 1) reqs.add(new Request().setDeleteContentRange(new DeleteContentRangeRequest()
                .setRange(new Range().setStartIndex(0).setEndIndex(lastEnd - 1).setTabId(tabId).setSegmentId(footnoteId))));
        reqs.add(new Request().setInsertText(new InsertTextRequest().setText(segs.plain())
                .setLocation(new Location().setTabId(tabId).setSegmentId(footnoteId).setIndex(0))));
        reqs.addAll(footnoteTextStyles(tabId, footnoteId, segs));
        return reqs;
    }

    /** Bold/italic/link runs only; a plain run needs no style request, the segment's default is right. */
    private static List<Request> footnoteTextStyles(String tabId, String footnoteId, Segments sg) {
        var out = new ArrayList<Request>();
        for (var r : sg.runs()) {
            var st = new TextStyle();
            if (sg.boldAt(r[0])) st.setBold(true);
            if (sg.italicAt(r[0])) st.setItalic(true);
            var link = sg.linkAt(r[0]);
            if (link != null) st.setLink(Links.resolve(link)).setUnderline(true).setForegroundColor(Links.BLUE);
            if (st.getBold() == null && st.getItalic() == null && st.getLink() == null) continue;
            out.add(new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setTextStyle(st).setFields("*")
                    .setRange(new Range().setStartIndex(r[0]).setEndIndex(r[1]).setTabId(tabId).setSegmentId(footnoteId))));
        }
        return out;
    }

    private static Footnote footnoteOf(Tab tab, String footnoteId) {
        var map = tab.getDocumentTab().getFootnotes();
        var fn = map == null ? null : map.get(footnoteId);
        if (fn == null || fn.getContent() == null || fn.getContent().isEmpty())
            Out.die("footnote " + footnoteId + " has no body content in tab " + Tabs.title(tab) + " (unexpected — please report)");
        return fn;
    }

    private static Tab oneTab(List<Tab> all, String tabSel) {
        if (tabSel == null && all.size() > 1) Out.die("the document has " + all.size() + " tabs; name one with --tab");
        var hits = tabSel == null ? List.of(all.get(0)) : Tabs.select(all, tabSel);
        if (hits.size() != 1) Out.die("--tab must name exactly one tab");
        return hits.get(0);
    }

    private static void verifyAdd(String id, String tabId, String footnoteId, String wantPlain) throws Exception {
        var all = Tabs.flat(DocRef.get(id));
        var tab = all.stream().filter(t -> Tabs.id(t).equals(tabId)).findFirst().orElse(null);
        var ref = tab == null ? null : Footnotes.refs(tab).stream().filter(r -> r.footnoteId().equals(footnoteId)).findFirst().orElse(null);
        var fn = tab == null ? null : footnoteOrNull(tab, footnoteId);
        var got = fn == null ? null : String.join(" ", Para.of(fn.getContent()).stream().map(Para::plain).toList());
        if (ref != null && Para.norm(wantPlain).equals(got))
            Out.data("VERIFIED: footnote [^" + ref.number() + "] (" + footnoteId + ") reads back \"" + got + "\"");
        else {
            Out.data("VERIFY FAILED: footnote " + footnoteId + " — expected \"" + wantPlain + "\", found " + (got == null ? "(no body)" : "\"" + got + "\""));
            System.exit(2);
        }
    }

    private static void verifyEdit(String id, String tabId, String footnoteId, int number, String wantPlain) throws Exception {
        var all = Tabs.flat(DocRef.get(id));
        var tab = all.stream().filter(t -> Tabs.id(t).equals(tabId)).findFirst().orElse(null);
        var fn = tab == null ? null : footnoteOrNull(tab, footnoteId);
        var got = fn == null ? null : String.join(" ", Para.of(fn.getContent()).stream().map(Para::plain).toList());
        if (Para.norm(wantPlain).equals(got))
            Out.data("VERIFIED: footnote [^" + number + "] (" + footnoteId + ") reads back \"" + got + "\"");
        else {
            Out.data("VERIFY FAILED: footnote " + footnoteId + " — expected \"" + wantPlain + "\", found " + (got == null ? "(no body)" : "\"" + got + "\""));
            System.exit(2);
        }
    }

    private static void verifyDelete(String id, String tabId, String footnoteId) throws Exception {
        var all = Tabs.flat(DocRef.get(id));
        var tab = all.stream().filter(t -> Tabs.id(t).equals(tabId)).findFirst().orElse(null);
        var stillThere = tab != null && Footnotes.refs(tab).stream().anyMatch(r -> r.footnoteId().equals(footnoteId));
        if (!stillThere) Out.data("VERIFIED: footnote " + footnoteId + " is gone");
        else { Out.data("VERIFY FAILED: footnote " + footnoteId + " is still present after apply"); System.exit(2); }
    }

    private static Footnote footnoteOrNull(Tab tab, String footnoteId) {
        var map = tab.getDocumentTab().getFootnotes();
        return map == null ? null : map.get(footnoteId);
    }
}
