package uskoag.gservices.slides;

/** One character run over [start,end). Null means "inherit", not "off". */
public record RichSpan(
        int start, int end,
        Boolean bold, Boolean italic, Boolean underline, Boolean strike,
        Double size, String font, String color, String link) {

    boolean plain() {
        return bold == null && italic == null && underline == null && strike == null
                && size == null && font == null && color == null && link == null;
    }

    RichSpan shift(int by) {
        return new RichSpan(start + by, end + by, bold, italic, underline, strike, size, font, color, link);
    }

    /** Adjacent runs with identical styling merge, which keeps the request list short. */
    boolean sameStyleAs(RichSpan o) {
        return eq(bold, o.bold) && eq(italic, o.italic) && eq(underline, o.underline) && eq(strike, o.strike)
                && eq(size, o.size) && eq(font, o.font) && eq(color, o.color) && eq(link, o.link);
    }

    private static boolean eq(Object a, Object b) { return a == null ? b == null : a.equals(b); }
}
