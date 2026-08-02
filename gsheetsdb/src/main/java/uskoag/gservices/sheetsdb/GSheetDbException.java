package uskoag.gservices.sheetsdb;

/** Anything that went wrong talking to the spreadsheet or to the H2 mirror behind it. */
public class GSheetDbException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GSheetDbException(String message) {
        super(message);
    }

    public GSheetDbException(String message, Throwable cause) {
        super(message, cause);
    }
}
