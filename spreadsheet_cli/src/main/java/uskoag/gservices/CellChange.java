package uskoag.gservices;

/** One cell changed by an UPDATE: its sheet row, A1 address, column letter, and old→new values. */
public record CellChange(int row, String cell, String col, String oldVal, String newVal) {}
