package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The marker a list paragraph shows, as the document itself numbers it: the level's glyph format ("%0.", "%1)")
 * with each %n replaced by that level's counter in its own style (1, 01, a, A, i, I). Counters run per list, and a
 * shallower item restarts every deeper level, exactly as Docs does, so a read-back reads like the page.
 */
final class ListLabel {

    private static final Pattern SLOT = Pattern.compile("%(\\d)");

    private final DocumentTab tab;
    private final Map<String, int[]> counters = new HashMap<>();

    ListLabel(DocumentTab tab) { this.tab = tab; }

    String next(Paragraph p) {
        if (!Styles.ordered(p, tab)) return "-";
        var lvl = Styles.level(p);
        var id = p.getBullet().getListId();
        var levels = tab.getLists().get(id).getListProperties().getNestingLevels();
        var c = counters.computeIfAbsent(id, k -> new int[9]);
        var start = levels.get(lvl).getStartNumber();
        c[lvl] = c[lvl] == 0 ? (start == null ? 1 : start) : c[lvl] + 1;
        for (var j = lvl + 1; j < c.length; j++) c[j] = 0;
        var fmt = levels.get(lvl).getGlyphFormat();
        return SLOT.matcher(fmt == null ? "%" + lvl + "." : fmt).replaceAll(m -> {
            var k = Integer.parseInt(m.group(1));
            return k < levels.size() ? format(Math.max(c[k], 1), levels.get(k).getGlyphType()) : "";
        });
    }

    static String format(int n, String type) {
        return switch (type == null ? "DECIMAL" : type) {
            case "ZERO_DECIMAL" -> n < 10 ? "0" + n : "" + n;
            case "ALPHA" -> alpha(n);
            case "UPPER_ALPHA" -> alpha(n).toUpperCase();
            case "ROMAN" -> roman(n);
            case "UPPER_ROMAN" -> roman(n).toUpperCase();
            default -> "" + n;
        };
    }

    private static String alpha(int n) {
        var sb = new StringBuilder();
        for (var k = n; k > 0; k = (k - 1) / 26) sb.insert(0, (char) ('a' + (k - 1) % 26));
        return sb.toString();
    }

    private static String roman(int n) {
        var vals = new int[]{1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        var syms = new String[]{"m", "cm", "d", "cd", "c", "xc", "l", "xl", "x", "ix", "v", "iv", "i"};
        var sb = new StringBuilder();
        for (var i = 0; i < vals.length; i++) while (n >= vals[i]) { sb.append(syms[i]); n -= vals[i]; }
        return sb.toString();
    }
}
