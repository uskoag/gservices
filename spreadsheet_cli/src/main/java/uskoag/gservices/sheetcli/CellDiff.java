package uskoag.gservices.sheetcli;

/**
 * One cell that differs between the two versions.
 *
 * @param at   its address in the newer version
 * @param was  its address in the older version, when rows or columns moved it; else null
 * @param note what kind of change it is when that matters more than the value: a formula replaced by a typed value, a cell that became an error
 */
record CellDiff(String tab, String at, String was, String from, String to, String note) {}
