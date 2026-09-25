package uskoag.gservices.docs;

import java.util.*;

/** The tab tree: position, id and indented title, so a later --tab can name exactly one. */
public final class VerbTabs {

    private VerbTabs() {}

    static void run(List<String> a) throws Exception {
        var json = Args.flag(a, "--json");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "tabs");
        var doc = DocRef.get(id);
        var all = Tabs.flat(doc);
        if (json) {
            var rows = new ArrayList<Map<String, Object>>();
            for (var i = 0; i < all.size(); i++) {
                var t = all.get(i);
                rows.add(Map.of("n", i + 1, "tabId", Tabs.id(t), "title", Tabs.title(t), "depth", Tabs.depth(t),
                        "path", Tabs.path(t, all), "chars", chars(t)));
            }
            Out.json(Map.of("documentId", doc.getDocumentId(), "title", doc.getTitle(), "revisionId", doc.getRevisionId(), "tabs", rows));
            return;
        }
        Out.data("# " + doc.getTitle() + "  (" + all.size() + " tab" + (all.size() == 1 ? "" : "s") + ")");
        Out.data("n\ttabId\tchars\ttitle");
        for (var i = 0; i < all.size(); i++) {
            var t = all.get(i);
            Out.data((i + 1) + "\t" + Tabs.id(t) + "\t" + chars(t) + "\t" + "  ".repeat(Tabs.depth(t)) + Tabs.title(t));
        }
    }

    /** The body's end index is its length in the API's own units, a cheap size hint without rendering. */
    static int chars(com.google.api.services.docs.v1.model.Tab t) {
        var dt = t.getDocumentTab();
        if (dt == null || dt.getBody() == null || dt.getBody().getContent() == null || dt.getBody().getContent().isEmpty()) return 0;
        var c = dt.getBody().getContent();
        return Objects.requireNonNullElse(c.get(c.size() - 1).getEndIndex(), 0);
    }
}
