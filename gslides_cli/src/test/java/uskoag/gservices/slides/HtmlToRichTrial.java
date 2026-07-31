package uskoag.gservices.slides;

/**
 * Not a JUnit test -- a trial main, run with:
 *   mvn exec:java -Dexec.mainClass=uskoag.gservices.slides.HtmlToRichTrial -Dexec.classpathScope=test
 */
public class HtmlToRichTrial {

    public static void main(String[] args) {
        show("<b>Bold</b> then <i>italic</i>");
        show("plain <b>bold <i>and italic</i></b> tail");
        show("<p>First para</p><p>Second para</p>");
        show("line one<br>line two");
        show("<ul><li>alpha</li><li>beta</li></ul>");
        show("<ol><li>one</li><li>two</li></ol>");
        show("<a href=\"https://kailaasa.org\">KAILASA</a>");
        show("<span style=\"color:#B45F06;font-size:30pt;font-family:Georgia\">styled</span>");
        show("<p style=\"text-align:center\">centred</p>");
        show("<u>under</u> <s>struck</s> <del>deleted</del>");
        show("परमशिव <b>Paramashiva</b> — em dash & ampersand");
        show("<div>no tags here</div>");
        show("bare text with no markup at all");
    }

    static void show(String html) {
        var rt = HtmlToRich.convert(html);
        System.out.println("HTML : " + html);
        System.out.println("TEXT : " + rt.text().replace("\n", "\\n"));
        for (var s : rt.spans())
            System.out.printf("  run  [%2d,%2d) %s%n", s.start(), s.end(), desc(s));
        for (var p : rt.paras())
            System.out.printf("  para [%2d,%2d) align=%s bullet=%s%n", p.start(), p.end(), p.align(), p.bullet());
        System.out.println();
    }

    static String desc(RichSpan s) {
        var b = new StringBuilder();
        if (Boolean.TRUE.equals(s.bold())) b.append("bold ");
        if (Boolean.TRUE.equals(s.italic())) b.append("italic ");
        if (Boolean.TRUE.equals(s.underline())) b.append("underline ");
        if (Boolean.TRUE.equals(s.strike())) b.append("strike ");
        if (s.size() != null) b.append("size=").append(s.size()).append(' ');
        if (s.font() != null) b.append("font=").append(s.font()).append(' ');
        if (s.color() != null) b.append("color=").append(s.color()).append(' ');
        if (s.link() != null) b.append("link=").append(s.link());
        return b.toString().trim();
    }
}
