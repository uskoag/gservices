package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.List;
import java.util.*;
import org.jsoup.nodes.Element;

import static uskoag.gservices.docs.Styles.*;

/**
 * One tab as simple html: headings, paragraphs, nested ul/ol, tables, and inline b/i/u/s/a plus a
 * span for colour, highlight, size and font. That is the whole vocabulary, on purpose — it is the
 * set a later edit verb will accept back. Inline suggestions become ins/del.
 */
final class HtmlRender {

    /** --flat-lists: list items as paragraphs with the document's own label typed in and its hanging indent, for email. */
    static boolean flatLists;

    private record Open(Element list, int level, String id, boolean ordered) {}

    private final DocumentTab tab;
    private final ListLabel labels;
    private final Map<String, Integer> footnotes = new LinkedHashMap<>();
    private final Deque<Open> open = new ArrayDeque<>();

    HtmlRender(DocumentTab tab) { this.tab = tab; this.labels = new ListLabel(tab); }

    void into(Element section) {
        if (tab == null || tab.getBody() == null) return;
        blocks(section, tab.getBody().getContent());
        footnotes(section);
    }

    private void blocks(Element parent, List<StructuralElement> els) {
        if (els == null) return;
        for (var e : els) {
            if (e.getParagraph() != null) paragraph(parent, e.getParagraph());
            else {
                open.clear();
                if (e.getTable() != null) table(parent, e.getTable());
            }
        }
        open.clear();
    }

    private void paragraph(Element parent, Paragraph p) {
        if (p.getBullet() != null) { item(parent, p); return; }
        open.clear();
        var h = heading(p);
        var el = new Element(h > 0 ? "h" + h : "p");
        inline(el, p.getElements());
        if (el.text().isBlank() && el.select("img").isEmpty()) return;
        parent.appendChild(el);
    }

    /**
     * Opens or closes nested lists until the stack matches this item: its level, its list and its kind, so a bullet
     * list right under a numbered item is a list of its own. Numbers are the document's: a list that a plain paragraph
     * interrupted reopens with {@code start}, and every numbered item carries its label (e.g. "(3)") in data-label.
     */
    private void item(Element parent, Paragraph p) {
        var lvl = level(p);
        var id = p.getBullet().getListId();
        var ord = ordered(p, tab);
        var label = labels.next(p);
        if (flatLists) { flat(parent, p, lvl, ord ? label : labels.symbol(p)); return; }
        while (!open.isEmpty() && (open.peek().level() > lvl || open.peek().level() == lvl && (!open.peek().id().equals(id) || open.peek().ordered() != ord))) open.pop();
        if (open.isEmpty() || open.peek().level() < lvl) {
            var list = new Element(ord ? "ol" : "ul");
            if (ord && labels.count(p) != 1) list.attr("start", "" + labels.count(p));
            var type = ord ? olType(labels.nesting(p)) : null;
            if (type != null) list.attr("type", type);
            var host = open.isEmpty() ? parent : Objects.requireNonNullElse(open.peek().list().children().last(), open.peek().list());
            host.appendChild(list);
            open.push(new Open(list, lvl, id, ord));
        }
        var li = open.peek().list().appendElement("li");
        if (ord) li.attr("data-label", label);
        inline(li, p.getElements());
    }

    private static String olType(NestingLevel nl) {
        return switch (nl == null || nl.getGlyphType() == null ? "" : nl.getGlyphType()) {
            case "ALPHA" -> "a";
            case "UPPER_ALPHA" -> "A";
            case "ROMAN" -> "i";
            case "UPPER_ROMAN" -> "I";
            default -> null;
        };
    }

    /** One list item as a paragraph: the label in a box as wide as the hanging indent, the text after it. */
    private void flat(Element parent, Paragraph p, int lvl, String label) {
        var nl = labels.nesting(p);
        var ps = p.getParagraphStyle();
        var start = pt(ps != null && ps.getIndentStart() != null ? ps.getIndentStart() : nl == null ? null : nl.getIndentStart());
        var first = pt(ps != null && ps.getIndentFirstLine() != null ? ps.getIndentFirstLine() : nl == null ? null : nl.getIndentFirstLine());
        var el = parent.appendElement("p").attr("data-list-level", "" + lvl).attr("style", "margin-left:" + start + "pt;text-indent:" + (first - start) + "pt");
        var bold = p.getBullet().getTextStyle() != null && Boolean.TRUE.equals(p.getBullet().getTextStyle().getBold());
        el.appendElement("span").attr("style", "display:inline-block;text-indent:0;min-width:" + (start - first) + "pt" + (bold ? ";font-weight:bold" : "")).text(label);
        inline(el, p.getElements());
    }

    private static double pt(Dimension d) { return d == null || d.getMagnitude() == null ? 0 : d.getMagnitude(); }

    private void table(Element parent, Table t) {
        var table = parent.appendElement("table");
        for (var r : t.getTableRows()) {
            var tr = table.appendElement("tr");
            for (var c : r.getTableCells()) {
                var td = tr.appendElement("td");
                var span = c.getTableCellStyle();
                if (span != null && span.getColumnSpan() != null && span.getColumnSpan() > 1) td.attr("colspan", "" + span.getColumnSpan());
                if (span != null && span.getRowSpan() != null && span.getRowSpan() > 1) td.attr("rowspan", "" + span.getRowSpan());
                blocks(td, c.getContent());
            }
        }
    }

    private void inline(Element el, List<ParagraphElement> els) {
        for (var e : els == null ? List.<ParagraphElement>of() : els) {
            if (e.getTextRun() != null) HtmlInline.run(el, e.getTextRun());
            else if (e.getInlineObjectElement() != null) {
                var id = e.getInlineObjectElement().getInlineObjectId();
                var uri = imageUri(tab, id);
                if (uri != null) el.appendElement("img").attr("src", uri).attr("alt", imageAlt(tab, id));
            } else if (e.getFootnoteReference() != null) {
                var n = footnotes.computeIfAbsent(e.getFootnoteReference().getFootnoteId(), k -> footnotes.size() + 1);
                el.appendElement("sup").appendElement("a").attr("href", "#fn-" + n).text("" + n);
            } else if (e.getPerson() != null && e.getPerson().getPersonProperties() != null) {
                var pp = e.getPerson().getPersonProperties();
                el.appendElement("a").attr("href", "mailto:" + pp.getEmail()).text(Objects.requireNonNullElse(pp.getName(), pp.getEmail()));
            } else if (e.getRichLink() != null && e.getRichLink().getRichLinkProperties() != null) {
                var rp = e.getRichLink().getRichLinkProperties();
                el.appendElement("a").attr("href", rp.getUri()).text(Objects.requireNonNullElse(rp.getTitle(), rp.getUri()));
            } else if (e.getHorizontalRule() != null) el.appendElement("hr");
        }
    }

    private void footnotes(Element section) {
        if (footnotes.isEmpty() || tab.getFootnotes() == null) return;
        var ol = section.appendElement("ol").addClass("footnotes");
        for (var id : new ArrayList<>(footnotes.keySet())) {
            var li = ol.appendElement("li").attr("id", "fn-" + footnotes.get(id));
            var fn = tab.getFootnotes().get(id);
            if (fn != null && fn.getContent() != null)
                for (var e : fn.getContent()) if (e.getParagraph() != null) inline(li, e.getParagraph().getElements());
        }
    }
}
