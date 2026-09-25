package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.Tab;
import java.util.ArrayList;
import java.util.List;

/** One tab's paragraphs, and the rule every anchor obeys: it must name exactly one paragraph. */
record Scope(Tab tab, List<Para> paras) {

    String tabId() { return Tabs.id(tab); }

    String name() { return Tabs.title(tab) + " (" + tabId() + ")"; }

    /** The single paragraph whose text starts with the anchor; zero or several is an error naming the candidates. */
    Para anchor(String what, String anchorText) {
        var a = Para.anchor(anchorText);
        if (a == null || a.isEmpty()) Out.die(what + ": empty anchor");
        var hits = paras.stream().filter(p -> p.plain().strip().startsWith(a)).toList();
        if (hits.size() == 1) return hits.get(0);
        if (hits.isEmpty()) {
            var key = a.length() > 12 ? a.substring(0, 12) : a;
            var near = paras.stream().filter(p -> p.plain().contains(key)).map(p -> "\n    near: \"" + p.preview() + "\"").toList();
            Out.die(what + ": no paragraph in tab " + name() + " starts with \"" + a + "\"" + String.join("", near));
        }
        Out.die(what + ": " + hits.size() + " paragraphs in tab " + name() + " start with \"" + a + "\" — lengthen the anchor:"
                + String.join("", hits.stream().map(p -> "\n    @" + p.start() + " \"" + p.preview() + "\"").toList()));
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
