package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.Tab;
import java.util.*;
import java.util.List;

/**
 * tab delete: which tabs go. A tab with child tabs is refused unless --with-children, because Google deletes
 * the children with it. A tab also selected through its parent is dropped from the list, since the parent's
 * delete already takes it, and a request for a tab that no longer exists would fail the whole batch.
 */
final class TabDelete {

    private TabDelete() {}

    static List<TabWant> plan(List<Tab> all, List<String> a) {
        var withChildren = Args.flag(a, "--with-children");
        var picked = new LinkedHashMap<String, Tab>();
        for (var sel = Args.val(a, "--tab", "-t"); sel != null; sel = Args.val(a, "--tab", "-t"))
            for (var t : Tabs.select(all, sel)) picked.put(Tabs.id(t), t);
        if (picked.isEmpty()) Out.die("tab delete needs --tab <sel> (repeatable, or comma-separated)");

        var out = new ArrayList<TabWant>();
        for (var t : picked.values()) {
            var ancestor = ancestorIn(t, picked, all);
            if (ancestor != null) { Out.data("skip " + Tabs.id(t) + " \"" + Tabs.title(t) + "\": goes with its parent \"" + Tabs.title(ancestor) + "\""); continue; }
            var below = descendants(t, all);
            if (!below.isEmpty() && !withChildren)
                Out.die("\"" + Tabs.title(t) + "\" has " + below.size() + " child tab(s), which Google deletes with it: "
                        + String.join(", ", below.stream().map(Tabs::title).toList()) + ". Add --with-children to delete them too.");
            out.add(new TabWant.Delete(Tabs.id(t), Tabs.title(t), VerbTabs.chars(t), below.size()));
        }
        var topLeft = all.stream().filter(t -> Tabs.parentId(t) == null && !picked.containsKey(Tabs.id(t))).count();
        if (topLeft == 0) Out.die("refused: that would delete every top-level tab, and a document must keep at least one");
        return out;
    }

    private static Tab ancestorIn(Tab t, Map<String, Tab> picked, List<Tab> all) {
        for (var p = parent(t, all); p != null; p = parent(p, all)) if (picked.containsKey(Tabs.id(p))) return p;
        return null;
    }

    static List<Tab> descendants(Tab t, List<Tab> all) {
        return all.stream().filter(x -> x != t && ancestorIs(x, t, all)).toList();
    }

    private static boolean ancestorIs(Tab x, Tab t, List<Tab> all) {
        for (var p = parent(x, all); p != null; p = parent(p, all)) if (p == t) return true;
        return false;
    }

    private static Tab parent(Tab t, List<Tab> all) {
        var pid = Tabs.parentId(t);
        if (pid == null) return null;
        for (var x : all) if (Tabs.id(x).equals(pid)) return x;
        return null;
    }
}
