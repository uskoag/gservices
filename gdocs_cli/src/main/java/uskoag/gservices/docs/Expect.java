package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

import static uskoag.gservices.docs.Styles.*;

/**
 * What one span of an inserted paragraph should look like once applied. After --apply the document is
 * read again and each span compared on what a reader sees: bold, italic, size, colour. Font family is
 * reported but not compared, because Google stores a font equal to the paragraph style's own as unset.
 */
record Expect(String tabId, String paraText, int offset, TextStyle style) {

    static String describe(TextStyle t) {
        if (t == null) return "(no style)";
        var parts = new ArrayList<String>();
        if (on(t.getBold())) parts.add("bold");
        if (on(t.getItalic())) parts.add("italic");
        if (t.getFontSize() != null && t.getFontSize().getMagnitude() != null) parts.add(t.getFontSize().getMagnitude() + "pt");
        var fg = hex(t.getForegroundColor());
        if (fg != null) parts.add(fg);
        if (t.getWeightedFontFamily() != null) parts.add(t.getWeightedFontFamily().getFontFamily());
        return parts.isEmpty() ? "(default text style)" : String.join(" ", parts);
    }

    private static String visible(TextStyle t) {
        if (t == null) return "";
        var sz = t.getFontSize() == null ? null : t.getFontSize().getMagnitude();
        return (on(t.getBold()) ? "bold " : "") + (on(t.getItalic()) ? "italic " : "") + (sz == null ? "" : sz + "pt ")
                + (hex(t.getForegroundColor()) == null ? "" : hex(t.getForegroundColor()));
    }

    /** Lines describing every mismatch; empty when the document looks as planned. */
    static List<String> verify(List<Expect> expects, Document doc) {
        var out = new ArrayList<String>();
        var all = Tabs.flat(doc);
        for (var e : expects) {
            var tab = all.stream().filter(t -> Tabs.id(t).equals(e.tabId())).findFirst().orElse(null);
            var want = Para.norm(e.paraText());
            var hits = tab == null ? List.<Para>of() : Para.of(tab.getDocumentTab()).stream().filter(p -> p.plain().equals(want)).toList();
            var head = "\"" + (want.length() > 50 ? want.substring(0, 50) + "…" : want) + "\" chars " + e.offset() + "+: ";
            if (hits.isEmpty()) { out.add(head + "paragraph not found after apply"); continue; }
            var p = hits.getLast();
            var run = p.runAt(p.start() + e.offset());
            var got = visible(run == null ? null : run.getTextStyle());
            if (!got.equals(visible(e.style()))) out.add(head + "expected [" + visible(e.style()).strip() + "] got [" + got.strip() + "]");
        }
        return out;
    }
}
