package uskoag.gservices.slides;

/** The text-range half of the address grammar: {@code <objectId>@<start>:<end>}, half-open. */
public record ElemRange(String elem, Integer start, Integer end) {

    static ElemRange parse(String s) {
        var at = s.lastIndexOf('@');
        if (at < 0) return new ElemRange(s, null, null);
        var r = VerbText.span(s.substring(at + 1));
        return new ElemRange(s.substring(0, at), r[0], r[1]);
    }

    /** No @range means the whole text, which needs its current length. */
    ElemRange resolve(String deck) throws Exception {
        if (start != null) return this;
        var pres = Deck.get(deck, "slides(objectId,pageElements)");
        var len = Runs.length(Els.textContent(Deck.element(pres, elem)));
        if (len == 0) Out.die(elem + " has no text to style");
        return new ElemRange(elem, 0, len);
    }
}
