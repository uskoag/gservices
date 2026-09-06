package uskoag.gservices;

import com.google.api.services.sheets.v4.model.BooleanCondition;
import com.google.api.services.sheets.v4.model.ConditionValue;
import com.google.api.services.sheets.v4.model.DataValidationRule;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SetDataValidationRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Builds the SetDataValidationRequest for the `validate` command. */
public class CellValidation {

    /** fromRange (A1 range ref, e.g. "'Allowed Values'!A2:A") xor list (comma-separated literals). */
    public static Request buildSetRequest(GridRange range, String fromRange, String list, boolean strict) {
        var condition = new BooleanCondition();
        if (fromRange != null) {
            // ONE_OF_RANGE requires a formula-style reference ("=Sheet!A1:A10"); auto-prefix if missing.
            var rangeRef = fromRange.startsWith("=") ? fromRange : "=" + fromRange;
            condition.setType("ONE_OF_RANGE")
                .setValues(List.of(new ConditionValue().setUserEnteredValue(rangeRef)));
        } else {
            var values = Stream.of(list.split(","))
                .map(v -> new ConditionValue().setUserEnteredValue(v.trim()))
                .toList();
            condition.setType("ONE_OF_LIST").setValues(values);
        }

        var rule = new DataValidationRule().setCondition(condition).setStrict(strict).setShowCustomUi(true);
        return new Request().setSetDataValidation(new SetDataValidationRequest().setRange(range).setRule(rule));
    }

    public static Request buildClearRequest(GridRange range) {
        return new Request().setSetDataValidation(new SetDataValidationRequest().setRange(range));
    }

    /**
     * One ONE_OF_RANGE rule per row, {ROW} in `template` substituted with that row's own 1-based
     * number, each applied to a single-row GridRange. Needed because — unlike conditional
     * formatting — a data-validation rule set once over a multi-row range does NOT shift its
     * formula's relative references per row; every row would otherwise evaluate the same
     * top-anchor cell. This is the per-row expansion that gives cascading/dependent dropdowns.
     */
    public static List<Request> buildPerRowRequests(int sheetId, int startCol, int endCol, int startRow, int endRow,
                                                      String template, boolean strict) {
        var requests = new ArrayList<Request>();
        for (int row = startRow; row <= endRow; row++) {
            var formula = "=" + template.replace("{ROW}", String.valueOf(row));
            var condition = new BooleanCondition().setType("ONE_OF_RANGE")
                .setValues(List.of(new ConditionValue().setUserEnteredValue(formula)));
            var rule = new DataValidationRule().setCondition(condition).setStrict(strict).setShowCustomUi(true);
            var rowRange = new GridRange().setSheetId(sheetId)
                .setStartRowIndex(row - 1).setEndRowIndex(row)
                .setStartColumnIndex(startCol - 1).setEndColumnIndex(endCol);
            requests.add(new Request().setSetDataValidation(new SetDataValidationRequest().setRange(rowRange).setRule(rule)));
        }
        return requests;
    }
}
