package uskoag.gservices.docs;

import java.util.*;

/**
 * diff <doc> — what changed between two versions, or (--steps) at every version in a range, so the way a
 * document evolved can be read edit by edit. Versions come from Drive's per-revision markdown export and
 * are cached, so a second walk over the same range makes no network call.
 */
public final class VerbDiff {

    private VerbDiff() {}

    static void run(List<String> a) throws Exception {
        var from = Args.val(a, "--from");
        var to = Args.val(a, "--to");
        var since = Args.val(a, "--since");
        var by = Args.val(a, "--by");
        var sel = Args.val(a, "--tab", "-t");
        var tabsArg = Args.val(a, "--tabs");
        var noTabs = Args.flag(a, "--no-tabs");
        var steps = Args.flag(a, "--steps");
        var each = Args.flag(a, "--each");
        if (Args.flag(a, "--no-activity")) Authors.useActivity = false;
        if (each) steps = true;
        var saved = Args.flag(a, "--saved");
        var every = Args.intVal(a, "--every");
        var json = Args.flag(a, "--json");
        var stat = Args.flag(a, "--stat");
        var ctx = Args.intVal(a, "--context");
        var fmt = Args.valOr(a, "--format", "md");
        RevExport.refresh = Args.flag(a, "--refresh");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "diff");
        if (ctx != null) DocDiff.context = ctx;

        var titles = noTabs ? List.<String>of() : tabsArg != null ? List.of(tabsArg.split("\\s*,\\s*")) : currentTabTitles(id);
        var range = RevRange.of(id, from, to, since, by, steps);
        var job = new DiffJob(id, fmt, titles, sel, json, stat);
        if (!steps) { job.pair(range.from(), range.to()); job.finish(true); return; }
        job.walk(RevRange.stepsBetween(id, range.from(), range.to(), saved, every == null ? 1 : every), by, each);
        job.finish(false);
    }

    private static List<String> currentTabTitles(String id) {
        try {
            var all = Tabs.flat(DocRef.get(id));
            return all.size() < 2 ? List.of() : all.stream().map(Tabs::title).toList();
        } catch (Exception e) {
            Out.info("tab titles unavailable (" + e.getMessage() + "); diff runs over the flattened export");
            return List.of();
        }
    }
}
