package uskoag.gservices;

/** One header-row cell: its column position, the header text, and its cell note (the "comment"). */
public record SchemaHeaderCell(int colNum, String colAddr, String value, String comment) {}
