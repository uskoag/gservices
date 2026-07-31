package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.OpaqueColor;
import com.google.api.services.slides.v1.model.OptionalColor;
import com.google.api.services.slides.v1.model.Outline;
import com.google.api.services.slides.v1.model.RgbColor;
import com.google.api.services.slides.v1.model.SolidFill;

public final class Colors {

    private Colors() {}

    static RgbColor rgb(String hex) {
        var h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) Out.die("colour must be RRGGBB or #RRGGBB, got: " + hex);
        return new RgbColor()
                .setRed(Integer.parseInt(h.substring(0, 2), 16) / 255f)
                .setGreen(Integer.parseInt(h.substring(2, 4), 16) / 255f)
                .setBlue(Integer.parseInt(h.substring(4, 6), 16) / 255f);
    }

    static OpaqueColor opaque(String hex) { return new OpaqueColor().setRgbColor(rgb(hex)); }

    static OptionalColor optional(String hex) { return new OptionalColor().setOpaqueColor(opaque(hex)); }

    static SolidFill fill(String hex, double alpha) {
        return new SolidFill().setColor(opaque(hex)).setAlpha((float) alpha);
    }

    static Outline noOutline() { return new Outline().setPropertyState("NOT_RENDERED"); }

    static String hex(RgbColor c) {
        if (c == null) return null;
        return String.format("#%02X%02X%02X",
                Math.round((c.getRed() != null ? c.getRed() : 0) * 255),
                Math.round((c.getGreen() != null ? c.getGreen() : 0) * 255),
                Math.round((c.getBlue() != null ? c.getBlue() : 0) * 255));
    }

    static String hex(OptionalColor c) {
        return c == null || c.getOpaqueColor() == null ? null : hex(c.getOpaqueColor().getRgbColor());
    }
}
