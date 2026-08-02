package uskoag.gservices.sheetsdb;

/** Identifier quoting and parameter coercion for the H2 mirror. */
final class Sql {

    private Sql() {}

    /**
     * A quoted SQL identifier.
     *
     * <p>Always quoted. Column names here are the model's field names and table names are tab titles,
     * and a tab title is whatever a human typed — spaces, punctuation, a reserved word. Quoting
     * unconditionally means none of that has to be thought about again.
     */
    static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /** A condition value as something JDBC will bind. Only enums need help. */
    static Object param(Object value) {
        return value instanceof Enum<?> e ? e.name() : value;
    }
}
