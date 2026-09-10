package uskoag.gservices.sheetsdb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import datapotter.datahelper.DataHelper_I;
import datapotter.datahelper.Field_I;

/**
 * Typed reads over the documents of one type.
 *
 * <pre>
 * import static uskoag.gservices.sheetsdb.Query.query;
 * import static com.example.model.Treaty_IR.*;          // $title, $year, $ratified
 *
 * List&lt;Treaty&gt; recent = query(db, Treaty.DEF)
 *         .gt($year, 2000)
 *         .eq($ratified, true)
 *         .orderByDesc($year)
 *         .limit(20)
 *         .list();
 *
 * Treaty one = query(db, Treaty.DEF).byKey("UAN-1997-03");
 * long n     = query(db, Treaty.DEF).eq($ratified, false).count();
 * </pre>
 *
 * <p>The entry point takes the database and the type definition, the same two arguments ArcadeDB's
 * {@code query(db, TYPEDEF)} takes, and for the same reason: the definition already carries the type
 * name and the {@code X::new} factory, so neither has to appear at the call site.
 *
 * <p>A field carries its own type, so field and value are bound by the generic signature and a
 * mismatch is a compile error rather than an empty result. {@code eq($year, "big")} does not compile.
 *
 * <h3>Two things this can do that the ArcadeDB original could not</h3>
 *
 * <p><b>Real grouping, and NOT.</b> ArcadeDB's builder joins conditions with a fixed precedence and
 * cannot express {@code (a OR b) AND (c OR d)} at all — its own javadoc says to drop to raw SQL. Here
 * {@link #group} nests:
 *
 * <pre>
 * query(db, Treaty.DEF)
 *         .group(g -&gt; g.eq($status, "signed").or().eq($status, "ratified"))
 *         .gt($year, 1990)
 *         .list();
 * </pre>
 *
 * <p><b>An unknown field is not a silent trap.</b> The graph engine re-read candidates with a flat
 * {@code record.get()}, so filtering an embedded field returned nothing rather than failing. Here a
 * field either belongs to the type or {@link #eq} throws naming it.
 *
 * <h3>Everything is eager, deliberately</h3>
 *
 * <p>{@link #iterator()} and {@link #stream()} are backed by {@link #list()} rather than by a live
 * JDBC cursor. A lazy cursor would outlive this call, and a caller who broke out of the loop early
 * would leak it. The data is a spreadsheet — already fully resident in the mirror, and bounded by
 * what Google will hold — so laziness would buy nothing and cost a resource leak.
 */
public final class Query<E extends DataHelper_I<E>> implements Iterable<E> {

    private enum Op { EQ, NEQ, LT, LE, GT, GE, LIKE, ILIKE, IN, BETWEEN, IS_NULL, IS_NOT_NULL }

    private sealed interface Node {
        /** Whether this node joins to the previous one with OR rather than AND. */
        boolean or();
    }

    private record Leaf(String field, Op op, Object v1, Object v2, boolean or) implements Node {}

    private record Group(List<Node> children, boolean or, boolean negated) implements Node {}

    private record Raw(String sql, List<Object> params, boolean or) implements Node {}

    /** Null on the condition-only child handed to {@link #group}. */
    private final SheetDocs<E> docs;
    private final TypeDef<E> def;

    private final List<Node> nodes = new ArrayList<>();
    private final List<String> orderBy = new ArrayList<>();
    private boolean nextIsOr = false;
    private int limit = -1;
    private int skip = -1;

    private Query(SheetDocs<E> docs, TypeDef<E> def) {
        this.docs = docs;
        this.def = def;
    }

    /** Start a query against the documents of {@code def}. */
    public static <E extends DataHelper_I<E>> Query<E> query(GSheetDb db, TypeDef<E> def) {
        if (db == null) throw new IllegalArgumentException("db is required");
        return new Query<>(db.docs(def), def);
    }

    // ---- joining ---------------------------------------------------------------

    /** Join the <b>next</b> condition with OR instead of AND. */
    public Query<E> or() {
        nextIsOr = true;
        return this;
    }

    /** Explicit AND for the next condition. Already the default; use it for readability. */
    public Query<E> and() {
        nextIsOr = false;
        return this;
    }

    /** A parenthesised group, joined to what precedes it by AND (or by {@link #or()}). */
    public Query<E> group(Consumer<Query<E>> body) {
        return group(body, false);
    }

    /** A negated parenthesised group — {@code NOT (...)}. */
    public Query<E> notGroup(Consumer<Query<E>> body) {
        return group(body, true);
    }

    private Query<E> group(Consumer<Query<E>> body, boolean negated) {
        var child = new Query<>((SheetDocs<E>) null, def);
        body.accept(child);
        if (!child.nodes.isEmpty()) {
            nodes.add(new Group(List.copyOf(child.nodes), nextIsOr, negated));
        }
        nextIsOr = false;
        return this;
    }

