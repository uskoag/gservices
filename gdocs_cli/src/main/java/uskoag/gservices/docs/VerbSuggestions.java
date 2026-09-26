package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.Request;
import java.util.*;

/** accept / reject / delete-suggestion, by id or in bulk (--mine, --all), dry run unless --apply, one batch. */
final class VerbSuggestions {

    private VerbSuggestions() {}

    static void run(String verb, List<String> a) throws Exception {
        var apply = Args.flag(a, "--apply");
        var mine = Args.flag(a, "--mine");
        var all = Args.flag(a, "--all");
        var id = DocRef.id(Args.req(a, "<doc>"));
        var ids = new ArrayList<String>();
        for (var s = Args.pos(a); s != null; s = Args.pos(a)) ids.add(s);
        Args.noneLeft(a, verb);
        if (ids.isEmpty() && !mine && !all) Out.die(verb + ": name suggestion id(s), or --mine, or --all");

        var threads = DocThread.all(Preview.get(id)).stream().filter(t -> !t.isComment() && "OPEN".equals(t.status())).toList();
        var chosen = new ArrayList<DocThread>();
        for (var s : ids) {
            var t = DocThread.find(threads, s);
            if (t == null) Out.die(verb + ": no open suggestion " + s + " (run `suggestions <doc>`)");
            chosen.add(t);
        }
        if (ids.isEmpty()) for (var t : threads) if (all || t.mine()) chosen.add(t);
        if (chosen.isEmpty()) { Out.data("no open suggestions match"); return; }
        if (verb.equals("delete-suggestion")) for (var t : chosen)
            if (!t.mine()) Out.die("delete-suggestion: " + t.id() + " was suggested by " + t.head().author() + "; only its author can delete it (reject it instead)");

        var op = switch (verb) { case "accept" -> "accept"; case "reject" -> "reject"; default -> "delete"; };
        var reqs = new ArrayList<Request>();
        for (var t : chosen) {
            reqs.add(Preview.suggestion(op, t.id()));
            Out.data(op + " " + t.id() + "  by " + (t.head() == null ? "?" : t.head().author()) + "  " + VerbComments.where(t)
                    + (t.summary() == null || t.summary().isBlank() ? "" : "  — " + t.summary().replace("\n", " ⏎ ")));
        }
        if (!apply) { Out.data("DRY RUN — " + reqs.size() + " suggestion(s). Nothing sent; re-run with --apply to commit."); return; }
        Journal.around(id, op, op + " " + chosen.stream().map(t -> t.id() + " (suggested by " + (t.head() == null ? "?" : t.head().author()) + ")").toList(), () -> Preview.send(id, reqs, null));
        Out.success(reqs.size() + " suggestion(s) " + (op.equals("accept") ? "accepted" : op.equals("reject") ? "rejected" : "deleted"));
    }
}
