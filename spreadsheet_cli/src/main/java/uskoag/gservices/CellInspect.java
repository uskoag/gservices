package uskoag.gservices;

import com.google.api.services.sheets.v4.model.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for the `inspect` command: turning per-cell background colors into
 * compact, range-oriented output.
 *
 * Notes are intrinsically cell-specific, so they need no special treatment.
 * Colors, however, are usually painted over blocks of cells; listing one line
 * per cell would be noise.  {@link #mergeRegions} greedily fuses same-colored
 * contiguous cells into maximal rectangles so the output reads the way the
 * sheet actually looks ("light-yellow A2:Q40", not 800 identical lines).
 */
public class CellInspect {

    /** A maximal rectangle painted a single background color. Coordinates are 1-based. */
    public static class Region {
        public final String colorHex;
        public final int startRow, startCol, endRow, endCol;

        Region(String colorHex, int startRow, int startCol, int endRow, int endCol) {
            this.colorHex = colorHex;
            this.startRow = startRow; this.startCol = startCol;
            this.endRow = endRow; this.endCol = endCol;
        }

        /** A1 range, e.g. "A2:D40", or a bare "B5" for a single cell. */
        public String a1() {
            String topLeft = CellRange.colToLetter(startCol) + startRow;
            if (startRow == endRow && startCol == endCol) return topLeft;
            return topLeft + ":" + CellRange.colToLetter(endCol) + endRow;
        }
    }

    /** Convert a Sheets API Color to "#rrggbb" (alpha ignored). null → null. */
    public static String colorToHex(Color c) {
        if (c == null) return null;
        return String.format("#%02x%02x%02x", comp(c.getRed()), comp(c.getGreen()), comp(c.getBlue()));
    }

    private static int comp(Float f) {
        int v = Math.round((f == null ? 0f : f) * 255f);
        return Math.max(0, Math.min(255, v));
    }

    /** True for the default (unformatted) white background, which we treat as "no fill". */
    public static boolean isWhite(String hex) {
        return hex == null || hex.equalsIgnoreCase("#ffffff");
    }

    // ── nearest-name palette (Google Sheets default swatches + primaries) ──
    private static final String[][] PALETTE = {
        {"#ffffff", "white"}, {"#000000", "black"},
        {"#f3f3f3", "very light gray"}, {"#efefef", "very light gray"}, {"#e7e6e6", "light gray"},
        {"#d9d9d9", "light gray"}, {"#cccccc", "light gray"}, {"#b7b7b7", "gray"},
        {"#999999", "gray"}, {"#666666", "dark gray"}, {"#434343", "dark gray"},
        {"#f4cccc", "light red"}, {"#ea9999", "pale red"}, {"#ff0000", "red"}, {"#cc0000", "dark red"},
        {"#fce5cd", "light orange"}, {"#f9cb9c", "pale orange"}, {"#ff9900", "orange"},
        {"#fff2cc", "light yellow"}, {"#ffe599", "pale yellow"}, {"#ffff00", "yellow"},
        {"#d9ead3", "light green"}, {"#b6d7a8", "pale green"}, {"#00ff00", "green"}, {"#38761d", "dark green"},
        {"#d0e0e3", "light cyan"}, {"#00ffff", "cyan"},
        {"#cfe2f3", "light blue"}, {"#9fc5e8", "pale blue"}, {"#0000ff", "blue"}, {"#1155cc", "dark blue"},
        {"#d9d2e9", "light purple"}, {"#b4a7d6", "pale purple"}, {"#9900ff", "purple"},
        {"#ead1dc", "light pink"}, {"#ff00ff", "magenta"},
        {"#b45f06", "brown"},
    };

    /** Coarse human label for a hex color (nearest palette swatch by RGB distance). */
    public static String approxName(String hex) {
        int[] t = rgb(hex);
        String best = null;
        long bestDist = Long.MAX_VALUE;
        for (String[] p : PALETTE) {
            int[] c = rgb(p[0]);
            long d = sq(t[0] - c[0]) + sq(t[1] - c[1]) + sq(t[2] - c[2]);
            if (d < bestDist) { bestDist = d; best = p[1]; }
        }
        return best;
    }

    private static long sq(int x) { return (long) x * x; }

    private static int[] rgb(String hex) {
        return new int[]{
            Integer.parseInt(hex.substring(1, 3), 16),
            Integer.parseInt(hex.substring(3, 5), 16),
            Integer.parseInt(hex.substring(5, 7), 16),
        };
    }

    /**
     * Greedily merge same-colored cells into maximal rectangles.
     * grid[r][c] = color hex, or null for no fill. {@code rowOffset}/{@code colOffset}
     * map grid index 0 → the 1-based sheet coordinate of the grid's top-left corner.
     * Regions come back in reading order (top→bottom, then left→right by top-left corner).
     */
    public static List<Region> mergeRegions(String[][] grid, int rowOffset, int colOffset) {
        int rows = grid.length;
        int cols = rows == 0 ? 0 : grid[0].length;
        boolean[][] seen = new boolean[rows][cols];
        List<Region> out = new ArrayList<>();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (seen[r][c] || grid[r][c] == null) continue;
                String key = grid[r][c];

                // Widen along the row while the color holds.
                int w = 1;
                while (c + w < cols && !seen[r][c + w] && key.equals(grid[r][c + w])) w++;

                // Grow downward only while the *entire* [c, c+w) span still matches.
                int h = 1;
                boolean grow = true;
                while (grow && r + h < rows) {
                    for (int cc = c; cc < c + w; cc++) {
                        if (seen[r + h][cc] || !key.equals(grid[r + h][cc])) { grow = false; break; }
                    }
                    if (grow) h++;
                }

                for (int rr = r; rr < r + h; rr++)
                    for (int cc = c; cc < c + w; cc++) seen[rr][cc] = true;

                out.add(new Region(key, r + rowOffset, c + colOffset,
                                        r + h - 1 + rowOffset, c + w - 1 + colOffset));
            }
        }
        return out;
    }
}
