package uskoag.gservices.docs;

import uskoag.gservices.comments.AiSign;
import com.google.api.services.docs.v1.model.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * images (list) and image insert | replace | resize | crop. An image is addressed by its inline
 * object id, which `images` prints together with the paragraph it sits in. Dry run unless --apply;
 * a dry run uploads and downloads nothing.
 *
 * <p>resize and crop both end in a delete-and-reinsert, never an in-place update: the Docs API's
 * full Request model was checked (only insertInlineImage and replaceImage touch a picture) and
 * neither can change the size or crop of an image that is already sitting in the document —
 * replaceImage keeps the old size, and there is no field to set crop offsets on an existing
 * object at all. So resize re-inserts the SAME picture (Google's own temporary content URI for
 * it) at a new size; crop downloads that picture, cuts it locally, and re-inserts the result.
 * Either way the object gets a new id, which the command prints.
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
        if (a.isEmpty()) Out.die("image needs a sub-verb: insert | replace | resize | crop");
        var sub = a.remove(0).toLowerCase();
        var apply = Args.flag(a, "--apply");
        var suggest = Args.flag(a, "--suggest");
        var keep = Args.flag(a, "--keep-upload");
        var showRequests = Args.flag(a, "--requests");
        var note = Args.val(a, "--note");
        var image = Args.val(a, "--image");
        var width = Args.dblVal(a, "--width");
        var height = Args.dblVal(a, "--height");
        var id = DocRef.id(Args.req(a, "<doc>"));
        DocRef.suggestions = "inline";
        var doc = DocRef.get(id);
        var all = Tabs.flat(doc);
        String tabId;
        int noteAt;
        String cropFrom = null;
        CropSpec crop = null;
        ImageCaption layout = null;
        var build = new ArrayList<java.util.function.Function<String, Request>>();
        switch (sub) {
            case "insert" -> {
                var tabSel = Args.val(a, "--tab", "-t");
                var after = Args.val(a, "--after");
                var afterImage = Args.val(a, "--after-image");
                var caption = Args.val(a, "--caption");
                var captionStyle = Args.val(a, "--caption-style");
                var fmt = ParaFmt.of(a, false);
                var cs = cropSpec(a);
                Args.noneLeft(a, "image insert");
                if (after != null && afterImage != null) Out.die("image insert: give --after or --after-image, not both");
                if (afterImage != null) after = Scope.IMAGE + afterImage;
                if (after == null) Out.die("image insert: --after \"<paragraph start>\" or --after-image <objectId> is required");
                if (captionStyle != null && caption == null) Out.die("image insert: --caption-style needs --caption");
                if (tabSel == null && all.size() > 1) Out.die("image insert: the document has " + all.size() + " tabs; name one with --tab");
                var tab = tabSel == null ? all.get(0) : Tabs.select(all, tabSel).get(0);
                tabId = Tabs.id(tab);
                var scope = new Scope(tab, Para.of(tab.getDocumentTab()));
                var p = scope.anchor("image insert --after", after);
                var at = p.end() - 1;
                noteAt = p.end();
                final var tid = tabId;
                if (cs.any()) { cropFrom = image; crop = cs; }
                var sz = sizeOf(width, height);
                // A new empty paragraph after the anchor (it inherits the anchor's style), then the image in it.
                build.add(url -> new Request().setInsertText(new InsertTextRequest().setText("\n").setLocation(new Location().setIndex(at).setTabId(tid))));
                build.add(url -> {
                    var r = new InsertInlineImageRequest().setUri(url).setLocation(new Location().setIndex(at + 1).setTabId(tid));
                    if (sz != null) r.setObjectSize(sz);
                    return new Request().setInsertInlineImage(r);
                });
                if (caption != null || fmt.any()) layout = ImageCaption.add(build, scope, p, at, caption, captionStyle, fmt);
                Out.data("insert image " + image + " as a new paragraph after \"" + p.preview() + "\" in " + scope.name()
                        + (sz == null ? "" : ", " + describe(sz)) + (cs.any() ? ", cropped (" + cs + ")" : "") + (suggest ? ", as a SUGGESTION" : ""));
                if (layout != null) Out.data("  " + layout.describe());
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
            case "resize" -> {
                var objectId = Args.req(a, "<objectId>");
                Args.noneLeft(a, "image resize");
                if (width == null && height == null) Out.die("image resize: --width and/or --height (inches) is required");
                var where = find(all, objectId);
                if (where == null) Out.die("image resize: no image " + objectId + " (run `images <doc>`)");
                var eo = where.tab().getDocumentTab().getInlineObjects().get(objectId).getInlineObjectProperties().getEmbeddedObject();
                var contentUri = eo.getImageProperties() == null ? null : eo.getImageProperties().getContentUri();
                if (contentUri == null) Out.die("image resize: Google gave no content URI for " + objectId + " to re-fetch its picture from");
                tabId = Tabs.id(where.tab());
                noteAt = where.index();
                final var tid = tabId;
                final var idx = where.index();
                var sz = sizeOf(width, height);
                image = contentUri;
                build.add(url -> new Request().setInsertInlineImage(new InsertInlineImageRequest().setUri(url)
                        .setLocation(new Location().setIndex(idx).setTabId(tid)).setObjectSize(sz)));
                build.add(url -> new Request().setDeleteContentRange(new DeleteContentRangeRequest()
                        .setRange(new Range().setTabId(tid).setStartIndex(idx + 1).setEndIndex(idx + 2))));
                Out.data("resize image " + objectId + " in " + Tabs.title(where.tab()) + " @" + idx + " to " + describe(sz)
                        + " (the same picture, reinserted at the new size — Google has no request that resizes one in place, so the object gets a new id)");
            }
            case "crop" -> {
                var objectId = Args.req(a, "<objectId>");
                var cs = cropSpec(a);
                Args.noneLeft(a, "image crop");
                if (!cs.any()) Out.die("image crop: give at least one of --left/--right/--top/--bottom (fractions of the current picture, 0..1)");
                var where = find(all, objectId);
                if (where == null) Out.die("image crop: no image " + objectId + " (run `images <doc>`)");
                var eo = where.tab().getDocumentTab().getInlineObjects().get(objectId).getInlineObjectProperties().getEmbeddedObject();
                var contentUri = eo.getImageProperties() == null ? null : eo.getImageProperties().getContentUri();
                if (contentUri == null) Out.die("image crop: Google gave no content URI for " + objectId + " to fetch its current picture from");
                tabId = Tabs.id(where.tab());
                noteAt = where.index();
                final var tid = tabId;
                final var idx = where.index();
                cropFrom = contentUri;
                crop = cs;
                image = "(a cropped copy of the current picture)";
                var oldW = eo.getSize() == null ? null : eo.getSize().getWidth();
                var sz = sizeOf(width, height);
                if (sz == null && oldW != null) sz = new Size().setWidth(new Dimension().setMagnitude(oldW.getMagnitude()).setUnit(oldW.getUnit()));
                final var fsz = sz;
                build.add(url -> {
                    var r = new InsertInlineImageRequest().setUri(url).setLocation(new Location().setIndex(idx).setTabId(tid));
                    if (fsz != null) r.setObjectSize(fsz);
                    return new Request().setInsertInlineImage(r);
                });
                build.add(url -> new Request().setDeleteContentRange(new DeleteContentRangeRequest()
                        .setRange(new Range().setTabId(tid).setStartIndex(idx + 1).setEndIndex(idx + 2))));
                Out.data("crop image " + objectId + " in " + Tabs.title(where.tab()) + " @" + idx + ": remove " + cs
                        + (sz == null ? ", auto-sized" : ", " + describe(sz))
                        + " (a locally cropped copy, reinserted — Google has no request that crops one in place, so the object gets a new id)");
            }
            default -> { Out.die("unknown image sub-verb: " + sub + " (insert | replace | resize | crop)"); return; }
        }
        if (image == null) Out.die("--image <file|https-url> is required");
        if (note != null) Out.data("note " + (suggest ? "(reply on its suggestion)" : "(comment on the image)") + ": " + AiSign.sign(note.strip()));
        var wc = new WriteControl().setRequiredRevisionId(doc.getRevisionId());
        if (suggest) Preview.suggestMode(wc);
        if (!apply) {
            if (showRequests) Out.data(com.google.api.client.json.gson.GsonFactory.getDefaultInstance().toPrettyString(build.stream().map(f -> f.apply("<image url>")).toList()));
            var extra = cropFrom != null ? " On --apply the current picture is downloaded, cropped locally, then uploaded to Drive, shared by link for the insert, then trashed."
                    : image.startsWith("https://") || image.startsWith("http://") ? ""
                    : " On --apply the file is uploaded to Drive, shared by link for the insert, then trashed.";
            Out.data("DRY RUN — nothing sent." + extra + " Re-run with --apply.");
            return;
        }
        Path cropTemp = null;
        if (cropFrom != null) {
            var bytes = Images.bytesOf(cropFrom);
            var cropped = cropPixels(bytes, crop);
            cropTemp = Files.createTempFile("gdocscli-crop-", ".png");
            Files.write(cropTemp, cropped.bytes());
            image = cropTemp.toString();
            Out.info("cropped to " + cropped.width() + "x" + cropped.height() + " px");
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
            Out.success("image " + (switch (sub) { case "insert" -> "inserted"; case "replace" -> "replaced"; case "resize" -> "resized"; default -> "cropped"; })
                    + (made.isEmpty() ? "" : ", object " + String.join(", ", made)) + (suggest ? " as a suggestion" : ""));
            var created = new ArrayList<String>();
            if (suggest) {
                for (var ids : Preview.createdSuggestionIds(res)) for (var s : ids) if (!created.contains(s)) created.add(s);
                Out.data("suggestion(s): " + (created.isEmpty() ? "NONE — Google did not record this as a suggestion" : String.join(", ", created)));
            }
            if (layout != null && !made.isEmpty()) verifyLayout(id, tabId, made.get(0), layout, suggest);
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
            if (cropTemp != null) try { Files.deleteIfExists(cropTemp); } catch (Exception ignored) {}
        }
    }

    private static void verifyLayout(String id, String tabId, String objectId, ImageCaption layout, boolean suggest) throws Exception {
        if (suggest) DocRef.suggestions = "accepted";
        var wants = layout.wants(tabId, objectId);
        var diffs = AlignCheck.verify(wants, DocRef.get(id));
        if (diffs.isEmpty()) { Out.data("VERIFIED: " + wants.size() + " paragraph(s) read back as " + layout.describe()); return; }
        Out.data("VERIFY FAILED: " + diffs.size() + " of " + wants.size() + " check(s) differ:");
        diffs.forEach(d -> Out.data("  " + d));
        System.exit(2);
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

    /** null when neither is given, meaning: don't set objectSize, let Google auto-size. Giving only
     * one dimension leaves the other unset too — Google preserves the picture's own aspect ratio for it. */
    private static Size sizeOf(Double width, Double height) {
        if (width == null && height == null) return null;
        var s = new Size();
        if (width != null) s.setWidth(new Dimension().setMagnitude(width * 72).setUnit("PT"));
        if (height != null) s.setHeight(new Dimension().setMagnitude(height * 72).setUnit("PT"));
        return s;
    }

    private static String describe(Size s) {
        if (s == null) return "auto-sized";
        var parts = new ArrayList<String>();
        if (s.getWidth() != null) parts.add(String.format("%.2f in wide", s.getWidth().getMagnitude() / 72));
        if (s.getHeight() != null) parts.add(String.format("%.2f in tall", s.getHeight().getMagnitude() / 72));
        return String.join(", ", parts);
    }

    /** Fractions (0..1) of the CURRENT picture's own width/height cut away from each edge — not
     * Google's CropProperties offsets (which are relative to the ORIGINAL, pre-crop image and are
     * read-only via this API anyway); this crops whatever is visible right now, further. */
    record CropSpec(double left, double right, double top, double bottom) {
        boolean any() { return left != 0 || right != 0 || top != 0 || bottom != 0; }
        @Override public String toString() {
            return String.format("left %.0f%%, right %.0f%%, top %.0f%%, bottom %.0f%%", left * 100, right * 100, top * 100, bottom * 100);
        }
    }

    private static CropSpec cropSpec(List<String> a) {
        var left = frac(a, "--left"); var right = frac(a, "--right");
        var top = frac(a, "--top"); var bottom = frac(a, "--bottom");
        if (left + right >= 1) Out.die("image crop: --left + --right must add to under 1 (leaves nothing)");
        if (top + bottom >= 1) Out.die("image crop: --top + --bottom must add to under 1 (leaves nothing)");
        return new CropSpec(left, right, top, bottom);
    }

    private static double frac(List<String> a, String flag) {
        var v = Args.dblVal(a, flag);
        if (v == null) return 0;
        if (v < 0 || v >= 1) Out.die(flag + " must be a fraction between 0 and 1 (not " + v + ")");
        return v;
    }

    record Cropped(byte[] bytes, int width, int height) {}

    private static Cropped cropPixels(byte[] src, CropSpec cs) throws Exception {
        var img = ImageIO.read(new ByteArrayInputStream(src));
        if (img == null) Out.die("crop: could not decode the downloaded picture");
        int w = img.getWidth(), h = img.getHeight();
        int lx = (int) Math.round(cs.left() * w), rx = (int) Math.round(cs.right() * w);
        int ty = (int) Math.round(cs.top() * h), by = (int) Math.round(cs.bottom() * h);
        int nw = w - lx - rx, nh = h - ty - by;
        if (nw <= 0 || nh <= 0) Out.die("crop: nothing would be left of a " + w + "x" + h + " picture (" + cs + ")");
        BufferedImage sub = img.getSubimage(lx, ty, nw, nh);
        var bos = new ByteArrayOutputStream();
        ImageIO.write(sub, "png", bos);
        return new Cropped(bos.toByteArray(), nw, nh);
    }
}
