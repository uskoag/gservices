package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.StructuralElement;
import java.util.ArrayList;
import java.util.List;

/** What a delete range takes with it, counted for the dry run: paragraphs, whole tables, pictures, page breaks. */
final class DeleteSpan {

    int paras, cellParas, tables, images, breaks, sections;
    final List<String> lines = new ArrayList<>();

    static DeleteSpan of(List<StructuralElement> els) {
        var d = new DeleteSpan();
        for (var e : els) d.add(e, true);
        return d;
    }

    private void add(StructuralElement e, boolean top) {
        if (e.getParagraph() != null) {
            if (top) { paras++; lines.add("  - \"" + Para.of(List.of(e)).get(0).preview() + "\""); } else cellParas++;
            for (var pe : e.getParagraph().getElements()) {
                if (pe.getInlineObjectElement() != null) images++;
                if (pe.getPageBreak() != null) breaks++;
            }
            if (e.getParagraph().getPositionedObjectIds() != null) images += e.getParagraph().getPositionedObjectIds().size();
        } else if (e.getTable() != null) {
            var t = e.getTable();
            tables++;
            lines.add("  - [table " + t.getRows() + "x" + t.getColumns() + " @" + Para.idx(e.getStartIndex()) + "-" + Para.idx(e.getEndIndex()) + "]");
            for (var r : t.getTableRows()) for (var c : r.getTableCells()) for (var inner : c.getContent()) add(inner, false);
        } else if (e.getSectionBreak() != null) sections++;
    }

    String summary() {
        return paras + " paragraph(s)" + (tables == 0 ? "" : ", " + tables + " table(s) holding " + cellParas + " paragraph(s)")
                + ", " + images + " picture(s)" + (breaks == 0 ? "" : ", " + breaks + " page break(s)") + (sections == 0 ? "" : ", " + sections + " section break(s)");
    }

    /** All of it for a short list, otherwise the first and last twenty: a 107-page annexure is a thousand lines. */
    List<String> shown() {
        if (lines.size() <= 40) return lines;
        var out = new ArrayList<>(lines.subList(0, 20));
        out.add("  … " + (lines.size() - 40) + " more …");
        out.addAll(lines.subList(lines.size() - 20, lines.size()));
        return out;
    }
}
