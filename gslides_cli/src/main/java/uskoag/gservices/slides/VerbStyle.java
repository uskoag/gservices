package uskoag.gservices.slides;

import java.util.List;

/**
 * Level-2 character styling: explicit run addressing, for surgical edits to text that
 * already exists. Read `runs` first to get the indices. For authoring, prefer
 * `text --set-html=` which needs no indices at all.
 */
public final class VerbStyle {

    private VerbStyle() {}

    static void run(List<String> a) throws Exception {
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var target = ElemRange.parse(Args.req(a, "<elem>[@S:E]"));

        var bold = tri(a, "--bold", "--no-bold");
        var italic = tri(a, "--italic", "--no-italic");
        var under = tri(a, "--underline", "--no-underline");
        var strike = tri(a, "--strike", "--no-strike");
        var size = Args.dblVal(a, "--size");
        var font = Args.val(a, "--font");
        var color = Args.val(a, "--color");
        var link = Args.val(a, "--link");
        Args.noneLeft(a, "style");

        if (bold == null && italic == null && under == null && strike == null
                && size == null && font == null && color == null && link == null)
            Out.die("style: nothing to change (--bold --italic --underline --strike --size --font --color --link)");

        GSlidesConfig.require(deck, "write");
        var r = target.resolve(deck);
        var span = new RichSpan(r.start(), r.end(), bold, italic, under, strike, size, font,
                color == null ? null : Css.hex(color), link);

        Api.flush(deck, List.of(RichRequests.textStyle(r.elem(), span)));
        Out.success("styled " + r.elem() + "@" + r.start() + ":" + r.end());
    }

    private static Boolean tri(List<String> a, String on, String off) {
        if (Args.flag(a, on)) return Boolean.TRUE;
        if (Args.flag(a, off)) return Boolean.FALSE;
        return null;
    }
}
