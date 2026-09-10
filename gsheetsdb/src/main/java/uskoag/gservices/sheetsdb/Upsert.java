package uskoag.gservices.sheetsdb;

import java.util.Collection;
import datapotter.datahelper.DataHelper_I;

/**
 * Typed writes over the documents of one type — the counterpart to {@link Query}, and named after
 * what it does: a save is an insert or an update depending only on whether the key is already there.
 *
 * <pre>
 * import static uskoag.gservices.sheetsdb.Upsert.upsert;
 *
 * upsert(db, Treaty.DEF).save(treaty);
 * upsert(db, Treaty.DEF).saveAll(batch);
 * upsert(db, Treaty.DEF).delete(withdrawn);
 *
 * db.flush();          // one batched API call, here and nowhere else
 * </pre>
 *
 * <p>Nothing here reaches Google. Writes land in the mirror and go out together on
 * {@link GSheetDb#flush()} — which is the difference between one request and one request per
 * document, and the reason this library caches rather than writing through.
 *
 * <h3>What identifies a document</h3>
 *
 * <p>Its {@linkplain TypeDef#keyField() key field}, never its position. A sheet row moves whenever
 * anything above it is inserted or deleted, so a type with no key declared is read-only here rather
 * than guessing which row an object came from. A key the sheet carries twice is refused outright,
 * naming the rows: two documents with one identity is corruption, and writing one of them would
 * silently discard the other.
 *
 * @param <E> the document type
 */
public final class Upsert<E extends DataHelper_I<E>> {

    private final SheetDocs<E> docs;

    private Upsert(SheetDocs<E> docs) {
        this.docs = docs;
    }

    /** Start writing documents of {@code def}. */
    public static <E extends DataHelper_I<E>> Upsert<E> upsert(GSheetDb db, TypeDef<E> def) {
        if (db == null) throw new IllegalArgumentException("db is required");
        return new Upsert<>(db.docs(def));
    }

    /**
     * Insert or update one document, in the mirror.
     *
     * <p>Matched to an existing document by the key field; a key not already present becomes a new
     * row after the last one.
     */
    public Upsert<E> save(E document) {
        docs.save(document);
        return this;
    }

    /** {@link #save} for many. */
    public Upsert<E> saveAll(Collection<E> documents) {
        if (documents != null) for (E d : documents) docs.save(d);
        return this;
    }

    /**
     * Remove a document, and schedule its sheet row for deletion.
     *
     * <p>Deleting shifts every row below it up by one, which invalidates the row numbers held for
     * every other document. Rather than patch them — bookkeeping that is wrong exactly when it
     * matters — the flush that carries a deletion re-reads afterwards. One extra API call, always
     * correct. Deleting something that is not there is not an error.
     */
    public Upsert<E> delete(E document) {
        docs.delete(document);
        return this;
    }

    /** {@link #delete} for many. */
    public Upsert<E> deleteAll(Collection<E> documents) {
        if (documents != null) for (E d : documents) docs.delete(d);
        return this;
    }

    /** Whether this type has anything waiting to be written. */
    public boolean isDirty() {
        return docs.isDirty();
    }
}
