package uskoag.gservices;

import com.google.api.services.sheets.v4.model.BasicFilter;
import com.google.api.services.sheets.v4.model.ClearBasicFilterRequest;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SetBasicFilterRequest;

/** Builds the SetBasicFilter / ClearBasicFilter requests for the `filter` command. */
public class CellFilter {

    /**
     * Turn on the "Create a filter" funnel buttons over the range (its first row is the header).
     * A sheet holds exactly one basic filter, so re-running just replaces it — never stacks.
     * A whole-sheet range is a GridRange carrying only the sheetId (all bounds unset).
     */
    public static Request buildSetRequest(GridRange range) {
        return new Request().setSetBasicFilter(
            new SetBasicFilterRequest().setFilter(new BasicFilter().setRange(range)));
    }

    public static Request buildClearRequest(Integer sheetId) {
        return new Request().setClearBasicFilter(new ClearBasicFilterRequest().setSheetId(sheetId));
    }
}