    /**
     * A raw SQL fragment against the mirror, with bound parameters — the escape hatch for the
     * occasional thing this DSL has no word for. Identifiers are the field names, quoted.
     *
     * <pre>
     * .where("LENGTH(\"title\") &gt; ?", 80)
     * </pre>
     */
    public Query<E> where(String sql, Object... params) {
        nodes.add(new Raw(sql, params == null ? List.of() : List.of(params), nextIsOr));
        nextIsOr = false;
        return this;
    }

    // ---- conditions ------------------------------------------------------------

    public <T> Query<E> eq(Field_I<E, T> field, T value)  { return add(field, Op.EQ, value, null); }

    /**
     * Not equal — and, following SQL rather than intuition, <b>an empty cell does not match</b>.
     *
     * <p>{@code NULL <> 'RATIFIED'} is unknown, not true, so a blank in that field is excluded along
     * with the documents that do equal it. This trips people up on a spreadsheet, where blank is the
     * commonest value in any optional field, so it is spelled out rather than discovered. To include
     * blanks, say so:
     *
     * <pre>
     * .group(g -&gt; g.neq($status, "RATIFIED").or().isNull($status))
     * </pre>
     *
     * <p>Kept as SQL's semantics deliberately: this compiles to SQL and offers a raw {@link #where}
     * alongside, and a DSL whose operators quietly disagree with the SQL beside them is worse than
     * one that is merely strict. Occasionally it is exactly what you want — {@code neq($field, "")}
     * excludes a name-less cell whether it holds an empty string or nothing at all.
     */
    public <T> Query<E> neq(Field_I<E, T> field, T value) { return add(field, Op.NEQ, value, null); }

    public <T> Query<E> lt(Field_I<E, T> field, T value)  { return add(field, Op.LT, value, null); }
    public <T> Query<E> le(Field_I<E, T> field, T value)  { return add(field, Op.LE, value, null); }
    public <T> Query<E> gt(Field_I<E, T> field, T value)  { return add(field, Op.GT, value, null); }
    public <T> Query<E> ge(Field_I<E, T> field, T value)  { return add(field, Op.GE, value, null); }

    /** SQL {@code LIKE} with {@code %} wildcards. String fields only. */
    public Query<E> like(Field_I<E, String> field, String pattern) {
        return add(field, Op.LIKE, pattern, null);
    }

    /** Case-insensitive {@code LIKE}. String fields only. */
    public Query<E> ilike(Field_I<E, String> field, String pattern) {
        return add(field, Op.ILIKE, pattern, null);
    }

    /** Membership. An empty collection is a condition nothing satisfies, which is what it says. */
    public <T> Query<E> in(Field_I<E, T> field, Collection<T> values) {
        return add(field, Op.IN, values == null ? List.of() : List.copyOf(values), null);
    }

    /** Inclusive range — {@code low <= field <= high}. */
    public <T> Query<E> between(Field_I<E, T> field, T low, T high) {
        return add(field, Op.BETWEEN, low, high);
    }

    public Query<E> isNull(Field_I<E, ?> field)    { return add(field, Op.IS_NULL, null, null); }
    public Query<E> isNotNull(Field_I<E, ?> field) { return add(field, Op.IS_NOT_NULL, null, null); }

    // ---- modifiers -------------------------------------------------------------

    /** Add a sort key. Call again for secondary keys, in order. */
    public Query<E> orderBy(Field_I<E, ?> field, boolean ascending) {
        orderBy.add(Sql.quote(named(field)) + (ascending ? " ASC" : " DESC"));
        return this;
    }

    public Query<E> orderByAsc(Field_I<E, ?> field)  { return orderBy(field, true); }
    public Query<E> orderByDesc(Field_I<E, ?> field) { return orderBy(field, false); }

    public Query<E> limit(int n) { this.limit = n; return this; }
    public Query<E> skip(int n)  { this.skip = n; return this; }

    // ---- terminals -------------------------------------------------------------

    /** Every matching document. */
    public List<E> list() {
        var sql = new StringBuilder("SELECT * FROM ").append(Sql.quote(def.name()));
        var params = new ArrayList<Object>();
        appendWhere(sql, params);
        appendOrderLimit(sql);
        return required().query(sql.toString(), params);
    }

    @Override
    public Iterator<E> iterator() {
        return list().iterator();
    }

    public Stream<E> stream() {
        return list().stream();
    }

    /** The first matching document, or empty. */
    public Optional<E> first() {
        return Optional.ofNullable(firstOrNull());
    }

    /** The first matching document, or {@code null}. */
    public E firstOrNull() {
        int keep = limit;
        try {
            if (limit < 0 || limit > 1) limit = 1;
            var found = list();
            return found.isEmpty() ? null : found.get(0);
        } finally {
            limit = keep;
        }
    }

