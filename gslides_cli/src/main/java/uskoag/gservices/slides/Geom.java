package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Dimension;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.PageElementProperties;
import com.google.api.services.slides.v1.model.Size;

/** Callers work in inches; EMU is the API's problem, not theirs. */
public final class Geom {

    static final double EMU_PER_INCH = 914400d;

    /** Default 10" x 5.625" 16:9 page, overridden from the deck's own pageSize when known. */
    static double pageW = 10.0, pageH = 5.625;

    private Geom() {}

    static long emu(double inches) { return Math.round(inches * EMU_PER_INCH); }

    static double inches(double emu) { return emu / EMU_PER_INCH; }

    static Dimension dim(double inches) {
        return new Dimension().setMagnitude((double) emu(inches)).setUnit("EMU");
    }

    static AffineTransform tfm(double x, double y) {
        return new AffineTransform().setScaleX(1.0).setScaleY(1.0).setShearX(0.0).setShearY(0.0)
                .setTranslateX((double) emu(x)).setTranslateY((double) emu(y)).setUnit("EMU");
    }

    static PageElementProperties props(String pageId, double x, double y, double w, double h) {
        return new PageElementProperties().setPageObjectId(pageId)
                .setSize(new Size().setWidth(dim(w)).setHeight(dim(h)))
                .setTransform(tfm(x, y));
    }

    static void adoptPageSize(Size pageSize) {
        if (pageSize == null) return;
        if (pageSize.getWidth() != null && pageSize.getWidth().getMagnitude() != null)
            pageW = inches(pageSize.getWidth().getMagnitude());
        if (pageSize.getHeight() != null && pageSize.getHeight().getMagnitude() != null)
            pageH = inches(pageSize.getHeight().getMagnitude());
    }

    /** Reported size is pre-scale, so the on-slide size needs the transform folded in. */
    static Box boxOf(PageElement el) {
        var t = el.getTransform();
        var s = el.getSize();
        if (t == null || s == null) return new Box(0, 0, 0, 0, 0);
        var sx = t.getScaleX() != null ? t.getScaleX() : 1.0;
        var sy = t.getScaleY() != null ? t.getScaleY() : 1.0;
        var w = s.getWidth() != null && s.getWidth().getMagnitude() != null ? s.getWidth().getMagnitude() * sx : 0;
        var h = s.getHeight() != null && s.getHeight().getMagnitude() != null ? s.getHeight().getMagnitude() * sy : 0;
        var x = t.getTranslateX() != null ? t.getTranslateX() : 0;
        var y = t.getTranslateY() != null ? t.getTranslateY() : 0;
        return new Box(round(inches(x)), round(inches(y)), round(inches(w)), round(inches(h)), rotationOf(t));
    }

    private static double rotationOf(AffineTransform t) {
        var shx = t.getShearX() != null ? t.getShearX() : 0.0;
        var sx = t.getScaleX() != null ? t.getScaleX() : 1.0;
        return shx == 0 ? 0 : round(Math.toDegrees(Math.atan2(shx, sx)));
    }

    private static double round(double v) { return Math.round(v * 1000d) / 1000d; }

    /**
     * The API stretches an image to its box, so a full-bleed image must be sized to its
     * own aspect and allowed to overflow, otherwise it distorts.
     */
    static Box cover(double aspect) {
        var pa = pageW / pageH;
        if (aspect >= pa) {
            var h = pageH; var w = h * aspect;
            return new Box((pageW - w) / 2, 0, w, h, 0);
        }
        var w = pageW; var h = w / aspect;
        return new Box(0, (pageH - h) / 2, w, h, 0);
    }
}
