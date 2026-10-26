package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;

/**
 * Stage S1: reset what the new paragraphs inherited, then make the lists. A source list that other paragraphs
 * interrupt is made as ONE list over its whole extent, so its numbering runs on, and the interrupting paragraphs
 * are then taken out of it or made into their own lists (the larger list first, the lists inside it after).
 * Leading tabs are put in just before each list is made and are consumed by it, so no position ever shifts.
 * Docs reads the tabs relative to the fewest in the range, which is why a stretch of nested items needs a neighbour with none.
 */
final class ImportLists {

    private static final String TEXT = "bold,italic,underline,strikethrough,smallCaps,backgroundColor,foregroundColor,fontSize,weightedFontFamily,baselineOffset,link",
            PARA = "namedStyleType,alignment,lineSpacing,direction,spaceAbove,spaceBelow,indentFirstLine,indentStart,indentEnd,keepLinesTogether,keepWithNext,avoidWidowAndOrphan,shading,borderTop,borderBottom,borderLeft,borderRight,borderBetween";

    record Op(int from, int to, String listId) {}

    private ImportLists() {}

    static List<Request> make(ImportPlan plan, ImportDest d, List<Para> dp, DocumentTab src, List<String> report) {
        var out = new ArrayList<Request>();
        out.add(new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setRange(d.range(d.p, d.end)).setTextStyle(new TextStyle()).setFields(TEXT)));
        out.add(new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest().setRange(d.range(d.p, d.end)).setParagraphStyle(new ParagraphStyle().setNamedStyleType("NORMAL_TEXT")).setFields(PARA)));
        out.add(new Request().setDeleteParagraphBullets(new DeleteParagraphBulletsRequest().setRange(d.range(d.p, d.end))));
        var flat = plan.flat;
        for (var op : ops(flat)) {
            var preset = ListPresetsOf.of(src.getLists().get(op.listId()), report, op.listId());
            Out.info("list op " + op.listId() + " paragraphs " + op.from() + ".." + op.to());
            out.add(new Request().setDeleteParagraphBullets(new DeleteParagraphBulletsRequest().setRange(d.range(dp.get(op.from()).start(), dp.get(op.to()).end()))));
            var tabs = 0;
            var min = Integer.MAX_VALUE;
            var gaps = false;
            for (var i = op.to(); i >= op.from(); i--) {
                var m = flat.get(i);
                if (!op.listId().equals(m.listId)) { gaps = true; continue; }
                min = Math.min(min, m.level);
                tabs += m.level;
                if (m.level > 0) out.add(new Request().setInsertText(new InsertTextRequest().setText("\t".repeat(m.level)).setLocation(d.loc(dp.get(i).start()))));
            }
            var anchor = min > 0 && !gaps && op.from() > 0 && !flat.get(op.from() - 1).listed() ? op.from() - 1 : -1;
            if (min > 0 && !gaps && anchor < 0) plan.note("nested list items whose level could not be kept (nothing plain beside them to anchor on)");
            var start = dp.get(anchor >= 0 ? anchor : op.from()).start();
            out.add(new Request().setCreateParagraphBullets(new CreateParagraphBulletsRequest().setRange(d.range(start, dp.get(op.to()).end() + tabs)).setBulletPreset(preset)));
            if (anchor >= 0) out.add(new Request().setDeleteParagraphBullets(new DeleteParagraphBulletsRequest().setRange(d.range(start, dp.get(anchor).end()))));
            for (var i = op.from(); i <= op.to(); i++) {
                if (flat.get(i).listed()) continue;
                var j = i;
                while (j + 1 <= op.to() && !flat.get(j + 1).listed()) j++;
                out.add(new Request().setDeleteParagraphBullets(new DeleteParagraphBulletsRequest().setRange(d.range(dp.get(i).start(), dp.get(j).end()))));
                i = j;
            }
        }
        return out;
    }

    /** Larger lists first, each over its whole extent unless that would swallow members of a list made before it; those become one list per unbroken stretch. */
    static List<Op> ops(List<SrcPara> flat) {
        var first = new LinkedHashMap<String, Integer>();
        var last = new HashMap<String, Integer>();
        for (var i = 0; i < flat.size(); i++) if (flat.get(i).listed()) { first.putIfAbsent(flat.get(i).listId, i); last.put(flat.get(i).listId, i); }
        var order = new ArrayList<>(first.keySet());
        order.sort(Comparator.comparingInt((String id) -> first.get(id) - last.get(id)).thenComparingInt(first::get));
        var bridged = new HashSet<String>();
        var whole = new ArrayList<Op>();
        var stretches = new ArrayList<Op>();
        for (var id : order) {
            var swallows = false;
            for (var i = first.get(id); i <= last.get(id); i++) if (bridged.contains(flat.get(i).listId)) swallows = true;
            if (!swallows) { bridged.add(id); whole.add(new Op(first.get(id), last.get(id), id)); continue; }
            for (var i = first.get(id); i <= last.get(id); i++) {
                if (!id.equals(flat.get(i).listId)) continue;
                var j = i;
                while (j + 1 <= last.get(id) && id.equals(flat.get(j + 1).listId)) j++;
                stretches.add(new Op(i, j, id));
                i = j;
            }
        }
        stretches.sort(Comparator.comparingInt(Op::from));
        whole.addAll(stretches);
        return whole;
    }
}
