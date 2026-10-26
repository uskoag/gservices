package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reads the destination back and compares it with the source, paragraph by paragraph: text, lists, tables, run styles. */
final class ImportVerify {

    private ImportVerify() {}

    /** Before styling: same number of paragraphs, same text in the same order. Anything else stops the import where it stands. */
    static void aligned(ImportPlan plan, List<Para> dp, ImportDest d, boolean tabs) {
        var bad = new ArrayList<String>();
        if (dp.size() != plan.flat.size()) bad.add(dp.size() + " paragraph(s) came back in " + d.where() + ", the source has " + plan.flat.size());
        for (var i = 0; i < Math.min(dp.size(), plan.flat.size()) && bad.size() < 6; i++) {
            var want = Para.norm(plan.flat.get(i).text);
            var got = dp.get(i).plain();
            if (!want.equals(got)) bad.add("paragraph " + (i + 1) + ": wrote \"" + clip(want) + "\" but read back \"" + clip(got) + "\"");
        }
        if (!bad.isEmpty()) throw new IllegalStateException("stage text: the structure read back differs from the source:\n  " + String.join("\n  ", bad)
                + "\n  the partial import sits at " + d.where() + "; nothing after this stage was applied");
    }

    static List<String> finish(ImportPlan plan, ImportDest d) {
        var bad = new ArrayList<String>();
        var dp = d.paras();
        var eff = new Eff(d.tab.getDocumentTab());
        if (dp.size() != plan.all.size()) { bad.add(dp.size() + " paragraph(s) in " + d.where() + ", expected " + plan.all.size()); return bad; }
        var flat = 0;
        var runs = 0;
        for (var i = 0; i < dp.size(); i++) {
            var sp = plan.all.get(i);
            var p = dp.get(i);
            var want = Para.norm(expected(sp));
            if (!want.equals(p.plain())) bad.add("paragraph " + (i + 1) + " text: expected \"" + clip(want) + "\" read back \"" + clip(p.plain()) + "\"");
            if (sp.listed() != (p.p().getBullet() != null)) bad.add("paragraph " + (i + 1) + ": list membership differs (\"" + clip(want) + "\")");
            else if (sp.listed() && Styles.level(p.p()) != sp.level) bad.add("paragraph " + (i + 1) + ": list level " + Styles.level(p.p()) + ", expected " + sp.level);
            if (!sp.breakOnly) { runs += styles(plan.flat.get(flat++), p, bad, eff); }
            if (bad.size() > 12) { bad.add("… more differences not listed"); return bad; }
        }
        var pairs = ImportTables.of(plan.tables, d.tables());
        for (var pr : pairs) {
            var st = pr.src();
            var t = pairs.indexOf(pr);
            for (var c : st.cells) if (c.colSpan > 1 || c.rowSpan > 1) {
                var cs = pr.dest().getTable().getTableRows().get(c.row).getTableCells().get(c.col).getTableCellStyle();
                if (cs == null || !Objects.equals(cs.getColumnSpan(), c.colSpan) || !Objects.equals(cs.getRowSpan(), c.rowSpan)) bad.add("table " + (t + 1) + " cell r" + (c.row + 1) + "c" + (c.col + 1) + ": merge " + c.rowSpan + "x" + c.colSpan + " not read back");
            }
        }
        Out.info("verified " + runs + " run(s)");
        return bad;
    }

    private static String expected(SrcPara sp) {
        if (sp.breakOnly) return String.valueOf(Para.OBJ);
        var sb = new StringBuilder(sp.text);
        for (var k = sp.objs.size() - 1; k >= 0; k--) if (!sp.objs.get(k).kind().equals("pagebreak")) sb.insert(sp.objs.get(k).at(), Para.OBJ);
        return sb.toString();
    }

    private static int styles(SrcPara sp, Para p, List<String> bad, Eff eff) {
        var n = 0;
        for (var r : sp.runs) {
            var shift = (int) sp.objs.stream().filter(o -> !o.kind().equals("pagebreak") && o.at() <= r.from()).count();
            var run = p.runAt(p.start() + r.from() + shift);
            if (run == null || r.from() >= sp.text.length()) continue;
            var want = r.style();
            var got = eff.text(p.p().getParagraphStyle().getNamedStyleType(), run.getTextStyle());
            var diff = new ArrayList<String>();
            if (want.getBold() != null && Styles.on(want.getBold()) != Styles.on(got.getBold())) diff.add("bold");
            if (want.getItalic() != null && Styles.on(want.getItalic()) != Styles.on(got.getItalic())) diff.add("italic");
            if (want.getUnderline() != null && Styles.on(want.getUnderline()) != Styles.on(got.getUnderline())) diff.add("underline");
            if (want.getForegroundColor() != null && !Objects.equals(Styles.hex(want.getForegroundColor()), Styles.hex(got.getForegroundColor()))) diff.add("text colour");
            if (want.getBackgroundColor() != null && !Objects.equals(Styles.hex(want.getBackgroundColor()), Styles.hex(got.getBackgroundColor()))) diff.add("highlight");
            if (want.getFontSize() != null && (got.getFontSize() == null || !Objects.equals(want.getFontSize().getMagnitude(), got.getFontSize().getMagnitude()))) diff.add("size");
            if (want.getLink() != null && want.getLink().getUrl() != null && !want.getLink().getUrl().equals(Styles.link(got))) diff.add("link");
            if (!diff.isEmpty() && bad.size() < 12) bad.add("run at \"" + clip(sp.text.substring(r.from(), Math.min(sp.text.length(), r.from() + 30))) + "\": " + String.join(", ", diff) + " differ");
            n++;
        }
        return n;
    }

    private static String clip(String s) { return s.length() > 60 ? s.substring(0, 60) + "…" : s; }
}
