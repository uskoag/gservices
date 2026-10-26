package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

/** The imported region of the destination tab, read back fresh: from p up to what stood after it before the import. */
final class ImportDest {

    final Document doc;
    final Tab tab;
    final String tabId;
    final int p, end;

    private ImportDest(Document doc, Tab tab, int p, int tail) {
        this.doc = doc; this.tab = tab; this.p = p;
        tabId = Tabs.id(tab);
        end = bodyEnd(tab) - tail;
    }

    static ImportDest read(String docId, String tabId, int p, int tail) throws Exception {
        var doc = DocRef.get(docId);
        for (var t : Tabs.flat(doc)) if (Tabs.id(t).equals(tabId)) return new ImportDest(doc, t, p, tail);
        throw new IllegalStateException("the tab " + tabId + " is gone from the document");
    }

    static int bodyEnd(Tab t) { var c = t.getDocumentTab().getBody().getContent(); return c.getLast().getEndIndex(); }

    List<Para> paras() { return Para.of(tab.getDocumentTab()).stream().filter(x -> x.start() >= p && x.end() <= end).toList(); }

    List<StructuralElement> tables() {
        var out = new ArrayList<StructuralElement>();
        for (var e : tab.getDocumentTab().getBody().getContent()) if (e.getTable() != null && Para.idx(e.getStartIndex()) >= p && e.getEndIndex() <= end) out.add(e);
        return out;
    }

    String where() { return "tab " + Tabs.title(tab) + " index " + p + ".." + end; }

    Range range(int from, int to) { return new Range().setTabId(tabId).setStartIndex(from).setEndIndex(to); }

    Location loc(int i) { return new Location().setTabId(tabId).setIndex(i); }
}
