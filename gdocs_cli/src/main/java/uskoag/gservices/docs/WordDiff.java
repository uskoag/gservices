package uskoag.gservices.docs;

import java.util.*;
import java.util.regex.Pattern;

/** Inside one changed paragraph: which words went and came, in git's word-diff notation [-old-]{+new+}. */
public final class WordDiff {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{M}\\p{N}'’]+|\\s+|.", Pattern.DOTALL);

    private WordDiff() {}

    static List<String> tokens(String s) {
        var out = new ArrayList<String>();
        var m = TOKEN.matcher(s);
        while (m.find()) out.add(m.group());
        return out;
    }

    /** Share of the words both sides keep, 0..1, whitespace not counted. */
    static double similarity(String x, String y) {
        var a = words(x);
        var b = words(y);
        if (a.isEmpty() && b.isEmpty()) return 1;
        var same = Diff.of(a, b).stream().filter(o -> o.k() == '=').count();
        return 2.0 * same / (a.size() + b.size());
    }

    private static List<String> words(String s) {
        return tokens(s).stream().filter(t -> !t.isBlank()).map(t -> t.toLowerCase()).toList();
    }

    /** The paragraph once, with only what differs marked; long unchanged runs are cut to "…" at --context words. */
    static String inline(String x, String y, int context) {
        var a = tokens(x);
        var b = tokens(y);
        var ops = Diff.of(a, b);
        var parts = new ArrayList<String[]>();
        var i = 0;
        while (i < ops.size()) {
            var k = ops.get(i).k();
            var sb = new StringBuilder();
            var del = new StringBuilder();
            if (k == '=') {
                while (i < ops.size() && ops.get(i).k() == '=') sb.append(a.get(ops.get(i++).a()));
                parts.add(new String[]{"=", sb.toString()});
            } else {
                while (i < ops.size() && ops.get(i).k() != '=') {
                    var o = ops.get(i++);
                    if (o.k() == '-') del.append(a.get(o.a())); else sb.append(b.get(o.b()));
                }
                parts.add(new String[]{"~", del.toString(), sb.toString()});
            }
        }
        var out = new StringBuilder();
        for (var p = 0; p < parts.size(); p++) {
            var part = parts.get(p);
            if (part[0].equals("=")) out.append(trim(part[1], context, p > 0, p < parts.size() - 1));
            else {
                if (!part[1].isEmpty()) out.append("[-").append(part[1]).append("-]");
                if (!part[2].isEmpty()) out.append("{+").append(part[2]).append("+}");
            }
        }
        return out.toString();
    }

    /** Keep `context` words on the sides touching a change, elide the rest of an unchanged run. */
    private static String trim(String same, int context, boolean changeBefore, boolean changeAfter) {
        if (context < 0) return same;
        var t = tokens(same);
        var words = t.stream().filter(x -> !x.isBlank()).count();
        var keep = (changeBefore ? context : 0) + (changeAfter ? context : 0);
        if (words <= keep + 3) return same;
        var head = changeBefore ? take(t, context, false) : "";
        var tail = changeAfter ? take(t, context, true) : "";
        return head + "…" + tail;
    }

    private static String take(List<String> t, int n, boolean fromEnd) {
        var out = new ArrayDeque<String>();
        var seen = 0;
        for (var k = 0; k < t.size() && seen <= n; k++) {
            var x = t.get(fromEnd ? t.size() - 1 - k : k);
            if (!x.isBlank() && ++seen > n) break;
            if (fromEnd) out.addFirst(x); else out.addLast(x);
        }
        return String.join("", out);
    }
}
