package uskoag.gservices.docs;

import java.util.*;
import uskoag.gservices.docs.Blocks.Block;

/**
 * Two versions, compared paragraph by paragraph. Each difference is one of: removed, added, changed
 * (same paragraph reworded; carries a word-level inline diff), format (same words, different markup or
 * paragraph kind), moved (the same paragraph now elsewhere).
 */
public final class DocDiff {

    record Change(String type, Block old, Block nu, String inline, String note) {
        Block at() { return nu != null ? nu : old; }
    }

    static double pairAt = 0.5;
    static int context = 8;

    private DocDiff() {}

    static List<Change> of(List<Block> a, List<Block> b) {
        var ops = Diff.of(a.stream().map(Block::raw).toList(), b.stream().map(Block::raw).toList());
        var out = new ArrayList<Change>();
        var i = 0;
        while (i < ops.size()) {
            if (ops.get(i).k() == '=') { i++; continue; }
            var dels = new ArrayList<Block>();
            var ins = new ArrayList<Block>();
            while (i < ops.size() && ops.get(i).k() != '=') {
                var o = ops.get(i++);
                if (o.k() == '-') dels.add(a.get(o.a())); else ins.add(b.get(o.b()));
            }
            hunk(dels, ins, out);
        }
        return moves(out);
    }

    /** Pair each removed paragraph with the added one it most resembles, keeping order; the rest stand alone. */
    private static void hunk(List<Block> dels, List<Block> ins, List<Change> out) {
        var used = new boolean[ins.size()];
        var from = 0;
        var pending = new ArrayList<Change>();
        for (var d : dels) {
            var best = -1;
            var bestSim = pairAt - 1e-9;
            for (var j = from; j < ins.size(); j++) {
                var s = d.plain().equals(ins.get(j).plain()) ? 2 : WordDiff.similarity(d.plain(), ins.get(j).plain());
                if (s > bestSim) { best = j; bestSim = s; }
            }
            if (best < 0) { pending.add(new Change("removed", d, null, null, null)); continue; }
            for (var j = from; j < best; j++) { pending.add(new Change("added", null, ins.get(j), null, null)); used[j] = true; }
            used[best] = true;
            from = best + 1;
            pending.add(paired(d, ins.get(best)));
        }
        for (var j = 0; j < ins.size(); j++) if (!used[j]) pending.add(new Change("added", null, ins.get(j), null, null));
        out.addAll(replaced(pending));
    }

    /** A removal straight followed by an addition, too different to pair by words, is one paragraph rewritten in place. */
    private static List<Change> replaced(List<Change> cs) {
        var out = new ArrayList<Change>();
        for (var k = 0; k < cs.size(); k++) {
            var c = cs.get(k);
            if (c.type().equals("removed") && k + 1 < cs.size() && cs.get(k + 1).type().equals("added")) {
                out.add(new Change("replaced", c.old(), cs.get(++k).nu(), null, null));
            } else out.add(c);
        }
        return out;
    }

    private static Change paired(Block d, Block n) {
        if (d.plain().equals(n.plain())) {
            var note = d.kind().equals(n.kind()) ? "markup" : d.kind() + " → " + n.kind();
            return new Change("format", d, n, WordDiff.inline(d.raw(), n.raw(), context), note);
        }
        var note = d.kind().equals(n.kind()) ? null : d.kind() + " → " + n.kind();
        return new Change("changed", d, n, WordDiff.inline(d.plain(), n.plain(), context), note);
    }

    /** A paragraph removed in one place and added unchanged in another is one move, not two edits. */
    private static List<Change> moves(List<Change> in) {
        var added = new HashMap<String, Deque<Integer>>();
        for (var k = 0; k < in.size(); k++)
            if (in.get(k).type().equals("added") && in.get(k).nu().plain().length() > 20)
                added.computeIfAbsent(in.get(k).nu().plain(), x -> new ArrayDeque<>()).add(k);
        var out = new ArrayList<Change>(in);
        var drop = new HashSet<Integer>();
        for (var k = 0; k < in.size(); k++) {
            var c = in.get(k);
            if (!c.type().equals("removed")) continue;
            var q = added.get(c.old().plain());
            if (q == null || q.isEmpty()) continue;
            var j = q.poll();
            var n = in.get(j).nu();
            out.set(k, new Change("moved", c.old(), n, null, c.old().raw().equals(n.raw()) ? null : "and restyled"));
            drop.add(j);
        }
        var res = new ArrayList<Change>();
        for (var k = 0; k < out.size(); k++) if (!drop.contains(k)) res.add(out.get(k));
        return res;
    }
}
