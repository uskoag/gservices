package uskoag.gservices.docs;

import java.util.*;

/** mvn exec:java -Dexec.mainClass=uskoag.gservices.docs.DiffSmokeTest -Dexec.classpathScope=test */
public class DiffSmokeTest {

    public static void main(String[] args) {
        var rnd = new Random(7);
        var words = "the nation council temple river summit charter treaty law people land grace vision member order".split(" ");
        var a = new ArrayList<String>();
        a.add("UAN");
        for (var i = 0; i < 30000; i++) {
            if (i % 40 == 0) { a.add("## Section " + i); continue; }
            var sb = new StringBuilder("Paragraph " + i + ":");
            for (var w = 0; w < 25; w++) sb.append(' ').append(words[rnd.nextInt(words.length)]);
            a.add(sb + ".");
        }
        a.add("USK");
        a.add("Final paragraph about the Timeline of the nation.");
        var b = new ArrayList<>(a);
        b.set(12003, b.get(12003).replaceFirst("Paragraph 12002: [a-z]+ [a-z]+ [a-z]+", "Paragraph 12002: three new words"));
        b.set(7002, "**" + b.get(7002) + "**");
        b.set(801, "### " + b.get(801).substring(3));
        b.set(25001, "A brand new paragraph that did not exist before at all.");
        b.add(20000, b.remove(500));

        var t0 = System.nanoTime();
        var ca = Blocks.of(String.join("\n\n", a), new TabSplit(List.of("UAN", "USK")));
        var cb = Blocks.of(String.join("\n\n", b), new TabSplit(List.of("UAN", "USK")));
        var t1 = System.nanoTime();
        var cs = DocDiff.of(ca, cb);
        System.out.println("blocks " + (t1 - t0) / 1_000_000 + " ms, diff " + (System.nanoTime() - t1) / 1_000_000 + " ms");
        var ms = (System.nanoTime() - t0) / 1_000_000;
        cs.forEach(c -> System.out.println(DiffOut.line(c) + "   @" + Blocks.where(c.at())));
        System.out.println(DiffOut.stat(cs) + "   in " + ms + " ms over " + a.size() + " paragraphs");
        System.out.println(WordDiff.inline("UAN publishes a selection of its member nations on its website. The published list reads:", "Active members of the UAN include:", 8));
    }
}
