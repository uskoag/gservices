package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.Request;
import java.util.*;

/**
 * Sends accept / reject / delete for chosen suggestion threads in ONE batch, then reads the document
 * back and checks every id is gone: no open thread, and no mark left on any run or paragraph of any tab.
 * The Docs API (Developer Preview) has native acceptSuggestion / rejectSuggestion / deleteSuggestion,
 * measured live 2026-10-16 (PRP 25), so nothing is emulated here.
 */
final class SuggestionAct {

    private SuggestionAct() {}

    static String past(String op) { return op.equals("accept") ? "accepted" : op.equals("reject") ? "rejected" : "deleted"; }

    static String line(String op, DocThread t) {
        return op + " " + t.id() + "  by " + (t.head() == null ? "?" : t.head().author()) + "  " + VerbComments.where(t)
                + (t.summary() == null || t.summary().isBlank() ? "" : "  — " + t.summary().replace("\n", " ⏎ "));
    }

    /** Returns the ids still present after the batch (empty means verified). */
    static List<String> send(String docId, String op, List<DocThread> chosen) throws Exception {
        var reqs = new ArrayList<Request>();
        for (var t : chosen) reqs.add(Preview.suggestion(op, t.id()));
        Journal.around(docId, op, op + " " + chosen.stream().map(t -> t.id() + " (suggested by " + (t.head() == null ? "?" : t.head().author()) + ")").toList(),
                () -> Preview.send(docId, reqs, null));
        Out.success(reqs.size() + " suggestion(s) " + past(op));
        return verify(docId, chosen.stream().map(DocThread::id).toList());
    }

    /** A plan's suggestion ops, one batch per kind, before its edits; false when the read-back found one still there. */
    static boolean plan(String docId, List<PlanFile.SuggestionOp> ops, boolean apply) throws Exception {
        var open = DocThread.all(Preview.get(docId)).stream().filter(t -> !t.isComment() && "OPEN".equals(t.status())).toList();
        var byKind = new LinkedHashMap<String, List<DocThread>>();
        for (var o : ops) {
            var t = DocThread.find(open, o.id());
            if (t == null) Out.die(o.label() + ": no open suggestion " + o.id() + " (run `suggestions <doc>`)");
            if (o.kind().equals("delete") && !t.mine()) Out.die(o.label() + ": " + o.id() + " is not yours to delete; reject it instead");
            Out.data("[" + o.label() + "] " + line(o.kind(), t));
            byKind.computeIfAbsent(o.kind(), k -> new ArrayList<>()).add(t);
        }
        if (!apply) { Out.data("DRY RUN — " + ops.size() + " suggestion op(s), sent before the plan's edits; the edits below are resolved against the document as it stands now."); return true; }
        var ok = true;
        for (var e : byKind.entrySet()) ok &= send(docId, e.getKey(), e.getValue()).isEmpty();
        return ok;
    }

    static List<String> verify(String docId, List<String> ids) throws Exception {
        var doc = Preview.get(docId);
        var left = new LinkedHashSet<String>();
        for (var t : DocThread.all(doc)) if (!t.isComment() && "OPEN".equals(t.status()) && ids.contains(t.id())) left.add(t.id());
        for (var tab : Tabs.flat(doc)) for (var p : Para.of(tab.getDocumentTab()))
            for (var s : DocThread.suggestionIds(p)) if (ids.contains(s)) left.add(s);
        if (left.isEmpty()) Out.data("VERIFIED: " + ids.size() + " suggestion id(s) no longer appear anywhere in the document");
        else Out.data("VERIFY FAILED: still present after the batch: " + String.join(", ", left));
        return new ArrayList<>(left);
    }
}
