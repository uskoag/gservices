package uskoag.gservices;

import com.google.api.services.sheets.v4.model.AddConditionalFormatRuleRequest;
import com.google.api.services.sheets.v4.model.BooleanCondition;
import com.google.api.services.sheets.v4.model.BooleanRule;
import com.google.api.services.sheets.v4.model.ConditionValue;
import com.google.api.services.sheets.v4.model.ConditionalFormatRule;
import com.google.api.services.sheets.v4.model.DeleteConditionalFormatRuleRequest;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Request;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Builds conditional-format rules that color-code exact-text matches — one rule per distinct value. */
public class CellHighlight {

    public static List<Request> buildAddRequests(GridRange range, List<String> values, List<String> colors, int startIndex) {
        var requests = new ArrayList<Request>();
        for (int i = 0; i < values.size(); i++) {
            var condition = new BooleanCondition().setType("TEXT_EQ")
                .setValues(List.of(new ConditionValue().setUserEnteredValue(values.get(i))));
            var format = new com.google.api.services.sheets.v4.model.CellFormat()
                .setBackgroundColor(CliColor.hexToColor(colors.get(i % colors.size())));
            var rule = new ConditionalFormatRule()
                .setRanges(List.of(range))
                .setBooleanRule(new BooleanRule().setCondition(condition).setFormat(format));
            requests.add(new Request().setAddConditionalFormatRule(
                new AddConditionalFormatRuleRequest().setRule(rule).setIndex(startIndex + i)));
        }
        return requests;
    }

    /** Atomically replace this range's highlight rules with a fresh set for `values`/`colors`. */
    public static List<Request> buildRefreshRequests(List<ConditionalFormatRule> existingRules, int sheetId,
                                                       GridRange range, List<String> values, List<String> colors) {
        return buildRefreshRequestsMulti(existingRules, sheetId, List.of(range), List.of(values), List.of(colors));
    }

    /**
     * Atomically refresh several highlight targets on ONE sheet in a single request list: delete every
     * stale highlight rule for any target first (descending indices, so earlier indices stay valid),
     * then append the fresh rules. Doing all deletes before all adds is what keeps the index math
     * correct when two targets live on the same sheet (e.g. a data column and its legend column) —
     * refreshing them with two independent calls would corrupt the second one's indices mid-batch.
     */
    public static List<Request> buildRefreshRequestsMulti(List<ConditionalFormatRule> existingRules, int sheetId,
            List<GridRange> ranges, List<List<String>> valuesPer, List<List<String>> colorsPer) {
        var matched = new java.util.TreeSet<Integer>(Collections.reverseOrder());
        for (var range : ranges) matched.addAll(matchingIndices(existingRules, range));
        var requests = new ArrayList<>(buildDeleteRequests(sheetId, new ArrayList<>(matched)));
        int nextIndex = (existingRules == null ? 0 : existingRules.size()) - matched.size();
        for (int t = 0; t < ranges.size(); t++) {
            requests.addAll(buildAddRequests(ranges.get(t), valuesPer.get(t), colorsPer.get(t), nextIndex));
            nextIndex += valuesPer.get(t).size();
        }
        return requests;
    }

    public static List<Request> buildDeleteRequests(int sheetId, List<Integer> descendingIndices) {
        var requests = new ArrayList<Request>();
        for (int idx : descendingIndices) {
            requests.add(new Request().setDeleteConditionalFormatRule(
                new DeleteConditionalFormatRuleRequest().setSheetId(sheetId).setIndex(idx)));
        }
        return requests;
    }

    /** Indices (within a sheet's conditionalFormats) of the highlight rules belonging to `range`'s
     *  anchor, in descending order — deleting from the end first keeps earlier indices valid within
     *  the same batchUpdate. */
    public static List<Integer> matchingIndices(List<ConditionalFormatRule> rules, GridRange range) {
        var out = new ArrayList<Integer>();
        if (rules == null) return out;
        for (int i = 0; i < rules.size(); i++) {
            if (isHighlightRuleFor(rules.get(i), range)) out.add(i);
        }
        Collections.reverse(out);
        return out;
    }

    /**
     * Is this an existing highlight rule belonging to `target`'s anchor? True only for a single-range
     * TEXT_EQ boolean rule sharing target's sheet, both column bounds, and start row — the END row is
     * deliberately ignored, so a legend/column that grew or shrank between runs (its end moves, its
     * anchor doesn't) is still recognized as the same stack and gets replaced rather than orphaned.
     * The TEXT_EQ + single-range gate keeps this from ever touching gradient rules, multi-range rules,
     * or a plain `format` fill the user set by hand.
     */
    private static boolean isHighlightRuleFor(ConditionalFormatRule rule, GridRange target) {
        var ranges = rule.getRanges();
        if (ranges == null || ranges.size() != 1) return false;
        var br = rule.getBooleanRule();
        if (br == null || br.getCondition() == null || !"TEXT_EQ".equals(br.getCondition().getType())) return false;
        var r = ranges.get(0);
        return eq(r.getSheetId(), target.getSheetId())
            && eq(r.getStartColumnIndex(), target.getStartColumnIndex())
            && eq(r.getEndColumnIndex(), target.getEndColumnIndex())
            && eq(r.getStartRowIndex(), target.getStartRowIndex());
    }

    private static boolean eq(Integer x, Integer y) {
        return (x == null ? 0 : x) == (y == null ? 0 : y);
    }
}
