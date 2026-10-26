package uskoag.gservices.sheetcli;

import java.util.*;

/** The diff for a person (structure, then cells grouped by tab, then who) or as JSON for a program. */
final class DiffReport {

    private DiffReport() {}

    static void text(GridDiff d, People who, boolean stat, int cap) {
        Out.data("diff \"" + d.to().title() + "\": " + side(d.from()) + "  ->  " + side(d.to()));
        if (who != null) Out.data("edited in between by: " + who.show());
        Out.data("");
        Out.data("STRUCTURE" + (d.struct().isEmpty() ? ": no tabs, rows or columns added, removed or moved" : ""));
        for (var s : d.struct()) Out.data("  [" + s.tab() + "] " + s.text());
        Out.data("");
        var byTab = new LinkedHashMap<String, List<CellDiff>>();
        for (var c : d.cells()) byTab.computeIfAbsent(c.tab(), k -> new ArrayList<>()).add(c);
        Out.data("CELLS" + (byTab.isEmpty() ? ": none changed" : ": " + d.cells().size() + " changed"));
        byTab.forEach((tab, cs) -> {
            var notes = new TreeMap<String, Integer>();
            for (var c : cs) if (c.note() != null) notes.merge(c.note(), 1, Integer::sum);
            Out.data("  [" + tab + "]  " + cs.size() + " changed" + (notes.isEmpty() ? "" : "; " + String.join(", ", notes.entrySet().stream().map(e -> e.getValue() + " " + e.getKey()).toList())));
            if (stat) return;
            for (var c : cs.subList(0, Math.min(cs.size(), cap)))
                Out.data("    " + c.at() + (c.was() == null ? "" : " (was " + c.was() + ")") + ": " + q(c.from()) + " -> " + q(c.to())
                        + (c.note() == null ? "" : "   <" + c.note() + ">"));
            if (cs.size() > cap) Out.data("    ... " + (cs.size() - cap) + " more (--limit n, or --sheet \"" + tab + "\")");
        });
    }

    private static String side(Grid g) { return g.source() + (g.time() == 0 ? "" : " " + Times.show(g.time())); }

    private static String q(String s) { return s.isEmpty() ? "(empty)" : TabDiff.clip(s); }

    static Map<String, Object> json(GridDiff d, People who) {
        var m = new LinkedHashMap<String, Object>();
        m.put("spreadsheet", d.to().title());
        m.put("from", Map.of("source", d.from().source(), "time", d.from().time() == 0 ? "" : Times.iso(d.from().time())));
        m.put("to", Map.of("source", d.to().source(), "time", d.to().time() == 0 ? "" : Times.iso(d.to().time())));
        if (who != null) m.put("editedInBetween", Map.of("people", who.names(), "confidence", who.confidence(), "source", who.source()));
        m.put("structure", d.struct());
        m.put("cells", d.cells());
        return m;
    }
}
