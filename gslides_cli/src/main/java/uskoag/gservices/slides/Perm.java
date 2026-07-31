package uskoag.gservices.slides;

/** One allowlist entry. Write implies read. */
public record Perm(String id, String name, boolean write) {

    boolean allows(String op) {
        return switch (op) {
            case "read" -> true;
            case "write" -> write;
            default -> false;
        };
    }

    String describe() {
        return (write ? "[write]" : "[read] ") + " " + id + (name == null || name.isBlank() ? "" : " (" + name + ")");
    }
}
