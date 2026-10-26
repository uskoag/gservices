package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * list — turn a run of EXISTING paragraphs into one list (or take them out of one). The typed marker of each
 * ("(a) ", "1. ", "- ") is removed and its rank becomes the nesting level, so a document written with typed
 * sub-points is repaired in place. The whole run is re-listed together, because the API cannot add a paragraph
 * to a list that already exists: a parent that is already a list item is taken out and put back with its children.
 */
final class VerbList {

    private static final Pattern MARKER = Pattern.compile("^(\\(([A-Za-z]{1,6}|\\d{1,3})\\)|([A-Za-z]{1,6}|\\d{1,3})[.)]|[-*+•–])[ \\u00a0]+(?=\\S)");

    private VerbList() {}

    static void run(List<String> a) throws Exception {
        var apply = Args.flag(a, "--apply");
        var keep = Args.flag(a, "--keep-marker");
        var auto = Args.flag(a, "--auto-level");
        var none = Args.flag(a, "--none");
        var number = Args.flag(a, "--number");
        var bullet = Args.flag(a, "--bullet");
        var preset = Args.val(a, "--preset");
        var level = Args.intVal(a, "--level");
        var tabSel = Args.val(a, "--tab", "-t");
        var from = Args.val(a, "--from");
        var to = Args.val(a, "--to");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "list");
        if (from == null) Out.die("list needs --from \"<start of the first paragraph>\" (and --to for the last, when more than one)");
        if ((none ? 1 : 0) + (number ? 1 : 0) + (bullet ? 1 : 0) + (preset != null ? 1 : 0) != 1) Out.die("list needs exactly one of --bullet, --number, --preset <NAME>, --none");
        if (auto && level != null) Out.die("give --level or --auto-level, not both");
        var kind = number ? "number" : bullet ? "bullet" : null;
        ListPresets.check("list", kind, preset, level);

        var doc = DocRef.get(id);
        var all = Tabs.flat(doc);
        if (tabSel == null && all.size() > 1) Out.die("list: the document has " + all.size() + " tabs; name one with --tab");
        var tab = tabSel == null ? all.get(0) : Tabs.select(all, tabSel).get(0);
        var tabId = Tabs.id(tab);
        var scope = new Scope(tab, Para.of(tab.getDocumentTab()));
        var first = scope.anchor("--from", from);
        var last = to == null ? first : scope.anchor("--to", to);
        var paras = scope.paras().stream().filter(p -> p.start() >= first.start() && p.start() <= last.start()).toList();
        if (paras.isEmpty() || last.start() < first.start()) Out.die("list: --to must come after --from");
        for (var p : paras) if (p.plain().isBlank()) Out.die("list: the paragraph at @" + p.start() + " is empty; an empty paragraph would end the list — remove it or run list on each side of it");

        var chosen = none ? null : preset != null ? preset : ListPresets.choose(kind, null, null, tab.getDocumentTab());
        var ctx = new MdList();
        ctx.assumeOpen = true;
        var plan = new ArrayList<Item>();
        for (var p : paras) {
            var m = MARKER.matcher(p.plain());
            var marker = !none && !keep && m.find() && (m.group(2) == null && m.group(3) == null || MdList.token(m.group(2) != null ? m.group(2) : m.group(3))) ? m.end() : 0;
            var lvl = level != null ? level : Styles.level(p.p());
            if (auto && !none) { var it = ctx.item(p.plain()); if (it != null) lvl = it.level(); }
            plan.add(new Item(p, marker, none ? 0 : lvl, marker > 0 ? p.plain().substring(0, marker).strip() : null));
        }

        var reqs = new ArrayList<Request>();
        var from0 = first.start();
        var to0 = last.end();
        if (paras.stream().anyMatch(p -> p.p().getBullet() != null)) reqs.add(new Request().setDeleteParagraphBullets(new DeleteParagraphBulletsRequest().setRange(rng(tabId, from0, to0))));
        if (none) for (var p : paras) { if (p.p().getBullet() != null) reqs.add(ListRuns.resetIndent(tabId, p.start(), p.end())); }
        else reqs.add(ListRuns.resetIndent(tabId, from0, to0));
        var delta = 0;
        for (var i = plan.size() - 1; i >= 0; i--) {
            var it = plan.get(i);
            var at = it.p().start();
            if (it.marker() > 0) reqs.add(new Request().setDeleteContentRange(new DeleteContentRangeRequest().setRange(rng(tabId, at, at + it.marker()))));
            if (it.level() > 0) reqs.add(new Request().setInsertText(new InsertTextRequest().setText("\t".repeat(it.level())).setLocation(new Location().setIndex(at).setTabId(tabId))));
            delta += it.level() - it.marker();
        }
        if (!none) reqs.add(new Request().setCreateParagraphBullets(new CreateParagraphBulletsRequest().setRange(rng(tabId, from0, to0 + delta)).setBulletPreset(chosen)));

        var expects = new ArrayList<Expect.Bullet>();
        for (var it : plan) {
            var text = it.p().plain().substring(it.marker());
            Out.data("  " + (none ? "no list" : "L" + it.level() + (it.typed() == null ? "" : ", typed \"" + it.typed() + "\" removed")) + ": " + text);
            expects.add(new Expect.Bullet(tabId, text, !none, none ? null : it.level(), none ? null : ListPresets.numbered(chosen)));
        }
        if (!none && plan.stream().anyMatch(it -> it.typed() != null && it.level() == 0 && it.typed().matches("\\d+") && !it.typed().equals("1")))
            Out.data("  ⚠ a numbered list always starts at 1: typed numbers are dropped, so start the range at the paragraph that is meant to be number 1");
        if (!apply) { Out.data("DRY RUN — " + paras.size() + " paragraph(s) in tab " + Tabs.title(tab) + (none ? " out of their list" : " into one " + chosen + " list") + ", " + reqs.size() + " request(s). Re-run with --apply."); return; }
        var batch = new BatchUpdateDocumentRequest().setRequests(reqs).setWriteControl(new WriteControl().setRequiredRevisionId(doc.getRevisionId()));
        Journal.around(id, "list", paras.size() + " paragraph(s) in tab " + Tabs.title(tab) + (none ? " out of a list" : " into a " + chosen + " list"), () -> Auth.docs().documents().batchUpdate(id, batch).execute());
        Out.success(paras.size() + " paragraph(s) " + (none ? "taken out of their list" : "made one list"));
        var diffs = Expect.verifyBullets(expects, DocRef.get(id));
        if (diffs.isEmpty()) { Out.data("VERIFIED: all " + expects.size() + " paragraph(s) read back with the planned list membership, nesting level and kind"); return; }
        Out.data("VERIFY FAILED: " + diffs.size() + " of " + expects.size() + " check(s) differ from the plan:");
        diffs.forEach(d -> Out.data("  " + d));
        System.exit(2);
    }

    record Item(Para p, int marker, int level, String typed) {}

    private static Range rng(String tabId, int from, int to) { return new Range().setTabId(tabId).setStartIndex(from).setEndIndex(to); }
}
