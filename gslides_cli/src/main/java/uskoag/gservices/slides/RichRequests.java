package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.CreateParagraphBulletsRequest;
import com.google.api.services.slides.v1.model.DeleteTextRequest;
import com.google.api.services.slides.v1.model.Dimension;
import com.google.api.services.slides.v1.model.InsertTextRequest;
import com.google.api.services.slides.v1.model.Link;
import com.google.api.services.slides.v1.model.ParagraphStyle;
import com.google.api.services.slides.v1.model.Range;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.TextStyle;
import com.google.api.services.slides.v1.model.UpdateParagraphStyleRequest;
import com.google.api.services.slides.v1.model.UpdateTextStyleRequest;
import java.util.ArrayList;
import java.util.List;

public final class RichRequests {

    private RichRequests() {}

    static Range range(int start, int end) {
        return new Range().setType("FIXED_RANGE").setStartIndex(start).setEndIndex(end);
    }

    static Request deleteAll(String objectId) {
        return new Request().setDeleteText(new DeleteTextRequest()
                .setObjectId(objectId).setTextRange(new Range().setType("ALL")));
    }

    static Request insert(String objectId, int at, String text) {
        return new Request().setInsertText(new InsertTextRequest()
                .setObjectId(objectId).setInsertionIndex(at).setText(text));
    }

    static Request deleteRange(String objectId, int start, int end) {
        return new Request().setDeleteText(new DeleteTextRequest()
                .setObjectId(objectId).setTextRange(range(start, end)));
    }

    /**
     * Replace an existing element's text and re-apply styling.
     *
     * Two gotchas are handled here rather than left to callers. Text inserted after a
     * delete inherits the leftover empty paragraph's character style, so the four
     * toggles are explicitly cleared over the whole range before the spans go on top --
     * meaning only what the HTML marked bold ends up bold. Size, font and colour are
     * deliberately NOT cleared, because inheriting those from the placeholder or theme
     * is almost always what is wanted.
     */
    static List<Request> setText(String objectId, RichText rt, int currentLength) {
        var reqs = new ArrayList<Request>();
        // deleteText on an already-empty element is rejected: startIndex 0 must be < endIndex 0.
        if (currentLength > 0) reqs.add(deleteAll(objectId));
        if (rt.text().isEmpty()) return reqs;
        reqs.add(insert(objectId, 0, rt.text()));
        reqs.add(clearToggles(objectId, rt.text().length()));
        reqs.addAll(styling(objectId, rt));
        return reqs;
    }

    static List<Request> styling(String objectId, RichText rt) {
        var reqs = new ArrayList<Request>();
        for (var s : rt.spans()) {
            var r = textStyle(objectId, s);
            if (r != null) reqs.add(r);
        }
        for (var p : rt.paras()) {
            if (p.align() != null) reqs.add(align(objectId, p.start(), p.end(), p.align()));
            if (p.bullet() != null) reqs.add(bullets(objectId, p.start(), p.end(), p.bullet()));
        }
        return reqs;
    }

    private static Request clearToggles(String objectId, int len) {
        return new Request().setUpdateTextStyle(new UpdateTextStyleRequest()
                .setObjectId(objectId).setTextRange(range(0, len))
                .setStyle(new TextStyle().setBold(false).setItalic(false)
                        .setUnderline(false).setStrikethrough(false))
                .setFields("bold,italic,underline,strikethrough"));
    }

    static Request textStyle(String objectId, RichSpan s) {
        var st = new TextStyle();
        var fields = new ArrayList<String>();
        if (s.bold() != null) { st.setBold(s.bold()); fields.add("bold"); }
        if (s.italic() != null) { st.setItalic(s.italic()); fields.add("italic"); }
        if (s.underline() != null) { st.setUnderline(s.underline()); fields.add("underline"); }
        if (s.strike() != null) { st.setStrikethrough(s.strike()); fields.add("strikethrough"); }
        if (s.size() != null) {
            st.setFontSize(new Dimension().setMagnitude(s.size()).setUnit("PT"));
            fields.add("fontSize");
        }
        if (s.font() != null) { st.setFontFamily(s.font()); fields.add("fontFamily"); }
        if (s.color() != null) { st.setForegroundColor(Colors.optional(s.color())); fields.add("foregroundColor"); }
        if (s.link() != null) { st.setLink(new Link().setUrl(s.link())); fields.add("link"); }
        if (fields.isEmpty()) return null;
        return new Request().setUpdateTextStyle(new UpdateTextStyleRequest()
                .setObjectId(objectId).setTextRange(range(s.start(), s.end()))
                .setStyle(st).setFields(String.join(",", fields)));
    }

    static Request align(String objectId, int start, int end, String alignment) {
        return new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest()
                .setObjectId(objectId).setTextRange(range(start, end))
                .setStyle(new ParagraphStyle().setAlignment(alignment)).setFields("alignment"));
    }

    static Request bullets(String objectId, int start, int end, String preset) {
        return new Request().setCreateParagraphBullets(new CreateParagraphBulletsRequest()
                .setObjectId(objectId).setTextRange(range(start, end)).setBulletPreset(preset));
    }
}
