package uskoag.gservices.sheetsdb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import datapotter.datahelper.DataHelper_I;
import datapotter.datahelper.Field_I;

/**
 * Fluent construction of a {@link TypeDef}, terminated by {@link #__()} — the same shape as
 * {@code SchemaBuilder} on the ArcadeDB side, so the two read alike on the page.
 *
 * <p>The field list is not something you pass in. It is read off a prototype instance when
 * {@link #__()} is called, through {@code fieldNames()} and {@code getPropertyType(...)} on the
 * generated accessors. That removes the one mistake this API could otherwise invite — a hand-kept
 * field list drifting out of step with the class it describes.
 *
 * @param <E> the document type
 */
public final class TypeDefBuilder<E extends DataHelper_I<E>> {

    private final Class<E> definition;
    private Supplier<E> factory;
    private String keyField;
    private int headerRow = 1;

    private TypeDefBuilder(Class<E> definition, Supplier<E> factory) {
        this.definition = definition;
        this.factory = factory;
    }

    /**
     * Start describing a document type. The usual form.
     *
     * <pre>
     * typeDef(Treaty.class, Treaty::new).key($treatyId).__()
     * </pre>
     */
    public static <E extends DataHelper_I<E>> TypeDefBuilder<E> typeDef(Class<E> definition,
            Supplier<E> factory) {
        if (definition == null) throw new IllegalArgumentException("definition is required");
        return new TypeDefBuilder<>(definition, factory);
    }

    /** The same, for a caller that would rather name the factory further down the chain. */
    public static <E extends DataHelper_I<E>> TypeDefBuilder<E> typeDef(Class<E> definition) {
        return typeDef(definition, null);
    }

    /** The constructor reference, when it was not given to {@link #typeDef(Class, Supplier)}. */
    public TypeDefBuilder<E> factory(Supplier<E> factory) {
        this.factory = factory;
        return this;
    }

    /** Declare the identifying field. Without one the type is read-only. */
    public TypeDefBuilder<E> key(Field_I<E, ?> field) {
        this.keyField = field == null ? null : field.name();
        return this;
    }

    /** Declare the identifying field by name — the escape hatch; prefer {@link #key(Field_I)}. */
    public TypeDefBuilder<E> key(String field) {
        this.keyField = field;
        return this;
    }

    /** 1-based header row. Defaults to 1. */
    public TypeDefBuilder<E> headerRow(int headerRow) {
        if (headerRow < 1) throw new IllegalArgumentException("headerRow is 1-based, got " + headerRow);
        this.headerRow = headerRow;
        return this;
    }

    /** Finish, validating what has to hold before anything touches a spreadsheet. */
    public TypeDef<E> __() {
        if (factory == null) {
            throw new IllegalStateException(definition.getSimpleName()
                    + ": no factory declared, so documents cannot be materialised without reflection. "
                    + "Use typeDef(" + definition.getSimpleName() + ".class, "
                    + definition.getSimpleName() + "::new).");
        }

        final E prototype = factory.get();
        if (prototype == null) {
            throw new IllegalStateException(definition.getSimpleName() + ": factory returned null.");
        }

        final var types = new LinkedHashMap<String, Class<?>>();
        final var order = new ArrayList<String>();
        for (String name : prototype.fieldNames()) {
            Class<?> t = prototype.getPropertyType(name);
            if (t == null) continue;                 // not a readable property; nothing to store
            order.add(name);
            types.put(name, t);
        }
        if (order.isEmpty()) {
            throw new IllegalStateException(definition.getSimpleName()
                    + ": no fields found. Is the class annotated with @Data and does it extend "
                    + definition.getSimpleName() + "_A?");
        }
        if (keyField != null && !types.containsKey(keyField)) {
            throw new IllegalStateException(definition.getSimpleName() + ": key field '" + keyField
                    + "' is not one of its fields " + order + ".");
        }

        return new Impl<>(definition, factory, definition.getSimpleName(),
                List.copyOf(order), Map.copyOf(types), keyField, headerRow);
    }

    private record Impl<E extends DataHelper_I<E>>(
            Class<E> definition,
            Supplier<E> factory,
            String name,
            List<String> fields,
            Map<String, Class<?>> types,
            String keyField,
            int headerRow) implements TypeDef<E> {

        @Override
        public Class<?> fieldType(String field) {
            return types.get(field);
        }
    }
}
