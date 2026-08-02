package uskoag.gservices.sheetsdb;

import java.util.List;
import java.util.function.Supplier;
import xyz.jphil.datahelper.DataHelper_I;

/**
 * A document type, and the sheet that holds its documents.
 *
 * <p>The counterpart of {@code TypeDef} on the ArcadeDB side, and the vocabulary is deliberately the
 * same one: a sheet holds <b>documents</b>, a document has <b>fields</b>. Nothing here says table,
 * row or column. The grid is storage, not the model — the same way ArcadeDB's pages and buckets are
 * storage and you never name them either.
 *
 * <p>Build one with {@link TypeDefBuilder#typeDef(Class, Supplier)} and keep it on the model:
 *
 * <pre>
 * &#64;Data
 * public final class Treaty extends Treaty_A {
 *     String treatyId;
 *     String title;
 *
 *     public static final TypeDef&lt;Treaty&gt; DEF =
 *             typeDef(Treaty.class, Treaty::new).key($treatyId).__();
 * }
 * </pre>
 *
 * <p>Two rules, neither of which can be overridden:
 *
 * <ul>
 *   <li><b>The sheet is named after the type.</b> {@code Treaty} lives in the tab called
 *       {@code Treaty}. There is no rename hook, because a mapping layer is a place for the two names
 *       to drift and the fix for drift is to correct the sheet.
 *   <li><b>A field's name is its own.</b> The field {@code titleShort} is the header
 *       {@code titleShort}. No case conversion, no mapping annotation.
 * </ul>
 *
 * <p>This is the opposite of the choice an ORM usually makes, and it is the point. Hibernate lets you
 * write {@code @Table(name="tbl_treaty_2")} and {@code @Column(name="ttl_sht")}, and the result is a
 * schema no human can read next to a model no schema can explain. Here a person opening the
 * spreadsheet is reading the class.
 *
 * @param <E> the document type
 */
public interface TypeDef<E extends DataHelper_I<E>> {

    /** The model class. */
    Class<E> definition();

    /**
     * A zero-reflection factory — the {@code Treaty::new} constructor reference.
     *
     * <p>Declared once so no reading code repeats it, and so nothing here needs
     * {@code Class.newInstance}: reflective instantiation would need GraalVM-native configuration,
     * and DataHelper avoids reflection everywhere else for exactly that reason.
     */
    Supplier<E> factory();

    /** The type name, which is also the sheet name: the class's simple name. */
    String name();

    /** Field names in declaration order. */
    List<String> fields();

    /** The declared Java type of a field, or {@code null} if the type has no such field. */
    Class<?> fieldType(String field);

    /**
     * The field that identifies a document, or {@code null} if none was declared.
     *
     * <p>A type with no key is <b>read-only</b>. Saving and deleting refuse it, because without a key
     * there is no way to say which existing document a given object is — and guessing overwrites
     * somebody's data.
     */
    String keyField();

    /** 1-based sheet row carrying the field names. Documents start on the row after. Defaults to 1. */
    int headerRow();
}
