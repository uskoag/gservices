package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;
import org.jsoup.nodes.Element;

/**
 * PRP 27, offline: `read --html` must number lists as the document does. A numbered list (1. / (1) at level 1)
 * interrupted by a plain paragraph, a bullet list right under one of its items, then the numbered list again:
 * 1. One, 2. Two, (1) Two-a, [Mid], 3. Three, • Bul, 4. Four. Fails before PRP 27 (the reopened <ol> has no start,
 * no data-label, and the bullet lands inside the numbered <ol>).
 *
 * mvn exec:java -Dexec.mainClass=uskoag.gservices.docs.HtmlListTest -Dexec.classpathScope=test
 */
public class HtmlListTest {

    public static void main(String[] args) {
        var tab = doc();
        var html = render(tab, false);
        System.out.println(html);
        var lis = html.select("li");
        SuggestionLiveTest.expect(labels(html).equals(List.of("1.", "2.", "(1)", "3.", "4.")), "data-label of numbered items: " + labels(html));
        SuggestionLiveTest.expect(olOf(html, "Three").attr("start").equals("3"), "the <ol> reopened after Mid starts at 3");
        SuggestionLiveTest.expect(olOf(html, "Four").attr("start").equals("4"), "the <ol> reopened after the bullet list starts at 4");
        var bul = lis.stream().filter(li -> li.ownText().equals("Bul")).findFirst().orElseThrow();
        SuggestionLiveTest.expect(bul.parent().tagName().equals("ul") && bul.parents().stream().noneMatch(e -> e.tagName().equals("ol")), "the bullet is in a <ul> of its own, not in the numbered list");

        var flat = render(tab, true);
        System.out.println(flat);
        var ps = flat.select("p[data-list-level]").stream().map(p -> p.select("span").first().text() + " " + p.ownText()).toList();
        SuggestionLiveTest.expect(ps.equals(List.of("1. One", "2. Two", "(1) Two-a", "3. Three", "● Bul", "4. Four")), "flat list labels: " + ps);
        SuggestionLiveTest.expect(flat.select("p[data-list-level=1]").attr("style").equals("margin-left:72.0pt;text-indent:-18.0pt"), "level-1 hanging indent");
        System.out.println("PASS: html list numbering, labels, separate bullet list, flat lists");
    }

    static Element render(DocumentTab tab, boolean flat) {
        HtmlRender.flatLists = flat;
        var section = new Element("section");
        new HtmlRender(tab).into(section);
        return section;
    }

    static List<String> labels(Element html) { return html.select("li[data-label]").stream().map(li -> li.attr("data-label")).toList(); }

    static Element olOf(Element html, String text) {
        return html.select("li").stream().filter(li -> li.ownText().equals(text)).findFirst().orElseThrow().parent();
    }

    static DocumentTab doc() {
        var num = new ListProperties().setNestingLevels(List.of(
                level("DECIMAL", "%0.", 36, 18), level("DECIMAL", "(%1)", 72, 54)));
        var bul = new ListProperties().setNestingLevels(List.of(new NestingLevel().setGlyphSymbol("●").setIndentStart(pt(36)).setIndentFirstLine(pt(18))));
        var content = new ArrayList<StructuralElement>();
        content.add(para("One", "n", 0)); content.add(para("Two", "n", 0)); content.add(para("Two-a", "n", 1));
        content.add(para("Mid", null, 0)); content.add(para("Three", "n", 0)); content.add(para("Bul", "b", 0)); content.add(para("Four", "n", 0));
        return new DocumentTab().setBody(new Body().setContent(content))
                .setLists(Map.of("n", new com.google.api.services.docs.v1.model.List().setListProperties(num), "b", new com.google.api.services.docs.v1.model.List().setListProperties(bul)));
    }

    static NestingLevel level(String type, String fmt, int start, int first) {
        return new NestingLevel().setGlyphType(type).setGlyphFormat(fmt).setIndentStart(pt(start)).setIndentFirstLine(pt(first));
    }

    static Dimension pt(double v) { return new Dimension().setMagnitude(v).setUnit("PT"); }

    static StructuralElement para(String text, String list, int lvl) {
        var p = new Paragraph().setElements(List.of(new ParagraphElement().setTextRun(new TextRun().setContent(text + "\n").setTextStyle(new TextStyle()))))
                .setParagraphStyle(new ParagraphStyle().setNamedStyleType("NORMAL_TEXT"));
        if (list != null) p.setBullet(new Bullet().setListId(list).setNestingLevel(lvl));
        return new StructuralElement().setParagraph(p);
    }
}
