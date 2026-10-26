package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;

/** The converted document read once into the units the replay needs, plus a count of everything that cannot be carried. */
final class ImportPlan {

    final List<Unit> units = new ArrayList<>();
    final List<SrcPara> flat = new ArrayList<>(), all = new ArrayList<>();
    final List<SrcTable> tables = new ArrayList<>();
    final Map<String, Integer> notes = new TreeMap<>(), headingFlat = new HashMap<>();
    final Map<String, EmbeddedObject> images = new LinkedHashMap<>();
    final Map<String, List<SrcPara>> footnotes = new LinkedHashMap<>();
    double contentWidth;
    private final Eff eff;
    private final DocumentTab src;
    private int seq;

    ImportPlan(DocumentTab src) {
        this.src = src;
        eff = new Eff(src);
        var ds = src.getDocumentStyle();
        if (ds != null && ds.getPageSize() != null) contentWidth = pt(ds.getPageSize().getWidth()) - pt(ds.getMarginLeft()) - pt(ds.getMarginRight());
        for (var el : src.getBody().getContent()) {
            if (el.getParagraph() != null) add(para(el.getParagraph(), false));
            else if (el.getTable() != null) add(table(el.getTable()));
            else if (el.getTableOfContents() != null) {
                note("table of contents rebuilt as plain paragraphs (entries link to the copied headings)");
                for (var t : el.getTableOfContents().getContent()) if (t.getParagraph() != null) add(para(t.getParagraph(), true));
            } else if (el.getSectionBreak() == null) note("unsupported block element");
        }
        finish();
    }

    static double pt(Dimension d) { return d == null || d.getMagnitude() == null ? 0 : d.getMagnitude(); }

    void note(String s) { notes.merge(s, 1, Integer::sum); }

    private void add(Unit u) { add(units, u); }

    private static void add(List<Unit> to, Unit u) {
        if (u instanceof SrcTable && (to.isEmpty() || to.getLast() instanceof SrcTable)) to.add(new SrcPara());
        to.add(u);
    }

    private void finish() {
        for (var i = 0; i < units.size(); i++) {
            if (units.get(i) instanceof SrcPara p && p.breakOnly && (i + 1 >= units.size() || units.get(i + 1) instanceof SrcTable)) {
                p.breakOnly = false;
                p.objs.clear();
                note("page break before a table or at the end");
            }
        }
        for (var u : units) if (u instanceof SrcTable t) tables.add(t);
        index(units);
        for (var i = 0; i < flat.size(); i++) if (flat.get(i).headingId != null) headingFlat.put(flat.get(i).headingId, i);
    }

    private void index(List<Unit> us) {
        for (var u : us) {
            if (u instanceof SrcPara p) {
                p.seq = seq++;
                p.beforeFlat = flat.size();
                all.add(p);
                if (!p.breakOnly) flat.add(p);
            } else if (u instanceof SrcTable t) for (var c : t.cells) index(c.units);
        }
    }

    private SrcPara para(Paragraph p, boolean toc) {
        var sp = new SrcPara();
        var ps = p.getParagraphStyle();
        var type = ps == null || ps.getNamedStyleType() == null ? "NORMAL_TEXT" : ps.getNamedStyleType();
        sp.style = eff.para(type, ps);
        if (ps != null && !toc) sp.headingId = ps.getHeadingId();
        if (p.getBullet() != null) { sp.listId = p.getBullet().getListId(); sp.level = p.getBullet().getNestingLevel() == null ? 0 : p.getBullet().getNestingLevel(); sp.mark = ImportLabel.style(src, p); }
        var sb = new StringBuilder();
        for (var e : p.getElements()) {
            if (e.getTextRun() != null) {
                var from = sb.length();
                sb.append(e.getTextRun().getContent());
                var st = eff.text(type, e.getTextRun().getTextStyle());
                if (st.getLink() != null && st.getLink().getUrl() == null && st.getLink().getHeadingId() == null && st.getLink().getHeading() == null) { st = Eff.without(st, "link"); note("link to a bookmark inside the source (dropped)"); }
                if (!sp.runs.isEmpty() && sp.runs.getLast().to() == from && sp.runs.getLast().style().equals(st)) sp.runs.add(new SrcRun(sp.runs.removeLast().from(), sb.length(), st));
                else sp.runs.add(new SrcRun(from, sb.length(), st));
            } else if (e.getInlineObjectElement() != null) {
                var id = e.getInlineObjectElement().getInlineObjectId();
                var io = src.getInlineObjects() == null ? null : src.getInlineObjects().get(id);
                var eo = io == null ? null : io.getInlineObjectProperties().getEmbeddedObject();
                if (eo == null || eo.getImageProperties() == null) note("embedded object that is not a picture (drawing or chart, not carried)");
                else { images.put(id, eo); sp.objs.add(new SrcObj(sb.length(), "image", id, 0)); }
            } else if (e.getPageBreak() != null) sp.objs.add(new SrcObj(sb.length(), "pagebreak", null, 0));
            else if (e.getFootnoteReference() != null) {
                var fid = e.getFootnoteReference().getFootnoteId();
                sp.objs.add(new SrcObj(sb.length(), "footnote", fid, 0));
                if (!footnotes.containsKey(fid)) footnote(fid);
            }
            else note("inline element of an unsupported kind (not carried)");
        }
        var t = sb.toString();
        sp.text = t.endsWith("\n") ? t.substring(0, t.length() - 1) : t;
        sp.runs.replaceAll(r -> new SrcRun(r.from(), Math.min(r.to(), sp.text.length() + 1), r.style()));
        sp.runs.removeIf(r -> r.from() >= r.to());
        sp.breakOnly = sp.text.isEmpty() && sp.objs.size() == 1 && "pagebreak".equals(sp.objs.get(0).kind());
        return sp;
    }

