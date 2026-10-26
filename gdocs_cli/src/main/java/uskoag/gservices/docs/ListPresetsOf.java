package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.List;
import java.util.ArrayList;

/** The API makes lists only from presets, so a source list gets the preset closest to its first three levels, and the gap is reported. */
final class ListPresetsOf {

    private ListPresetsOf() {}

    static String of(List src, java.util.List<String> report, String id) {
        var lv = src.getListProperties().getNestingLevels();
        var g = new ArrayList<String>();
        for (var i = 0; i < Math.min(3, lv.size()); i++) {
            var l = lv.get(i);
            var type = l.getGlyphType() == null || l.getGlyphType().equals("GLYPH_TYPE_UNSPECIFIED") ? "" : l.getGlyphType();
            g.add(type.isEmpty() ? "bullet" : type + " " + (l.getGlyphFormat() == null ? "" : l.getGlyphFormat()));
        }
        var l0 = lv.get(0);
        var preset = switch (l0.getGlyphType() == null ? "" : l0.getGlyphType()) {
            case "UPPER_ALPHA" -> "NUMBERED_UPPERALPHA_ALPHA_ROMAN";
            case "UPPER_ROMAN" -> "NUMBERED_UPPERROMAN_UPPERALPHA_DECIMAL";
            case "ZERO_DECIMAL" -> "NUMBERED_ZERODECIMAL_ALPHA_ROMAN";
            case "DECIMAL" -> l0.getGlyphFormat() != null && l0.getGlyphFormat().contains(")") ? "NUMBERED_DECIMAL_ALPHA_ROMAN_PARENS" : "NUMBERED_DECIMAL_ALPHA_ROMAN";
            case "ALPHA", "ROMAN" -> "NUMBERED_DECIMAL_ALPHA_ROMAN";
            default -> "BULLET_DISC_CIRCLE_SQUARE";
        };
        var exact = switch (preset) {
            case "NUMBERED_DECIMAL_ALPHA_ROMAN" -> g.size() > 2 && g.get(1).startsWith("ALPHA") && g.get(2).startsWith("ROMAN") && l0.getGlyphFormat().equals("%0.");
            case "BULLET_DISC_CIRCLE_SQUARE" -> g.stream().allMatch(s -> s.equals("bullet"));
            default -> true;
        };
        if (!exact) report.add("note: list " + id + " (" + String.join(" | ", g) + ") gets " + preset + " — nearest preset, glyphs differ at some levels");
        return preset;
    }
}
