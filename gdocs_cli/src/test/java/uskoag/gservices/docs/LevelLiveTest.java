package uskoag.gservices.docs;

import java.nio.file.Files;
import java.util.*;

/**
 * PRP 26, live, in a fresh tab of the PRP 08 scratch doc (deleted at the end). A numbered list 1. One / 2. **Two** /
 * plain "Mid" / 3. Three, and a bullet list Alpha / Beta (level 1) / Delta. Then:
 * insert a level-1 item under "Two"; insert a level-2 bullet under the level-1 "Beta" (the exact PRP 26 report);
 * re-level "Three" with `list --level 1`. Each must read back at its level, in the SAME list, numbering continuous
 * across "Mid", bold kept. Fails before PRP 26 (VERIFY FAILED, exit 2: Google joins the neighbour's level).
 *
 * mvn exec:java -Dexec.mainClass=uskoag.gservices.docs.LevelLiveTest -Dexec.classpathScope=test [-Dexec.args="<doc> <account>"]
 */
public class LevelLiveTest {

    public static void main(String[] args) throws Exception {
        var doc = args.length > 0 ? args[0] : SuggestionLiveTest.DOC;
        var acct = args.length > 1 ? args[1] : SuggestionLiveTest.ACCOUNT;
        Auth.email = acct;
        var tab = "PRP26_" + Long.toString(System.nanoTime(), 36);
        SuggestionLiveTest.cli(acct, "tab", "add", doc, "--title", tab, "--apply");
        try {
            var md = Files.createTempFile("prp26", ".md");
            Files.writeString(md, "1. One\n2. **Two** bold\n3. Mid\n4. Three\n\nPlain between lists\n\n- Alpha\n    - Beta\n- Delta\n");
            SuggestionLiveTest.cli(acct, "write", doc, "--tab", tab, "--from", md.toString(), "--apply");
            SuggestionLiveTest.cli(acct, "list", doc, "--tab", tab, "--from", "Mid", "--none", "--apply");
            SuggestionLiveTest.cli(acct, "insert", doc, "--tab", tab, "--after", "Two", "--list", "number", "--level", "1", "--text", "Two_sub", "--apply");
            SuggestionLiveTest.cli(acct, "insert", doc, "--tab", tab, "--after", "Beta", "--list", "bullet", "--level", "2", "--text", "Beta_reply", "--apply");
            SuggestionLiveTest.cli(acct, "list", doc, "--tab", tab, "--from", "Three", "--number", "--level", "1", "--apply");
            var got = state(doc, tab);
            got.forEach(System.out::println);
            var want = List.of("1. One L0 A", "2. **Two** bold L0 A", "a. Two_sub L1 A", "Mid", "b. Three L1 A", "Plain between lists", "- Alpha L0 B", "- Beta L1 B", "- Beta_reply L2 B", "- Delta L0 B");
            SuggestionLiveTest.expect(got.equals(want), "levels, lists and numbering\n  want " + want + "\n  got  " + got);
            System.out.println("PASS: levels set inside existing lists, one list each, numbering continuous across a plain paragraph, bold kept");
        } finally { SuggestionLiveTest.cli(acct, "tab", "delete", doc, "--tab", tab, "--apply"); }
    }

    /** Each non-empty paragraph as "label text level list-letter", list ids renamed A, B... in order of appearance. */
    static List<String> state(String doc, String sel) throws Exception {
        var t = Tabs.select(Tabs.flat(Preview.get(doc)), sel).get(0).getDocumentTab();
        var labels = new ListLabel(t);
        var names = new LinkedHashMap<String, String>();
        var out = new ArrayList<String>();
        for (var p : Para.of(t)) {
            if (p.plain().isBlank()) continue;
            var b = p.p().getBullet();
            if (b == null) { out.add(p.plain()); continue; }
            var name = names.computeIfAbsent(b.getListId(), k -> "" + (char) ('A' + names.size()));
            out.add(labels.next(p.p()) + " " + bold(p) + " L" + Styles.level(p.p()) + " " + name);
        }
        return out;
    }

    static String bold(Para p) {
        var s = new StringBuilder();
        for (var r : p.runs()) {
            var txt = Styles.text(r).replace("\n", "");
            s.append(r.getTextStyle() != null && Boolean.TRUE.equals(r.getTextStyle().getBold()) ? "**" + txt + "**" : txt);
        }
        return s.toString();
    }
}
