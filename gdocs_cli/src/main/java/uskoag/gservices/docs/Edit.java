package uskoag.gservices.docs;

import java.util.List;

/** One requested change, as written in a plan or on the command line; nothing here knows an index yet. */
sealed interface Edit {

    String label();

    String tab();

    /** Optional free text carried with the edit (PRP 08 posts it on the suggestion); unused by plain edits. */
    String note();

    /** Replace {@code find} with {@code with}, within the paragraph starting {@code in} when given, else anywhere in the tab. */
    record Replace(String label, String tab, String in, Integer inOcc, String find, String with, boolean all, boolean literal, String note) implements Edit {}

    /** New paragraphs after the paragraph starting {@code after}, each styled like its {@code like} paragraph, else like the anchor. */
    record Insert(String label, String tab, String after, Integer afterOcc, List<NewPara> paras, String note) implements Edit {}

    /** Remove everything from the paragraph starting {@code from} through the one starting {@code to} (the first match after from), or just from, or to the end of the tab: whole tables, pictures and page breaks in between go too. *Occ picks the Nth (1-based) of several identical anchors. */
    record Delete(String label, String tab, String from, Integer fromOcc, String to, Integer toOcc, boolean toEnd, String note) implements Edit {}

    /** Remove a native table of contents of the tab ({@code index} 1-based, needed only when there are several). */
    record TocDelete(String label, String tab, Integer index, String note) implements Edit {}

    /**
     * style, when given, is a named paragraph style (normal, heading1..6, title, subtitle): no list, no
     * inherited text style. list = "bullet" forces a bulleted paragraph even when neither the anchor nor
     * {@code like} already carries one, in a default disc preset (or {@code like}'s own preset when it
     * has one) — otherwise a bullet can only ever be copied from an existing one. spacing = "tight" sets
     * spaceAbove/spaceBelow to 0, independent of (and applied after) style/like's own spacing.
     * list may also be "number"; level (0..8) nests the paragraph in a list of its own, made together with
     * the neighbouring list paragraphs of this insert; preset is an API list preset name overriding the default for the kind.
     */
    record NewPara(String text, String like, String style, String list, String spacing, Integer level, String preset, ParaFmt fmt) {
        NewPara(String text, String like, String style, String list, String spacing, Integer level, String preset) {
            this(text, like, style, list, spacing, level, preset, ParaFmt.NONE);
        }
    }

    /** One line saying what the edit does, for the journal: what a later reader needs to recognise it in a diff. */
    static String describe(Edit e) {
        return e.label() + " [" + e.tab() + "] " + switch (e) {
            case Replace r -> "replace \"" + r.find() + "\" with \"" + r.with() + "\"";
            case Insert i -> "insert after \"" + i.after() + "\": " + i.paras().stream().map(NewPara::text).toList();
            case Delete d -> "delete \"" + d.from() + "\"" + (d.to() == null ? "" : " .. \"" + d.to() + "\"");
            case TocDelete t -> "toc-delete" + (t.index() == null ? "" : " #" + t.index());
        };
    }
}
