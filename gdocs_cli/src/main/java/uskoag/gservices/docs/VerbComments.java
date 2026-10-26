package uskoag.gservices.docs;

import uskoag.gservices.comments.Post;
import uskoag.gservices.comments.Identity;
import uskoag.gservices.comments.AiSign;
import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;

/**
 * comments / suggestions (listing) and comment add|reply|resolve|reopen|edit|delete|unsign.
 * Everything the tool writes is signed "AI(<name>): " (AiSign). Writes are dry runs unless --apply.
 * A resolve or an assignment that carries text goes as TWO posts, the text first and then a bare action
 * post: Google never lets a post with an action be deleted, so only an empty post is ever stuck.
 */
final class VerbComments {

    private VerbComments() {}

    static void list(String verb, List<String> a) throws Exception {
        var json = Args.flag(a, "--json");
        var awaiting = Args.flag(a, "--awaiting");
        var mine = Args.flag(a, "--mine");
        var status = Args.valOr(a, "--status", awaiting ? "open" : "open").toLowerCase();
        var tabSel = Args.val(a, "--tab", "-t");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, verb);
        var doc = Preview.get(id);
        var tabIds = tabSel == null ? null : Tabs.select(Tabs.flat(doc), tabSel).stream().map(Tabs::id).toList();
        var kind = verb.equals("suggestions") ? "suggestion" : "comment";
        var hits = DocThread.all(doc).stream()
                .filter(t -> t.kind().equals(kind))
                .filter(t -> status.equals("all") || status.equalsIgnoreCase(t.status()))
                .filter(t -> tabIds == null || tabIds.contains(t.tabId()))
                .filter(t -> !awaiting || t.awaiting())
                .filter(t -> !mine || t.mine())
                .toList();
        if (json) { Out.json(hits.stream().map(VerbComments::asMap).toList()); return; }
        if (hits.isEmpty()) {
            var none = Preview.comments(doc).isEmpty() && Preview.suggestions(doc).isEmpty();
            Out.data("no " + kind + " threads match" + (none ? " (the document returned no comment or suggestion threads at all)" : ""));
            return;
        }
        for (var t : hits) print(t);
        Out.data(hits.size() + " " + kind + " thread(s)" + (awaiting ? " awaiting an answer" : "") + ", revision " + doc.getRevisionId());
    }

    static void run(List<String> a) throws Exception {
        if (a.isEmpty()) Out.die("comment needs a sub-verb: add | reply | resolve | reopen | edit | delete | unsign");
        var sub = a.remove(0).toLowerCase();
        var apply = Args.flag(a, "--apply");
        var showRequests = Args.flag(a, "--requests");
        var id = DocRef.id(Args.req(a, "<doc>"));
        var doc = Preview.get(id);
        var threads = DocThread.all(doc);
        var reqs = new ArrayList<Request>();
        var report = new ArrayList<String>();
        WriteControl wc = null;
        switch (sub) {
            case "add" -> {
                var tabSel = Args.val(a, "--tab", "-t");
                var paraText = Args.val(a, "--para", "--in");
                var find = Args.val(a, "--find");
                var text = Args.text(a, "--text", "--text-file");
                var assign = Args.val(a, "--assign");
                Args.noneLeft(a, "comment add");
                if (text == null || text.isBlank()) Out.die("comment add: --text is required");
                if (paraText == null) Out.die("comment add: --para \"<paragraph start>\" is required (the comment is anchored there)");
                var all = Tabs.flat(doc);
                if (tabSel == null && all.size() > 1) Out.die("comment add: the document has " + all.size() + " tabs; name one with --tab");
                var tab = tabSel == null ? all.get(0) : one(all, tabSel);
                var scope = new Scope(tab, Para.of(tab.getDocumentTab()));
                var p = scope.anchor("comment add --para", paraText);
                int start, end;
                if (find == null) { start = p.start(); end = p.end() - 1; }
                else {
                    var hits = scope.occurrences(find, p);
                    if (hits.size() != 1) Out.die("comment add: \"" + find + "\" occurs " + hits.size() + " times in that paragraph; it must occur once");
                    start = hits.get(0);
                    end = start + Para.norm(find).length();
                }
                var content = AiSign.sign(text.strip());
                reqs.add(Preview.insertComment(Tabs.id(tab), start, end, content, assign));
                report.add("add comment in " + scope.name() + " on \"" + p.preview() + "\"" + (find == null ? " (whole paragraph)" : " on \"" + find + "\"")
                        + (assign == null ? "" : ", assigned to " + assign));
                report.add("  text: " + content);
                wc = new WriteControl().setRequiredRevisionId(doc.getRevisionId());
            }
            case "reply", "resolve", "reopen" -> {
                var t = thread(threads, Args.req(a, "<threadId>"));
                var text = Args.text(a, "--text", "--text-file");
                var assign = Args.val(a, "--assign");
                var resolve = sub.equals("resolve") || Args.flag(a, "--resolve");
                var reopen = sub.equals("reopen");
                Args.noneLeft(a, "comment " + sub);
                if (sub.equals("reply") && (text == null || text.isBlank())) Out.die("comment reply: --text is required");
                if ((resolve || reopen || assign != null) && !t.isComment()) Out.die("comment " + sub + ": " + t.id() + " is a suggestion thread; resolve/reopen/assign apply to comment threads (accept or reject a suggestion instead)");
                report.add(sub + " on " + t.kind() + " " + t.id() + " (" + where(t) + ")");
                if (text != null && !text.isBlank()) {
                    var content = AiSign.sign(text.strip());
                    reqs.add(Preview.reply(t, content, null, null));
                    report.add("  reply: " + content);
                }
                if (assign != null) { reqs.add(Preview.reply(t, null, null, assign)); report.add("  then a bare post assigning it to " + assign); }
                if (resolve) { reqs.add(Preview.reply(t, null, "RESOLVE", null)); report.add("  then a bare RESOLVE post (no text, so nothing undeletable carries words)"); }
                if (reopen) { reqs.add(Preview.reply(t, null, "REOPEN", null)); report.add("  then a bare REOPEN post"); }
            }
            case "edit" -> {
                var postId = Args.req(a, "<postId>");
                var text = Args.text(a, "--text", "--text-file");
                Args.noneLeft(a, "comment edit");
                if (text == null || text.isBlank()) Out.die("comment edit: --text is required");
                var hit = postIn(threads, postId);
                refuseUneditable(hit.thread(), hit.post());
                var content = AiSign.sign(text.strip());
                reqs.add(Preview.updatePost(hit.thread(), postId, content));
                report.add("edit post " + postId + " in " + hit.thread().kind() + " " + hit.thread().id() + " (" + where(hit.thread()) + ")");
                report.add("  was: " + hit.post().content());
                report.add("  now: " + content);
            }
            case "delete" -> {
                var target = Args.req(a, "<threadId|postId>");
                Args.noneLeft(a, "comment delete");
                var t = DocThread.find(threads, target);
                if (t != null) {
                    if (!t.isComment()) Out.die("comment delete: " + target + " is a suggestion thread; use `reject` (or delete-suggestion as its author)");
                    if (!t.mine()) Out.die("comment delete: thread " + target + " was started by " + t.head().author() + "; Google lets only its author delete it");
                    reqs.add(Preview.deleteComment(target));
                    report.add("delete comment thread " + target + " (" + where(t) + ") with all " + t.posts().size() + " post(s)");
                    t.posts().forEach(p -> report.add("  " + p.author() + ": " + Objects.requireNonNullElse(p.content(), "(" + p.action() + ")")));
                } else {
                    var hit = postIn(threads, target);
                    if (hit.post().head()) Out.die("comment delete: " + target + " is the first post of thread " + hit.thread().id() + "; delete the thread instead");
                    if (!hit.post().me()) Out.die("comment delete: post " + target + " was written by " + hit.post().author() + "; Google lets only its author delete it");
                    if (hit.post().action() != null) Out.die("comment delete: post " + target + " carries a " + hit.post().action() + " action: Google never deletes such posts, and refuses to edit one that has no text (measured 2026-09-25)");
                    reqs.add(Preview.deleteReply(hit.thread(), target));
                    report.add("delete reply " + target + " in " + hit.thread().kind() + " " + hit.thread().id() + ": " + hit.post().content());
                }
                report.add("  NOTE: a deleted comment is NOT in the document's version history");
            }
            case "unsign" -> {
                var allMine = Args.flag(a, "--all-mine");
                var postId = allMine ? null : Args.req(a, "<postId> (or --all-mine)");
                Args.noneLeft(a, "comment unsign");
                for (var t : threads) for (var p : t.posts()) {
                    if (postId != null ? !p.postId().equals(postId) : !(p.me() && p.signed() && !p.deleted())) continue;
                    if (!p.signed()) Out.die("comment unsign: post " + p.postId() + " carries no AI signature");
                    if (!p.head() || t.isComment()) {
                        reqs.add(Preview.updatePost(t, p.postId(), AiSign.unsign(p.content())));
                        report.add("unsign " + p.postId() + " (" + t.kind() + " " + t.id() + "): " + AiSign.unsign(p.content()));
                    } else report.add("SKIP " + p.postId() + ": the first post of a suggestion thread cannot be edited");
                }
                if (postId != null && reqs.isEmpty() && report.isEmpty()) Out.die("comment unsign: no post " + postId);
                if (reqs.isEmpty()) { report.forEach(Out::data); Out.data("nothing to unsign"); return; }
            }
            default -> Out.die("unknown comment sub-verb: " + sub + " (add | reply | resolve | reopen | edit | delete | unsign)");
        }
        report.forEach(Out::data);
        if (showRequests) Out.data(com.google.api.client.json.gson.GsonFactory.getDefaultInstance().toPrettyString(reqs));
        if (!apply) { Out.data("DRY RUN — " + reqs.size() + " request(s). Nothing sent; re-run with --apply to commit."); return; }
        var res = Preview.send(id, reqs, wc);
        var made = new ArrayList<String>();
        for (var r : Preview.list(res.get("replies"))) {
            var ic = Preview.map(r.get("insertComment"));
            if (!ic.isEmpty()) made.add("comment " + Preview.str(Preview.map(ic.get("commentThread")), "commentId"));
            var ar = Preview.map(r.get("addCommentReply"));
            if (!ar.isEmpty()) made.add("post " + Preview.str(Preview.map(ar.get("post")), "postId"));
        }
        Out.success(reqs.size() + " request(s) applied" + (made.isEmpty() ? "" : "; created " + String.join(", ", made)));
    }

    record Hit(DocThread thread, Post post) {}

    private static Hit postIn(List<DocThread> threads, String postId) {
        for (var t : threads) { var p = t.post(postId); if (p != null) return new Hit(t, p); }
        Out.die("no post " + postId + " in this document (run `comments <doc>` / `suggestions <doc>` for ids)");
        return null;
    }

    private static void refuseUneditable(DocThread t, Post p) {
        if (!p.me()) Out.die("post " + p.postId() + " was written by " + p.author() + "; Google lets only its author edit it");
        if (p.head() && !t.isComment()) Out.die("post " + p.postId() + " is the first post of a suggestion thread, which Google does not let anyone edit");
    }

    private static DocThread thread(List<DocThread> all, String id) {
        var t = DocThread.find(all, id);
        if (t == null) Out.die("no comment or suggestion thread " + id + " in this document (run `comments <doc>` / `suggestions <doc>`)");
        return t;
    }

    private static Tab one(List<Tab> all, String sel) {
        var hits = Tabs.select(all, sel);
        if (hits.size() != 1) Out.die("--tab must name exactly one tab");
        return hits.get(0);
    }

    static String where(DocThread t) {
        if (t.tabId() == null) return "OPEN".equals(t.status()) ? "location not found" : "no longer in the text";
        return t.tabTitle() + (t.para() == null ? "" : ", ¶ \"" + t.anchorText() + "\"");
    }

    private static void print(DocThread t) {
        var flag = t.awaiting() ? "  [AWAITING]" : "";
        Out.data((t.isComment() ? "c " : "s ") + t.id() + "  " + t.status() + "  " + where(t) + flag);
        if (t.quote() != null && !t.quote().isBlank()) Out.data("    on: \"" + clip(t.quote(), 100) + "\"");
        if (t.summary() != null && !t.summary().isBlank()) Out.data("    change: " + clip(t.summary(), 160));
        for (var p : t.posts()) {
            if (p.deleted()) { Out.data("    " + p.postId() + "  (deleted)"); continue; }
            var body = p.content() == null || p.content().isBlank() ? "" : p.content().replace("\n", " ⏎ ");
            Out.data("    " + p.postId() + "  " + p.author() + (p.me() ? " (me)" : "") + (p.role().equals("other") ? "" : " " + Identity.label(p.role())) + "  " + Objects.requireNonNullElse(p.time(), "")
                    + (p.action() == null ? "" : "  [" + p.action() + "]") + (body.isEmpty() ? "" : "  " + body));
        }
    }

    private static String clip(String s, int n) { s = s.replace("\n", " ⏎ "); return s.length() > n ? s.substring(0, n) + "…" : s; }

    private static Map<String, Object> asMap(DocThread t) {
        var m = new LinkedHashMap<String, Object>();
        m.put("kind", t.kind()); m.put("id", t.id()); m.put("status", t.status()); m.put("awaiting", t.awaiting());
        m.put("tabId", t.tabId()); m.put("tab", t.tabTitle()); m.put("para", t.anchorText());
        if (t.quote() != null) m.put("quote", t.quote());
        if (t.summary() != null) m.put("summary", t.summary());
        m.put("posts", t.posts().stream().map(p -> {
            var pm = new LinkedHashMap<String, Object>();
            pm.put("postId", p.postId()); pm.put("author", p.author()); pm.put("me", p.me()); pm.put("time", p.time());
            pm.put("content", p.content()); pm.put("signed", p.signed()); pm.put("role", p.role());
            if (p.action() != null) pm.put("action", p.action());
            if (p.deleted()) pm.put("deleted", true);
            return pm;
        }).toList());
        return m;
    }
}
