package uskoag.gservices.docs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Markdown list lines to (level, kind, text). Level comes from indentation (a tab is four columns) and from the
 * marker's own rank: under "1." an "(a)" is one level deeper even at the same indent, and "(i)" one deeper still.
 * Letter and roman markers are only read as markers inside an open list, so ordinary sentences are left alone.
 */
final class MdList {

    static final int BULLET = 0, DEC = 1, ALPHA = 2, ROMAN = 3;

    record Item(int level, String list, String text, String marker, int cls) {}

    private static final Pattern
            BUL = Pattern.compile("^([-*+•–])[ \\u00a0]+(\\S.*)$"),
            DECI = Pattern.compile("^(\\d{1,3})[.)][ \\u00a0]+(\\S.*)$"),
            PAREN = Pattern.compile("^\\(([A-Za-z]{1,6}|\\d{1,3})\\)[ \\u00a0]+(\\S.*)$"),
            LETTER = Pattern.compile("^([A-Za-z]{1,6})[.)][ \\u00a0]+(\\S.*)$"),
            ROMAN_TOKEN = Pattern.compile("^([ivxlcdm]+|[IVXLCDM]+)$");

    private final List<int[]> stack = new ArrayList<>();
    private String lastAlpha = "";
    /** Read letter markers even with no list open yet: for a range already known to be a list. */
    boolean assumeOpen;

    boolean open() { return !stack.isEmpty(); }

    void reset() { stack.clear(); lastAlpha = ""; }

    static int indent(String line) {
        var w = 0;
        for (var c : line.toCharArray()) { if (c == ' ') w++; else if (c == '\t') w += 4; else break; }
        return w;
    }

    /** The item this line is, or null (the list stays open: the caller decides whether a plain line ends it). */
    Item item(String line) {
        var in = indent(line);
        var s = line.strip();
        var m = BUL.matcher(s);
        if (m.matches()) return place(in, BULLET, m.group(1), m.group(2));
        m = DECI.matcher(s);
        if (m.matches()) return place(in, DEC, m.group(1), m.group(2));
        var letters = open() || assumeOpen;
        m = PAREN.matcher(s);
        if (m.matches() && letters && token(m.group(1))) return place(in, cls(m.group(1)), m.group(1), m.group(2));
        m = LETTER.matcher(s);
        if (m.matches() && letters && token(m.group(1))) return place(in, cls(m.group(1)), m.group(1), m.group(2));
        return null;
    }

    static boolean token(String t) {
        return t.chars().allMatch(Character::isDigit) || t.length() == 1 || ROMAN_TOKEN.matcher(t).matches();
    }

    /** Digits are decimal; a single letter is alpha unless it is i, v or x continuing no alphabet run; longer roman strings are roman. */
    private int cls(String t) {
        if (t.chars().allMatch(Character::isDigit)) return DEC;
        var l = t.toLowerCase();
        if (l.length() > 1) return ROMAN;
        var c = l.charAt(0);
        var roman = (c == 'i' && !lastAlpha.equals("h")) || (c == 'v' && !lastAlpha.equals("u")) || (c == 'x' && !lastAlpha.equals("w"));
        if (roman) return ROMAN;
        lastAlpha = l;
        return ALPHA;
    }

    private static boolean deeper(int parent, int child) {
        return (parent == DEC && (child == ALPHA || child == ROMAN)) || (parent == ALPHA && child == ROMAN);
    }

    private Item place(int in, int cls, String marker, String text) {
        var level = 0;
        for (var k = stack.size() - 1; k >= 0; k--) {
            var e = stack.get(k);
            if (in > e[0] || (in == e[0] && deeper(e[1], cls))) { level = k + 1; break; }
            if (in == e[0] && (e[1] == cls || e[1] == BULLET || cls == BULLET)) { level = k; break; }
        }
        while (stack.size() > level) stack.remove(stack.size() - 1);
        stack.add(new int[]{in, cls});
        return new Item(level, cls == BULLET ? "bullet" : "number", text.strip(), marker, cls);
    }

    /** Insert / plan paragraphs: markdown list syntax in a paragraph that asks for nothing else becomes a list paragraph. */
    static List<Edit.NewPara> expand(List<Edit.NewPara> paras) {
        var ctx = new MdList();
        var out = new ArrayList<Edit.NewPara>();
        for (var np : paras) {
            var explicit = np.like() != null || np.style() != null || np.list() != null || np.level() != null || np.preset() != null;
            var it = explicit ? null : ctx.item(np.text());
            if (it == null) { ctx.reset(); out.add(np); continue; }
            out.add(new Edit.NewPara(it.text(), null, null, it.list(), np.spacing(), it.level(), null, np.fmt()));
        }
        return out;
    }
}
