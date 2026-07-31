package uskoag.gservices.slides;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class VerbExport {

    private VerbExport() {}

    static void run(List<String> a) throws Exception {
        var slide = Args.intVal(a, "--slide");
        var start = Args.intVal(a, "--start");
        var end = Args.intVal(a, "--end");
        var outArg = Args.val(a, "--out");
        var quick = Args.flag(a, "--quick");
        var size = Args.valOr(a, "--size", "LARGE").toUpperCase();
        var maxWidth = Args.intVal(a, "--max-width");
        var delay = Args.intVal(a, "--delay");
        var video = Args.flag(a, "--video");
        var duration = Args.dblVal(a, "--duration");
        var deck = Deck.presId(Args.req(a, "<deck>"));
        Args.noneLeft(a, "export");
        GSlidesConfig.require(deck, "read");

        var pres = Deck.get(deck, "title,slides(objectId)");
        var slides = pres.getSlides() == null ? List.<com.google.api.services.slides.v1.model.Page>of() : pres.getSlides();
        if (slides.isEmpty()) Out.die("deck has no slides");

        var from = slide != null ? slide : (start != null ? start : 1);
        var to = slide != null ? slide : (end != null ? end : slides.size());
        if (from < 1 || from > slides.size()) Out.die("--start/--slide out of range (1-" + slides.size() + "): " + from);
        if (to < 1 || to > slides.size()) Out.die("--end out of range (1-" + slides.size() + "): " + to);
        if (from > to) Out.die("--start must be <= --end");

        var single = from == to && slide != null;
        var dir = outDir(outArg, deck, single);
        var token = quick ? null : Auth.credential().getAccessToken();
        var pause = delay != null ? delay : 3000;
        var written = new ArrayList<Path>();

        for (var n = from; n <= to; n++) {
            var pageId = slides.get(n - 1).getObjectId();
            Out.info("rendering slide " + n + " (" + pageId + ")" + (quick ? " via getThumbnail" : " full resolution"));
            var data = quick ? PngExport.viaThumbnail(deck, pageId, size)
                             : PngExport.fullRes(deck, pageId, token);
            if (maxWidth != null) data = PngExport.downscale(data, maxWidth);

            var target = (single && outArg != null && outArg.toLowerCase().endsWith(".png"))
                    ? Path.of(outArg) : dir.resolve(PngExport.name(n, pageId));
            written.add(PngExport.write(target, data));
            Out.data(target.toAbsolutePath().toString());

            if (!quick && n < to) Thread.sleep(pause);
        }

        if (video) VideoExport.build(written, dir, pres.getTitle(), duration != null ? duration : 1.0);
        Out.success(written.size() + " slide(s) rendered");
    }

    private static Path outDir(String outArg, String deck, boolean single) {
        if (outArg == null) return Path.of(single ? "." : deck);
        return outArg.toLowerCase().endsWith(".png") ? parentOf(outArg) : Path.of(outArg);
    }

    private static Path parentOf(String file) {
        var p = Path.of(file).toAbsolutePath().getParent();
        return p != null ? p : Path.of(".");
    }
}
