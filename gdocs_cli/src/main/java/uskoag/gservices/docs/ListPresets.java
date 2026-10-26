package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.DocumentTab;

/**
 * The API cannot add a paragraph to an existing list except by splitting a paragraph already in it, so
 * a bullet copied from elsewhere starts a new list with the preset whose first glyph matches.
 */
final class ListPresets {

    static final String BULLET = "BULLET_DISC_CIRCLE_SQUARE", NUMBER = "NUMBERED_DECIMAL_ALPHA_ROMAN";

    private ListPresets() {}

    /** The preset a list paragraph gets: an explicit one, else the kind asked for, else {@code like}'s own when it is in a list. */
    static String choose(String list, String preset, Para like, DocumentTab tab) {
        if (preset != null) return preset;
        var inList = like != null && like.p().getBullet() != null;
        var ordered = inList && Styles.ordered(like.p(), tab);
        if ("number".equals(list)) return ordered ? of(like, tab) : NUMBER;
        if ("bullet".equals(list)) return inList && !ordered ? of(like, tab) : BULLET;
        return inList ? of(like, tab) : BULLET;
    }

    /** True for a numbered preset, false for a bullet one, null when the name says neither. */
    static Boolean numbered(String preset) {
        return preset == null ? null : preset.startsWith("NUMBERED_") ? Boolean.TRUE : preset.startsWith("BULLET_") ? Boolean.FALSE : null;
    }

    static void check(String label, String list, String preset, Integer level) {
        if (list != null && !list.equals("bullet") && !list.equals("number")) Out.die(label + ": list must be \"bullet\" or \"number\", not \"" + list + "\"");
        if (preset != null && numbered(preset) == null) Out.die(label + ": preset must be an API list preset name (BULLET_... or NUMBERED_..., e.g. " + NUMBER + "), not \"" + preset + "\"");
        if (preset != null && list != null && numbered(preset) != list.equals("number")) Out.die(label + ": preset " + preset + " is not a " + list + " list");
        if (level != null && (level < 0 || level > 8)) Out.die(label + ": level must be 0..8, not " + level);
    }

    static String of(Para like, DocumentTab tab) {
        var list = tab.getLists() == null ? null : tab.getLists().get(like.p().getBullet().getListId());
        var levels = list == null || list.getListProperties() == null ? null : list.getListProperties().getNestingLevels();
        var lvl = levels == null || levels.isEmpty() ? null : levels.get(0);
        if (lvl == null) return BULLET;
        var type = lvl.getGlyphType();
        if (type != null && !type.equals("GLYPH_TYPE_UNSPECIFIED") && !type.equals("NONE")) return switch (type) {
            case "UPPER_ALPHA" -> "NUMBERED_UPPERALPHA_ALPHA_ROMAN";
            case "UPPER_ROMAN" -> "NUMBERED_UPPERROMAN_UPPERALPHA_DECIMAL";
            case "ZERO_DECIMAL" -> "NUMBERED_ZERODECIMAL_ALPHA_ROMAN";
            default -> NUMBER;
        };
        var sym = lvl.getGlyphSymbol() == null ? "" : lvl.getGlyphSymbol();
        return switch (sym) {
            case "❖" -> "BULLET_DIAMONDX_ARROW3D_SQUARE";
            case "➔", "➢" -> "BULLET_ARROW_DIAMOND_DISC";
            case "★" -> "BULLET_STAR_CIRCLE_SQUARE";
            case "◆" -> "BULLET_DIAMOND_CIRCLE_SQUARE";
            default -> BULLET;
        };
    }
}
