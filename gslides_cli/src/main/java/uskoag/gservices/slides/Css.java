package uskoag.gservices.slides;

import java.util.LinkedHashMap;
import java.util.Map;

/** Just enough inline-style parsing for the attributes HTML can express that markdown cannot. */
public final class Css {

    private Css() {}

    static Map<String, String> parse(String style) {
        var out = new LinkedHashMap<String, String>();
        if (style == null || style.isBlank()) return out;
        for (var decl : style.split(";")) {
            var i = decl.indexOf(':');
            if (i > 0) out.put(decl.substring(0, i).trim().toLowerCase(), decl.substring(i + 1).trim());
        }
        return out;
    }

    /** Accepts 14pt, 14px, 14 -- all read as points, which is what Slides wants. */
    static Double points(String v) {
        if (v == null) return null;
        var n = v.replaceAll("(?i)(pt|px)\\s*$", "").trim();
        try { return Double.valueOf(n); } catch (NumberFormatException e) { return null; }
    }

    static String hex(String v) {
        if (v == null) return null;
        var s = v.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        return s.matches("(?i)[0-9a-f]{6}") ? s.toUpperCase() : null;
    }

    static String font(String v) {
        if (v == null) return null;
        var first = v.split(",")[0].trim();
        return first.replaceAll("^['\"]|['\"]$", "");
    }
}
