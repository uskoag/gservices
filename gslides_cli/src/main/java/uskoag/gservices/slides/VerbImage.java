package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.ReplaceImageRequest;
import java.nio.file.Path;
import java.util.List;

/**
 * Replaces the image CONTENT of an existing element, so position, size and z-order all
 * survive. Prints the old source URL first, which makes the swap reversible.
 */
public final class VerbImage {

    private VerbImage() {}

    static void run(List<String> a) throws Exception {
        var sub = a.isEmpty() ? "" : a.get(0);
        if (sub.equals("upload")) { a.remove(0); upload(a); return; }

        var src = Args.val(a, "--src");
        var method = Args.valOr(a, "--method", "CENTER_CROP");
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var elem = Args.req(a, "<elem>");
        Args.noneLeft(a, "image");
        if (src == null) Out.die("image: --src <localPath|url> is required");
        GSlidesConfig.require(deck, "write");

        var pres = Deck.get(deck, "slides(objectId,pageElements(objectId,image(sourceUrl)))");
        var el = Deck.element(pres, elem);
        if (el.getImage() == null) Out.die(elem + " is " + Els.typeOf(el) + ", not an IMAGE");
        Out.info("old source: " + Els.imageUrl(el));

        var url = Images.urlFor(src);
        Api.flush(deck, List.of(new Request().setReplaceImage(new ReplaceImageRequest()
                .setImageObjectId(elem).setUrl(url).setImageReplaceMethod(method.toUpperCase()))));
        Out.success("replaced image on " + elem);
    }

    /** Exposed mostly for debugging; every image-taking flag uploads on its own. */
    private static void upload(List<String> a) throws Exception {
        var name = Args.val(a, "--name");
        var folder = Args.val(a, "--folder");
        var local = Args.req(a, "<localPath>");
        Args.noneLeft(a, "image upload");
        var up = Images.upload(Path.of(local), name, folder);
        Out.data("id=" + up.id());
        Out.data("lh3=" + up.lh3Url());
        Out.data("uc=" + up.ucUrl());
        Out.data("thumb=" + up.thumbUrl());
        Out.success("uploaded and shared read-only-by-link");
    }
}
