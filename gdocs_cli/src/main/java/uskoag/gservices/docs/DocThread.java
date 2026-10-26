package uskoag.gservices.docs;

import uskoag.gservices.comments.Post;
import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;

/**
 * A comment thread or a suggestion thread, placed: which tab, which paragraph (given as the same
 * opening-text anchor the edit verbs take), and its posts. Built from one read, like every address
 * in this tool: re-read before acting on a later revision.
 */
record DocThread(String kind, String id, String status, String tabId, String tabTitle, Para para, String quote, String summary, List<Post> posts) {

    boolean isComment() { return kind.equals("comment"); }

    String idKey() { return isComment() ? "commentId" : "suggestionId"; }

    Post head() { return posts.isEmpty() ? null : posts.get(0); }

    boolean mine() { return head() != null && head().me(); }

    /** A human has spoken and the AI has not answered (see Post.awaiting). */
    boolean awaiting() { return Post.awaiting(status, posts); }

    /** The paragraph's opening, usable as --para / "after" / "in" in the edit verbs. */
    String anchorText() {
        if (para == null) return null;
        var s = para.plain().replace(Para.OBJ, ' ').strip();
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    Post post(String postId) { return Post.find(posts, postId); }

    static List<DocThread> all(Document doc) {
        var tabs = Tabs.flat(doc);
        var out = new ArrayList<DocThread>();
        record Where(Tab tab, Para para) {}
        var byAnchor = new HashMap<String, Where>();
        var bySuggestion = new HashMap<String, Where>();
        for (var t : tabs) {
            var paras = Para.of(t.getDocumentTab());
            var scope = new Scope(t, paras);
            for (var e : Preview.anchors(t.getDocumentTab()).entrySet()) {
                var ranges = Preview.list(Preview.map(e.getValue()).get("ranges"));
                var start = ranges.isEmpty() ? null : (Number) ranges.get(0).get("startIndex");
                byAnchor.put(e.getKey(), new Where(t, start == null ? null : scope.paraAt(start.intValue())));
            }
            for (var p : paras) for (var sid : suggestionIds(p)) bySuggestion.putIfAbsent(sid, new Where(t, p));
        }
        for (var c : Preview.comments(doc)) {
            var w = byAnchor.get(Preview.str(c, "anchorId"));
            out.add(new DocThread("comment", Preview.str(c, "commentId"), Preview.str(c, "status"),
                    w == null ? null : Tabs.id(w.tab()), w == null ? null : Tabs.title(w.tab()), w == null ? null : w.para(),
                    Preview.str(c, "plainTextQuote"), null, Post.of(c)));
        }
        for (var s : Preview.suggestions(doc)) {
            var sid = Preview.str(s, "suggestionId");
            var w = bySuggestion.get(sid);
            out.add(new DocThread("suggestion", sid, Preview.str(s, "status"),
                    w == null ? null : Tabs.id(w.tab()), w == null ? null : Tabs.title(w.tab()), w == null ? null : w.para(),
                    null, Preview.str(s, "summaryText"), Post.of(s)));
        }
        return out;
    }

    static DocThread find(List<DocThread> all, String id) {
        for (var t : all) if (t.id().equals(id)) return t;
        return null;
    }

    /** Every suggestion id that touches a paragraph: inserted or deleted text, a text-style or paragraph-style change, a bullet change. */
    static Set<String> suggestionIds(Para p) {
        var ids = new LinkedHashSet<String>();
        var para = p.p();
        if (para.getSuggestedParagraphStyleChanges() != null) ids.addAll(para.getSuggestedParagraphStyleChanges().keySet());
        if (para.getSuggestedBulletChanges() != null) ids.addAll(para.getSuggestedBulletChanges().keySet());
        if (para.getElements() != null) for (var e : para.getElements()) {
            var r = e.getTextRun();
            if (r == null) continue;
            if (r.getSuggestedInsertionIds() != null) ids.addAll(r.getSuggestedInsertionIds());
            if (r.getSuggestedDeletionIds() != null) ids.addAll(r.getSuggestedDeletionIds());
            if (r.getSuggestedTextStyleChanges() != null) ids.addAll(r.getSuggestedTextStyleChanges().keySet());
        }
        return ids;
    }
}
