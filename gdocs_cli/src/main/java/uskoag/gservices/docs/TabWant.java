package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;

/** One change to the tab tree. index is the API's own: zero-based among the parent's children, null for "at the end". */
sealed interface TabWant {

    record Add(String title, String parentId, String parentTitle, Integer index) implements TabWant {}

    record Rename(String tabId, String from, String title) implements TabWant {}

    /** Google deletes a tab's child tabs with it, so descendants is a count for the reviewer, not further requests. */
    record Delete(String tabId, String title, int chars, int descendants) implements TabWant {}

    /** sendIndex is what Google is told, index where the tab should end up; they differ on a move down within one parent. */
    record Move(String tabId, String title, String parentId, String parentTitle, Integer index, Integer sendIndex) implements TabWant {}

    static Request request(TabWant w) {
        return switch (w) {
            case Add x -> new Request().setAddDocumentTab(new AddDocumentTabRequest().setTabProperties(
                    new TabProperties().setTitle(x.title()).setParentTabId(x.parentId()).setIndex(x.index())));
            case Rename x -> new Request().setUpdateDocumentTabProperties(new UpdateDocumentTabPropertiesRequest()
                    .setTabProperties(new TabProperties().setTabId(x.tabId()).setTitle(x.title())).setFields("title"));
            case Move x -> new Request().setUpdateDocumentTabProperties(new UpdateDocumentTabPropertiesRequest()
                    .setTabProperties(new TabProperties().setTabId(x.tabId()).setParentTabId(x.parentId()).setIndex(x.sendIndex()))
                    .setFields(x.sendIndex() == null ? "parentTabId" : "parentTabId,index"));
            case Delete x -> new Request().setDeleteTab(new DeleteTabRequest().setTabId(x.tabId()));
        };
    }

    static String describe(TabWant w) {
        return switch (w) {
            case Add x -> "add \"" + x.title() + "\" " + where(x.parentTitle(), x.index());
            case Rename x -> "rename " + x.tabId() + " \"" + x.from() + "\" → \"" + x.title() + "\"";
            case Move x -> "move " + x.tabId() + " \"" + x.title() + "\" " + where(x.parentTitle(), x.index());
            case Delete x -> "delete " + x.tabId() + " \"" + x.title() + "\" (" + x.chars() + " chars" + (x.descendants() == 0 ? ", no child tabs)" : ", and its " + x.descendants() + " child tab(s) with it)");
        };
    }

    private static String where(String parentTitle, Integer index) {
        return (parentTitle == null ? "at top level" : "under \"" + parentTitle + "\"")
                + (index == null ? ", last" : ", position " + (index + 1));
    }

    /** The id the change applies to; an Add's id only exists once the reply names it. */
    static String tabId(TabWant w, String created) {
        return switch (w) { case Add x -> created; case Rename x -> x.tabId(); case Move x -> x.tabId(); case Delete x -> x.tabId(); };
    }

    /** Null when the tab reads back as asked, else what differs. A Delete is checked by its absence, in VerbTab. */
    static String mismatch(TabWant w, Tab t, List<Tab> all) {
        var p = new ArrayList<String>();
        var title = switch (w) { case Add x -> x.title(); case Rename x -> x.title(); case Move x -> x.title(); case Delete x -> x.title(); };
        if (!Tabs.title(t).equals(title)) p.add("title is \"" + Tabs.title(t) + "\"");
        var parent = switch (w) { case Add x -> x.parentId(); case Move x -> x.parentId(); case Rename x -> null; case Delete x -> null; };
        var index = switch (w) { case Add x -> x.index(); case Move x -> x.index(); case Rename x -> null; case Delete x -> null; };
        if (!(w instanceof Rename || w instanceof Delete) && !Objects.equals(Tabs.parentId(t), parent))
            p.add("parent is " + t.getTabProperties().getParentTabId());
        if (index != null && Tabs.siblingIndex(t, all) != index) p.add("at position " + (Tabs.siblingIndex(t, all) + 1));
        return p.isEmpty() ? null : String.join("; ", p);
    }
}
