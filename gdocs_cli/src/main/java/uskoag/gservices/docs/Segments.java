package uskoag.gservices.docs;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The markdown subset accepted in inserted text: **bold**, *italic*, [text](target) and \* for a literal asterisk, nothing else.
 * A target is kept as written; Links.resolve turns it into a URL or tab link once the document is known.
 */
record Segments(String plain, List<int[]> bold, List<int[]> italic, List<Span> links) {

    record Span(int from, int to, String target) {}

    static Segments parse(String md) {
        var plain = new StringBuilder();
        var bold = new ArrayList<int[]>();
        var italic = new ArrayList<int[]>();
        var links = new ArrayList<Span>();
        var i = 0;
        var boldAt = -1;
        var italicAt = -1;
        int linkAt = -1, linkClose = -1, linkAfter = -1;
        var target = "";
        while (i < md.length()) {
            if (i == linkClose) {
                links.add(new Span(linkAt, plain.length(), target));
                i = linkAfter;
                linkAt = linkClose = -1;
            } else if (md.charAt(i) == '[' && linkAt < 0 && linkTarget(md, i) != null) {
                var close = md.indexOf("](", i + 1);
                linkAt = plain.length();
                linkClose = close;
                linkAfter = md.indexOf(')', close + 2) + 1;
                target = md.substring(close + 2, linkAfter - 1);
                i++;
            } else if (md.startsWith("\\*", i)) {
                plain.append('*');
                i += 2;
            } else if (md.startsWith("**", i)) {
                if (boldAt < 0) boldAt = plain.length(); else { bold.add(new int[]{boldAt, plain.length()}); boldAt = -1; }
                i += 2;
            } else if (md.charAt(i) == '*') {
                if (italicAt < 0) italicAt = plain.length(); else { italic.add(new int[]{italicAt, plain.length()}); italicAt = -1; }
                i++;
            } else plain.append(md.charAt(i++));
        }
        if (boldAt >= 0 || italicAt >= 0) Out.die("unbalanced ** or * in: " + md);
        return new Segments(plain.toString(), bold, italic, links);
    }

    /** `[` opens a link only when `](target)` follows on the same line, so a literal "[DRAFT]" stays text. */
    private static String linkTarget(String md, int open) {
        var close = md.indexOf("](", open + 1);
        if (close < 0 || md.substring(open, close).contains("\n")) return null;
        var paren = md.indexOf(')', close + 2);
        return paren < 0 || paren == close + 2 ? null : md.substring(close + 2, paren);
    }

    boolean boldAt(int i) { return in(bold, i); }

    boolean italicAt(int i) { return in(italic, i); }

    String linkAt(int i) {
        for (var s : links) if (i >= s.from() && i < s.to()) return s.target();
        return null;
    }

    private static boolean in(List<int[]> spans, int i) {
        for (var s : spans) if (i >= s[0] && i < s[1]) return true;
        return false;
    }

    /** Maximal ranges of plain text sharing one bold/italic/link combination. */
    List<int[]> runs() {
        var out = new ArrayList<int[]>();
        var from = 0;
        for (var i = 1; i <= plain.length(); i++)
            if (i == plain.length() || boldAt(i) != boldAt(from) || italicAt(i) != italicAt(from) || !Objects.equals(linkAt(i), linkAt(from))) {
                out.add(new int[]{from, i});
                from = i;
            }
        return out;
    }
}
