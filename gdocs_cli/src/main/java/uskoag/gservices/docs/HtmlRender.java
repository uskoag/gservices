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

    private final DocumentTab tab;
    private final Map<String, Integer> footnotes = new LinkedHashMap<>();
    private final Deque<Element> lists = new ArrayDeque<>();
    private final Deque<Integer> levels = new ArrayDeque<>();

    HtmlRender(DocumentTab tab) { this.tab = tab; }

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
                lists.clear(); levels.clear();
                if (e.getTable() != null) table(parent, e.getTable());
            }
        }
        lists.clear(); levels.clear();
    }

    private void paragraph(Element parent, Paragraph p) {
        if (p.getBullet() != null) { item(parent, p); return; }
        lists.clear(); levels.clear();
        var h = heading(p);
        var el = new Element(h > 0 ? "h" + h : "p");
        inline(el, p.getElements());
        if (el.text().isBlank() && el.select("img").isEmpty()) return;
        parent.appendChild(el);
    }

    /** Opens or closes nested lists until the stack depth matches this item's nesting level. */
    private void item(Element parent, Paragraph p) {
        var lvl = level(p);
        while (!levels.isEmpty() && levels.peek() > lvl) { lists.pop(); levels.pop(); }
        if (levels.isEmpty() || levels.peek() < lvl) {
            var list = new Element(ordered(p, tab) ? "ol" : "ul");
            var host = lists.isEmpty() ? parent : Objects.requireNonNullElse(lists.peek().children().last(), lists.peek());
            host.appendChild(list);
            lists.push(list); levels.push(lvl);
        }
        var li = lists.peek().appendElement("li");
        inline(li, p.getElements());
    }

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
