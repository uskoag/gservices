package uskoag.gservices.docs;

import java.util.*;

/**
 * Where one tab ends and the next begins inside an export. Drive's per-revision export flattens every tab
 * into one file; the tab titles come back as ordinary lines, so a line that is exactly a known tab title,
 * met in sidebar order, is taken as that tab's start. When no titles are known the whole export is one
 * unnamed tab and the output says so.
 */
public final class TabSplit {

    private final List<String> titles;
    private int next;

    TabSplit(List<String> titles) { this.titles = titles; }

    static TabSplit none() { return new TabSplit(List.of()); }

    boolean known() { return !titles.isEmpty(); }

    String first() { return titles.isEmpty() ? "" : titles.getFirst(); }

    String at(String rawLine) {
        if (next >= titles.size() || rawLine.length() > 120) return null;
        var plain = Blocks.plain(rawLine);
        for (var i = next; i < titles.size(); i++) {
            if (plain.equalsIgnoreCase(titles.get(i).strip())) { next = i + 1; return titles.get(i); }
        }
        return null;
    }
}
