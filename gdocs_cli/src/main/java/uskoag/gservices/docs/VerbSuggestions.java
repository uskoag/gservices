package uskoag.gservices.docs;

import java.util.*;

/**
 * accept / reject / delete-suggestion (also spelled `suggestion accept|reject|delete`), by id or in bulk
 * (--mine, --all, narrowed by --tab and --by), dry run unless --apply, one batch, verified read-back.
 */
final class VerbSuggestions {

    private VerbSuggestions() {}

    static void sub(List<String> a) throws Exception {
        if (a.isEmpty()) Out.die("suggestion: accept, reject or delete");
        var op = a.remove(0).toLowerCase();
        switch (op) {
            case "accept", "reject" -> run(op, a);
            case "delete" -> run("delete-suggestion", a);
            default -> Out.die("suggestion: accept, reject or delete, not '" + op + "'");
        }
    }

    static void run(String verb, List<String> a) throws Exception {
        var apply = Args.flag(a, "--apply");
        var mine = Args.flag(a, "--mine");
        var all = Args.flag(a, "--all");
        var tab = Args.val(a, "--tab", "-t");
        var by = Args.val(a, "--by");
        var id = DocRef.id(Args.req(a, "<doc>"));
        var ids = new ArrayList<String>();
        for (var s = Args.pos(a); s != null; s = Args.pos(a)) ids.add(s);
        Args.noneLeft(a, verb);
        if (ids.isEmpty() && !mine && !all) Out.die(verb + ": name suggestion id(s), or --mine, or --all");

        var doc = Preview.get(id);
        var tabIds = tab == null ? null : Tabs.select(Tabs.flat(doc), tab).stream().map(Tabs::id).toList();
        var threads = DocThread.all(doc).stream().filter(t -> !t.isComment() && "OPEN".equals(t.status())).toList();
        var chosen = new ArrayList<DocThread>();
        for (var s : ids) {
            var t = DocThread.find(threads, s);
            if (t == null) Out.die(verb + ": no open suggestion " + s + " (run `suggestions <doc>`)");
            chosen.add(t);
        }
        if (ids.isEmpty()) for (var t : threads) if (all || t.mine()) chosen.add(t);
        chosen.removeIf(t -> (tabIds != null && !tabIds.contains(t.tabId())) || (by != null && !byMatches(t, by)));
        if (chosen.isEmpty()) { Out.data("no open suggestions match"); return; }
        if (verb.equals("delete-suggestion")) for (var t : chosen)
            if (!t.mine()) Out.die("delete-suggestion: " + t.id() + " was suggested by " + t.head().author() + "; only its author can delete it (reject it instead)");

        var op = switch (verb) { case "accept" -> "accept"; case "reject" -> "reject"; default -> "delete"; };
        chosen.forEach(t -> Out.data(SuggestionAct.line(op, t)));
        if (!apply) { Out.data("DRY RUN — " + chosen.size() + " suggestion(s). Nothing sent; re-run with --apply to commit."); return; }
        if (!SuggestionAct.send(id, op, chosen).isEmpty()) System.exit(2);
    }

    private static boolean byMatches(DocThread t, String by) {
        return t.head() != null && t.head().author() != null && t.head().author().toLowerCase().contains(by.toLowerCase());
    }
}
