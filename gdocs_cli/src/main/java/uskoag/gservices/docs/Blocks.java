package uskoag.gservices.docs;

import java.util.*;
import java.util.regex.Pattern;

/**
 * An exported version cut into paragraphs, each knowing its tab and the heading it sits under — what
 * lets a change be reported as "USK › Timeline" instead of "line 212". The raw markdown is the
 * comparison key (so a restyle is a change); the plain text is what decides whether it was only a restyle.
 */
public final class Blocks {

    record Block(int line, String raw, String plain, String kind, String tab, String section) {}

    private static final Pattern
            HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$"),
            BULLET = Pattern.compile("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$"),
            LINK = Pattern.compile("!?\\[([^\\]]*)]\\([^)]*\\)"),
            ESCAPE = Pattern.compile("\\\\([\\\\`*_{}\\[\\]()#+\\-.!|<>~=])"),
            EMPH = Pattern.compile("(?<![\\p{L}\\p{N}\\\\])[*_](?=\\S)|(?<=\\S)(?<!\\\\)[*_](?![\\p{L}\\p{N}])"),
            SPACES = Pattern.compile("\\s+");

    private Blocks() {}

    static List<Block> of(String md, TabSplit tabs) {
        var out = new ArrayList<Block>();
        var lines = md.replace("\r\n", "\n").split("\n", -1);
        var section = "";
        var tab = tabs.first();
        for (var i = 0; i < lines.length; i++) {
            var raw = lines[i].stripTrailing();
            if (raw.isBlank()) continue;
            var t = tabs.at(raw);
            var kind = kind(raw);
            var plain = plain(raw);
            if (t != null) { tab = t; section = ""; }
            else if (kind.startsWith("h") || isBoldLine(raw)) section = plain;
            out.add(new Block(i + 1, raw, plain, kind, tab, section));
        }
        return out;
    }

    static String kind(String line) {
        var quoted = line.startsWith(">");
        var raw = quoted ? line.substring(1).stripLeading() : line;
        var h = HEADING.matcher(raw);
        if (h.matches()) return "h" + h.group(1).length();
        var b = BULLET.matcher(raw);
        if (b.matches()) return (Character.isDigit(b.group(2).charAt(0)) ? "numbered" : "bullet") + "/" + (b.group(1).length() / 2 + 1);
        if (quoted) return "quote";
        if (raw.startsWith("|")) return "table-row";
        return "para";
    }

    /** The words a reader sees: no heading or list markers, no emphasis, links reduced to their text, escapes undone. */
    static String plain(String raw) {
        var s = raw.startsWith(">") ? raw.substring(1).stripLeading() : raw;
        var h = HEADING.matcher(s);
        if (h.matches()) s = h.group(2);
        var b = BULLET.matcher(s);
        if (b.matches()) s = b.group(3);
        if (s.indexOf('[') >= 0) s = LINK.matcher(s).replaceAll("$1");
        if (s.indexOf('*') >= 0 || s.indexOf('_') >= 0 || s.indexOf('~') >= 0) {
            s = s.replace("**", "").replace("__", "").replace("~~", "");
            s = EMPH.matcher(s).replaceAll("");
        }
        if (s.indexOf('\\') >= 0) s = ESCAPE.matcher(s).replaceAll("$1");
        return SPACES.matcher(s.replace((char) 0xa0, (char) 32)).replaceAll(" ").strip();
    }

    private static boolean isBoldLine(String raw) {
        var s = raw.strip();
        return s.startsWith("**") && s.endsWith("**") && s.length() < 120 && s.indexOf("**", 2) == s.length() - 2;
    }

    static String where(Block b) {
        var tab = b.tab() == null || b.tab().isEmpty() ? "" : b.tab();
        var sec = b.section() == null || b.section().isEmpty() ? "" : b.section();
        if (sec.length() > 60) sec = sec.substring(0, 57) + "...";
        return tab.isEmpty() ? sec : sec.isEmpty() ? tab : tab + " › " + sec;
    }
}
