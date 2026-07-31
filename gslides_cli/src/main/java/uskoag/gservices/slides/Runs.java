package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.TextContent;
import java.util.ArrayList;
import java.util.List;

/**
 * Text is not a string. It is a character sequence with two independent overlays:
 * character runs, and paragraphs. Both are addressed by half-open [start,end) index
 * ranges, and you cannot style safely without reading them first.
 */
public final class Runs {

    private Runs() {}

    static List<RunInfo> of(TextContent tc) {
        var out = new ArrayList<RunInfo>();
        if (tc == null || tc.getTextElements() == null) return out;
        for (var te : tc.getTextElements()) {
            var tr = te.getTextRun();
            if (tr == null || tr.getContent() == null) continue;
            out.add(new RunInfo(nz(te.getStartIndex()), nz(te.getEndIndex()),
                    tr.getContent(), Styles.summary(tr.getStyle())));
        }
        return out;
    }

    static List<ParaInfo> paras(TextContent tc) {
        var out = new ArrayList<ParaInfo>();
        if (tc == null || tc.getTextElements() == null) return out;
        for (var te : tc.getTextElements()) {
            var pm = te.getParagraphMarker();
            if (pm == null) continue;
            var bullet = pm.getBullet() == null ? null
                    : (pm.getBullet().getGlyph() == null ? "bulleted" : pm.getBullet().getGlyph());
            out.add(new ParaInfo(nz(te.getStartIndex()), nz(te.getEndIndex()),
                    Styles.paraSummary(pm.getStyle()), bullet));
        }
        return out;
    }

    /**
     * The raw style of the run covering an index. Needed because a delete+insert replace
     * would otherwise let the new text inherit the insertion point's style and silently
     * drop the formatting the replaced span carried.
     */
    static com.google.api.services.slides.v1.model.TextStyle styleAt(TextContent tc, int index) {
        if (tc == null || tc.getTextElements() == null) return null;
        for (var te : tc.getTextElements()) {
            var tr = te.getTextRun();
            if (tr == null) continue;
            var s = nz(te.getStartIndex());
            var e = nz(te.getEndIndex());
            if (index >= s && index < e) return tr.getStyle();
        }
        return null;
    }

    static int length(TextContent tc) {
        var runs = of(tc);
        return runs.isEmpty() ? 0 : runs.get(runs.size() - 1).end();
    }

    private static int nz(Integer i) { return i == null ? 0 : i; }
}