    private void footnote(String id) {
        var body = new ArrayList<SrcPara>();
        footnotes.put(id, body);
        var fn = src.getFootnotes() == null ? null : src.getFootnotes().get(id);
        if (fn != null) for (var e : fn.getContent()) if (e.getParagraph() != null) body.add(para(e.getParagraph(), true));
    }

    private SrcTable table(Table t) {
        var st = new SrcTable();
        st.rows = t.getRows();
        st.cols = t.getColumns();
        if (t.getTableStyle() != null && t.getTableStyle().getTableColumnProperties() != null)
            for (var c : t.getTableStyle().getTableColumnProperties()) st.widths.add(pt(c.getWidth()));
        for (var r : t.getTableRows()) {
            st.minHeights.add(r.getTableRowStyle() == null ? 0d : pt(r.getTableRowStyle().getMinRowHeight()));
            for (var c : r.getTableCells()) {
                var sc = new SrcCell();
                sc.row = st.cells.size() / st.cols;
                sc.col = st.cells.size() % st.cols;
                sc.style = c.getTableCellStyle();
                if (sc.style != null) { sc.rowSpan = sc.style.getRowSpan() == null ? 1 : sc.style.getRowSpan(); sc.colSpan = sc.style.getColumnSpan() == null ? 1 : sc.style.getColumnSpan(); }
                for (var e : c.getContent()) {
                    if (e.getTable() != null) { add(sc.units, table(e.getTable())); continue; }
                    if (e.getParagraph() == null) { note("unsupported block inside a cell (not carried)"); continue; }
                    var p = para(e.getParagraph(), false);
                    p.cell = true;
                    if (p.breakOnly) { p.breakOnly = false; p.objs.clear(); note("page break inside a table cell"); }
                    sc.units.add(p);
                }
                if (sc.units.isEmpty()) sc.units.add(new SrcPara());
                st.cells.add(sc);
            }
        }
        return st;
    }

    /** Applies the per-table border flags, tables in document order with nested ones straight after their parent. */
    void borders(List<Boolean> flags) {
        var all = new ArrayList<SrcTable>();
        collect(tables, all);
        if (flags.size() != all.size()) { if (!flags.isEmpty()) note("table borders: the docx export lists " + flags.size() + " tables, the document has " + all.size() + " (Docs default borders used)"); return; }
        for (var i = 0; i < all.size(); i++) all.get(i).bordered = flags.get(i);
    }

    private static void collect(List<SrcTable> ts, List<SrcTable> out) {
        for (var t : ts) { out.add(t); for (var c : t.cells) collect(c.units.stream().filter(u -> u instanceof SrcTable).map(u -> (SrcTable) u).toList(), out); }
    }

    List<String> summary() {
        var out = new ArrayList<String>();
        var runs = flat.stream().mapToInt(p -> p.runs.size()).sum();
        var objs = all.stream().flatMap(p -> p.objs.stream()).toList();
        out.add(all.size() + " paragraph(s) (" + flat.stream().filter(SrcPara::listed).count() + " in lists, "
                + flat.stream().filter(p -> p.headingId != null).count() + " headings), " + runs + " styled run(s), " + tables.size() + " table(s)");
        for (var t : tables) out.add("  table " + t.rows + "x" + t.cols + ", " + t.cells.stream().filter(c -> c.colSpan > 1 || c.rowSpan > 1).count() + " merged cell(s)");
        out.add(objs.stream().filter(o -> o.kind().equals("image")).count() + " picture(s), " + footnotes.size() + " footnote(s), " + objs.stream().filter(o -> o.kind().equals("pagebreak")).count() + " page break(s)");
        notes.forEach((k, v) -> out.add("  not carried: " + k + (v > 1 ? " ×" + v : "")));
        return out;
    }
}
