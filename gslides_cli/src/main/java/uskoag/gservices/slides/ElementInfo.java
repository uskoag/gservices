package uskoag.gservices.slides;

import java.util.List;

/** Null fields are omitted by Gson, so an element only reports what it actually has. */
public record ElementInfo(
        String id,
        String type,
        Integer z,
        Double x, Double y, Double w, Double h, Double rot,
        String text,
        String imageUrl,
        List<ElementInfo> children) {

    static ElementInfo of(com.google.api.services.slides.v1.model.PageElement el, int z, boolean withText) {
        var b = Geom.boxOf(el);
        var txt = withText ? Els.textOf(el) : null;
        if (txt != null && txt.isEmpty()) txt = null;
        var kids = (List<ElementInfo>) null;
        if (el.getElementGroup() != null && el.getElementGroup().getChildren() != null) {
            kids = new java.util.ArrayList<>();
            var i = 0;
            for (var c : el.getElementGroup().getChildren()) kids.add(of(c, i++, withText));
        }
        return new ElementInfo(el.getObjectId(), Els.typeOf(el), z,
                b.x(), b.y(), b.w(), b.h(), b.rot() == 0 ? null : b.rot(),
                txt, Els.imageUrl(el), kids);
    }
}
