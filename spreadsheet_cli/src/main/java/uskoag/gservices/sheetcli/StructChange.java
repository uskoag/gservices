package uskoag.gservices.sheetcli;

/** One structural change: a tab added, removed or renamed, rows or columns inserted, deleted or moved. */
record StructChange(String tab, String kind, String text) {}
