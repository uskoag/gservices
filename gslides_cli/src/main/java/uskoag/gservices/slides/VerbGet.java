package uskoag.gservices.slides;

import java.util.List;

/** Read at whichever depth the address specifies: deck, page, or one element. */
public final class VerbGet {

    private VerbGet() {}

    static void run(List<String> a) throws Exception {
        var format = Args.valOr(a, "--format", "json");
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var pageRef = Args.pos(a);
        var elemRef = Args.pos(a);
        Args.noneLeft(a, "get");

        if (pageRef == null) { deckInfo(deck, format); return; }

        var pres = Deck.get(deck, "title,pageSize,slides(objectId,pageElements)");
        Geom.adoptPageSize(pres.getPageSize());
        var page = Deck.page(pres, pageRef);

        if (elemRef == null) {
            var info = PageInfo.of(page, pageNumber(pres, page), true);
            if (format.equals("json")) Out.json(info); else text(info);
            return;
        }

        var el = Deck.findOn(page, elemRef);
        if (el == null) Out.die("no element " + elemRef + " on page " + page.getObjectId());
        Out.json(ElementInfo.of(el, 0, true));
    }

    private static void deckInfo(String deck, String format) throws Exception {
        var pres = Deck.get(deck, "title,pageSize,slides(objectId)");
        Geom.adoptPageSize(pres.getPageSize());
        var n = pres.getSlides() == null ? 0 : pres.getSlides().size();
        var info = new DeckInfo(deck, pres.getTitle(), Geom.pageW, Geom.pageH, n, Deck.url(deck));
        if (format.equals("json")) Out.json(info);
        else {
            Out.data("title:  " + info.title());
            Out.data("size:   " + String.format("%.2f x %.2f in", info.widthIn(), info.heightIn()));
            Out.data("slides: " + info.slides());
            Out.data("url:    " + info.url());
        }
    }

    private static int pageNumber(com.google.api.services.slides.v1.model.Presentation pres,
                                 com.google.api.services.slides.v1.model.Page page) {
        var slides = pres.getSlides();
        for (var i = 0; i < slides.size(); i++)
            if (slides.get(i).getObjectId().equals(page.getObjectId())) return i + 1;
        return -1;
    }

    private static void text(PageInfo info) {
        Out.data("slide " + info.n() + "  pageId=" + info.pageId()
                + (info.background() == null ? "" : "  bg=" + info.background()));
        for (var e : info.elements()) line(e, "  ");
    }

    private static void line(ElementInfo e, String indent) {
        Out.data(String.format("%s[z%d] %-24s %-18s %5.2f,%-5.2f %4.2fx%-4.2f%s",
                indent, e.z(), e.id(), e.type(),
                e.x(), e.y(), e.w(), e.h(),
                e.text() == null ? "" : "  " + e.text().replace("\n", " / ")));
        if (e.children() != null) for (var c : e.children()) line(c, indent + "    ");
    }
}
