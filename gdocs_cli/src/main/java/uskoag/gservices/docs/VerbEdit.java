package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;

/**
 * replace / insert / apply — one engine. Every edit is resolved against a fresh read, all of them go
 * in ONE batchUpdate ordered highest index first per tab, and the batch carries the read's revision as
 * requiredRevisionId, so an edit made by anyone in between fails the whole batch instead of landing
 * in the wrong place. Dry run unless --apply.
 */
public final class VerbEdit {

    private VerbEdit() {}

    static void run(String verb, List<String> a) throws Exception {
        var apply = Args.flag(a, "--apply");
        var showRequests = Args.flag(a, "--requests");
        var id = DocRef.id(Args.req(a, "<doc>"));
        var edits = switch (verb) {
            case "replace" -> List.<Edit>of(new Edit.Replace("replace", Args.val(a, "--tab", "-t"), Args.val(a, "--in"),
                    Args.val(a, "--find"), Args.val(a, "--with"), Args.flag(a, "--all"), Args.val(a, "--note")));
            case "insert" -> List.<Edit>of(insertFromFlags(a));
            case "delete" -> List.<Edit>of(new Edit.Delete("delete", Args.val(a, "--tab", "-t"), Args.val(a, "--from"), Args.val(a, "--to"), Args.val(a, "--note")));
            default -> PlanFile.read(Optional.ofNullable(Args.val(a, "--plan")).orElseGet(() -> { Out.die("apply needs --plan <file.json>"); return null; }));
        };
        Args.noneLeft(a, verb);
        if (edits.isEmpty()) Out.die("no edits to apply");

        DocRef.suggestions = "inline";
        var doc = DocRef.get(id);
        var all = Tabs.flat(doc);
        var planned = new ArrayList<Planned>();
        for (var i = 0; i < edits.size(); i++) planned.add(resolve(i, edits.get(i), all, edits));
        planned.sort(Comparator.comparing(Planned::tabId).thenComparing(Comparator.comparingInt(Planned::at).reversed())
                .thenComparing(Comparator.comparingInt(Planned::order).reversed()));
        var reqs = planned.stream().flatMap(p -> p.requests().stream()).toList();

        planned.stream().sorted(Comparator.comparingInt(Planned::order)).forEach(p -> {
            Out.data("[" + edits.get(p.order()).label() + "] " + p.report().get(0));
            p.report().subList(1, p.report().size()).forEach(Out::data);
        });
        var batch = new BatchUpdateDocumentRequest().setRequests(reqs).setWriteControl(new WriteControl().setRequiredRevisionId(doc.getRevisionId()));
        if (showRequests) Out.data(com.google.api.client.json.gson.GsonFactory.getDefaultInstance().toPrettyString(batch));
        if (!apply) {
            Out.data("DRY RUN — " + edits.size() + " edit(s), " + reqs.size() + " request(s), against revision " + doc.getRevisionId()
                    + ". Nothing sent; re-run with --apply to commit.");
            return;
        }
        var res = Auth.docs().documents().batchUpdate(id, batch).execute();
        var rev = res.getWriteControl() == null ? "?" : res.getWriteControl().getRequiredRevisionId();
        Out.success(edits.size() + " edit(s) applied in one batch (" + reqs.size() + " requests); new revision " + rev);
        verify(id, planned);
    }

    /** Read the document again and compare every inserted span with what was planned, so a style Google dropped is reported rather than found later. */
    private static void verify(String id, List<Planned> planned) throws Exception {
        var expects = planned.stream().flatMap(p -> p.expects().stream()).toList();
        if (expects.isEmpty()) return;
        var diffs = Expect.verify(expects, DocRef.get(id));
        if (diffs.isEmpty()) { Out.data("VERIFIED: all " + expects.size() + " inserted span(s) read back with the planned bold/italic/size/colour"); return; }
        Out.data("VERIFY FAILED: " + diffs.size() + " of " + expects.size() + " inserted span(s) differ from the plan:");
        diffs.forEach(d -> Out.data("  " + d));
        System.exit(2);
    }

    private static Edit insertFromFlags(List<String> a) {
        var tab = Args.val(a, "--tab", "-t");
        var after = Args.val(a, "--after");
        var like = Args.val(a, "--like");
        var note = Args.val(a, "--note");
        var paras = new ArrayList<Edit.NewPara>();
        for (var t = Args.val(a, "--text"); t != null; t = Args.val(a, "--text")) paras.add(new Edit.NewPara(t, like));
        return new Edit.Insert("insert", tab, after, paras, note);
    }

    private static Planned resolve(int i, Edit e, List<Tab> all, List<Edit> edits) {
        if (e.tab() == null && all.size() > 1) Out.die(e.label() + ": the document has " + all.size() + " tabs; name one with tab/--tab");
        var tab = e.tab() == null ? all.get(0) : oneTab(all, e);
        var scope = new Scope(tab, Para.of(tab.getDocumentTab()));
        return switch (e) {
            case Edit.Replace r -> BuildReplace.build(i, r, scope, collides(r, e, edits, all));
            case Edit.Insert ins -> BuildInsert.build(i, ins, scope);
            case Edit.Delete d -> BuildDelete.build(i, d, scope);
        };
    }

    private static Tab oneTab(List<Tab> all, Edit e) {
        var hits = Tabs.select(all, e.tab());
        if (hits.size() != 1) Out.die(e.label() + ": tab must name exactly one tab");
        return hits.get(0);
    }

    /** replaceAllText would also hit text another edit in the same batch writes, so such a replace goes by range. */
    private static boolean collides(Edit.Replace r, Edit self, List<Edit> edits, List<Tab> all) {
        var find = Para.norm(r.find() == null ? "" : r.find());
        var tabId = r.tab() == null ? Tabs.id(all.get(0)) : Tabs.id(Tabs.select(all, r.tab()).get(0));
        for (var o : edits) {
            if (o == self || !(o.tab() == null ? Tabs.id(all.get(0)) : Tabs.id(Tabs.select(all, o.tab()).get(0))).equals(tabId)) continue;
            var written = switch (o) {
                case Edit.Replace x -> x.with() == null ? "" : x.with();
                case Edit.Insert x -> String.join("\n", x.paras().stream().map(p -> Segments.parse(p.text()).plain()).toList());
                case Edit.Delete x -> "";
            };
            if (Para.norm(written).contains(find)) return true;
        }
        return false;
    }
}
