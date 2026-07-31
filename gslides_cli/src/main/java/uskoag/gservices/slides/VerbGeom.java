package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.UpdatePageElementTransformRequest;
import com.google.api.services.slides.v1.model.UpdatePageElementsZOrderRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * The API has no set-size request: an element's size is fixed at creation and resizing
 * is expressed as scale on its transform. So a target width in inches becomes
 * target/base scale, which needs the element's unscaled base size read back first.
 */
public final class VerbGeom {

    private VerbGeom() {}

    static void run(List<String> a) throws Exception {
        var at = Args.val(a, "--at");
        var size = Args.val(a, "--size");
        var z = Args.val(a, "--z");
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var elem = Args.req(a, "<elem>");
        Args.noneLeft(a, "geom");
        if (at == null && size == null && z == null)
            Out.die("geom: nothing to change (--at X,Y --size W,H --z front|back|forward|backward)");

        var pres = Deck.get(deck, "slides(objectId,pageElements)");
        var el = Deck.element(pres, elem);
        var reqs = new ArrayList<Request>();

        if (at != null || size != null) {
            var cur = Geom.boxOf(el);
            var pos = at != null ? pair(at, "--at") : new double[]{ cur.x(), cur.y() };
            var t = new AffineTransform().setUnit("EMU")
                    .setTranslateX((double) Geom.emu(pos[0])).setTranslateY((double) Geom.emu(pos[1]))
                    .setShearX(0.0).setShearY(0.0);
            if (size != null) {
                var want = pair(size, "--size");
                var base = baseSize(el);
                if (base == null) Out.die(elem + " reports no base size, so it cannot be resized");
                t.setScaleX(Geom.emu(want[0]) / base[0]).setScaleY(Geom.emu(want[1]) / base[1]);
            } else {
                t.setScaleX(el.getTransform().getScaleX()).setScaleY(el.getTransform().getScaleY());
            }
            reqs.add(new Request().setUpdatePageElementTransform(new UpdatePageElementTransformRequest()
                    .setObjectId(elem).setApplyMode("ABSOLUTE").setTransform(t)));
        }

        if (z != null)
            reqs.add(new Request().setUpdatePageElementsZOrder(new UpdatePageElementsZOrderRequest()
                    .setPageElementObjectIds(List.of(elem)).setOperation(zOp(z))));

        Api.flush(deck, reqs);
        Out.success("geometry updated on " + elem);
    }

    private static double[] baseSize(com.google.api.services.slides.v1.model.PageElement el) {
        var s = el.getSize();
        if (s == null || s.getWidth() == null || s.getHeight() == null) return null;
        var w = s.getWidth().getMagnitude();
        var h = s.getHeight().getMagnitude();
        return (w == null || h == null || w == 0 || h == 0) ? null : new double[]{ w, h };
    }

    private static String zOp(String v) {
        return switch (v.toLowerCase()) {
            case "front", "bring-to-front" -> "BRING_TO_FRONT";
            case "back", "send-to-back" -> "SEND_TO_BACK";
            case "forward" -> "BRING_FORWARD";
            case "backward" -> "SEND_BACKWARD";
            default -> { Out.die("--z expects front|back|forward|backward, got: " + v); yield null; }
        };
    }

    private static double[] pair(String v, String flag) {
        var p = v.split(",");
        if (p.length != 2) Out.die(flag + " expects two comma-separated inches, got: " + v);
        return new double[]{ Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()) };
    }
}
