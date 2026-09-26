package uskoag.gservices.docs;

import java.util.List;

/** One requested change, as written in a plan or on the command line; nothing here knows an index yet. */
sealed interface Edit {

    String label();

    String tab();

    /** Optional free text carried with the edit (PRP 08 posts it on the suggestion); unused by plain edits. */
    String note();

    /** Replace {@code find} with {@code with}, within the paragraph starting {@code in} when given, else anywhere in the tab. */
    record Replace(String label, String tab, String in, String find, String with, boolean all, String note) implements Edit {}

    /** New paragraphs after the paragraph starting {@code after}, each styled like its {@code like} paragraph, else like the anchor. */
    record Insert(String label, String tab, String after, List<NewPara> paras, String note) implements Edit {}

    /** Remove whole paragraphs, from the one starting {@code from} through the one starting {@code to} (or just {@code from}). */
    record Delete(String label, String tab, String from, String to, String note) implements Edit {}

    record NewPara(String text, String like) {}

    /** One line saying what the edit does, for the journal: what a later reader needs to recognise it in a diff. */
    static String describe(Edit e) {
        return e.label() + " [" + e.tab() + "] " + switch (e) {
            case Replace r -> "replace \"" + r.find() + "\" with \"" + r.with() + "\"";
            case Insert i -> "insert after \"" + i.after() + "\": " + i.paras().stream().map(NewPara::text).toList();
            case Delete d -> "delete \"" + d.from() + "\"" + (d.to() == null ? "" : " .. \"" + d.to() + "\"");
        };
    }
}
