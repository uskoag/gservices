package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.Request;
import java.util.List;

/**
 * An edit resolved against the document: where it lands, what it will send, the lines that show a
 * reviewer what changes, and what the text should look like afterwards so --apply can check it.
 * Applied highest index first, so no edit shifts the indexes another relies on.
 */
record Planned(int order, String tabId, int at, List<Request> requests, List<String> report, List<Expect> expects,
               List<Expect.Bullet> bulletExpects, List<AlignCheck.Want> alignWants) {

    Planned(int order, String tabId, int at, List<Request> requests, List<String> report) {
        this(order, tabId, at, requests, report, List.of(), List.of(), List.of());
    }

    Planned(int order, String tabId, int at, List<Request> requests, List<String> report, List<Expect> expects) {
        this(order, tabId, at, requests, report, expects, List.of(), List.of());
    }
}
