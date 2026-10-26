package uskoag.gservices.sheetcli;

import java.util.*;

/** One tab, older against newer: structural changes from the alignment, then every paired cell that differs. */
final class TabDiff {

    private static final List<String> ERRORS = List.of("#REF!", "#ERROR!", "#N/A", "#VALUE!", "#DIV/0!", "#NAME?", "#NUM!", "#NULL!");

    private TabDiff() {}

    static void of(Grid.Tab a, Grid.Tab b, boolean formulas, List<StructChange> struct, List<CellDiff> cells) {
        var al = new TabAlign(a, b);
        var tab = b.title();
        runs(al.colMap, b.cols(), true, al, tab, struct);
        runs(al.rowMap, b.rows(), false, al, tab, struct);
        for (var c : al.colRekeyed)
            struct.add(new StructChange(tab, "header", "header changed: " + A1.col(al.colMap[c]) + "1 \"" + a.value(0, c) + "\" -> \"" + b.value(0, al.colMap[c]) + "\""
                    + (c == al.colMap[c] ? "" : " (was column " + A1.col(c) + ")")));
        al.moved.forEach((i, j) -> struct.add(new StructChange(tab, "row-moved", "row moved: was row " + (i + 1) + ", now row " + (j + 1)
                + label(a.value(i, 0)))));
        for (var i = 0; i < al.rowMap.length; i++) {
            var j = al.rowMap[i];
            if (j < 0) continue;
            for (var c = 0; c < al.colMap.length; c++) {
                var d = al.colMap[c];
                if (d >= 0) cell(a, b, i, c, j, d, formulas, tab, cells);
            }
        }
    }

    private static void cell(Grid.Tab a, Grid.Tab b, int i, int c, int j, int d, boolean formulas, String tab, List<CellDiff> out) {
        var ov = a.value(i, c);
        var nv = b.value(j, d);
        var of = a.formula(i, c);
        var nf = b.formula(j, d);
        String note = null;
        if (!of.isEmpty() && nf.isEmpty()) note = nv.isEmpty() ? "formula cleared" : "formula replaced by a typed value";
        else if (of.isEmpty() && !nf.isEmpty()) note = "formula added";
        else if (!of.equals(nf) && formulas) note = "formula changed";
        if (isError(nv) && !isError(ov)) note = note == null ? "now an error" : "now an error, " + note;
        if (ov.equals(nv) && note == null) return;
        var at = A1.col(d) + (j + 1);
        var was = A1.col(c) + (i + 1);
        var showF = formulas || note != null && (note.contains("formula") || note.contains("error"));
        out.add(new CellDiff(tab, at, at.equals(was) ? null : was, show(ov, of, showF), show(nv, nf, showF), note));
    }

    private static String show(String v, String f, boolean withFormula) {
        if (!withFormula || f.isEmpty()) return v;
        return f + (v.isEmpty() ? "" : "  [" + v + "]");
    }

    private static boolean isError(String v) {
        for (var e : ERRORS) if (v.contains(e)) return true;
        return false;
    }

    /** Consecutive unmatched columns (or rows), on either side, said once as a run. */
    private static void runs(int[] map, int bSize, boolean cols, TabAlign al, String tab, List<StructChange> out) {
        var what = cols ? "column" : "row";
        var del = new ArrayList<Integer>();
        for (var i = 0; i < map.length; i++) if (map[i] < 0) del.add(i);
        for (var r : group(del)) out.add(new StructChange(tab, what + "s-deleted", count(r, what) + " deleted: " + span(r, cols)
                + " of the older version" + sample(r, cols, al.a, true)));
        var ins = new ArrayList<Integer>();
        for (var j : al.unmatchedB(map, bSize)) ins.add(j);
        for (var r : group(ins)) {
            var before = nextOld(map, r.getLast() + 1);
            out.add(new StructChange(tab, what + "s-inserted", count(r, what) + " inserted: now " + span(r, cols)
                    + (before < 0 ? ", at the end" : ", before what was " + what + " " + (cols ? A1.col(before) : String.valueOf(before + 1)))
                    + sample(r, cols, al.b, false)));
        }
    }

    private static int nextOld(int[] map, int fromB) {
        var best = -1;
        for (var i = 0; i < map.length; i++) if (map[i] >= fromB && (best < 0 || map[i] < map[best])) best = i;
        return best;
    }

    private static List<List<Integer>> group(List<Integer> xs) {
        var out = new ArrayList<List<Integer>>();
        for (var x : xs) {
            if (out.isEmpty() || out.getLast().getLast() != x - 1) out.add(new ArrayList<>());
            out.getLast().add(x);
        }
        return out;
    }

    private static String count(List<Integer> r, String what) { return r.size() + " " + what + (r.size() == 1 ? "" : "s"); }

    private static String span(List<Integer> r, boolean cols) {
        var f = cols ? A1.col(r.getFirst()) : String.valueOf(r.getFirst() + 1);
        var l = cols ? A1.col(r.getLast()) : String.valueOf(r.getLast() + 1);
        return (r.size() == 1 ? (cols ? "column " : "row ") + f : (cols ? "columns " : "rows ") + f + ":" + l);
    }

    /** What was in them, briefly: column headers, or the rows' first cells. */
    private static String sample(List<Integer> r, boolean cols, Grid.Tab t, boolean old) {
        var vals = new ArrayList<String>();
        for (var x : r.subList(0, Math.min(r.size(), 6))) {
            var v = cols ? t.value(0, x) : t.value(x, 0);
            if (!v.isBlank()) vals.add("\"" + clip(v) + "\"");
        }
        if (vals.isEmpty()) return "";
        return (cols ? "; headers " : "; column A ") + String.join(", ", vals) + (r.size() > 6 ? ", ..." : "");
    }

    private static String label(String v) { return v.isBlank() ? "" : " (\"" + clip(v) + "\")"; }

    static String clip(String s) { s = s.replace("\n", "⏎"); return s.length() > 60 ? s.substring(0, 60) + "…" : s; }
}
