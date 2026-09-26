package uskoag.gservices.docs;

import java.util.*;

/**
 * A sequence diff built for prose, where most of a large document is unchanged and paragraphs are
 * nearly all unique: the common head and tail are skipped first (so a three-word edit in a thousand
 * pages costs a scan, not a matrix), then lines unique to both sides anchor the rest (patience), and
 * only small leftover gaps get a full LCS. A leftover gap too large for LCS is honestly reported as
 * removed-then-added rather than guessed at.
 */
public final class Diff {

    /** '=' same, '-' only in a, '+' only in b; a/b are indexes into each side (-1 when absent). */
    record Op(char k, int a, int b) {}

    private static final long LCS_CELLS = 4_000_000;

    private Diff() {}

    static <T> List<Op> of(List<T> a, List<T> b) {
        var out = new ArrayList<Op>();
        run(a, 0, a.size(), b, 0, b.size(), out);
        return out;
    }

    private static <T> void run(List<T> a, int a0, int a1, List<T> b, int b0, int b1, List<Op> out) {
        var head = new ArrayList<Op>();
        while (a0 < a1 && b0 < b1 && a.get(a0).equals(b.get(b0))) head.add(new Op('=', a0++, b0++));
        var tail = new ArrayDeque<Op>();
        while (a0 < a1 && b0 < b1 && a.get(a1 - 1).equals(b.get(b1 - 1))) tail.addFirst(new Op('=', --a1, --b1));
        out.addAll(head);
        middle(a, a0, a1, b, b0, b1, out);
        out.addAll(tail);
    }

    private static <T> void middle(List<T> a, int a0, int a1, List<T> b, int b0, int b1, List<Op> out) {
        if (a0 == a1 || b0 == b1) { gap(a0, a1, b0, b1, out); return; }
        var anchors = anchors(a, a0, a1, b, b0, b1);
        if (!anchors.isEmpty()) {
            int pa = a0, pb = b0;
            for (var m : anchors) {
                run(a, pa, m[0], b, pb, m[1], out);
                out.add(new Op('=', m[0], m[1]));
                pa = m[0] + 1; pb = m[1] + 1;
            }
            run(a, pa, a1, b, pb, b1, out);
        } else if ((long) (a1 - a0) * (b1 - b0) <= LCS_CELLS) lcs(a, a0, a1, b, b0, b1, out);
        else gap(a0, a1, b0, b1, out);
    }

    private static void gap(int a0, int a1, int b0, int b1, List<Op> out) {
        for (var i = a0; i < a1; i++) out.add(new Op('-', i, -1));
        for (var j = b0; j < b1; j++) out.add(new Op('+', -1, j));
    }

    /** Pairs of positions of items occurring exactly once on each side, longest increasing run of them. */
    private static <T> List<int[]> anchors(List<T> a, int a0, int a1, List<T> b, int b0, int b1) {
        var ca = new HashMap<T, int[]>();
        for (var i = a0; i < a1; i++) ca.computeIfAbsent(a.get(i), k -> new int[]{0, -1, 0, -1})[0]++;
        for (var i = a0; i < a1; i++) ca.get(a.get(i))[1] = i;
        for (var j = b0; j < b1; j++) { var c = ca.get(b.get(j)); if (c != null) { c[2]++; c[3] = j; } }
        var pairs = new ArrayList<int[]>();
        for (var i = a0; i < a1; i++) {
            var c = ca.get(a.get(i));
            if (c[0] == 1 && c[2] == 1 && c[1] == i) pairs.add(new int[]{i, c[3]});
        }
        return lis(pairs);
    }

    private static List<int[]> lis(List<int[]> pairs) {
        var n = pairs.size();
        var tails = new int[n];
        var prev = new int[n];
        var len = 0;
        for (var i = 0; i < n; i++) {
            int lo = 0, hi = len;
            while (lo < hi) { var mid = (lo + hi) >>> 1; if (pairs.get(tails[mid])[1] < pairs.get(i)[1]) lo = mid + 1; else hi = mid; }
            prev[i] = lo > 0 ? tails[lo - 1] : -1;
            tails[lo] = i;
            if (lo == len) len++;
        }
        var res = new ArrayList<int[]>();
        for (var i = len == 0 ? -1 : tails[len - 1]; i >= 0; i = prev[i]) res.add(pairs.get(i));
        Collections.reverse(res);
        return res;
    }

    private static <T> void lcs(List<T> a, int a0, int a1, List<T> b, int b0, int b1, List<Op> out) {
        int n = a1 - a0, m = b1 - b0;
        var t = new int[n + 1][m + 1];
        for (var i = n - 1; i >= 0; i--)
            for (var j = m - 1; j >= 0; j--)
                t[i][j] = a.get(a0 + i).equals(b.get(b0 + j)) ? t[i + 1][j + 1] + 1 : Math.max(t[i + 1][j], t[i][j + 1]);
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a.get(a0 + i).equals(b.get(b0 + j))) { out.add(new Op('=', a0 + i++, b0 + j++)); }
            else if (t[i + 1][j] >= t[i][j + 1]) out.add(new Op('-', a0 + i++, -1));
            else out.add(new Op('+', -1, b0 + j++));
        }
        while (i < n) out.add(new Op('-', a0 + i++, -1));
        while (j < m) out.add(new Op('+', -1, b0 + j++));
    }
}
