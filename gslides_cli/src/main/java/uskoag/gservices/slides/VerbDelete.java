package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.Request;
import java.util.List;

public final class VerbDelete {

    private VerbDelete() {}

    static void run(List<String> a) throws Exception {
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var elem = Args.req(a, "<elem>");
        Args.noneLeft(a, "delete");
        Api.flush(deck, List.of(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(elem))));
        Out.success("deleted " + elem);
    }
}
