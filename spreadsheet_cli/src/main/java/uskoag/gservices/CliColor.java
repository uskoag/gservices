package uskoag.gservices;

import com.google.api.services.sheets.v4.model.Color;

import java.util.List;

public class CliColor {

    /** Soft, mutually distinguishable swatches (matches CellInspect's palette names) — the
     *  default when `highlight` isn't given explicit --colors; cycled if more values than colors. */
    public static final List<String> DEFAULT_PALETTE = List.of(
        "#d9ead3", // light green
        "#fff2cc", // light yellow
        "#f4cccc", // light red
        "#cfe2f3", // light blue
        "#d9d2e9", // light purple
        "#fce5cd", // light orange
        "#d0e0e3", // light cyan
        "#ead1dc"  // light pink
    );

    /** "#rrggbb" or "rrggbb" → Sheets API Color (0..1 floats). */
    public static Color hexToColor(String hex) {
        var h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) throw new IllegalArgumentException("color must be #rrggbb: " + hex);
        var r = Integer.parseInt(h.substring(0, 2), 16) / 255f;
        var g = Integer.parseInt(h.substring(2, 4), 16) / 255f;
        var b = Integer.parseInt(h.substring(4, 6), 16) / 255f;
        return new Color().setRed(r).setGreen(g).setBlue(b);
    }
}
