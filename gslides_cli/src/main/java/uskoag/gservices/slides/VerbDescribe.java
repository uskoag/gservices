package uskoag.gservices.slides;

import java.util.ArrayList;
import java.util.List;

/**
 * The verb to call first. Because slide element ids are not derivable, the primitives
 * are unusable without a way to discover them -- and dumping each slide in turn is the
 * context-flooding mistake `describeschema` exists to prevent in uskoag-sheetcli.
 */
public final class VerbDescribe {

    private VerbDescribe() {}

    static void run(List<String> a) throws Exception {
        var format = Args.valOr(a, "--format", "text");
        var deck = Deck.presId(Args.req(a, "<deck>"));
        Args.noneLeft(a, "describe");
        GSlidesConfig.require(deck, "read");

        var pres = Deck.get(deck, "title,pageSize,slides(objectId,pageElements)");
        Geom.adoptPageSize(pres.getPageSize());
        var slides = pres.getSlides() == null ? List.<com.google.api.services.slides.v1.model.Page>of() : pres.getSlides();

        if (format.equals("json")) {
            var pages = new ArrayList<PageInfo>();
            for (var i = 0; i < slides.size(); i++) pages.add(PageInfo.of(slides.get(i), i + 1, true));
            Out.json(pages);
            return;
        }

        Out.data("Deck \"" + pres.getTitle() + "\" -- " + slides.size() + " slides, "
                + fmt(Geom.pageW) + " x " + fmt(Geom.pageH) + " in");
        for (var i = 0; i < slides.size(); i++) {
            var s = slides.get(i);
            Out.data(String.format("[%3d] %s", i + 1, s.getObjectId()));
            if (s.getPageElements() == null) continue;
            for (var el : s.getPageElements()) {
                var b = Geom.boxOf(el);
                Out.data(String.format("      %-24s %-18s %5.2f,%-5.2f %4.2fx%-4.2f  %s",
                        el.getObjectId(), Els.typeOf(el), b.x(), b.y(), b.w(), b.h(), Els.preview(el, 60)));
            }
        }
    }

    private static String fmt(double v) { return String.format("%.2f", v); }
}
