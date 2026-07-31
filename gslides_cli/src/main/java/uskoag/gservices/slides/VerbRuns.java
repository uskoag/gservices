package uskoag.gservices.slides;

import java.util.List;

public final class VerbRuns {

    private VerbRuns() {}

    static void run(List<String> a) throws Exception {
        var format = Args.valOr(a, "--format", "text");
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var elemRef = Args.req(a, "<elem>");
        Args.noneLeft(a, "runs");

        var pres = Deck.get(deck, "slides(objectId,pageElements)");
        var el = Deck.element(pres, elemRef);
        var tc = Els.textContent(el);
        if (tc == null) Out.die(elemRef + " is " + Els.typeOf(el) + " and carries no text");

        var runs = Runs.of(tc);
        var paras = Runs.paras(tc);

        if (format.equals("json")) {
            Out.json(new RunsOut(elemRef, Runs.length(tc), runs, paras));
            return;
        }

        Out.data("=== " + elemRef + "  (" + runs.size() + " runs, " + Runs.length(tc) + " chars) ===");
        for (var r : runs)
            Out.data(String.format("  [%4d,%4d) %-28s \"%s\"", r.start(), r.end(), r.style(), show(r.text())));
        if (!paras.isEmpty()) {
            Out.data("--- paragraphs ---");
            for (var p : paras)
                Out.data(String.format("  [%4d,%4d) %s%s", p.start(), p.end(),
                        p.style() == null ? "(inherit)" : p.style(),
                        p.bullet() == null ? "" : "  bullet=" + p.bullet()));
        }
    }

    private static String show(String t) {
        var s = t.replace("\n", "↵");
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
