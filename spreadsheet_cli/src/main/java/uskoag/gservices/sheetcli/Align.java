package uskoag.gservices.sheetcli;

import java.util.*;

/**
 * Pairs two sequences of keys (column headers, row keys) the way a person would: the longest run that
 * stays in order, so 4 columns inserted before M come out as 4 unmatched new columns and everything after
 * them still matched, rather than 4000 shifted cells. The common head and tail are matched first, so the
 * quadratic part only ever sees the region that actually changed.
 */
final class Align {

    /** Above this many key pairs the middle is matched by position, and the report says so. */
    static final long LIMIT = 16_000_000L;
    static boolean positionalFallback;

    private Align() {}

    /** map[i] = index in b matched to a[i], or -1 when a[i] has no partner. */
    static int[] match(List<String> a, List<String> b) {
        var map = new int[a.size()];
        Arrays.fill(map, -1);
        var head = 0;
        while (head < a.size() && head < b.size() && a.get(head).equals(b.get(head))) { map[head] = head; head++; }
        var tail = 0;
        while (tail < a.size() - head && tail < b.size() - head
                && a.get(a.size() - 1 - tail).equals(b.get(b.size() - 1 - tail))) {
            map[a.size() - 1 - tail] = b.size() - 1 - tail;
            tail++;
        }
        var n = a.size() - head - tail;
        var m = b.size() - head - tail;
        if (n == 0 || m == 0) return map;
        if ((long) n * m > LIMIT) {
            positionalFallback = true;
            for (var i = 0; i < Math.min(n, m); i++) map[head + i] = head + i;
            return map;
        }
        lcs(a, b, head, n, m, map);
        return map;
    }

    private static void lcs(List<String> a, List<String> b, int off, int n, int m, int[] map) {
        var dp = new int[n + 1][m + 1];
        for (var i = n - 1; i >= 0; i--)
            for (var j = m - 1; j >= 0; j--)
                dp[i][j] = a.get(off + i).equals(b.get(off + j)) ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a.get(off + i).equals(b.get(off + j))) { map[off + i] = off + j; i++; j++; }
            else if (dp[i + 1][j] >= dp[i][j + 1]) i++;
            else j++;
        }
    }

    /**
     * Unmatched runs that sit in the same gap and have the same length are the same items with a changed
     * key (a typo fixed in a header, a renamed row label), not a delete plus an insert: pair them up when
     * `alike` agrees. Returns the indexes of a that were paired this way.
     */
    static Set<Integer> pairGaps(int[] map, int bSize, java.util.function.BiPredicate<Integer, Integer> alike) {
        var paired = new HashSet<Integer>();
        var usedB = new boolean[bSize];
        for (var x : map) if (x >= 0) usedB[x] = true;
        int i = 0, prevB = -1;
        while (i < map.length) {
            if (map[i] >= 0) { prevB = map[i]; i++; continue; }
            var start = i;
            while (i < map.length && map[i] < 0) i++;
            var nextB = i < map.length ? map[i] : bSize;
            var freeB = new ArrayList<Integer>();
            for (var j = prevB + 1; j < nextB; j++) if (!usedB[j]) freeB.add(j);
            if (freeB.size() == i - start)
                for (var k = 0; k < freeB.size(); k++)
                    if (alike.test(start + k, freeB.get(k))) { map[start + k] = freeB.get(k); usedB[freeB.get(k)] = true; paired.add(start + k); }
        }
        return paired;
    }
}
