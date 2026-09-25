package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.docs.v1.model.Tab;
import java.util.*;

/** Tabs nest; everything here works on the depth-first flattening, which is also the order the Docs sidebar shows. */
public final class Tabs {

    private Tabs() {}

    static List<Tab> flat(Document d) {
        var out = new ArrayList<Tab>();
        walk(d.getTabs(), out);
        return out;
    }

    private static void walk(List<Tab> tabs, List<Tab> out) {
        if (tabs == null) return;
        for (var t : tabs) { out.add(t); walk(t.getChildTabs(), out); }
    }

    static String id(Tab t) { return t.getTabProperties().getTabId(); }

    static String title(Tab t) { return Objects.requireNonNullElse(t.getTabProperties().getTitle(), ""); }

    static int depth(Tab t) { return Objects.requireNonNullElse(t.getTabProperties().getNestingLevel(), 0); }

    /** "Parent / Child / Grandchild", so a tab titled "Notes" under two parents stays distinguishable. */
    static String path(Tab t, List<Tab> all) {
        var byId = new HashMap<String, Tab>();
        for (var x : all) byId.put(id(x), x);
        var parts = new LinkedList<String>();
        for (var cur = t; cur != null; cur = byId.get(cur.getTabProperties().getParentTabId())) parts.addFirst(title(cur));
        return String.join(" / ", parts);
    }

    /** Comma-separated selectors, each a tab id, a 1-based position, or a title (exact, else a unique substring). */
    static List<Tab> select(List<Tab> all, String sel) {
        if (sel == null) return all;
        var out = new ArrayList<Tab>();
        for (var s : sel.split(",")) out.add(one(all, s.trim()));
        return out;
    }

    private static Tab one(List<Tab> all, String s) {
        for (var t : all) if (id(t).equals(s)) return t;
        if (s.chars().allMatch(Character::isDigit) && !s.isEmpty()) {
            var n = Integer.parseInt(s);
            if (n >= 1 && n <= all.size()) return all.get(n - 1);
            Out.die("tab " + n + " out of range: the document has " + all.size() + " tab(s)");
        }
        for (var t : all) if (title(t).equalsIgnoreCase(s)) return t;
        var hits = all.stream().filter(t -> title(t).toLowerCase().contains(s.toLowerCase())).toList();
        if (hits.size() == 1) return hits.get(0);
        Out.die(hits.isEmpty() ? "no tab matches '" + s + "' (run `tabs <doc>` to list them)"
                : "'" + s + "' matches " + hits.size() + " tabs: " + String.join(", ", hits.stream().map(Tabs::title).toList()) + " — use the tab id");
        return null;
    }
}
