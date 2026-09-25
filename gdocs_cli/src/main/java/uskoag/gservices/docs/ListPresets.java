package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.DocumentTab;

/**
 * The API cannot add a paragraph to an existing list except by splitting a paragraph already in it, so
 * a bullet copied from elsewhere starts a new list with the preset whose first glyph matches.
 */
final class ListPresets {

    private ListPresets() {}

    static String of(Para like, DocumentTab tab) {
        var list = tab.getLists() == null ? null : tab.getLists().get(like.p().getBullet().getListId());
        var levels = list == null || list.getListProperties() == null ? null : list.getListProperties().getNestingLevels();
        var lvl = levels == null || levels.isEmpty() ? null : levels.get(0);
        if (lvl == null) return "BULLET_DISC_CIRCLE_SQUARE";
        var type = lvl.getGlyphType();
        if (type != null && !type.equals("GLYPH_TYPE_UNSPECIFIED") && !type.equals("NONE")) return switch (type) {
            case "UPPER_ALPHA" -> "NUMBERED_UPPERALPHA_ALPHA_ROMAN";
            case "UPPER_ROMAN" -> "NUMBERED_UPPERROMAN_UPPERALPHA_DECIMAL";
            case "ZERO_DECIMAL" -> "NUMBERED_ZERODECIMAL_ALPHA_ROMAN";
            default -> "NUMBERED_DECIMAL_ALPHA_ROMAN";
        };
        var sym = lvl.getGlyphSymbol() == null ? "" : lvl.getGlyphSymbol();
        return switch (sym) {
            case "❖" -> "BULLET_DIAMONDX_ARROW3D_SQUARE";
            case "➔", "➢" -> "BULLET_ARROW_DIAMOND_DISC";
            case "★" -> "BULLET_STAR_CIRCLE_SQUARE";
            case "◆" -> "BULLET_DIAMOND_CIRCLE_SQUARE";
            default -> "BULLET_DISC_CIRCLE_SQUARE";
        };
    }
}
