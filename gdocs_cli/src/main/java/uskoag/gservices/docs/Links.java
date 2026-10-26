package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Link targets as written in markdown: a URL, `tab:<sel>` (any --tab selector), or `#tab=<id>`, which is how
 * `read` prints a link to a tab, so a read-edit-write round trip keeps it. A tab link is the API's own
 * Link.tabId, not a URL, so it survives the document being copied.
 */
final class Links {

    /** The document's tabs, set by the verb once it has read the document; tab targets resolve against it. */
    static List<Tab> tabs = List.of();

    /** The colour Docs gives a link it styles itself, #1155cc. */
    static final OptionalColor BLUE = new OptionalColor().setColor(new Color().setRgbColor(new RgbColor().setRed(17 / 255f).setGreen(85 / 255f).setBlue(204 / 255f)));

    private Links() {}

    static Link resolve(String target) {
        if (target == null || target.isBlank()) Out.die("empty link target");
        var t = target.strip();
        if (t.startsWith("tab:")) return new Link().setTabId(Tabs.id(one(t.substring(4).strip())));
        if (t.startsWith("#tab=")) return new Link().setTabId(Tabs.id(one(t.substring(5).strip())));
        if (t.startsWith("heading:")) return heading(t.substring(8).strip(), true);
        if (t.startsWith("#heading=")) return heading(t.substring(9).strip(), false);
        return new Link().setUrl(t);
    }

    /** A link to a heading paragraph of any tab: named by the start of its text, or by the heading id `read` prints. */
    static Link heading(String key, boolean byText) {
        var hits = new ArrayList<Link>();
        for (var t : tabs) for (var p : Para.of(t.getDocumentTab())) {
            var h = p.p().getParagraphStyle() == null ? null : p.p().getParagraphStyle().getHeadingId();
            if (h == null || !(byText ? Para.anchorForms(key).stream().anyMatch(f -> p.plain().strip().startsWith(f)) : h.equals(key))) continue;
            hits.add(new Link().setHeading(new HeadingLink().setId(h).setTabId(Tabs.id(t))));
        }
        if (hits.size() != 1) Out.die("link target heading \"" + key + "\" matches " + hits.size() + " heading paragraph(s); it must match exactly one (a paragraph without a heading style has no heading id)");
        return hits.get(0);
    }

    private static Tab one(String sel) {
        var hits = Tabs.select(tabs, sel);
        if (hits.size() != 1) Out.die("link target tab '" + sel + "' must name exactly one tab");
        return hits.get(0);
    }

    /** What a person reading the dry run recognises: the URL, or the tab by title and id. */
    static String describe(Link l) {
        if (l == null) return "(no link)";
        if (l.getHeading() != null) return "heading " + l.getHeading().getId();
        if (l.getTabId() == null) return Styles.link(new TextStyle().setLink(l));
        var t = tabs.stream().filter(x -> Tabs.id(x).equals(l.getTabId())).findFirst();
        return "tab \"" + t.map(Tabs::title).orElse("?") + "\" (" + l.getTabId() + ")";
    }
}
