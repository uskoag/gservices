package uskoag.gservices.docs;

import java.util.regex.Pattern;

/**
 * Every comment or reply this tool writes starts "AI(<name>): ", so people in the document can tell a
 * post the AI wrote from one a human wrote. ON unless --no-ai-sign. The name comes from --ai-name, else
 * UKAG_AI_NAME, else "Claude": the jar cannot know which model drives it, so the agent says.
 */
final class AiSign {

    static String name;
    static boolean off;

    /** Docs and logseq-style editors decorate text, so the match skips markup and spacing: "^^AI^^ ( x ):" still counts. */
    private static final Pattern SIG = Pattern.compile("^[\\s^*_~]*AI[\\s^*_~]*(\\([^)]*\\))?[\\s^*_~]*:\\s*", Pattern.CASE_INSENSITIVE);

    private AiSign() {}

    static String prefix() {
        var n = name != null ? name : System.getenv("UKAG_AI_NAME");
        return "AI(" + (n == null || n.isBlank() ? "Claude" : n.strip()) + "): ";
    }

    static String sign(String text) {
        if (off || isSigned(text)) return text;
        return prefix() + text;
    }

    static boolean isSigned(String text) { return text != null && SIG.matcher(text).find(); }

    static String unsign(String text) { return text == null ? null : SIG.matcher(text).replaceFirst(""); }
}
