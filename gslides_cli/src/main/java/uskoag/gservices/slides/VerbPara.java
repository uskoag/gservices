package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.Dimension;
import com.google.api.services.slides.v1.model.ParagraphStyle;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.UpdateParagraphStyleRequest;
import java.util.ArrayList;
import java.util.List;

/** Paragraph-level styling: the second, independent overlay on the same character sequence. */
public final class VerbPara {

    private VerbPara() {}

    static void run(List<String> a) throws Exception {
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var target = ElemRange.parse(Args.req(a, "<elem>[@S:E]"));

        var align = Args.val(a, "--align");
        var bullets = Args.val(a, "--bullets");
        var lineSpacing = Args.dblVal(a, "--line-spacing");
        var indent = Args.dblVal(a, "--indent");
        var above = Args.dblVal(a, "--space-above");
        var below = Args.dblVal(a, "--space-below");
        Args.noneLeft(a, "para");

        if (align == null && bullets == null && lineSpacing == null
                && indent == null && above == null && below == null)
            Out.die("para: nothing to change (--align --bullets --line-spacing --indent --space-above --space-below)");

        GSlidesConfig.require(deck, "write");
        var r = target.resolve(deck);
        var reqs = new ArrayList<Request>();

        var style = new ParagraphStyle();
        var fields = new ArrayList<String>();
        if (align != null) { style.setAlignment(align.toUpperCase()); fields.add("alignment"); }
        if (lineSpacing != null) { style.setLineSpacing(lineSpacing.floatValue()); fields.add("lineSpacing"); }
        if (indent != null) { style.setIndentStart(pt(Geom.emu(indent))); fields.add("indentStart"); }
        if (above != null) { style.setSpaceAbove(pts(above)); fields.add("spaceAbove"); }
        if (below != null) { style.setSpaceBelow(pts(below)); fields.add("spaceBelow"); }
        if (!fields.isEmpty())
            reqs.add(new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest()
                    .setObjectId(r.elem()).setTextRange(RichRequests.range(r.start(), r.end()))
                    .setStyle(style).setFields(String.join(",", fields))));

        if (bullets != null) reqs.add(RichRequests.bullets(r.elem(), r.start(), r.end(), preset(bullets)));

        Api.flush(deck, reqs);
        Out.success("paragraph style applied to " + r.elem() + "@" + r.start() + ":" + r.end());
    }

    private static Dimension pt(long emu) { return new Dimension().setMagnitude((double) emu).setUnit("EMU"); }

    private static Dimension pts(double v) { return new Dimension().setMagnitude(v).setUnit("PT"); }

    /** Accept the short names an agent would guess as well as the API's own presets. */
    private static String preset(String v) {
        return switch (v.toLowerCase()) {
            case "disc", "bullet", "bulleted" -> "BULLET_DISC_CIRCLE_SQUARE";
            case "number", "numbered", "digit" -> "NUMBERED_DIGIT_ALPHA_ROMAN";
            case "arrow" -> "BULLET_ARROW_DIAMOND_DISC";
            case "check" -> "BULLET_CHECKBOX";
            default -> v.toUpperCase();
        };
    }
}
