package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.Request;
import java.util.ArrayList;
import java.util.List;

/**
 * --id is the point of this verb. A slide element otherwise carries an opaque
 * server-assigned objectId, so naming it at creation converts the deck into a namespace
 * the caller controls: addressable later, safe to re-run, and exactly deletable.
 */
public final class VerbCreate {

    private VerbCreate() {}

    static void run(List<String> a) throws Exception {
        var kind = Args.req(a, "text|box|ellipse|line|image|video").toLowerCase();

        var id = Ids.validate(Args.val(a, "--id"));
        var at = Args.val(a, "--at");
        var size = Args.val(a, "--size");
        var cover = Args.flag(a, "--cover");
        var aspect = Args.dblVal(a, "--aspect");
        var fill = Args.val(a, "--fill");
        var alpha = Args.dblVal(a, "--alpha");
        var outline = Args.val(a, "--outline");
        var weight = Args.dblVal(a, "--weight");
        var noOutline = Args.flag(a, "--no-outline");
        var html = Args.val(a, "--html");
        var plain = Args.text(a, "--text", "--text-file");
        var src = Args.val(a, "--src");
        var youtube = Args.val(a, "--youtube");
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var pageRef = Args.req(a, "<page>");
        Args.noneLeft(a, "create " + kind);

        var pres = Deck.get(deck, "pageSize,slides(objectId)");
        Geom.adoptPageSize(pres.getPageSize());
        var pageId = Deck.pageId(deck, pageRef);
        var box = cover ? Geom.cover(aspect == null ? 16.0 / 9 : aspect) : box(at, size, kind);
        var rt = richText(html, plain);

        var reqs = new ArrayList<Request>(switch (kind) {
            case "text" -> CreateRequests.textBox(id, pageId, box, rt);
            case "box" -> CreateRequests.shape(id, pageId, box, "RECTANGLE", fill, alpha, outline, weight, rt);
            case "ellipse" -> CreateRequests.shape(id, pageId, box, "ELLIPSE", fill, alpha, outline, weight, rt);
            case "line" -> List.of(CreateRequests.line(id, pageId, box, "STRAIGHT"));
            case "image" -> List.of(CreateRequests.image(id, pageId, box, imageUrl(src)));
            case "video" -> List.of(CreateRequests.video(id, pageId, box, require(youtube, "--youtube")));
            default -> { Out.die("create: unknown kind " + kind); yield List.<Request>of(); }
        });
        if (noOutline && id != null) reqs.add(CreateRequests.noOutline(id));

        var res = Api.flush(deck, reqs);
        if (id != null) Out.data(id);
        else if (res != null && res.getReplies() != null && !res.getReplies().isEmpty())
            Out.data(createdId(res.getReplies().get(0)));
        Out.success("created " + kind + " on " + pageId);
    }

    private static String createdId(com.google.api.services.slides.v1.model.Response r) {
        if (r.getCreateShape() != null) return r.getCreateShape().getObjectId();
        if (r.getCreateImage() != null) return r.getCreateImage().getObjectId();
        if (r.getCreateVideo() != null) return r.getCreateVideo().getObjectId();
        if (r.getCreateLine() != null) return r.getCreateLine().getObjectId();
        return "";
    }

    private static RichText richText(String html, String plain) {
        if (html != null && plain != null) Out.die("create: pass --html or --text, not both");
        if (html != null) return HtmlToRich.convert(html);
        return plain == null ? null : RichText.plain(plain);
    }

    private static String imageUrl(String src) throws Exception {
        return Images.urlFor(require(src, "--src"));
    }

    private static String require(String v, String flag) {
        if (v == null) Out.die("create: " + flag + " is required");
        return v;
    }

    /** --at X,Y and --size W,H, both in inches. */
    private static Box box(String at, String size, String kind) {
        if (at == null || size == null)
            Out.die("create " + kind + ": --at X,Y and --size W,H are required (inches), or --cover for full-bleed");
        var p = pair(at, "--at");
        var s = pair(size, "--size");
        return new Box(p[0], p[1], s[0], s[1], 0);
    }

    private static double[] pair(String v, String flag) {
        var parts = v.split(",");
        if (parts.length != 2) Out.die(flag + " expects two comma-separated inches, got: " + v);
        return new double[]{ Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()) };
    }
}
