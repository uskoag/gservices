package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.*;
import java.util.*;
import java.util.List;
import uskoag.gservices.comments.Post;

import static uskoag.gservices.comments.Wire.*;

/**
 * A comment thread, placed: which slide, which element (by the same objectId every other verb takes),
 * which character range of its text, and its posts. Built from one read, like every address in this
 * tool: re-read before acting on a later revision.
 *
 * @param slide   1-based slide number, null when the anchor is on no slide (a layout, a master, or gone)
 * @param target  the element as `describe` would show it, or "the slide itself" / "speaker notes"
 */
record SlideThread(String id, String status, Integer slide, String pageId, String objectId, String target, String range, String quote, List<Post> posts) {

    record Where(Integer slide, String pageId, String objectId, String target, String range) {}

    Post head() { return posts.isEmpty() ? null : posts.get(0); }

    boolean mine() { return head() != null && head().me(); }

    boolean awaiting() { return Post.awaiting(status, posts); }

    Post post(String postId) { return Post.find(posts, postId); }

    String where() {
        if (slide == null) return "OPEN".equals(status) ? "location not found" : "no longer on any slide";
        return "slide " + slide + ", " + target + (range == null ? "" : " " + range);
    }

    static List<SlideThread> all(Presentation pres) {
        var where = new HashMap<String, Where>();
        var slides = pres.getSlides() == null ? List.<Page>of() : pres.getSlides();
        for (var i = 0; i < slides.size(); i++) {
            var s = slides.get(i);
            index(where, s, i + 1, s, false);
            var sp = s.getSlideProperties();
            if (sp != null && sp.getNotesPage() != null) index(where, sp.getNotesPage(), i + 1, s, true);
        }
        var out = new ArrayList<SlideThread>();
        for (var c : CommentApi.comments(pres)) {
            var w = where.get(str(c, "anchorId"));
            out.add(new SlideThread(str(c, "commentId"), str(c, "status"),
                    w == null ? null : w.slide(), w == null ? null : w.pageId(), w == null ? null : w.objectId(),
                    w == null ? null : w.target(), w == null ? null : w.range(), str(c, "plainTextQuote"), Post.of(c)));
        }
        return out;
    }

    static SlideThread find(List<SlideThread> all, String id) {
        for (var t : all) if (t.id().equals(id)) return t;
        return null;
    }

    private static void index(Map<String, Where> where, Page page, int n, Page slide, boolean notes) {
        for (var a : CommentApi.anchors(page)) {
            var oas = list(a.get("objectAnchors"));
            if (oas.isEmpty()) continue;
            var oa = oas.get(0);
            var objectId = str(oa, "objectId");
            var target = describe(page, objectId, notes) + (oas.size() > 1 ? " (+" + (oas.size() - 1) + " more objects)" : "");
            where.putIfAbsent(str(a, "anchorId"), new Where(n, slide.getObjectId(), objectId, target, range(oa)));
        }
    }

    private static String describe(Page page, String objectId, boolean notes) {
        if (objectId == null || objectId.equals(page.getObjectId())) return notes ? "speaker notes page" : "the slide itself";
        var el = Deck.findOn(page, objectId);
        var label = (notes ? "notes " : "") + objectId;
        if (el == null) return label;
        var text = Els.preview(el, 40);
        return label + " (" + Els.typeOf(el) + ")" + (text.isEmpty() ? "" : " \"" + text + "\"");
    }

    /** "@S:E" for a text range, "cell R,C @S:E" (1-based, as --cell takes it) in a table; null when the whole object is anchored. */
    private static String range(Map<String, Object> oa) {
        var shape = list(map(oa.get("shapeTextAnchors")).get("ranges"));
        if (!shape.isEmpty()) return span(shape.get(0));
        var cells = list(map(oa.get("tableCellAnchors")).get("cellRanges"));
        if (cells.isEmpty()) return null;
        var loc = map(cells.get(0).get("cellLocation"));
        var rs = list(cells.get(0).get("ranges"));
        var cell = "cell " + (Objects.requireNonNullElse(num(loc, "rowIndex"), 0) + 1) + "," + (Objects.requireNonNullElse(num(loc, "columnIndex"), 0) + 1);
        return rs.isEmpty() ? cell : cell + " " + span(rs.get(0));
    }

    private static String span(Map<String, Object> r) {
        return "@" + Objects.requireNonNullElse(num(r, "startIndex"), 0) + ":" + (num(r, "endIndex") == null ? "" : num(r, "endIndex"));
    }
}
