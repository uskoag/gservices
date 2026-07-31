package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.CreateSlideRequest;
import com.google.api.services.slides.v1.model.DeleteObjectRequest;
import com.google.api.services.slides.v1.model.DuplicateObjectRequest;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.LayoutReference;
import com.google.api.services.slides.v1.model.UpdateSlidesPositionRequest;
import java.util.ArrayList;
import java.util.List;

public final class VerbPage {

    private VerbPage() {}

    static void run(List<String> a) throws Exception {
        var sub = Args.req(a, "add|delete|duplicate|move");
        switch (sub) {
            case "add" -> add(a);
            case "delete" -> delete(a);
            case "duplicate" -> duplicate(a);
            case "move" -> move(a);
            default -> Out.die("page: expected add|delete|duplicate|move, got: " + sub);
        }
    }

    private static void add(List<String> a) throws Exception {
        var count = Args.intVal(a, "--count");
        var at = Args.intVal(a, "--at");
        var id = Ids.validate(Args.val(a, "--id"));
        var layout = Args.valOr(a, "--layout", "BLANK");
        var deck = Deck.presId(Args.req(a, "<deck>"));
        Args.noneLeft(a, "page add");
        GSlidesConfig.require(deck, "write");

        var n = count == null ? 1 : count;
        if (n > 1 && id != null) Out.die("page add: --id only makes sense with a single slide");

        var reqs = new ArrayList<Request>();
        var ids = new ArrayList<String>();
        for (var i = 0; i < n; i++) {
            var objectId = id != null ? id : null;
            var r = new CreateSlideRequest()
                    .setSlideLayoutReference(new LayoutReference().setPredefinedLayout(layout.toUpperCase()));
            if (objectId != null) { r.setObjectId(objectId); ids.add(objectId); }
            if (at != null) r.setInsertionIndex(at - 1 + i);
            reqs.add(new Request().setCreateSlide(r));
        }

        var res = Api.flush(deck, reqs);
        if (res != null && res.getReplies() != null)
            for (var rep : res.getReplies())
                if (rep.getCreateSlide() != null) Out.data(rep.getCreateSlide().getObjectId());
        Out.success(n + " slide(s) added");
    }

    private static void delete(List<String> a) throws Exception {
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var pageRef = Args.req(a, "<page>");
        Args.noneLeft(a, "page delete");
        GSlidesConfig.require(deck, "write");
        var pageId = Deck.pageId(deck, pageRef);
        Api.flush(deck, List.of(new Request().setDeleteObject(new DeleteObjectRequest().setObjectId(pageId))));
        Out.success("deleted slide " + pageId);
    }

    private static void duplicate(List<String> a) throws Exception {
        var toEnd = Args.flag(a, "--end");
        var newId = Ids.validate(Args.val(a, "--id"));
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var pageRef = Args.req(a, "<page>");
        Args.noneLeft(a, "page duplicate");
        GSlidesConfig.require(deck, "write");

        var pageId = Deck.pageId(deck, pageRef);
        var dup = new DuplicateObjectRequest().setObjectId(pageId);
        if (newId != null) dup.setObjectIds(java.util.Map.of(pageId, newId));
        var res = Api.flush(deck, List.of(new Request().setDuplicateObject(dup)));
        if (res == null) return;

        var created = res.getReplies().get(0).getDuplicateObject().getObjectId();
        Out.data(created);
        if (toEnd) {
            var total = Deck.slides(deck).size();
            Api.flush(deck, List.of(new Request().setUpdateSlidesPosition(new UpdateSlidesPositionRequest()
                    .setSlideObjectIds(List.of(created)).setInsertionIndex(total))));
            Out.info("moved to end (slide " + total + ")");
        }
        Out.success("duplicated " + pageId + " -> " + created);
    }

    private static void move(List<String> a) throws Exception {
        var to = Args.intVal(a, "--to");
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var pageRef = Args.req(a, "<page>");
        Args.noneLeft(a, "page move");
        if (to == null) Out.die("page move: --to N is required");
        GSlidesConfig.require(deck, "write");

        var pageId = Deck.pageId(deck, pageRef);
        Api.flush(deck, List.of(new Request().setUpdateSlidesPosition(new UpdateSlidesPositionRequest()
                .setSlideObjectIds(List.of(pageId)).setInsertionIndex(to - 1))));
        Out.success("moved " + pageId + " to position " + to);
    }
}
