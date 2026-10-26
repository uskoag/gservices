package uskoag.gservices.sheetcli;

/** A refusal or a usage error: its message is the whole report, no stack trace. */
final class CliError extends RuntimeException {
    CliError(String m) { super(m); }
}
