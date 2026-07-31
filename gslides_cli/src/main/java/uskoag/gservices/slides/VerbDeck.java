package uskoag.gservices.slides;

import com.google.api.services.drive.model.File;
import com.google.api.services.slides.v1.model.Presentation;
import java.util.List;

/**
 * `deck new` doubles as the smoke-test fixture, so testing needs no pre-existing deck.
 *
 * Page size is not settable: presentations.create ignores every field but title, and the
 * API exposes no request to change pageSize afterwards. A new deck is therefore Google's
 * default 16:9; for anything else, --copy-of a template that already has it.
 */
public final class VerbDeck {

    private VerbDeck() {}

    static void run(List<String> a) throws Exception {
        var sub = Args.req(a, "new");
        if (!sub.equals("new")) Out.die("deck: only 'new' is supported, got: " + sub);

        var copyOf = Args.val(a, "--copy-of");
        var title = Args.req(a, "\"<title>\"");
        Args.noneLeft(a, "deck new");

        var id = copyOf == null ? create(title) : copy(Deck.presId(copyOf), title);

        GSlidesConfig.grant(id, title, true);
        Out.data(id);
        Out.data(Deck.url(id));
        Out.success("created \"" + title + "\"");
    }

    private static String create(String title) throws Exception {
        var pres = Auth.slides().presentations()
                .create(new Presentation().setTitle(title)).execute();
        return pres.getPresentationId();
    }

    private static String copy(String srcId, String title) throws Exception {
        GSlidesConfig.require(srcId, "read");
        var copy = Auth.drive().files()
                .copy(srcId, new File().setName(title))
                .setFields("id").execute();
        Out.info("copied " + srcId + " -> " + copy.getId());
        return copy.getId();
    }
}
