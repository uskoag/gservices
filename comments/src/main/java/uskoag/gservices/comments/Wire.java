package uskoag.gservices.comments;

import java.util.*;

/** The preview fields have no client classes yet, so they arrive as GenericJson maps; these read them. */
public final class Wire {

    private Wire() {}

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> list(Object o) {
        return o instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    public static String str(Map<String, Object> m, String k) { return m.get(k) == null ? null : m.get(k).toString(); }

    public static boolean yes(Map<String, Object> m, String k) { return Boolean.TRUE.equals(m.get(k)); }

    public static Integer num(Map<String, Object> m, String k) { return m.get(k) instanceof Number n ? n.intValue() : null; }
}
