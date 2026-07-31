package uskoag.gservices;

import java.util.List;

public record SpreadsheetSchema(String spreadsheetName, String spreadsheetId, String spreadsheetNotes, List<SheetSchema> sheets) {}
