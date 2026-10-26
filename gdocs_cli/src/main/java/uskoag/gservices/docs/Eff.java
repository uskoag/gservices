package uskoag.gservices.docs;

import com.google.api.client.util.GenericData;
import com.google.api.services.docs.v1.model.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Effective styles of the converted document. A run or paragraph carries only what it overrides; the rest comes
 * from NORMAL_TEXT and the paragraph's named style in the SOURCE, which differ from the destination's, so every
 * property is written out explicitly and the copy looks like the source whatever the target document's defaults.
 */
final class Eff {

    private static final String[] EDGES = {"borderTop", "borderBottom", "borderLeft", "borderRight", "borderBetween"};

    private final Map<String, NamedStyle> named = new HashMap<>();

    Eff(DocumentTab t) {
        if (t.getNamedStyles() != null) for (var s : t.getNamedStyles().getStyles()) named.put(s.getNamedStyleType(), s);
    }

    TextStyle text(String type, TextStyle run) {
        var out = new TextStyle();
        for (var t : new String[]{"NORMAL_TEXT", type}) { var n = named.get(t); if (n != null && n.getTextStyle() != null) put(out, n.getTextStyle()); }
        if (run != null) put(out, run);
        return out;
    }

    ParagraphStyle para(String type, ParagraphStyle own) {
        var out = new ParagraphStyle();
        for (var t : new String[]{"NORMAL_TEXT", type}) { var n = named.get(t); if (n != null && n.getParagraphStyle() != null) put(out, n.getParagraphStyle()); }
        if (own != null) put(out, own);
        out.setNamedStyleType(type);
        var res = new ParagraphStyle();
        for (var k : out.keySet()) {
            if (k.equals("headingId") || k.equals("tabStops") || Arrays.asList(EDGES).contains(k) && out.get(k) instanceof ParagraphBorder b && !visible(b)) continue;
            res.set(k, out.get(k));
        }
        out = res;
        var zero = new Dimension().setMagnitude(0d).setUnit("PT");
        if (out.getAlignment() == null) out.setAlignment("START");
        if (out.getLineSpacing() == null) out.setLineSpacing(100f);
        for (var k : new String[]{"spaceAbove", "spaceBelow", "indentStart", "indentFirstLine", "indentEnd"}) if (out.get(k) == null) out.set(k, zero);
        return out;
    }

    private static boolean visible(ParagraphBorder b) { return b.getWidth() != null && b.getWidth().getMagnitude() != null && b.getWidth().getMagnitude() > 0; }

    private static void put(GenericData to, GenericData from) { for (var k : from.keySet()) to.set(k, from.get(k)); }

    static ParagraphStyle withoutPageBreak(ParagraphStyle s) {
        var out = new ParagraphStyle();
        for (var k : s.keySet()) if (!k.equals("pageBreakBefore")) out.set(k, s.get(k));
        return out;
    }

    static TextStyle without(TextStyle s, String drop) {
        var out = new TextStyle();
        for (var k : s.keySet()) if (!k.equals(drop)) out.set(k, s.get(k));
        return out;
    }

    static String mask(GenericData g) { return String.join(",", g.keySet()); }
}
