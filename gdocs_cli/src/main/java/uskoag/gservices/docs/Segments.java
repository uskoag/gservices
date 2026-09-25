package uskoag.gservices.docs;

import java.util.ArrayList;
import java.util.List;

/** The markdown subset accepted in inserted text: **bold** and *italic*, nothing else. */
record Segments(String plain, List<int[]> bold, List<int[]> italic) {

    static Segments parse(String md) {
        var plain = new StringBuilder();
        var bold = new ArrayList<int[]>();
        var italic = new ArrayList<int[]>();
        var i = 0;
        var boldAt = -1;
        var italicAt = -1;
        while (i < md.length()) {
            if (md.startsWith("**", i)) {
                if (boldAt < 0) boldAt = plain.length(); else { bold.add(new int[]{boldAt, plain.length()}); boldAt = -1; }
                i += 2;
            } else if (md.charAt(i) == '*') {
                if (italicAt < 0) italicAt = plain.length(); else { italic.add(new int[]{italicAt, plain.length()}); italicAt = -1; }
                i++;
            } else plain.append(md.charAt(i++));
        }
        if (boldAt >= 0 || italicAt >= 0) Out.die("unbalanced ** or * in: " + md);
        return new Segments(plain.toString(), bold, italic);
    }

    boolean boldAt(int i) { return in(bold, i); }

    boolean italicAt(int i) { return in(italic, i); }

    private static boolean in(List<int[]> spans, int i) {
        for (var s : spans) if (i >= s[0] && i < s[1]) return true;
        return false;
    }

    /** Maximal ranges of plain text sharing one bold/italic combination. */
    List<int[]> runs() {
        var out = new ArrayList<int[]>();
        var from = 0;
        for (var i = 1; i <= plain.length(); i++)
            if (i == plain.length() || boldAt(i) != boldAt(from) || italicAt(i) != italicAt(from)) { out.add(new int[]{from, i}); from = i; }
        return out;
    }
}
