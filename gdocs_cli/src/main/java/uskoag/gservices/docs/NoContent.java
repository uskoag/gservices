package uskoag.gservices.docs;

/** Google answered 400/404 for a revision number: it holds no content of its own. */
final class NoContent extends IllegalStateException {

    NoContent(long rev) { super("revision " + rev + ": Google keeps no content for that revision number"); }
}
