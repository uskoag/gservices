package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;

/**
 * images (list) and image insert | replace. An image is addressed by its inline object id, which
 * `images` prints together with the paragraph it sits in. Dry run unless --apply; a dry run uploads
 * nothing, because an upload is made public for the moment of the insert.
 */
final class VerbImage {

    private VerbImage() {}

    static void list(List<String> a) throws Exception {
        var json = Args.flag(a, "--json");
        var tabSel = Args.val(a, "--tab", "-t");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "images");
        var doc = DocRef.get(id);
        var rows = new ArrayList<Map<String, Object>>();
        for (var t : Tabs.select(Tabs.flat(doc), tabSel)) {
            var dt = t.getDocumentTab();
            if (dt == null || dt.getInlineObjects() == null) continue;
            for (var p : Para.of(dt)) for (var e : p.p().getElements()) {
                if (e.getInlineObjectElement() == null) continue;
                var oid = e.getInlineObjectElement().getInlineObjectId();
                var eo = dt.getInlineObjects().get(oid).getInlineObjectProperties().getEmbeddedObject();
                var m = new LinkedHashMap<String, Object>();
                m.put("objectId", oid); m.put("tab", Tabs.title(t)); m.put("tabId", Tabs.id(t)); m.put("index", e.getStartIndex());
                m.put("size", size(eo.getSize()));
                if (eo.getTitle() != null) m.put("title", eo.getTitle());
                if (eo.getDescription() != null) m.put("description", eo.getDescription());
                var near = p.plain().replace(Para.OBJ, ' ').strip();
                m.put("para", near.isEmpty() ? "(paragraph holds only the image)" : near.length() > 40 ? near.substring(0, 40) : near);
                var sugg = e.getInlineObjectElement().getSuggestedInsertionIds();
                if (sugg != null && !sugg.isEmpty()) m.put("suggestedInsertion", sugg);
                var del = e.getInlineObjectElement().getSuggestedDeletionIds();
                if (del != null && !del.isEmpty()) m.put("suggestedDeletion", del);
                rows.add(m);
            }
        }
        if (json) { Out.json(rows); return; }
        if (rows.isEmpty()) { Out.data("no images"); return; }
        for (var m : rows) Out.data(m.get("objectId") + "  " + m.get("tab") + " @" + m.get("index") + "  " + m.get("size") + "  ¶ \"" + m.get("para") + "\""
                + (m.containsKey("suggestedInsertion") ? "  [suggested insertion]" : "") + (m.containsKey("suggestedDeletion") ? "  [suggested deletion]" : "") + (m.containsKey("title") ? "  title=\"" + m.get("title") + "\"" : ""));
        Out.data(rows.size() + " image(s)");
    }

    static void run(List<String> a) throws Exception {
        if (a.isEmpty()) Out.die("image needs a sub-verb: insert | replace");
        var sub = a.remove(0).toLowerCase();
        var apply = Args.flag(a, "--apply");
        var suggest = Args.flag(a, "--suggest");
        var keep = Args.flag(a, "--keep-upload");
        var showRequests = Args.flag(a, "--requests");
        var note = Args.val(a, "--note");
        var image = Args.val(a, "--image");
        var width = Args.dblVal(a, "--width");
        var id = DocRef.id(Args.req(a, "<doc>"));
        DocRef.suggestions = "inline";
        var doc = DocRef.get(id);
        var all = Tabs.flat(doc);
        String tabId;
        int noteAt;
        var build = new ArrayList<java.util.function.Function<String, Request>>();
        switch (sub) {
            case "insert" -> {
                var tabSel = Args.val(a, "--tab", "-t");
                var after = Args.val(a, "--after");
                Args.noneLeft(a, "image insert");
                if (after == null) Out.die("image insert: --after \"<paragraph start>\" is required");
                if (tabSel == null && all.size() > 1) Out.die("image insert: the document has " + all.size() + " tabs; name one with --tab");
                var tab = tabSel == null ? all.get(0) : Tabs.select(all, tabSel).get(0);
                tabId = Tabs.id(tab);
                var scope = new Scope(tab, Para.of(tab.getDocumentTab()));
                var p = scope.anchor("image insert --after", after);
                var at = p.end() - 1;
                noteAt = p.end();
                final var tid = tabId;
                // A new empty paragraph after the anchor (it inherits the anchor's style), then the image in it.
                build.add(url -> new Request().setInsertText(new InsertTextRequest().setText("\n").setLocation(new Location().setIndex(at).setTabId(tid))));
                build.add(url -> {
                    var r = new InsertInlineImageRequest().setUri(url).setLocation(new Location().setIndex(at + 1).setTabId(tid));
                    if (width != null) r.setObjectSize(new Size().setWidth(new Dimension().setMagnitude(width * 72).setUnit("PT")));
                    return new Request().setInsertInlineImage(r);
                });
                Out.data("insert image " + image + " as a new paragraph after \"" + p.preview() + "\" in " + scope.name()
                        + (width == null ? "" : ", " + width + " in wide") + (suggest ? ", as a SUGGESTION" : ""));
            }
            case "replace" -> {
                var objectId = Args.req(a, "<objectId>");
                Args.noneLeft(a, "image replace");
                var where = find(all, objectId);
                if (where == null) Out.die("image replace: no image " + objectId + " (run `images <doc>`)");
                tabId = Tabs.id(where.tab());
                noteAt = where.index();
                final var tid = tabId;
                final var idx = where.index();
                if (suggest) {
                    // Google applies replaceImage directly even in SUGGEST mode (measured 2026-09-25), so a
                    // reviewable replace is the new picture inserted before the old, the old one deleted,
                    // both as suggestions, at the old picture's size.
                    var size = where.tab().getDocumentTab().getInlineObjects().get(objectId).getInlineObjectProperties().getEmbeddedObject().getSize();
                    build.add(url -> new Request().setInsertInlineImage(new InsertInlineImageRequest().setUri(url)
                            .setLocation(new Location().setIndex(idx).setTabId(tid)).setObjectSize(size)));
                    build.add(url -> new Request().setDeleteContentRange(new DeleteContentRangeRequest()
                            .setRange(new Range().setTabId(tid).setStartIndex(idx + 1).setEndIndex(idx + 2))));
                } else build.add(url -> new Request().setReplaceImage(new ReplaceImageRequest().setImageObjectId(objectId).setUri(url)
                        .setImageReplaceMethod("CENTER_CROP").setTabId(tid)));
                Out.data("replace image " + objectId + " in " + Tabs.title(where.tab()) + " @" + idx + " with " + image
                        + (suggest ? ", as a SUGGESTION: new picture inserted at the old one's size, old one suggested for deletion"
                                   : " (keeps its size; the new picture is centre-cropped to it)"));
            }
            default -> { Out.die("unknown image sub-verb: " + sub + " (insert | replace)"); return; }
        }
        if (image == null) Out.die("--image <file|https-url> is required");
        if (note != null) Out.data("note " + (suggest ? "(reply on its suggestion)" : "(comment on the image)") + ": " + AiSign.sign(note.strip()));
        var wc = new WriteControl().setRequiredRevisionId(doc.getRevisionId());
        if (suggest) Preview.suggestMode(wc);
        if (!apply) {
            if (showRequests) Out.data(com.google.api.client.json.gson.GsonFactory.getDefaultInstance().toPrettyString(build.stream().map(f -> f.apply("<image url>")).toList()));
            var local = !(image.startsWith("https://") || image.startsWith("http://"));
            Out.data("DRY RUN — nothing uploaded, nothing sent." + (local ? " On --apply the file is uploaded to Drive, shared by link for the insert, then trashed." : "") + " Re-run with --apply.");
            return;
        }
        var src = Images.source(image);
        try {
            var reqs = build.stream().map(f -> f.apply(src.url())).toList();
            var res = Journal.around(id, "image", "image " + image, () -> Preview.send(id, reqs, wc));
            var made = new ArrayList<String>();
            for (var r : Preview.list(res.get("replies"))) {
                var ii = Preview.map(r.get("insertInlineImage"));
                if (!ii.isEmpty()) made.add(Preview.str(ii, "objectId"));
            }
            Out.success("image " + (sub.equals("insert") ? "inserted" : "replaced") + (made.isEmpty() ? "" : ", object " + String.join(", ", made)) + (suggest ? " as a suggestion" : ""));
            var created = new ArrayList<String>();
            if (suggest) {
                for (var ids : Preview.createdSuggestionIds(res)) for (var s : ids) if (!created.contains(s)) created.add(s);
                Out.data("suggestion(s): " + (created.isEmpty() ? "NONE — Google did not record this as a suggestion" : String.join(", ", created)));
            }
            if (note != null && !note.isBlank()) {
                Request nr;
                if (suggest) nr = created.isEmpty() ? null : Preview.replyToSuggestion(created.get(0), AiSign.sign(note.strip()));
                else nr = Preview.insertComment(tabId, noteAt, noteAt + 1, AiSign.sign(note.strip()), null);
                if (nr == null) Out.error("no suggestion to attach the note to; note not posted");
                else { Preview.send(id, List.of(nr), null); Out.success("note posted"); }
            }
            if (suggest && created.isEmpty()) System.exit(2);
        } finally {
            if (!keep) Images.cleanup(src);
        }
    }

    record Where(Tab tab, int index) {}

    private static Where find(List<Tab> all, String objectId) {
        for (var t : all) for (var p : Para.of(t.getDocumentTab())) for (var e : p.p().getElements())
            if (e.getInlineObjectElement() != null && objectId.equals(e.getInlineObjectElement().getInlineObjectId())) return new Where(t, e.getStartIndex());
        return null;
    }

    private static String size(Size s) {
        if (s == null || s.getWidth() == null || s.getHeight() == null) return "?";
        return String.format("%.1fx%.1f in", s.getWidth().getMagnitude() / 72, s.getHeight().getMagnitude() / 72);
    }
}
