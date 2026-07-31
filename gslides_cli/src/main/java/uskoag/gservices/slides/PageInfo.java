package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.Page;
import java.util.ArrayList;
import java.util.List;

public record PageInfo(int n, String pageId, String background, List<ElementInfo> elements) {

    static PageInfo of(Page page, int n, boolean withText) {
        var els = new ArrayList<ElementInfo>();
        if (page.getPageElements() != null) {
            var z = 0;
            for (var e : page.getPageElements()) els.add(ElementInfo.of(e, z++, withText));
        }
        return new PageInfo(n, page.getObjectId(), background(page), els);
    }

    private static String background(Page page) {
        var pp = page.getPageProperties();
        if (pp == null || pp.getPageBackgroundFill() == null) return null;
        var bg = pp.getPageBackgroundFill();
        if (bg.getSolidFill() != null && bg.getSolidFill().getColor() != null)
            return Colors.hex(bg.getSolidFill().getColor().getRgbColor());
        if (bg.getStretchedPictureFill() != null) return "image";
        return null;
    }
}
