package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.ParagraphStyle;
import com.google.api.services.docs.v1.model.TextStyle;
import java.util.ArrayList;
import java.util.List;

/** One source paragraph, reduced to what the replay needs: its text, its objects, its effective styles. */
final class SrcPara implements Unit {

    String text = "", listId, headingId;
    int level, beforeFlat, seq;
    boolean breakOnly, cell;
    ParagraphStyle style;
    /** The list label's own style (bold "1."), which Docs keeps on the paragraph mark. */
    TextStyle mark;
    final List<SrcObj> objs = new ArrayList<>();
    final List<SrcRun> runs = new ArrayList<>();

    boolean listed() { return listId != null; }

    String line() { return text; }
}
