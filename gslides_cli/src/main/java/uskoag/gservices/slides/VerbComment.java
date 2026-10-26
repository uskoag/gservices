package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.Request;
import java.util.*;
import java.util.List;
import uskoag.gservices.comments.AiSign;
import uskoag.gservices.comments.Post;

/**
 * comment add|reply|resolve|reopen|edit|delete|unsign — the same verbs, rules and dry-run default as
 * uskoag-gdocscli. Everything written is signed "AI(<name>): " (AiSign). A resolve or an assignment
 * that carries text goes as TWO posts, the text first and then a bare action post: Google never lets a
 * post with an action be deleted, so only an empty post is ever stuck.
 */
final class VerbComment {

    private VerbComment() {}

    record Hit(SlideThread thread, Post post) {}

    static void run(List<String> a) throws Exception {
        if (a.isEmpty()) Out.die("comment needs a sub-verb: add | reply | resolve | reopen | edit | delete | unsign");
        var sub = a.remove(0).toLowerCase();
        var apply = Args.flag(a, "--apply");
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var pres = CommentApi.get(deck);
        var threads = SlideThread.all(pres);
        var reqs = new ArrayList<Request>();
        var report = new ArrayList<String>();
        String pin = null;
        switch (sub) {
            case "add" -> {
                var on = Args.val(a, "--on");
                var slide = Args.intVal(a, "--slide");
                var find = Args.val(a, "--find");
                var cell = Args.val(a, "--cell");
                var text = Args.text(a, "--text", "--text-file");
                var assign = Args.val(a, "--assign");
                Args.noneLeft(a, "comment add");
                if (text == null || text.isBlank()) Out.die("comment add: --text is required");
                var target = CommentTarget.resolve(pres, on, slide, find, cell);
                var content = AiSign.sign(text.strip());
                reqs.add(CommentApi.insert(target.anchor(), content, assign));
                report.add("add comment on " + target.label() + (assign == null ? "" : ", assigned to " + assign));
                report.add("  text: " + content);
                pin = pres.getRevisionId();
            }
            case "reply", "resolve", "reopen" -> {
                var t = thread(threads, Args.req(a, "<threadId>"));
                var text = Args.text(a, "--text", "--text-file");
                var assign = Args.val(a, "--assign");
                var resolve = sub.equals("resolve") || Args.flag(a, "--resolve");
                var reopen = sub.equals("reopen");
                Args.noneLeft(a, "comment " + sub);
                if (sub.equals("reply") && (text == null || text.isBlank())) Out.die("comment reply: --text is required");
                report.add(sub + " on comment " + t.id() + " (" + t.where() + ")");
                if (text != null && !text.isBlank()) {
                    var content = AiSign.sign(text.strip());
                    reqs.add(CommentApi.reply(t.id(), content, null, null));
                    report.add("  reply: " + content);
                }
                if (assign != null) { reqs.add(CommentApi.reply(t.id(), null, null, assign)); report.add("  then a bare post assigning it to " + assign); }
                if (resolve) { reqs.add(CommentApi.reply(t.id(), null, "RESOLVE", null)); report.add("  then a bare RESOLVE post (no text, so nothing undeletable carries words)"); }
                if (reopen) { reqs.add(CommentApi.reply(t.id(), null, "REOPEN", null)); report.add("  then a bare REOPEN post"); }
            }
            case "edit" -> {
                var postId = Args.req(a, "<postId>");
                var text = Args.text(a, "--text", "--text-file");
                Args.noneLeft(a, "comment edit");
                if (text == null || text.isBlank()) Out.die("comment edit: --text is required");
                var hit = postIn(threads, postId);
                if (!hit.post().me()) Out.die("post " + postId + " was written by " + hit.post().author() + "; Google lets only its author edit it");
                if (hit.post().actionOnly()) Out.die("post " + postId + " is a bare " + hit.post().action() + " post; Google refuses to edit a post that has no text");
                var content = AiSign.sign(text.strip());
                reqs.add(CommentApi.updatePost(hit.thread().id(), postId, content));
                report.add("edit post " + postId + " in comment " + hit.thread().id() + " (" + hit.thread().where() + ")");
                report.add("  was: " + hit.post().content());
                report.add("  now: " + content);
            }
            case "delete" -> delete(threads, Args.req(a, "<threadId|postId>"), a, reqs, report);
            case "unsign" -> {
                var allMine = Args.flag(a, "--all-mine");
                var postId = allMine ? null : Args.req(a, "<postId> (or --all-mine)");
                Args.noneLeft(a, "comment unsign");
                for (var t : threads) for (var p : t.posts()) {
                    if (postId != null ? !p.postId().equals(postId) : !(p.me() && p.signed() && !p.deleted())) continue;
                    if (!p.signed()) Out.die("comment unsign: post " + p.postId() + " carries no AI signature");
                    reqs.add(CommentApi.updatePost(t.id(), p.postId(), AiSign.unsign(p.content())));
                    report.add("unsign " + p.postId() + " (comment " + t.id() + "): " + AiSign.unsign(p.content()));
                }
                if (postId != null && reqs.isEmpty()) Out.die("comment unsign: no post " + postId);
                if (reqs.isEmpty()) { Out.data("nothing to unsign"); return; }
            }
            default -> Out.die("unknown comment sub-verb: " + sub + " (add | reply | resolve | reopen | edit | delete | unsign)");
        }
        report.forEach(Out::data);
        if (!apply || Api.dryRun) {
            Out.data("DRY RUN — " + reqs.size() + " request(s). Nothing sent; re-run with --apply to commit.");
            if (Out.verbose) for (var r : reqs) Out.data("  " + Api.describe(r));
            return;
        }
        var made = CommentApi.created(CommentApi.send(deck, reqs, pin));
        Out.success(reqs.size() + " request(s) applied" + (made.isEmpty() ? "" : "; created " + String.join(", ", made)));
    }

