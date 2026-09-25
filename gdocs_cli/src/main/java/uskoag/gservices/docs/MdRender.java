package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.List;
import java.util.*;

import static uskoag.gservices.docs.Styles.*;

/**
 * One tab as markdown. Blocks are separated by a blank line, except consecutive list items, which
 * stay together so they read as one list. Footnotes are collected as they are referenced and written
 * at the end of the tab, numbered in reading order.
 */
final class MdRender {

    private final DocumentTab tab;
    private final Map<String, Integer> footnotes = new LinkedHashMap<>();
    private final MdInline inline;
    private final List<String> out = new ArrayList<>();
    private boolean lastWasItem;

    MdRender(DocumentTab tab) {
        this.tab = tab;
        this.inline = new MdInline(tab, footnotes);
    }

    String render() {
        if (tab == null || tab.getBody() == null) return "";
        blocks(tab.getBody().getContent());
        footnotes();
        return String.join("\n", out).strip() + "\n";
    }

    private void blocks(List<StructuralElement> els) {
        if (els == null) return;
        for (var e : els) {
            if (e.getParagraph() != null) paragraph(e.getParagraph());
            else if (e.getTable() != null) table(e.getTable());
            else if (e.getTableOfContents() != null) block("<!-- table of contents -->");
        }
    }

    private void paragraph(Paragraph p) {
        var text = inline.line(p.getElements());
        if (p.getBullet() != null) {
            if (!lastWasItem) out.add("");
            out.add("  ".repeat(level(p)) + (ordered(p, tab) ? "1. " : "- ") + text);
            lastWasItem = true;
            return;
        }
        if (text.isBlank()) return;
        var h = heading(p);
        block(h > 0 ? "#".repeat(h) + " " + text.replace("<br>", " ") : text);
    }

    private void block(String s) {
        out.add("");
        out.add(s);
        lastWasItem = false;
    }

    /** A cell holding several paragraphs keeps them apart with <br>; a literal pipe would end the cell. */
    private void table(Table t) {
        var rows = new ArrayList<String>();
        var width = 0;
        for (var r : t.getTableRows()) {
            var cells = new ArrayList<String>();
            for (var c : r.getTableCells()) cells.add(cell(c.getContent()));
            width = Math.max(width, cells.size());
            rows.add("| " + String.join(" | ", cells) + " |");
        }
        if (rows.isEmpty()) return;
        out.add("");
        out.add(rows.get(0));
        out.add("|" + " --- |".repeat(width));
        out.addAll(rows.subList(1, rows.size()));
        lastWasItem = false;
    }

    private String cell(List<StructuralElement> content) {
        var parts = new ArrayList<String>();
        for (var e : content == null ? List.<StructuralElement>of() : content) {
            if (e.getParagraph() != null) {
                var s = inline.line(e.getParagraph().getElements());
                if (!s.isBlank()) parts.add((e.getParagraph().getBullet() != null ? "• " : "") + s);
            } else if (e.getTable() != null) parts.add("[nested table]");
        }
        return String.join("<br>", parts).replace("|", "\\|");
    }

    private void footnotes() {
        if (footnotes.isEmpty() || tab.getFootnotes() == null) return;
        out.add("");
        for (var i = 0; i < footnotes.size(); i++) {
            var id = new ArrayList<>(footnotes.keySet()).get(i);
            var fn = tab.getFootnotes().get(id);
            var parts = new ArrayList<String>();
            if (fn != null && fn.getContent() != null)
                for (var e : fn.getContent()) if (e.getParagraph() != null) parts.add(inline.line(e.getParagraph().getElements()).strip());
            out.add("[^" + (i + 1) + "]: " + String.join(" ", parts).strip());
        }
    }
}
