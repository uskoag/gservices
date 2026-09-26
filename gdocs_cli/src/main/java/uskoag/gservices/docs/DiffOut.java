package uskoag.gservices.docs;

import java.util.*;
import uskoag.gservices.docs.DocDiff.Change;

/** A diff as an agent reads it: grouped under tab › heading, one line per change, and a count at the end. */
public final class DiffOut {

    private DiffOut() {}

    static void text(List<Change> changes, String indent) {
        var group = (String) null;
        for (var c : changes) {
            var w = Blocks.where(c.at());
            if (!w.equals(group)) { group = w; Out.data(indent + "[" + (w.isEmpty() ? "(top)" : w) + "]"); }
            Out.data(indent + line(c));
        }
    }

    static String line(Change c) {
        var note = c.note() == null ? "" : " (" + c.note() + ")";
        return switch (c.type()) {
            case "removed" -> "- removed  L" + c.old().line() + note + ": " + q(c.old().plain());
            case "added" -> "+ added    L" + c.nu().line() + note + ": " + q(c.nu().plain());
            case "changed" -> "~ changed  L" + c.old().line() + "→L" + c.nu().line() + note + ": " + c.inline();
            case "format" -> "= format   L" + c.old().line() + "→L" + c.nu().line() + note + ": " + c.inline();
            case "replaced" -> "# replaced L" + c.old().line() + "→L" + c.nu().line() + note + ": " + q(c.old().plain()) + "\n             with: " + q(c.nu().plain());
            case "moved" -> "> moved    L" + c.old().line() + "→L" + c.nu().line() + note + " from [" + Blocks.where(c.old()) + "]: " + q(c.nu().plain());
            default -> c.type();
        };
    }

    private static String q(String s) { return "\"" + s + "\""; }

    static String stat(List<Change> changes) {
        var n = new TreeMap<String, Integer>();
        long gone = 0, came = 0;
        for (var c : changes) {
            n.merge(c.type(), 1, Integer::sum);
            if (c.old() != null && !c.type().equals("moved") && !c.type().equals("format")) gone += words(c.old().plain());
            if (c.nu() != null && !c.type().equals("moved") && !c.type().equals("format")) came += words(c.nu().plain());
        }
        if (changes.isEmpty()) return "no text change";
        var parts = new ArrayList<String>();
        for (var k : List.of("changed", "replaced", "added", "removed", "moved", "format")) if (n.containsKey(k)) parts.add(n.get(k) + " " + k);
        return changes.size() + " change(s): " + String.join(", ", parts) + " · paragraphs touched hold " + gone + " → " + came + " words";
    }

    private static long words(String s) { return s.isBlank() ? 0 : s.strip().split("\\s+").length; }

    static List<Map<String, Object>> json(List<Change> changes) {
        var out = new ArrayList<Map<String, Object>>();
        for (var c : changes) {
            var m = new LinkedHashMap<String, Object>();
            m.put("type", c.type());
            m.put("tab", c.at().tab());
            m.put("section", c.at().section());
            if (c.old() != null) { m.put("oldLine", c.old().line()); m.put("old", c.old().plain()); m.put("oldKind", c.old().kind()); }
            if (c.nu() != null) { m.put("newLine", c.nu().line()); m.put("new", c.nu().plain()); m.put("newKind", c.nu().kind()); }
            if (c.type().equals("format")) { m.put("oldMarkdown", c.old().raw()); m.put("newMarkdown", c.nu().raw()); }
            if (c.inline() != null) m.put("inline", c.inline());
            if (c.note() != null) m.put("note", c.note());
            out.add(m);
        }
        return out;
    }

    static Map<String, Object> rev(RevSpec r) {
        var m = new LinkedHashMap<String, Object>();
        m.put("revision", r.n());
        if (r.saved() != null) m.put("history", RevSpec.cache.indexOf(r.saved()) + 1);
        m.put("when", r.when());
        m.put("who", r.who());
        return m;
    }
}
