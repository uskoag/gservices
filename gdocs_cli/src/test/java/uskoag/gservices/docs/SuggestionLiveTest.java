package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;

/**
 * PRP 25, live, on the PRP 08 scratch doc. Appends a marker paragraph, suggests a deletion, an insertion
 * and a link on it, runs `suggestion accept <ids> --apply` through the CLI entry point, checks the text and
 * the link; then suggests a deletion, runs `suggestion reject`, checks the text is untouched; removes the
 * marker. Fails before PRP 25 (`suggestion` is an unknown command, exit 1).
 *
 * mvn exec:java -Dexec.mainClass=uskoag.gservices.docs.SuggestionLiveTest -Dexec.classpathScope=test [-Dexec.args="<doc> <account>"]
 */
public class SuggestionLiveTest {

    static final String DOC = "1Gj_unhKbUqzWXqrZxezP7EV4q0UjJseKsiQg8rpDXq0", ACCOUNT = "legal@uskfoundation.or.ke";

    public static void main(String[] args) throws Exception {
        var doc = args.length > 0 ? args[0] : DOC;
        var acct = args.length > 1 ? args[1] : ACCOUNT;
        Auth.email = acct;
        cleanup(doc);
        var marker = "PRP25 marker " + Long.toString(System.nanoTime(), 36) + ": Dear Sir/Madam, see the video.";
        var p = append(doc, marker);
        try {
            var ids = suggest(doc, List.of(
                    link(p, marker, "video"),
                    new Request().setInsertText(new InsertTextRequest().setText(" please").setLocation(new Location().setIndex(p.start() + marker.indexOf(" the video")))),
                    del(p, marker, "/Madam")));
            cli(acct, "suggestion", "accept", doc, ids, "--apply");
            var after = find(doc, "PRP25 marker");
            expect(after.plain().endsWith(": Dear Sir, see please the video."), "accepted text: " + after.plain());
            expect(linked(after, "video"), "link on 'video' after accept");

            var rid = suggest(doc, List.of(del(after, after.plain(), " please")));
            cli(acct, "suggestion", "reject", doc, rid, "--apply");
            var back = find(doc, "PRP25 marker");
            expect(back.plain().equals(after.plain()), "rejected text unchanged: " + back.plain());
            System.out.println("PASS: suggestion accept (deletion, insertion, link) and reject, verified");
        } finally { cleanup(doc); }
    }

    /** Removes every marker paragraph, including one left behind by a run that exited early. */
    static void cleanup(String doc) throws Exception {
        for (var q = find(doc, "PRP25 marker"); q != null; q = find(doc, "PRP25 marker"))
            Auth.docs().documents().batchUpdate(doc, new BatchUpdateDocumentRequest().setRequests(List.of(new Request()
                    .setDeleteContentRange(new DeleteContentRangeRequest().setRange(new Range().setStartIndex(q.start() - 1).setEndIndex(q.end() - 1)))))).execute();
    }

    static void cli(String acct, String... rest) {
        var a = new ArrayList<>(List.of("-e", acct));
        for (var s : rest) a.addAll(Arrays.asList(s.split(" ")));
        GDocsCli.main(a.toArray(String[]::new));
    }

    static Para append(String doc, String text) throws Exception {
        var body = Auth.docs().documents().get(doc).execute().getBody().getContent();
        var end = body.get(body.size() - 1).getEndIndex() - 1;
        Auth.docs().documents().batchUpdate(doc, new BatchUpdateDocumentRequest().setRequests(List.of(new Request()
                .setInsertText(new InsertTextRequest().setText("\n" + text).setLocation(new Location().setIndex(end)))))).execute();
        return find(doc, "PRP25 marker");
    }

    static Para find(String doc, String start) throws Exception {
        var d = Preview.get(doc);
        for (var p : Para.of(Tabs.flat(d).get(0).getDocumentTab())) if (p.plain().startsWith(start)) return p;
        return null;
    }

    static String suggest(String doc, List<Request> reqs) throws Exception {
        var wc = new WriteControl();
        Preview.suggestMode(wc);
        var ids = new LinkedHashSet<String>();
        Preview.createdSuggestionIds(Preview.send(doc, reqs, wc)).forEach(ids::addAll);
        expect(ids.size() == reqs.size(), "one suggestion per request, got " + ids);
        System.out.println("suggested: " + ids);
        return String.join(" ", ids);
    }

    static Request del(Para p, String text, String what) {
        var at = p.start() + text.indexOf(what);
        return new Request().setDeleteContentRange(new DeleteContentRangeRequest().setRange(new Range().setStartIndex(at).setEndIndex(at + what.length())));
    }

    static Request link(Para p, String text, String what) {
        var at = p.start() + text.indexOf(what);
        return new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setFields("link")
                .setTextStyle(new TextStyle().setLink(new Link().setUrl("https://example.org/prp25")))
                .setRange(new Range().setStartIndex(at).setEndIndex(at + what.length())));
    }

    static boolean linked(Para p, String what) {
        var at = p.start() + p.plain().indexOf(what);
        for (var e : p.p().getElements()) {
            var r = e.getTextRun();
            if (r != null && e.getStartIndex() <= at && e.getEndIndex() > at)
                return r.getTextStyle() != null && r.getTextStyle().getLink() != null && r.getSuggestedTextStyleChanges() == null;
        }
        return false;
    }

    static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("FAIL: " + what);
    }
}
