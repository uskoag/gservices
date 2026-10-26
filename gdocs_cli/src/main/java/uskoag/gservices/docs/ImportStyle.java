package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Stage C: paragraph styles, table cell styles and widths, then text styles last (bullets and paragraph styles
 * set after text styles would wipe them). Positions come from a fresh read of the destination, matched to the
 * source paragraph by paragraph in document order.
 */
final class ImportStyle {

    private static final String[] EDGES = {"borderTop", "borderBottom", "borderLeft", "borderRight"};

    private ImportStyle() {}

    static List<Request> make(ImportPlan plan, ImportDest d, List<Para> dp) {
        var out = new ArrayList<Request>();
        for (var i = 0; i < dp.size(); i++) {
            var ps = plan.flat.get(i).cell ? Eff.withoutPageBreak(plan.flat.get(i).style) : plan.flat.get(i).style;
            out.add(new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest().setRange(d.range(dp.get(i).start(), dp.get(i).end()))
                    .setParagraphStyle(ps).setFields(Eff.mask(ps))));
        }
        tables(plan, d, out);
        return out;
    }

    /** Last of all: setting a link after a text style would give the text the link look again. */
    static List<Request> texts(ImportPlan plan, ImportDest d, List<Para> dp) {
        var out = new ArrayList<Request>();
        for (var i = 0; i < dp.size(); i++) text(plan, d, dp, i, out);
        return out;
    }

    private static void text(ImportPlan plan, ImportDest d, List<Para> dp, int i, List<Request> out) {
        var sp = plan.flat.get(i);
        var at = dp.get(i).start();
        for (var r : sp.runs) {
            var st = r.style().clone();
            if (ImportLinks.toHeading(st)) st = Eff.without(st, "link");
            if (st.isEmpty()) continue;
            out.add(new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setRange(d.range(at + r.from(), at + r.to())).setTextStyle(st).setFields(Eff.mask(st))));
        }
    }

    private static void tables(ImportPlan plan, ImportDest d, List<Request> out) {
        var width = destWidth(d);
        for (var pr : ImportTables.of(plan.tables, d.tables())) {
            var st = pr.src();
            var start = Para.idx(pr.dest().getStartIndex());
            var at = new Location().setTabId(d.tabId).setIndex(start);
            var total = st.widths.stream().mapToDouble(Double::doubleValue).sum();
            var scale = pr.depth() == 0 && width > 0 && total > width ? width / total : 1;
            for (var c = 0; c < st.widths.size(); c++)
                out.add(new Request().setUpdateTableColumnProperties(new UpdateTableColumnPropertiesRequest().setTableStartLocation(at).setColumnIndices(List.of(c))
                        .setTableColumnProperties(new TableColumnProperties().setWidthType("FIXED_WIDTH").setWidth(new Dimension().setMagnitude(st.widths.get(c) * scale).setUnit("PT"))).setFields("width,widthType")));
            for (var r = 0; r < st.rows; r++) if (st.minHeights.get(r) > 0)
                out.add(new Request().setUpdateTableRowStyle(new UpdateTableRowStyleRequest().setTableStartLocation(at).setRowIndices(List.of(r))
                        .setTableRowStyle(new TableRowStyle().setMinRowHeight(new Dimension().setMagnitude(st.minHeights.get(r)).setUnit("PT"))).setFields("minRowHeight")));
            for (var cell : st.cells) {
                var cs = cellStyle(cell.style, st.bordered);
                if (cs.isEmpty()) continue;
                var range = new TableRange().setRowSpan(1).setColumnSpan(1).setTableCellLocation(new TableCellLocation().setTableStartLocation(at).setRowIndex(cell.row).setColumnIndex(cell.col));
                out.add(new Request().setUpdateTableCellStyle(new UpdateTableCellStyleRequest().setTableRange(range).setTableCellStyle(cs).setFields(Eff.mask(cs))));
            }
        }
    }

    private static TableCellStyle cellStyle(TableCellStyle src, boolean bordered) {
        var cs = new TableCellStyle();
        if (src != null) for (var k : src.keySet()) if (!k.equals("columnSpan") && !k.equals("rowSpan")) cs.set(k, src.get(k));
        if (!bordered) for (var e : EDGES) if (cs.get(e) == null) cs.set(e, new TableCellBorder().setWidth(new Dimension().setMagnitude(0d).setUnit("PT")).setDashStyle("SOLID"));
        for (var e : EDGES) if (cs.get(e) instanceof TableCellBorder x) {
            var b = x.clone();
            if (b.getColor() == null || b.getColor().getColor() == null) b.setColor(new OptionalColor().setColor(new Color().setRgbColor(new RgbColor())));
            cs.set(e, b);
        }
        return cs;
    }

    private static double destWidth(ImportDest d) {
        var ds = d.tab.getDocumentTab().getDocumentStyle();
        if (ds == null || ds.getPageSize() == null) return 0;
        return ImportPlan.pt(ds.getPageSize().getWidth()) - ImportPlan.pt(ds.getMarginLeft()) - ImportPlan.pt(ds.getMarginRight());
    }
}
