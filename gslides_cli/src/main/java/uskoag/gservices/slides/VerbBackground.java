package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.PageBackgroundFill;
import com.google.api.services.slides.v1.model.PageProperties;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.StretchedPictureFill;
import com.google.api.services.slides.v1.model.UpdatePagePropertiesRequest;
import java.util.List;

public final class VerbBackground {

    private VerbBackground() {}

    static void run(List<String> a) throws Exception {
        var color = Args.val(a, "--color");
        var image = Args.val(a, "--image");
        var clear = Args.flag(a, "--clear");
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var pageRef = Args.req(a, "<page>");
        Args.noneLeft(a, "background");

        var given = (color != null ? 1 : 0) + (image != null ? 1 : 0) + (clear ? 1 : 0);
        if (given != 1) Out.die("background: pass exactly one of --color HEX, --image <path|url>, --clear");

        var pageId = Deck.pageId(deck, pageRef);
        var fill = new PageBackgroundFill();
        if (color != null) fill.setSolidFill(Colors.fill(Css.hex(color), 1.0));
        else if (image != null) fill.setStretchedPictureFill(
                new StretchedPictureFill().setContentUrl(Images.urlFor(image)));
        else fill.setPropertyState("NOT_RENDERED");

        Api.flush(deck, List.of(new Request().setUpdatePageProperties(new UpdatePagePropertiesRequest()
                .setObjectId(pageId)
                .setPageProperties(new PageProperties().setPageBackgroundFill(fill))
                .setFields("pageBackgroundFill"))));
        Out.success("background updated on " + pageId);
    }
}
