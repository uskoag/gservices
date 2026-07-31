package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.Page;
import java.util.List;

/** Speaker notes are just a shape carrying text on the slide's notes page, so they reduce to the text primitive. */
public final class VerbNotes {

    private static final String FIELDS =
            "slides(objectId,slideProperties(notesPage(notesProperties(speakerNotesObjectId),pageElements)))";

    private VerbNotes() {}

    static void run(List<String> a) throws Exception {
        var set = !a.isEmpty() && a.get(0).equals("set");
        if (set) a.remove(0);

        var html = Args.val(a, "--html");
        var body = Args.text(a, "--text", "--file");
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var pageRef = Args.req(a, "<page>");
        if (set && body == null && html == null) body = Args.pos(a);
        Args.noneLeft(a, set ? "notes set" : "notes");

        var pres = Deck.get(deck, FIELDS);
        var page = Deck.page(pres, pageRef);
        var notesId = speakerNotesId(page);
        if (notesId == null) Out.die("slide " + page.getObjectId() + " exposes no speaker-notes shape");

        var existing = notesText(page, notesId);
        if (!set) { Out.data(existing); return; }

        var rt = html != null ? HtmlToRich.convert(html) : RichText.plain(body == null ? "" : body);
        Api.flush(deck, RichRequests.setText(notesId, rt, existing.length()));
        Out.success("speaker notes set on " + page.getObjectId());
    }

    private static String speakerNotesId(Page page) {
        var sp = page.getSlideProperties();
        if (sp == null || sp.getNotesPage() == null || sp.getNotesPage().getNotesProperties() == null) return null;
        return sp.getNotesPage().getNotesProperties().getSpeakerNotesObjectId();
    }

    private static String notesText(Page page, String notesId) {
        var els = page.getSlideProperties().getNotesPage().getPageElements();
        if (els == null) return "";
        for (var el : els)
            if (notesId.equals(el.getObjectId())) return Els.textOf(el);
        return "";
    }
}
