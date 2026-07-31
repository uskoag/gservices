package uskoag.gservices;

import org.jsoup.Jsoup;

import java.util.ArrayList;

/**
 * Removes quoted reply history from an email body, keeping only the newly-typed (top-posted) content.
 *
 * <p>Reading a thread compounds tokens: message 2 quotes 1, message 3 quotes 2+1, and so on. When the
 * {@code --strip-quotes} flag is set, each message's body is reduced to just its new content, so an AI
 * consumer reads every reply once instead of re-reading the whole history at every level.</p>
 *
 * <p>Heuristic, tuned for the common top-posting case (Gmail/Apple/Outlook). Interleaved or bottom-posted
 * replies may lose new content — that trade-off is accepted for the token win.</p>
 */
final class QuoteStripper {

    private QuoteStripper() {}

    /** Truncates a plain-text body at the first reply/forward boundary and trims any trailing {@code >} quote block. */
    static String stripPlain(String text) {
        if (text == null || text.isBlank()) return text;
        var lines = text.split("\n", -1);
        var cut = lines.length;
        for (var i = 0; i < lines.length; i++) {
            if (isBoundary(lines, i)) { cut = i; break; }
        }
        var kept = new ArrayList<String>();
        for (var i = 0; i < cut; i++) kept.add(lines[i]);
        var end = kept.size();
        while (end > 0) {
            var t = kept.get(end - 1).trim();
            if (t.isEmpty() || t.startsWith(">")) end--; else break;
        }
        var sb = new StringBuilder();
        for (var i = 0; i < end; i++) { if (i > 0) sb.append('\n'); sb.append(kept.get(i)); }
        return sb.toString().strip();
    }

    /** Removes Gmail/Apple {@code blockquote}/{@code gmail_quote} containers from an HTML body via jsoup. */
    static String stripHtml(String html) {
        if (html == null || html.isBlank()) return html;
        var doc = Jsoup.parse(html);
        doc.select("blockquote, div.gmail_quote, div.gmail_extra, div.gmail_attr, "
                + "#divRplyFwdMsg, #appendonsend, div.moz-cite-prefix").remove();
        var body = doc.body();
        return (body != null ? body.html() : doc.html()).strip();
    }

    /** True when line {@code i} starts the quoted section: an "On … wrote:" attribution or an Outlook divider/header. */
    private static boolean isBoundary(String[] lines, int i) {
        var line = lines[i].trim();
        if (line.isEmpty()) return false;
        if (line.matches("(?i)-{2,}\\s*original message\\s*-{2,}")) return true;
        if (line.matches("_{10,}")) return true;
        if (line.startsWith("On ")) {
            var joined = "";
            for (var k = i; k < Math.min(i + 3, lines.length); k++) {
                joined = joined.isEmpty() ? lines[k].trim() : joined + " " + lines[k].trim();
                if (joined.endsWith("wrote:")) return true;
            }
        }
        if (line.matches("(?i)from:\\s.*")) {
            var hasWhen = false; var hasTo = false;
            for (var k = i + 1; k < Math.min(i + 5, lines.length); k++) {
                var t = lines[k].trim();
                if (t.matches("(?i)(sent|date):\\s.*")) hasWhen = true;
                if (t.matches("(?i)to:\\s.*")) hasTo = true;
            }
            if (hasWhen && hasTo) return true;
        }
        return false;
    }
}