    private static void delete(List<SlideThread> threads, String target, List<String> a, List<Request> reqs, List<String> report) {
        Args.noneLeft(a, "comment delete");
        var t = SlideThread.find(threads, target);
        if (t != null) {
            if (!t.mine()) Out.die("comment delete: thread " + target + " was started by " + t.head().author() + "; Google lets only its author delete it");
            reqs.add(CommentApi.deleteComment(target));
            report.add("delete comment thread " + target + " (" + t.where() + ") with all " + t.posts().size() + " post(s)");
            t.posts().forEach(p -> report.add("  " + p.author() + ": " + Objects.requireNonNullElse(p.content(), "(" + p.action() + ")")));
        } else {
            var hit = postIn(threads, target);
            if (hit.post().head()) Out.die("comment delete: " + target + " is the first post of thread " + hit.thread().id() + "; delete the thread instead");
            if (!hit.post().me()) Out.die("comment delete: post " + target + " was written by " + hit.post().author() + "; Google lets only its author delete it");
            if (hit.post().action() != null) Out.die("comment delete: post " + target + " carries a " + hit.post().action() + " action, and Google never deletes such posts");
            reqs.add(CommentApi.deleteReply(hit.thread().id(), target));
            report.add("delete reply " + target + " in comment " + hit.thread().id() + ": " + hit.post().content());
        }
        report.add("  NOTE: a deleted comment is NOT in the deck's version history");
    }

    private static Hit postIn(List<SlideThread> threads, String postId) {
        for (var t : threads) { var p = t.post(postId); if (p != null) return new Hit(t, p); }
        Out.die("no post " + postId + " in this deck (run `comments <deck>` for ids)");
        return null;
    }

    private static SlideThread thread(List<SlideThread> all, String id) {
        var t = SlideThread.find(all, id);
        if (t == null) Out.die("no comment thread " + id + " in this deck (run `comments <deck> --status all`)");
        return t;
    }
}
