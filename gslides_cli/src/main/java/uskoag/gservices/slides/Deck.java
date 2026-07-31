package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Presentation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The address grammar. A sheet's A1 notation is derivable from the grid; a slide's is
 * not, because an element exists only because something created it and carries an
 * opaque server id. So addressing here is resolve-then-use:
 *
 *   deck     presentation id, or any Slides URL it can be extracted from
 *   page     1-based slide number, or a pageId
 *   element  objectId (caller-assigned at create time, or discovered via `describe`)
 */
public final class Deck {

    private static final Pattern
            URL_ID = Pattern.compile("/presentation/d/([A-Za-z0-9_-]+)"),
            BARE_ID = Pattern.compile("[A-Za-z0-9_-]{20,}"),
            DIGITS = Pattern.compile("\\d+");

    private Deck() {}

    static String presId(String ref) {
        if (ref == null) Out.die("missing <deck> (presentation id or Slides URL)");
        if (BARE_ID.matcher(ref).matches()) return ref;
        var m = URL_ID.matcher(ref);
        if (m.find()) return m.group(1);
        Out.die("cannot extract a presentation id from: " + ref);
        return null;
    }

    static Presentation get(String presId, String fields) throws Exception {
        var req = Auth.slides().presentations().get(presId);
        if (fields != null) req.setFields(fields);
        return req.execute();
    }

    static List<Page> slides(String presId) throws Exception {
        var p = get(presId, "slides(objectId)");
        return p.getSlides() == null ? List.of() : p.getSlides();
    }

    /** A slide number costs one extra call; a pageId costs none. */
    static String pageId(String presId, String pageRef) throws Exception {
        if (pageRef == null) Out.die("missing <page> (slide number or pageId)");
        if (!DIGITS.matcher(pageRef).matches()) return pageRef;
        var slides = slides(presId);
        return slides.get(index(pageRef, slides.size())).getObjectId();
    }

    static int index(String pageRef, int total) {
        var n = Integer.parseInt(pageRef);
        if (n < 1 || n > total) Out.die("slide number out of range (1-" + total + "): " + n);
        return n - 1;
    }

    static Page page(Presentation pres, String pageRef) {
        var slides = pres.getSlides();
        if (slides == null || slides.isEmpty()) Out.die("deck has no slides");
        if (DIGITS.matcher(pageRef).matches()) return slides.get(index(pageRef, slides.size()));
        for (var s : slides) if (pageRef.equals(s.getObjectId())) return s;
        Out.die("no such page: " + pageRef);
        return null;
    }

    /** Elements can be nested in groups, so this walks the tree rather than one list. */
    static PageElement element(Presentation pres, String objectId) {
        if (pres.getSlides() != null)
            for (var s : pres.getSlides()) {
                var hit = find(s.getPageElements(), objectId);
                if (hit != null) return hit;
            }
        Out.die("no such element: " + objectId);
        return null;
    }

    static PageElement findOn(Page page, String objectId) {
        return find(page.getPageElements(), objectId);
    }

    private static PageElement find(List<PageElement> els, String objectId) {
        if (els == null) return null;
        for (var e : els) {
            if (objectId.equals(e.getObjectId())) return e;
            if (e.getElementGroup() != null) {
                var hit = find(e.getElementGroup().getChildren(), objectId);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    static List<PageElement> imagesOn(Page page) {
        var out = new ArrayList<PageElement>();
        if (page.getPageElements() != null)
            for (var e : page.getPageElements()) if (e.getImage() != null) out.add(e);
        return out;
    }

    static String url(String presId) {
        return "https://docs.google.com/presentation/d/" + presId + "/edit";
    }
}
