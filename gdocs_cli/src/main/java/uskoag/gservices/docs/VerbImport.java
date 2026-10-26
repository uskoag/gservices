package uskoag.gservices.docs;

import java.nio.file.*;
import java.util.List;

/**
 * import — replay a whole .docx (or another Google Doc) into a tab of a live Google Doc: every paragraph, table,
 * list, picture and style of the source, mechanically and in order. Dry run unless --apply.
 */
final class VerbImport {

    private VerbImport() {}

    static void run(List<String> a) throws Exception {
        var apply = Args.flag(a, "--apply");
        var atEnd = Args.flag(a, "--at-end");
        var tabSel = Args.val(a, "--tab", "-t");
        var from = Args.val(a, "--from");
        var after = Args.val(a, "--after");
        var dump = Args.val(a, "--dump");
        var fromTab = Args.val(a, "--from-tab");
        ImportSource.keep = Args.flag(a, "--keep-source");
        if (from == null) Out.die("import needs --from <file.docx | Google Doc id/url>");
        if (dump != null) {
            try (var src = ImportSource.open(from)) { Files.writeString(Path.of(dump), Out.GSON.toJson(src.doc)); }
            Out.success("converted structure written to " + dump);
            return;
        }
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "import");
        if (atEnd && after != null) Out.die("import: --at-end and --after are two answers to one question");
        var doc = DocRef.get(id);
        var all = Tabs.flat(doc);
        if (tabSel == null && all.size() > 1) Out.die("import: the document has " + all.size() + " tabs; name one with --tab");
        var tab = tabSel == null ? all.get(0) : Tabs.select(all, tabSel).get(0);
        var has = Para.of(tab.getDocumentTab()).stream().anyMatch(p -> !p.plain().isBlank());
        if (has && !atEnd && after == null) Out.die("import: tab " + Tabs.title(tab) + " already has text; say --at-end or --after \"<start of a paragraph>\"");
        if (after != null) new Scope(tab, Para.of(tab.getDocumentTab())).anchor("import --after", after);

        try (var src = ImportSource.open(from)) {
            if (fromTab != null) src.tab = Tabs.select(Tabs.flat(src.doc), fromTab).get(0);
            var srcTab = src.tab.getDocumentTab();
            var plan = new ImportPlan(srcTab);
            plan.borders(src.tableBorders());
            Out.data("import " + src.name + (fromTab == null ? "" : " tab " + Tabs.title(src.tab)) + " into tab " + Tabs.title(tab) + (after != null ? " after \"" + after + "\"" : has ? " at the end" : " (empty tab)"));
            plan.summary().forEach(Out::data);
            if (!apply) { Out.data("DRY RUN — nothing written to the document. Re-run with --apply."); return; }
                var diffs = Journal.around(id, "import", plan.all.size() + " paragraph(s), " + plan.tables.size() + " table(s) from " + src.name + " into tab " + Tabs.title(tab),
                        () -> ImportApply.run(id, doc, tab, after, plan, srcTab, src::pictureLinks));
                diffs.stream().filter(s -> s.startsWith("note: ")).forEach(s -> Out.data("  approximated: " + s.substring(6)));
                var bad = diffs.stream().filter(s -> !s.startsWith("note: ")).toList();
                plan.notes.forEach((k, v) -> Out.data("  not carried: " + k + (v > 1 ? " ×" + v : "")));
                if (bad.isEmpty()) Out.data("VERIFIED: " + plan.all.size() + " paragraph(s), " + plan.tables.size() + " table(s) and every styled run read back as written");
                else { Out.data("VERIFY FAILED:"); bad.forEach(s -> Out.data("  " + s)); System.exit(2); }
                Out.success("imported " + src.name + " into tab " + Tabs.title(tab));
        }
    }
}
