package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.TextContent;

public final class Els {

    private Els() {}

    static String typeOf(PageElement el) {
        if (el.getShape() != null) return "SHAPE/" + el.getShape().getShapeType();
        if (el.getImage() != null) return "IMAGE";
        if (el.getElementGroup() != null) return "GROUP";
        if (el.getLine() != null) return "LINE";
        if (el.getTable() != null) return "TABLE";
        if (el.getVideo() != null) return "VIDEO";
        if (el.getSheetsChart() != null) return "CHART";
        return "OTHER";
    }

    static TextContent textContent(PageElement el) {
        if (el.getShape() != null) return el.getShape().getText();
        return null;
    }

    static String textOf(TextContent tc) {
        if (tc == null || tc.getTextElements() == null) return "";
        var sb = new StringBuilder();
        for (var te : tc.getTextElements())
            if (te.getTextRun() != null && te.getTextRun().getContent() != null)
                sb.append(te.getTextRun().getContent());
        return sb.toString();
    }

    static String textOf(PageElement el) { return textOf(textContent(el)); }

    /** One-line preview for `describe`, which must stay compact to be worth calling. */
    static String preview(PageElement el, int max) {
        var t = textOf(el).strip();
        if (t.isEmpty()) {
            var url = imageUrl(el);
            return url == null ? "" : "<image>";
        }
        t = t.replace("\n", " / ").replaceAll("\\s+", " ");
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    static String imageUrl(PageElement el) {
        if (el.getImage() == null) return null;
        var u = el.getImage().getSourceUrl();
        return u != null ? u : el.getImage().getContentUrl();
    }
}
