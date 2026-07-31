package uskoag.gservices.slides;

import java.nio.charset.StandardCharsets;

/** Long text lives in resources rather than in source string literals. */
public final class Res {

    private Res() {}

    static String text(String name) {
        try (var in = Res.class.getResourceAsStream("/" + name)) {
            if (in == null) return "(resource missing: " + name + ")\n";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "(cannot read " + name + ": " + e.getMessage() + ")\n";
        }
    }
}
