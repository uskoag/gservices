package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.UpdatePageElementsZOrderRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-bleed image behind a slide's existing content, plus a translucent scrim so text
 * over it stays legible, plus bringing the pre-existing elements back to the front.
 *
 * The image is sized to its own aspect and allowed to overflow, because the API stretches
 * an image to its box and would otherwise distort it. Both added elements get
 * deterministic ids derived from the page, which is what lets --undo remove exactly them.
 */
public final class VerbImageBg {

    private VerbImageBg() {}

    static void run(List<String> a) throws Exception {
        var undo = Args.flag(a, "--undo");
        var scrim = Args.dblVal(a, "--scrim");
        var aspect = Args.dblVal(a, "--aspect");
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var pageRef = Args.req(a, "<page>");
        var src = undo ? null : Args.req(a, "<localPath|url>");
        Args.noneLeft(a, "imagebg");
        GSlidesConfig.require(deck, "write");

        var pres = Deck.get(deck, "pageSize,slides(objectId,pageElements(objectId))");
        Geom.adoptPageSize(pres.getPageSize());
        var page = Deck.page(pres, pageRef);
        var pageId = page.getObjectId();
        var safe = pageId.replaceAll("[^A-Za-z0-9_-]", "");
        var imgId = "bgimg_" + safe;
        var scrimId = "bgscrim_" + safe;

        if (undo) {
            Api.flush(deck, List.of(
                    new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(imgId)),
                    new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(scrimId))));
            Out.success("removed background (" + imgId + ", " + scrimId + ") from " + pageId);
            return;
        }

        var existing = new ArrayList<String>();
        if (page.getPageElements() != null)
            for (var el : page.getPageElements()) existing.add(el.getObjectId());

        var alpha = scrim == null ? 0.55 : scrim;
        var box = Geom.cover(aspect == null ? 16.0 / 9 : aspect);
        var full = new Box(0, 0, Geom.pageW, Geom.pageH, 0);

        var reqs = new ArrayList<Request>();
        reqs.add(CreateRequests.image(imgId, pageId, box, Images.urlFor(src)));
        reqs.addAll(CreateRequests.shape(scrimId, pageId, full, "RECTANGLE", "000000", alpha, null, null, null));
        reqs.add(CreateRequests.noOutline(scrimId));
        if (!existing.isEmpty())
            reqs.add(new Request().setUpdatePageElementsZOrder(new UpdatePageElementsZOrderRequest()
                    .setPageElementObjectIds(existing).setOperation("BRING_TO_FRONT")));

        Api.flush(deck, reqs);
        Out.success("background added to " + pageId + " (img=" + imgId + " scrim=" + scrimId + " alpha=" + alpha + ")");
    }
}
