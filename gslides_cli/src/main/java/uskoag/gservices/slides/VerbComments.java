package uskoag.gservices.slides;

import java.util.*;
import java.util.List;
import uskoag.gservices.comments.Identity;

/** comments — every comment thread with its slide, element, quoted text and posts. Read-only. */
final class VerbComments {

    private VerbComments() {}

    static void run(List<String> a) throws Exception {
        var json = Args.flag(a, "--json");
        var awaiting = Args.flag(a, "--awaiting");
        var mine = Args.flag(a, "--mine");
        var status = Args.valOr(a, "--status", "open").toLowerCase();
        var slide = Args.intVal(a, "--slide");
        var deck = Deck.presId(Args.req(a, "<deck>"));
        Args.noneLeft(a, "comments");
        if (!Set.of("open", "resolved", "all").contains(status)) Out.die("comments: --status open|resolved|all");

        var pres = CommentApi.get(deck);
        var all = SlideThread.all(pres);
        var hits = all.stream()
                .filter(t -> status.equals("all") || status.equalsIgnoreCase(t.status()))
                .filter(t -> slide == null || slide.equals(t.slide()))
                .filter(t -> !awaiting || t.awaiting())
                .filter(t -> !mine || t.mine())
                .toList();
        if (json) { Out.json(hits.stream().map(VerbComments::asMap).toList()); return; }
        if (hits.isEmpty()) {
            Out.data("no comment threads match" + (all.isEmpty() ? " (the deck returned no comment threads at all)" : ""));
            return;
        }
        for (var t : hits) print(t);
        Out.data(hits.size() + " comment thread(s)" + (awaiting ? " awaiting an answer" : "") + ", revision " + pres.getRevisionId());
    }

    private static void print(SlideThread t) {
        Out.data("c " + t.id() + "  " + t.status() + "  " + t.where() + (t.awaiting() ? "  [AWAITING]" : ""));
        if (t.quote() != null && !t.quote().isBlank()) Out.data("    on: \"" + clip(t.quote(), 100) + "\"");
        for (var p : t.posts()) {
            if (p.deleted()) { Out.data("    " + p.postId() + "  (deleted)"); continue; }
            var body = p.content() == null || p.content().isBlank() ? "" : p.content().replace("\n", " ⏎ ");
            Out.data("    " + p.postId() + "  " + p.author() + (p.me() ? " (me)" : "")
                    + (p.role().equals("other") ? "" : " " + Identity.label(p.role())) + "  " + Objects.requireNonNullElse(p.time(), "")
                    + (p.action() == null ? "" : "  [" + p.action() + "]") + (body.isEmpty() ? "" : "  " + body));
        }
    }

    private static String clip(String s, int n) { s = s.replace("\n", " ⏎ "); return s.length() > n ? s.substring(0, n) + "…" : s; }

    private static Map<String, Object> asMap(SlideThread t) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", t.id()); m.put("status", t.status()); m.put("awaiting", t.awaiting());
        m.put("slide", t.slide()); m.put("pageId", t.pageId()); m.put("objectId", t.objectId());
        m.put("target", t.target()); m.put("range", t.range());
        if (t.quote() != null) m.put("quote", t.quote());
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
