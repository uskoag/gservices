package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.Tab;
import java.util.ArrayList;
import java.util.List;

/** One tab's paragraphs, and the rule every anchor obeys: it must name exactly one paragraph. */
record Scope(Tab tab, List<Para> paras) {

    String tabId() { return Tabs.id(tab); }

    String name() { return Tabs.title(tab) + " (" + tabId() + ")"; }

    Para anchor(String what, String anchorText) { return anchor(what, anchorText, null, null); }

    Para anchor(String what, String anchorText, Integer occurrence) { return anchor(what, anchorText, occurrence, null); }

    /**
     * The paragraph whose text starts with the anchor. Several identical starts are an error naming the candidates,
     * unless {@code occurrence} (1-based) picks one. With {@code notBefore}, only paragraphs from that one on are
     * considered and the first match wins — how a delete's "to" is resolved after its "from".
     */
    /** An anchor that names a picture instead of text: IMAGE + objectId, so an image-only paragraph can be an anchor. */
    static final String IMAGE = "\u0001image:";

    Para imagePara(String what, String objectId) {
        for (var p : paras) for (var e : p.p().getElements())
            if (e.getInlineObjectElement() != null && objectId.equals(e.getInlineObjectElement().getInlineObjectId())) return p;
        Out.die(what + ": no image " + objectId + " in tab " + name() + " (run `images <doc>`)");
        return null;
    }

    Para anchor(String what, String anchorText, Integer occurrence, Para notBefore) {
        if (anchorText != null && anchorText.startsWith(IMAGE)) return imagePara(what, anchorText.substring(IMAGE.length()));
        var forms = Para.anchorForms(anchorText);
        if (forms.isEmpty()) Out.die(what + ": empty anchor");
        var pool = paras.stream().filter(p -> notBefore == null || p.start() >= notBefore.start()).toList();
        var hits = List.<Para>of();
        for (var a : forms) { hits = pool.stream().filter(p -> p.plain().strip().startsWith(a)).toList(); if (!hits.isEmpty()) break; }
        var a = forms.get(0);
        if (occurrence != null) {
            if (occurrence < 1) Out.die(what + ": occurrence is 1-based");
            if (occurrence > hits.size()) Out.die(what + ": occurrence " + occurrence + " asked for, but only " + hits.size() + " paragraph(s) in tab " + name() + " start with \"" + a + "\"");
            return hits.get(occurrence - 1);
        }
        if (hits.size() == 1 || notBefore != null && !hits.isEmpty()) return hits.get(0);
        if (hits.isEmpty()) {
            var key = a.length() > 12 ? a.substring(0, 12) : a;
            var near = paras.stream().filter(p -> p.plain().contains(key)).map(p -> "\n    near: \"" + p.preview() + "\"").toList();
            Out.die(what + ": no paragraph " + (notBefore == null ? "" : "after the \"from\" paragraph ") + "in tab " + name() + " starts with \"" + a + "\"" + String.join("", near));
        }
        var n = new int[]{0};
        Out.die(what + ": " + hits.size() + " paragraphs in tab " + name() + " start with \"" + a + "\" — lengthen the anchor, or pick one with --occurrence N (1-based, in document order):"
                + String.join("", hits.stream().map(p -> "\n    #" + ++n[0] + " @" + p.start() + " \"" + p.preview() + "\"").toList()));
        return null;
    }

    /** Document index of every occurrence of {@code find}, within one paragraph or across the tab. */
    List<Integer> occurrences(String find, Para within) {
        var f = Para.norm(find);
        var out = new ArrayList<Integer>();
        for (var p : within != null ? List.of(within) : paras) {
            var t = p.plain();
            for (var i = t.indexOf(f); i >= 0; i = t.indexOf(f, i + 1)) out.add(p.start() + i);
        }
        return out;
    }

    Para paraAt(int docIndex) {
        for (var p : paras) if (p.start() <= docIndex && docIndex < p.end()) return p;
        return null;
    }
}
