package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.Request;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Ops keep the --op=value form and are applied IN ORDER, because they are ordered and
 * repeatable (two --replace then a --style) which a flag map cannot express. Each
 * text-changing op flushes before the next runs, so later ops see fresh indices.
 */
public final class VerbText {

    private VerbText() {}

    static void run(List<String> a) throws Exception {
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var elem = Args.req(a, "<elem>");
        if (a.isEmpty()) Out.die("text: needs at least one op (--set= --set-html= --replace= --replace-all= --delete=)");

        for (var op : new ArrayList<>(a)) applyOp(deck, elem, op);
        Out.success("text updated on " + elem);
    }

    private static void applyOp(String deck, String elem, String op) throws Exception {
        var body = op.contains("=") ? op.substring(op.indexOf('=') + 1) : "";
        var reqs = new ArrayList<Request>();

        if (op.startsWith("--set-html-file=")) reqs.addAll(setHtml(deck, elem, read(body)));
        else if (op.startsWith("--set-html=")) reqs.addAll(setHtml(deck, elem, body));
        else if (op.startsWith("--set-file="))
            reqs.addAll(RichRequests.setText(elem, RichText.plain(read(body)), lengthOf(deck, elem)));
        else if (op.startsWith("--set="))
            reqs.addAll(RichRequests.setText(elem, RichText.plain(unquote(body)), lengthOf(deck, elem)));
        else if (op.startsWith("--replace-all=")) reqs.addAll(replace(deck, elem, body, true));
        else if (op.startsWith("--replace=")) reqs.addAll(replace(deck, elem, body, false));
        else if (op.startsWith("--delete=")) {
            var r = span(body);
            reqs.add(RichRequests.deleteRange(elem, r[0], r[1]));
        } else {
            Out.die("text: unknown op " + op);
        }

        Out.info(op);
        Api.flush(deck, reqs);
    }

    private static List<Request> setHtml(String deck, String elem, String html) throws Exception {
        var rt = HtmlToRich.convert(html);
        Out.info("html -> " + rt.text().length() + " chars, " + rt.spans().size()
                + " run(s), " + rt.paras().size() + " styled paragraph(s)");
        return RichRequests.setText(elem, rt, lengthOf(deck, elem));
    }

    static int lengthOf(String deck, String elem) throws Exception {
        var pres = Deck.get(deck, "slides(objectId,pageElements)");
        return Runs.length(Els.textContent(Deck.element(pres, elem)));
    }

    /**
     * Emitted right-to-left so each group's indices are still valid when it executes
     * (batchUpdate applies requests in order).
     *
     * The style of the run covering each match is captured BEFORE the delete and
     * re-applied to the inserted text. Without that, the new text inherits whatever
     * style sits at the insertion point and the replaced span silently loses its own
     * formatting -- replacing an italic word would hand back a non-italic one.
     */
    private static List<Request> replace(String deck, String elem, String spec, boolean all) throws Exception {
        var arrow = spec.indexOf("=>");
        if (arrow < 0) { Out.die("expected OLD=>NEW in: " + spec); return List.of(); }
        var oldS = unquote(spec.substring(0, arrow));
        var newS = unquote(spec.substring(arrow + 2));

        var pres = Deck.get(deck, "slides(objectId,pageElements)");
        var tc = Els.textContent(Deck.element(pres, elem));
        var text = Els.textOf(tc);

        var hits = new ArrayList<Integer>();
        for (var i = text.indexOf(oldS); i >= 0; i = text.indexOf(oldS, i + oldS.length())) {
            hits.add(i);
            if (!all) break;
        }
        if (hits.isEmpty()) { Out.error("not found in " + elem + ": \"" + oldS + "\""); return List.of(); }

        var reqs = new ArrayList<Request>();
        for (var i = hits.size() - 1; i >= 0; i--) {
            var at = hits.get(i);
            var keep = Runs.styleAt(tc, at);
            reqs.add(RichRequests.deleteRange(elem, at, at + oldS.length()));
            if (!newS.isEmpty()) {
                reqs.add(RichRequests.insert(elem, at, newS));
                var restore = RichRequests.textStyle(elem, Styles.toSpan(keep, at, at + newS.length()));
                if (restore != null) reqs.add(restore);
            }
        }
        Out.info("replacing " + hits.size() + " occurrence(s)");
        return reqs;
    }

    static String currentText(String deck, String elem) throws Exception {
        var pres = Deck.get(deck, "slides(objectId,pageElements)");
        return Els.textOf(Deck.element(pres, elem));
    }

    private static String read(String path) {
        try { return Files.readString(Path.of(path)); }
        catch (Exception e) { Out.die("cannot read " + path + ": " + e.getMessage()); return null; }
    }

    static int[] span(String s) {
        var p = s.split(":");
        if (p.length != 2) Out.die("expected START:END, got: " + s);
        return new int[]{ Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()) };
    }

    static String unquote(String s) {
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'")))
            return s.substring(1, s.length() - 1);
        return s;
    }
}
