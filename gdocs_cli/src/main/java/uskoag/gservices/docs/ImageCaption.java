package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.List;
import java.util.function.Function;

import static uskoag.gservices.docs.BuildInsert.loc;
import static uskoag.gservices.docs.BuildInsert.range;

/**
 * The paragraphs `image insert` leaves behind: the picture paragraph at {@code at+1} (the picture is one index
 * long), and, with a caption, a second paragraph right under it. Both are reset to the normal style first — an
 * anchor that is a heading or a list item would otherwise hand its style to a picture and its caption — then
 * centred; the picture is kept with the caption so the two never part at a page break. {@code plain} is null
 * when only the picture paragraph is formatted.
 */
record ImageCaption(String plain, ParaFmt image, ParaFmt caption, boolean italic) {

    private static final String RESET = "namedStyleType,alignment,keepWithNext,keepLinesTogether,lineSpacing,spaceAbove,spaceBelow,indentStart,indentFirstLine,indentEnd";

    static boolean italic(String captionStyle) {
        return switch (captionStyle == null ? "italic-center" : captionStyle) {
            case "italic-center" -> true;
            case "center" -> false;
            default -> { Out.die("--caption-style must be italic-center or center, not \"" + captionStyle + "\""); yield false; }
        };
    }

    /** Queues the caption paragraph and every style request after the picture requests already in {@code build}. */
    static ImageCaption add(List<Function<String, Request>> build, Scope s, Para anchor, int at, String captionMd, String captionStyle, ParaFmt asked) {
        var withCaption = captionMd != null;
        var italic = italic(captionStyle);
        var img = withCaption ? new ParaFmt(asked.align() != null ? asked.align() : "CENTER", asked.keepWithNext() != null ? asked.keepWithNext() : Boolean.TRUE, asked.keepLinesTogether()) : asked;
        var sg = withCaption ? Segments.parse(captionMd) : null;
        var capStart = at + 3;
        var capEnd = withCaption ? capStart + sg.plain().length() + 1 : at + 3;
        if (withCaption) build.add(url -> new Request().setInsertText(new InsertTextRequest().setText("\n" + sg.plain()).setLocation(loc(s, at + 2))));
        if (anchor.p().getBullet() != null)
            build.add(url -> new Request().setDeleteParagraphBullets(new DeleteParagraphBulletsRequest().setRange(range(s, at + 1, capEnd))));
        var cap = new ParaFmt("CENTER", null, null);
        if (withCaption) {
            build.add(url -> reset(s, at + 1, at + 3, img));
            build.add(url -> reset(s, capStart, capEnd, cap));
            for (var r : RunStyles.of(s, sg, capStart, null)) build.add(url -> r);
            if (italic) build.add(url -> new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setTextStyle(new TextStyle().setItalic(true)).setFields("italic")
                    .setRange(range(s, capStart, capEnd - 1))));
        } else if (img.any()) build.add(url -> img.request(range(s, at + 1, at + 3)));
        return new ImageCaption(withCaption ? sg.plain() : null, img, withCaption ? cap : null, withCaption && italic);
    }

    private static Request reset(Scope s, int from, int to, ParaFmt f) {
        return new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest().setRange(range(s, from, to)).setFields(RESET)
                .setParagraphStyle(f.style().setNamedStyleType("NORMAL_TEXT")));
    }

    String describe() {
        return (plain == null ? "" : "caption \"" + plain + "\" (" + caption.describe() + (italic ? ", italic" : "") + "); ") + "picture paragraph: " + image.describe();
    }

    /** What the document should hold after apply: the picture's paragraph, and the caption's. */
    List<AlignCheck.Want> wants(String tabId, String objectId) {
        var out = new java.util.ArrayList<AlignCheck.Want>();
        if (image.any()) out.add(AlignCheck.Want.image(tabId, objectId, image));
        if (plain != null) out.add(AlignCheck.Want.text(tabId, plain, caption).italic(italic));
        return out;
    }
}
