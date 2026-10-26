package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;

/** Turns tab add / rename / move flags into TabWants, resolving every selector against the read just made. */
final class TabPlan {

    private TabPlan() {}

    static List<TabWant> add(List<Tab> all, List<String> a) {
        var title = Args.val(a, "--title");
        var file = Args.val(a, "--titles-file");
        if ((title == null) == (file == null)) Out.die("tab add needs exactly one of --title \"<t>\" or --titles-file <file>");
        var titles = title != null ? List.of(title) : VerbTab.titlesFile(file);
        if (titles.isEmpty()) Out.die("--titles-file " + file + " has no titles");
        var place = place(all, a, false, null);
        var out = new ArrayList<TabWant>();
        for (var i = 0; i < titles.size(); i++)
            out.add(new TabWant.Add(titles.get(i), place.parentId, place.parentTitle, place.index == null ? null : place.index + i));
        return out;
    }

    static List<TabWant> rename(List<Tab> all, List<String> a) {
        var t = tab(all, a);
        var title = Args.val(a, "--title");
        if (title == null || title.isBlank()) Out.die("tab rename needs --title \"<new title>\"");
        return List.of(new TabWant.Rename(Tabs.id(t), Tabs.title(t), title));
    }

    /**
     * Google reads a move's index against the siblings as they stand BEFORE the tab is lifted out (measured), so a
     * tab moving down within its own parent is sent one further than where it should end up. index is the final one.
     */
    static List<TabWant> move(List<Tab> all, List<String> a) {
        var t = tab(all, a);
        var place = place(all, a, true, t);
        if (place.parentId != null) for (var p = byId(all, place.parentId); p != null; p = byId(all, Tabs.parentId(p)))
            if (p == t) Out.die("cannot move \"" + Tabs.title(t) + "\" under itself or one of its own child tabs");
        var same = Objects.equals(place.parentId, Tabs.parentId(t));
        var cur = Tabs.siblingIndex(t, all);
        var end = place.index;
        if (end != null && same && place.afterAnchor && cur < end) end--;
        if (end != null && same) end = Math.min(end, Tabs.siblingCount(place.parentId, all) - 1);
        var send = end != null && same && cur <= end ? Integer.valueOf(end + 1) : end;
        return List.of(new TabWant.Move(Tabs.id(t), Tabs.title(t), place.parentId, place.parentTitle, end, send));
    }

    private record Place(String parentId, String parentTitle, Integer index, boolean afterAnchor) {}

    /**
     * --parent / --top say which parent, --after / --index where among its children (--index is 1-based, like
     * every position this tool prints). --after alone implies its own parent. A move with no parent flag keeps the current one.
     */
    private static Place place(List<Tab> all, List<String> a, boolean moving, Tab self) {
        var parentSel = Args.val(a, "--parent");
        var top = Args.flag(a, "--top");
        var afterSel = Args.val(a, "--after");
        var index = Args.intVal(a, "--index");
        if (parentSel != null && top) Out.die("--parent and --top contradict each other");
        if (afterSel != null && index != null) Out.die("give --after or --index, not both");
        var parent = parentSel == null ? null : one(all, parentSel);
        var after = afterSel == null ? null : one(all, afterSel);
        String pid;
        if (parent != null) pid = Tabs.id(parent);
        else if (top) pid = null;
        else if (after != null) pid = Tabs.parentId(after);
        else pid = moving ? Tabs.parentId(self) : null;
        if (after != null && !Objects.equals(Tabs.parentId(after), pid))
            Out.die("--after \"" + Tabs.title(after) + "\" is not a child of " + (pid == null ? "the top level" : "\"" + Tabs.title(byId(all, pid)) + "\""));
        if (pid != null && Tabs.depth(byId(all, pid)) >= 2)
            Out.die("\"" + Tabs.title(byId(all, pid)) + "\" is already at the third level; Google allows tabs nested 3 deep, no more");
        var count = Tabs.siblingCount(pid, all);
        Integer at = after != null ? Tabs.siblingIndex(after, all) + 1 : null;
        if (index != null) {
            if (index < 1 || index > count + 1) Out.die("--index " + index + " out of range: there are " + count + " tab(s) there, so 1.." + (count + 1));
            at = index - 1;
        }
        return new Place(pid, pid == null ? null : Tabs.title(byId(all, pid)), at, after != null);
    }

    private static Tab tab(List<Tab> all, List<String> a) {
        var sel = Args.val(a, "--tab", "-t");
        if (sel == null) Out.die("needs --tab <sel> (tab id, 1-based position from `tabs`, or title)");
        return one(all, sel);
    }

    private static Tab one(List<Tab> all, String sel) {
        var hits = Tabs.select(all, sel);
        if (hits.size() != 1) Out.die("'" + sel + "' must name exactly one tab");
        return hits.get(0);
    }

    private static Tab byId(List<Tab> all, String id) {
        for (var t : all) if (Tabs.id(t).equals(id)) return t;
        return null;
    }

    /** Titles need not be unique for Google, but a title that is not unique cannot be used as --tab afterwards. */
    static void warnDuplicates(List<Tab> all, List<TabWant> wants) {
        var seen = new HashMap<String, Integer>();
        for (var t : all) seen.merge(Tabs.title(t).toLowerCase(), 1, Integer::sum);
        for (var w : wants) {
            switch (w) {
                case TabWant.Add x -> seen.merge(x.title().toLowerCase(), 1, Integer::sum);
                case TabWant.Rename x -> { seen.merge(x.from().toLowerCase(), -1, Integer::sum); seen.merge(x.title().toLowerCase(), 1, Integer::sum); }
                case TabWant.Move x -> {}
                case TabWant.Delete x -> seen.merge(x.title().toLowerCase(), -1, Integer::sum);
            }
        }
        seen.forEach((k, n) -> { if (n > 1) Out.warn( + n + " tabs will be titled \"" + k + "\"; select them by tab id, not by title"); });
    }
}