    /**
     * The document with this key, or {@code null} — the shortest form of the commonest lookup.
     *
     * <p>Throws if the sheet carries that key on more than one row. Two documents with one identity
     * is corruption rather than a result, and picking one of them would be a coin toss.
     *
     * <p>Ignores every condition already on this query: a key lookup is a key lookup.
     */
    public E byKey(Object key) {
        return required().byKey(key);
    }

    /** How many documents match. Honours {@link #limit(int)}. */
    public long count() {
        var inner = new StringBuilder("SELECT 1 FROM ").append(Sql.quote(def.name()));
        var params = new ArrayList<Object>();
        appendWhere(inner, params);
        appendOrderLimit(inner);
        // The derived table needs a name: H2 tolerates an anonymous one, the SQL standard does not,
        // and there is no reason to depend on the tolerance.
        return required().scalarLong("SELECT COUNT(*) FROM (" + inner + ") AS __counted", params);
    }

    /** Whether anything matches. */
    public boolean exists() {
        return firstOrNull() != null;
    }

    // ---- internals -------------------------------------------------------------

    private SheetDocs<E> required() {
        if (docs == null) {
            throw new IllegalStateException(
                    "This Query is the condition-only builder handed to group(...); it collects "
                    + "conditions and cannot be executed. Call the terminal on the outer query.");
        }
        return docs;
    }

    private String named(Field_I<E, ?> field) {
        if (field == null) throw new IllegalArgumentException("field is required");
        String name = field.name();
        if (def.fieldType(name) == null) {
            throw new IllegalArgumentException("'" + name + "' is not a field of " + def.name()
                    + ". Fields: " + def.fields());
        }
        return name;
    }

    private Query<E> add(Field_I<E, ?> field, Op op, Object v1, Object v2) {
        nodes.add(new Leaf(named(field), op, v1, v2, nextIsOr));
        nextIsOr = false;
        return this;
    }

    private void appendWhere(StringBuilder sql, List<Object> params) {
        if (nodes.isEmpty()) return;
        sql.append(" WHERE ");
        render(nodes, sql, params);
    }

    private void render(List<Node> ns, StringBuilder sql, List<Object> params) {
        boolean first = true;
        for (Node n : ns) {
            if (!first) sql.append(n.or() ? " OR " : " AND ");
            first = false;
            switch (n) {
                case Leaf l -> renderLeaf(l, sql, params);
                case Group g -> {
                    if (g.negated()) sql.append("NOT ");
                    sql.append('(');
                    render(g.children(), sql, params);
                    sql.append(')');
                }
                case Raw r -> {
                    sql.append('(').append(r.sql()).append(')');
                    params.addAll(r.params());
                }
            }
        }
    }

    private void renderLeaf(Leaf l, StringBuilder sql, List<Object> params) {
        String f = Sql.quote(l.field());
        switch (l.op()) {
            case EQ  -> { sql.append(f).append(" = ?");  params.add(Sql.param(l.v1())); }
            case NEQ -> { sql.append(f).append(" <> ?"); params.add(Sql.param(l.v1())); }
            case LT  -> { sql.append(f).append(" < ?");  params.add(Sql.param(l.v1())); }
            case LE  -> { sql.append(f).append(" <= ?"); params.add(Sql.param(l.v1())); }
            case GT  -> { sql.append(f).append(" > ?");  params.add(Sql.param(l.v1())); }
            case GE  -> { sql.append(f).append(" >= ?"); params.add(Sql.param(l.v1())); }
            case LIKE -> { sql.append(f).append(" LIKE ?"); params.add(Sql.param(l.v1())); }
            case ILIKE -> {
                sql.append("UPPER(").append(f).append(") LIKE UPPER(?)");
                params.add(Sql.param(l.v1()));
            }
            case IN -> {
                var values = (Collection<?>) l.v1();
                if (values.isEmpty()) {
                    sql.append("1 = 0");            // matches nothing, which is what an empty IN means
                    return;
                }
                sql.append(f).append(" IN (");
                boolean first = true;
                for (Object v : values) {
                    if (!first) sql.append(", ");
                    first = false;
                    sql.append('?');
                    params.add(Sql.param(v));
                }
                sql.append(')');
            }
            case BETWEEN -> {
                sql.append(f).append(" BETWEEN ? AND ?");
                params.add(Sql.param(l.v1()));
                params.add(Sql.param(l.v2()));
            }
            case IS_NULL -> sql.append(f).append(" IS NULL");
            case IS_NOT_NULL -> sql.append(f).append(" IS NOT NULL");
        }
    }

    private void appendOrderLimit(StringBuilder sql) {
        if (!orderBy.isEmpty()) sql.append(" ORDER BY ").append(String.join(", ", orderBy));
        // Inlined rather than bound: these are validated ints, so there is nothing to inject, and
        // H2 will not accept a parameter in every position OFFSET/FETCH can appear.
        if (skip > 0) sql.append(" OFFSET ").append(skip).append(" ROWS");
        if (limit >= 0) sql.append(" FETCH NEXT ").append(limit).append(" ROWS ONLY");
    }
}
