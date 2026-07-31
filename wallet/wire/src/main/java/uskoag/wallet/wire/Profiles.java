package uskoag.wallet.wire;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The known tools, their scopes, and where each one used to keep its per-app-key token store.
 *
 * <p>The legacy roots are here purely so {@code import} can find what to migrate. They differ per tool
 * because each grew its own directory over time, which is exactly the sprawl the wallet replaces with
 * one inventory.
 */
public final class Profiles {

    public static final String GSHEETS = "gsheets", GDRIVE = "gdrive", GMAIL = "gmail", GSLIDES = "gslides";

    private static final Map<String, List<String>> SCOPES = Map.of(
            GSHEETS, List.of("https://www.googleapis.com/auth/spreadsheets",
                    "https://www.googleapis.com/auth/drive"),
            GDRIVE, List.of("https://www.googleapis.com/auth/drive"),
            GMAIL, List.of("https://www.googleapis.com/auth/gmail.modify",
                    "https://www.googleapis.com/auth/gmail.compose"),
            GSLIDES, List.of("https://www.googleapis.com/auth/presentations",
                    "https://www.googleapis.com/auth/drive.readonly",
                    "https://www.googleapis.com/auth/drive.file"));

    private Profiles() {
    }

    public static List<String> scopes(String profile) {
        var s = SCOPES.get(profile);
        if (s == null) throw new IllegalArgumentException("unknown profile '" + profile
                + "' — known: " + String.join(", ", SCOPES.keySet()));
        return s;
    }

    public static List<String> known() {
        return SCOPES.keySet().stream().sorted().toList();
    }

    /**
     * Every scope every known tool asks for, deduplicated.
     *
     * <p>This is what a plain {@code login} requests, and it is the deliberate default: consenting once
     * for everything is one browser round trip, whereas consenting per tool means a round trip each
     * time you first use one — and, with the old store, a silent 403 when the second tool reused the
     * first one's narrower token. Anyone who wants less can pass an explicit scope list.
     */
    public static List<String> allScopes() {
        var all = new java.util.LinkedHashSet<String>();
        known().forEach(p -> all.addAll(SCOPES.get(p)));
        return List.copyOf(all);
    }

    /** Where this tool's {@code <email>/tokens_<md5>} directories live today. */
    public static Path legacyRoot(String profile) {
        var home = Path.of(System.getProperty("user.home"), "uskoag");
        return switch (profile) {
            case GSHEETS -> home.resolve("gservices").resolve("spreadsheet_cli");
            case GSLIDES -> home.resolve("gservices").resolve("gslides_cli");
            case GDRIVE -> home.resolve("gdrive");
            default -> home.resolve("gdrive_gdocs_auth");
        };
    }
}
