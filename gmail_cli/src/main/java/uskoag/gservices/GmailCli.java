package uskoag.gservices;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListDraftsResponse;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.Profile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;

import xyz.jphil.windows_console_set_unicode_output.WindowsConsoleSetUnicodeOutput;
import xyz.jphil.windows_console_set_unicode_output.WindowsConsoleSetUnicodeOutput.EnableResult;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Command-line Gmail companion for Claude Code.
 *
 * <p>Reads, searches and drafts emails, and manages labels (create/rename/recolor/delete labels and
 * add/remove them on messages or threads). It does NOT send mail (drafting only).
 * OAuth tokens are encrypted at rest with a user-supplied app-key (see uskoag-gservices-oauth):
 * the app-key derives the encryption key and the token-folder name, so a forgotten/changed
 * app-key simply triggers a fresh login rather than exposing tokens.</p>
 *
 * <pre>
 * Global flags (any position):
 *   --email,   -e &lt;email&gt;      Gmail account (required)
 *   (--app-key/-k removed: a secret on argv is readable by any process and lands in shell
 *   history and AI transcripts. Use uskoag-wallet, or type it at the hidden prompt.)
 *                               If omitted, prompted on the console (hidden).
 *   --json                      Emit machine-readable JSON instead of text
 *   --verbose, -v               Log progress to stderr
 *
 * Commands:
 *   auth                                  Run / verify OAuth, print the account profile
 *   reauth                                Forget the stored token and re-consent (once, for label writes)
 *   profile                               Print the account profile
 *   labels                                List all labels
 *   label-create &lt;name&gt;                   Create a label  [--text-color --bg-color] [--hide|--show]
 *   label-update &lt;id|name&gt;                Rename/recolor a label  [--name] [colors] [--hide|--show]
 *   label-delete &lt;id|name&gt;                Delete a user label
 *   label   &lt;labels&gt; &lt;id...&gt;              Add label(s) to messages (threads with --threads) [--create-missing]
 *   unlabel &lt;labels&gt; &lt;id...&gt;              Remove label(s) from messages (threads with --threads)
 *   modify  &lt;id...&gt;                       Add and/or remove labels [--add &lt;l&gt;] [--remove &lt;l&gt;] [--threads]
 *   list                                  List recent messages
 *        [-q "&lt;gmail query&gt;"] [-l &lt;label&gt;] [-n &lt;max&gt;] [--include-spam-trash] [--bodies] [--strip-quotes]
 *   search "&lt;gmail query&gt;"                 List messages matching a query (shorthand for list -q)
 *        [-l &lt;label&gt;] [-n &lt;max&gt;] [--include-spam-trash] [--bodies] [--strip-quotes]
 *   read &lt;messageId&gt;                       Read a full message (headers + decoded body + attachments)
 *        [--html] [--headers-only] [--raw] [--strip-quotes]
 *   thread &lt;threadId&gt;                      Read every message in a thread   [--strip-quotes]
 *   read-batch &lt;id...&gt;                     Batch-read many messages under one auth (gmail.batch())
 *        [--ids-file &lt;path&gt;] [--ids-stdin] [--html] [--headers-only] [--strip-quotes]
 *   thread-batch &lt;id...&gt;                   Batch-read many threads under one auth
 *        [--ids-file &lt;path&gt;] [--ids-stdin] [--strip-quotes]
 *   search-batch &lt;query...&gt;                Run many queries under one auth (JSON keyed by query)
 *        [--queries-file &lt;path&gt;] [--queries-stdin] [-l &lt;label&gt;] [-n &lt;max&gt;] [--include-spam-trash] [--bodies] [--strip-quotes]
 *   drafts                                List existing drafts             [-n &lt;max&gt;]
 *   draft-read &lt;draftId&gt;                   Read a single draft
 *   draft                                 Create a draft (never sends)
 *        --to &lt;a,b&gt; [--cc ..] [--bcc ..] [-s &lt;subject&gt;]
 *        (-b "&lt;body&gt;" | --body-file &lt;path&gt; | --body-stdin) [--html]
 *        [--attach &lt;file&gt; ...] [--reply-to &lt;messageId&gt;] [--thread &lt;threadId&gt;]
 *   attachment &lt;messageId&gt; &lt;attachmentId&gt;  Download an attachment   [--out &lt;path&gt;]
 * </pre>
 */
public class GmailCli {

    private static final String APP_NAME = "GmailCli-v1.0";

    /** What the app-key became: a name that selects a credential and unlocks nothing. */
    private static final String PROFILE = "gmail";
    static final String USER = "me";
    private static final Path CREDS_BASE =
            Paths.get(System.getProperty("user.home"), "uskoag", "gservices", "gmail_cli");

    private static boolean verbose = false;
    static boolean json = false;
    static Gmail gmail;

    // ---- flag specification -------------------------------------------------

    /** Flags that consume the following token as a value. Short aliases map to the canonical name. */
    private static final Map<String, String> VALUE_FLAGS = Map.ofEntries(
            Map.entry("--email", "email"), Map.entry("-e", "email"),

            Map.entry("--query", "query"), Map.entry("-q", "query"),
            Map.entry("--label", "label"), Map.entry("-l", "label"),
            Map.entry("--max", "max"), Map.entry("-n", "max"),
            Map.entry("--to", "to"),
            Map.entry("--cc", "cc"),
            Map.entry("--bcc", "bcc"),
            Map.entry("--subject", "subject"), Map.entry("-s", "subject"),
            Map.entry("--body", "body"), Map.entry("-b", "body"),
            Map.entry("--body-file", "body-file"),
            Map.entry("--reply-to", "reply-to"),
            Map.entry("--thread", "thread"),
            Map.entry("--out", "out"),
            Map.entry("--ids-file", "ids-file"),
            Map.entry("--queries-file", "queries-file"),
            // label management
            Map.entry("--name", "name"),
            Map.entry("--add", "add"),
            Map.entry("--remove", "remove"),
            Map.entry("--text-color", "text-color"),
            Map.entry("--bg-color", "bg-color"), Map.entry("--background-color", "bg-color"),
            Map.entry("--label-list-visibility", "label-list-visibility"),
            Map.entry("--message-list-visibility", "message-list-visibility")
    );

    /** Flags that take no value. */
    private static final Map<String, String> BOOL_FLAGS = Map.ofEntries(
            Map.entry("--json", "json"),
            Map.entry("--verbose", "verbose"), Map.entry("-v", "verbose"),
            Map.entry("--html", "html"),
            Map.entry("--headers-only", "headers-only"),
            Map.entry("--strip-quotes", "strip-quotes"),
            Map.entry("--no-quotes", "strip-quotes"),
            Map.entry("--no-history", "strip-quotes"),
            Map.entry("--raw", "raw"),
            Map.entry("--include-spam-trash", "include-spam-trash"),
            Map.entry("--body-stdin", "body-stdin"),
            Map.entry("--probe", "probe"),
            Map.entry("--bodies", "bodies"),
            Map.entry("--full", "bodies"),
            Map.entry("--ids-stdin", "ids-stdin"),
            Map.entry("--queries-stdin", "queries-stdin"),
            // label management
            Map.entry("--threads", "threads"),
            Map.entry("--create-missing", "create-missing"),
            Map.entry("--hide", "hide"),
            Map.entry("--show", "show")
    );

    /** Value-flags that may repeat and accumulate (e.g. --attach a --attach b). */
    private static final String REPEATABLE_ATTACH = "--attach";

    public static void main(String[] args) {
        enableUnicodeOutput();

        Args a;
        try {
            a = new Args(args);
        } catch (IllegalArgumentException e) {
            logError(e.getMessage());
            System.exit(2);
            return;
        }

        verbose = a.has("verbose");
        json = a.has("json");
        logInfo("unicode console: " + WindowsConsoleSetUnicodeOutput.getLastResult());

        List<String> pos = a.positionals;
        if (pos.isEmpty()) {
            printUsage();
            System.exit(1);
            return;
        }

        String command = pos.get(0).toLowerCase();

        try {
            switch (command) {
                case "help", "--help", "-h" -> { printUsage(); return; }
                case "auth", "login" -> { connect(a); cmdProfile(); }
                case "reauth", "relogin" -> { reauth(a); connect(a); cmdProfile(); }
                case "profile", "whoami" -> { connect(a); cmdProfile(); }
                case "listprofiles", "list-profiles", "accounts" -> cmdListProfiles(a);
                case "labels" -> { connect(a); cmdLabels(); }
                case "label-create", "label-new" -> {
                    requirePositional(pos, 1, "label-create requires a <name>");
                    connect(a);
                    GmailLabels.create(a, pos.get(1));
                }
                case "label-update", "label-edit", "label-rename" -> {
                    requirePositional(pos, 1, "label-update requires a <labelId|name>");
                    connect(a);
                    GmailLabels.update(a, pos.get(1));
                }
                case "label-delete", "label-del" -> {
                    requirePositional(pos, 1, "label-delete requires a <labelId|name>");
                    connect(a);
                    GmailLabels.delete(a, pos.get(1));
                }
                case "label", "label-add", "tag" -> {
                    requirePositional(pos, 1, "label requires <labels> then <id...>  (e.g. label work,urgent <msgId>)");
                    connect(a);
                    GmailLabelApply.apply(a, splitCsv(pos.get(1)), List.of());
                }
                case "unlabel", "label-remove", "untag" -> {
                    requirePositional(pos, 1, "unlabel requires <labels> then <id...>  (e.g. unlabel UNREAD <msgId>)");
                    connect(a);
                    GmailLabelApply.apply(a, List.of(), splitCsv(pos.get(1)));
                }
                case "modify", "relabel" -> {
                    connect(a);
                    GmailLabelApply.modify(a);
                }
                case "list" -> { connect(a); cmdList(a, a.get("query")); }
                case "search" -> {
                    String q = pos.size() > 1 ? String.join(" ", pos.subList(1, pos.size())) : a.get("query");
                    if (q == null || q.isBlank()) { logError("search requires a query, e.g. search \"from:alice is:unread\""); System.exit(1); }
                    connect(a);
                    cmdList(a, q);
                }
                case "read" -> {
                    requirePositional(pos, 1, "read requires a <messageId>");
                    connect(a);
                    cmdRead(a, pos.get(1));
                }
                case "thread" -> {
                    requirePositional(pos, 1, "thread requires a <threadId>");
                    connect(a);
                    cmdThread(a, pos.get(1));
                }
                case "read-batch", "read_batch" -> { requireBatchInput(a, pos, "ids"); connect(a); cmdReadBatch(a); }
                case "thread-batch", "thread_batch" -> { requireBatchInput(a, pos, "ids"); connect(a); cmdThreadBatch(a); }
                case "search-batch", "search_batch" -> { requireBatchInput(a, pos, "queries"); connect(a); cmdSearchBatch(a); }
                case "drafts" -> { connect(a); cmdDrafts(a); }
                case "draft-read" -> {
                    requirePositional(pos, 1, "draft-read requires a <draftId>");
                    connect(a);
                    cmdDraftRead(a, pos.get(1));
                }
                case "draft" -> { connect(a); cmdDraft(a); }
                case "attachment" -> {
                    requirePositional(pos, 2, "attachment requires <messageId> <attachmentId>");
                    connect(a);
                    cmdAttachment(a, pos.get(1), pos.get(2));
                }
                default -> {
                    logError("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException ge) {
            handleApiError(ge, a);
            System.exit(1);
        } catch (Exception e) {
            logError(e.getMessage() == null ? e.toString() : e.getMessage());
            if (verbose) e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * Makes stdout/stderr emit UTF-8 so unicode mail (subjects, bodies, non-Latin scripts, emoji)
     * renders instead of mojibake on Windows. The library sets the console code page
     * ({@code SetConsoleOutputCP 65001}, so a human terminal renders glyphs correctly) and swaps the
     * streams to UTF-8; if it can't (no attached console — e.g. output piped to an agent), we still pin
     * the streams to UTF-8 so the captured bytes are valid. No-op-safe on non-Windows / older JDKs.
     */
    private static void enableUnicodeOutput() {
        EnableResult r = WindowsConsoleSetUnicodeOutput.enable();
        if (r instanceof EnableResult.Success || r instanceof EnableResult.AlreadyEnabled) return;
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }

    /**
     * Prints a concise message for a Google API error. A 403 "insufficient scopes" means a stored login
     * predates the label-write permission (gmail.modify) — the token keeps its original scopes, so we
     * point the user at {@code reauth} to re-grant rather than at Google's opaque error body.
     */
    private static void handleApiError(com.google.api.client.googleapis.json.GoogleJsonResponseException ge, Args a) {
        int code = ge.getStatusCode();
        String detail = ge.getDetails() != null && ge.getDetails().getMessage() != null
                ? ge.getDetails().getMessage() : ge.getStatusMessage();
        logError("Gmail API error " + code + (detail != null ? ": " + detail : ""));
        boolean scope = code == 403 && detail != null
                && (detail.toLowerCase().contains("scope") || detail.toLowerCase().contains("insufficient")
                    || detail.toLowerCase().contains("permission"));
        if (scope) {
            String em = a.get("email");
            logError("Your saved login lacks the label-write permission (gmail.modify). Re-grant once:");
            logError("  uskoag-gmailcli reauth -e " + (em == null ? "<email>" : em));
        }
        if (verbose) ge.printStackTrace(System.err);
    }

    /** Splits a comma/newline-separated label list into trimmed, non-blank tokens (spaces kept — names may contain them). */
    static List<String> splitCsv(String s) {
        if (s == null || s.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String t : s.split("[,\\n]")) { String v = t.trim(); if (!v.isEmpty()) out.add(v); }
        return out;
    }

    // ---- connection / auth --------------------------------------------------

    private static void connect(Args a) throws IOException, java.security.GeneralSecurityException {
        String email = a.get("email");
        if (email == null || email.isBlank()) {
            logError("Missing required --email/-e <email>");
            System.exit(2);
        }
        Path emailDir = CREDS_BASE.resolve(email);
        Path credsFile = emailDir.resolve("credentials.json");
        if (!Files.exists(credsFile)) {
            Files.createDirectories(emailDir);
            logError("OAuth client credentials not found for " + email + ".");
            logError("Place your Google Cloud OAuth client (Desktop app type) JSON at:");
            logError("  " + credsFile);
            logError("Then enable the Gmail API for that project and re-run. First run opens a browser for consent.");
            System.exit(3);
        }

        requireDesktopClient(credsFile, email);

        logInfo("Connecting as " + email + " (creds dir: " + emailDir + ")");
        var spec = AccessSpec.of("gmail", PROFILE, APP_NAME, email,
                        java.util.List.of(GmailScopes.GMAIL_MODIFY, GmailScopes.GMAIL_COMPOSE))
                .legacyRoot(CREDS_BASE.toString());
        var access = Credentials.access(spec, () -> AppKeyPrompt.ask(APP_NAME));
        gmail = GmailService.gmail(access, APP_NAME);
        logInfo("Authenticated via " + access.sourceName() + ".");
    }

    /**
     * Re-consent has moved to the wallet, and this redirects rather than disappearing.
     *
     * <p>It used to delete this tool's own stored token so the next {@link #connect} would run a fresh
     * browser flow — which required building an {@link OAuthToken} with an app-key, one of the last
     * three places in this file that still did. The wallet holds the token now, so the wallet is where
     * it is dropped and re-granted, and doing it from here could only ever have deleted a legacy store
     * nothing reads any more.
     */
    private static void reauth(Args a) {
        String email = a.get("email");
        var who = email == null || email.isBlank() ? "<email>" : email;
        logError("re-consent has moved to the USK OAG GServices Wallet, which holds every refresh token"
                + " on this machine.");
        logError("Run instead:");
        logError("  uskoag-walletcli login " + who + "        (re-grant, widening scopes if needed)");
        logError("  uskoag-walletcli forget " + who + "       (drop the stored token first)");
        System.exit(2);
    }

    /**
     * Fail fast if {@code credentials.json} is not a "Desktop app" (installed) OAuth client.
     *
     * <p>The OAuth flow uses a loopback browser redirect ({@code http://localhost:8888/Callback}).
     * Google auto-allows loopback redirects only for <em>Desktop app</em> clients; a
     * <em>Web application</em> client requires that exact URI to be pre-registered, so it fails the
     * consent step with the opaque {@code Error 400: redirect_uri_mismatch}. We catch that here and
     * print actionable guidance instead of letting the user wander into Google's error page.</p>
     */
    private static void requireDesktopClient(Path credsFile, String email) {
        String type = clientType(credsFile);
        // Unreadable → let the normal OAuth flow surface the real error; "installed" → correct type.
        if (type == null || "installed".equals(type)) return;

        boolean web = "web".equals(type);
        logError((web ? "This is a \"Web application\" OAuth client" : "This is not a \"Desktop app\" OAuth client")
                + ": " + credsFile);
        logError("The loopback consent flow (redirect http://localhost:8888/Callback) only works with a");
        logError("\"Desktop app\" client; a Web client rejects it with: Error 400: redirect_uri_mismatch.");
        logError("Fix: in Google Cloud Console create an OAuth client ID of type 'Desktop app', download its");
        logError("JSON (it has a top-level \"installed\" key), and replace the file above.");
        logError("For a personal @gmail.com (non-Workspace) account also: set the consent screen User Type to");
        logError("'External', add " + email + " as a Test user, and click through the 'unverified app' warning.");
        System.exit(3);
    }

    /** Classifies an OAuth client-secrets file: "installed" (Desktop app), "web", or "unknown"; null if unreadable. */
    private static String clientType(Path credsFile) {
        try (var r = Files.newBufferedReader(credsFile, StandardCharsets.UTF_8)) {
            GoogleClientSecrets secrets = GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), r);
            if (secrets.getInstalled() != null) return "installed";
            if (secrets.getWeb() != null) return "web";
            return "unknown";
        } catch (Exception e) {
            return null;
        }
    }

    // ---- commands -----------------------------------------------------------

    private static void cmdProfile() throws IOException {
        Profile p = gmail.users().getProfile(USER).execute();
        if (json) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("emailAddress", p.getEmailAddress());
            m.put("messagesTotal", p.getMessagesTotal());
            m.put("threadsTotal", p.getThreadsTotal());
            m.put("historyId", p.getHistoryId());
            emitJson(m);
        } else {
            System.out.println("Email:    " + p.getEmailAddress());
            System.out.println("Messages: " + p.getMessagesTotal());
            System.out.println("Threads:  " + p.getThreadsTotal());
        }
    }

    /**
     * Lists the per-email accounts configured under {@link #CREDS_BASE}: their OAuth client type and
     * how many token-stores exist. {@code --probe} calls {@code getProfile} for each to verify it live,
     * without ever opening a browser.
     *
     * <p>The {@code tokenStores} count is now a legacy reading and is labelled as one. Those
     * directories are the old per-app-key stores; the credential that actually answers a call lives in
     * the wallet, so a count of zero here says nothing about whether the account works. Which is
     * exactly why {@code --probe} exists and why it is worth more than it used to be.
     */
    private static void cmdListProfiles(Args a) throws Exception {
        boolean probe = a.has("probe");

        List<Path> dirs = List.of();
        if (Files.isDirectory(CREDS_BASE)) {
            try (var s = Files.list(CREDS_BASE)) {
                dirs = s.filter(Files::isDirectory).sorted().toList();
            }
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Path dir : dirs) {
            String email = dir.getFileName().toString();
            Path credsFile = dir.resolve("credentials.json");
            boolean hasCreds = Files.exists(credsFile);
            String type = hasCreds ? clientType(credsFile) : null;

            int tokenStores = 0;
            try (var s = Files.list(dir)) {
                tokenStores = (int) s.filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().startsWith("tokens_"))
                        .count();
            } catch (IOException ignore) { }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("email", email);
            m.put("hasCredentials", hasCreds);
            m.put("clientType", hasCreds ? (type == null ? "unreadable" : type) : "none");
            m.put("tokenStores", tokenStores);
            if (probe) probeAccount(m, email, hasCreds, type);
            out.add(m);
        }

        if (json) { emitJson(out); return; }

        if (out.isEmpty()) {
            System.out.println("(no accounts found under " + CREDS_BASE + ")");
            return;
        }
        int i = 1;
        for (Map<String, Object> m : out) {
            String ct = String.valueOf(m.get("clientType"));
            System.out.println("[" + (i++) + "] " + m.get("email"));
            System.out.println("    credentials : " + (Boolean.TRUE.equals(m.get("hasCredentials"))
                    ? "yes (" + ct + ("web".equals(ct) ? " - WRONG type; needs Desktop app" : "") + ")"
                    : "no"));
            System.out.println("    token-stores: " + m.get("tokenStores") + " (legacy on-disk stores)"
                    + (probe ? "" : "   (login state unverified - re-run with --probe)"));
            if (probe) {
                String line = String.valueOf(m.getOrDefault("status", "?"));
                if (m.containsKey("messagesTotal"))
                    line += "  (" + m.get("messagesTotal") + " msgs, " + m.get("threadsTotal") + " threads)";
                if (m.containsKey("error")) line += "  - " + m.get("error");
                System.out.println("    live status : " + line);
            }
        }
        logInfo(out.size() + " account(s)");
    }

    /**
     * Probes one account's live login state; writes status fields into {@code m}.
     *
     * <p>Goes through the same credential seam as a real command, which is what makes the answer worth
     * anything: it verifies the route that will actually be used rather than the presence of a file.
     * It never opens a browser — a {@code getProfile} against an account the wallet holds is a plain
     * read, and one it does not hold comes back as a refusal with the reason attached.
     *
     * <p>A failure here is recorded against the account and the sweep continues. The whole purpose of
     * the verb is to show several accounts side by side, and dying on the first unauthorised one would
     * hide the state of every account after it.
     */
    private static void probeAccount(Map<String, Object> m, String email, boolean hasCreds, String type) {
        if (hasCreds && !"installed".equals(type)) {
            m.put("status", "skipped (not a Desktop-app client)");
            return;
        }
        try {
            var spec = AccessSpec.of("gmail", PROFILE, APP_NAME, email,
                            java.util.List.of(GmailScopes.GMAIL_MODIFY, GmailScopes.GMAIL_COMPOSE))
                    .legacyRoot(CREDS_BASE.toString());
            Gmail g = GmailService.gmail(Credentials.access(spec), APP_NAME);
            Profile p = g.users().getProfile(USER).execute();
            m.put("status", "ok");
            m.put("messagesTotal", p.getMessagesTotal());
            m.put("threadsTotal", p.getThreadsTotal());
        } catch (Exception e) {
            m.put("status", "not reachable");
            m.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static void cmdLabels() throws IOException {
        ListLabelsResponse resp = gmail.users().labels().list(USER).execute();
        List<Label> labels = resp.getLabels() == null ? List.of() : resp.getLabels();
        if (json) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Label l : labels) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", l.getId());
                m.put("name", l.getName());
                m.put("type", l.getType());
                out.add(m);
            }
            emitJson(out);
        } else {
            for (Label l : labels) {
                System.out.println(l.getId() + "\t" + l.getName() + "\t(" + l.getType() + ")");
            }
            logInfo(labels.size() + " label(s)");
        }
    }

    private static void cmdList(Args a, String query) throws IOException {
        long max = a.getLong("max", 20L);
        String label = a.get("label");
        String labelId = (label != null && !label.isBlank()) ? resolveLabelId(label) : null;
        boolean bodies = a.has("bodies");
        boolean preferHtml = a.has("html");

        List<Map<String, Object>> summaries =
                searchSummaries(query, max, labelId, a.has("include-spam-trash"), bodies, preferHtml, a.has("strip-quotes"));

        if (json) {
            emitJson(summaries);
        } else {
            if (summaries.isEmpty()) { logInfo("No messages."); return; }
            int i = 1;
            for (Map<String, Object> s : summaries) {
                if (s.get("error") != null) {
                    System.out.println("[" + (i++) + "] id=" + s.get("id") + "  ERROR: " + s.get("error"));
                    continue;
                }
                System.out.println("[" + (i++) + "] id=" + s.get("id") + "  thread=" + s.get("threadId"));
                System.out.println("    From:    " + nz(s.get("from")));
                System.out.println("    Date:    " + nz(s.get("date")));
                System.out.println("    Subject: " + nz(s.get("subject")));
                Object labels = s.get("labels");
                if (labels instanceof List<?> ll && !ll.isEmpty()) System.out.println("    Labels:  " + String.join(", ", ll.stream().map(String::valueOf).toList()));
                System.out.println("    > " + nz(s.get("snippet")));
                if (bodies && s.get("body") != null) {
                    System.out.println();
                    System.out.println(s.get("body"));
                    System.out.println();
                }
            }
            logInfo(summaries.size() + " message(s)");
        }
    }

    private static void cmdRead(Args a, String messageId) throws IOException {
        if (a.has("raw")) {
            Message m = gmail.users().messages().get(USER, messageId).setFormat("raw").execute();
            byte[] raw = Base64.decodeBase64(m.getRaw());
            System.out.write(raw);
            System.out.flush();
            return;
        }

        Message m = gmail.users().messages().get(USER, messageId).setFormat("full").execute();
        Map<String, Object> out = readMap(m, a.has("headers-only"), a.has("html"), a.has("strip-quotes"));

        if (json) {
            emitJson(out);
        } else {
            System.out.println("From:    " + nz(out.get("from")));
            System.out.println("To:      " + nz(out.get("to")));
            if (out.get("cc") != null) System.out.println("Cc:      " + out.get("cc"));
            System.out.println("Date:    " + nz(out.get("date")));
            System.out.println("Subject: " + nz(out.get("subject")));
            if (m.getLabelIds() != null && !m.getLabelIds().isEmpty())
                System.out.println("Labels:  " + String.join(", ", m.getLabelIds()));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> attachments = (List<Map<String, Object>>) out.get("attachments");
            if (attachments != null && !attachments.isEmpty()) {
                System.out.println("Attachments:");
                for (Map<String, Object> at : attachments)
                    System.out.println("  - " + at.get("filename") + " (" + at.get("mimeType")
                            + ", " + at.get("size") + " bytes)  attachmentId=" + at.get("attachmentId"));
            }
            if (!a.has("headers-only")) {
                System.out.println();
                System.out.println(out.get("body") == null ? "" : out.get("body"));
            }
        }
    }

    private static void cmdThread(Args a, String threadId) throws IOException {
        com.google.api.services.gmail.model.Thread t =
                gmail.users().threads().get(USER, threadId).setFormat("full").execute();
        List<Message> msgs = t.getMessages() == null ? List.of() : t.getMessages();
        boolean stripQuotes = a.has("strip-quotes");

        if (json) {
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("threadId", threadId);
            wrap.put("messages", threadMessageMaps(t, stripQuotes));
            emitJson(wrap);
        } else {
            logInfo("Thread " + threadId + ": " + msgs.size() + " message(s)");
            int i = 1;
            for (Message m : msgs) {
                MessagePart payload = m.getPayload();
                System.out.println("===== message " + (i++) + " (id=" + m.getId() + ") =====");
                System.out.println("From:    " + nz(header(payload, "From")));
                System.out.println("Date:    " + nz(header(payload, "Date")));
                System.out.println("Subject: " + nz(header(payload, "Subject")));
                StringBuilder plain = new StringBuilder(), html = new StringBuilder();
                collectText(payload, plain, html);
                System.out.println();
                System.out.println(bodyText(plain, html, false, stripQuotes));
                System.out.println();
            }
        }
    }

    private static void cmdDrafts(Args a) throws IOException {
        long max = a.getLong("max", 20L);
        ListDraftsResponse resp = gmail.users().drafts().list(USER).setMaxResults(max).execute();
        List<Draft> drafts = resp.getDrafts() == null ? List.of() : resp.getDrafts();

        List<String> msgIds = new ArrayList<>(drafts.size());
        for (Draft d : drafts) msgIds.add(d.getMessage().getId());
        List<BatchItem<Message>> fetched = batchGetMessages(msgIds, "metadata", List.of("To", "Subject", "Date"));

        List<Map<String, Object>> out = new ArrayList<>();
        for (int idx = 0; idx < drafts.size(); idx++) {
            Draft d = drafts.get(idx);
            BatchItem<Message> item = fetched.get(idx);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("draftId", d.getId());
            m.put("messageId", d.getMessage().getId());
            if (item.value != null) {
                m.put("to", header(item.value.getPayload(), "To"));
                m.put("subject", header(item.value.getPayload(), "Subject"));
                m.put("snippet", item.value.getSnippet());
            } else {
                m.put("error", item.error);
            }
            out.add(m);
        }

        if (json) {
            emitJson(out);
        } else {
            if (out.isEmpty()) { logInfo("No drafts."); return; }
            for (Map<String, Object> m : out) {
                System.out.println("draftId=" + m.get("draftId") + "  to=" + nz(m.get("to")) + "  subject=" + nz(m.get("subject")));
                System.out.println("    > " + nz(m.get("snippet")));
            }
            logInfo(out.size() + " draft(s)");
        }
    }

    private static void cmdDraftRead(Args a, String draftId) throws IOException {
        Draft d = gmail.users().drafts().get(USER, draftId).setFormat("full").execute();
        Message m = d.getMessage();
        MessagePart payload = m.getPayload();
        StringBuilder plain = new StringBuilder(), html = new StringBuilder();
        collectText(payload, plain, html);
        String body = plain.length() > 0 ? plain.toString() : html.toString();

        if (json) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("draftId", d.getId());
            out.put("messageId", m.getId());
            out.put("to", header(payload, "To"));
            out.put("cc", header(payload, "Cc"));
            out.put("subject", header(payload, "Subject"));
            out.put("body", body);
            emitJson(out);
        } else {
            System.out.println("draftId: " + d.getId());
            System.out.println("To:      " + nz(header(payload, "To")));
            if (header(payload, "Cc") != null) System.out.println("Cc:      " + header(payload, "Cc"));
            System.out.println("Subject: " + nz(header(payload, "Subject")));
            System.out.println();
            System.out.println(body);
        }
    }

    private static void cmdDraft(Args a) throws Exception {
        String email = a.get("email");
        String to = a.get("to");
        String cc = a.get("cc");
        String bcc = a.get("bcc");
        String subject = a.get("subject");
        boolean html = a.has("html");
        String replyTo = a.get("reply-to");
        String threadId = a.get("thread");

        String body = resolveBody(a);

        // Reply handling: inherit thread, recipients, subject and threading headers from the original.
        String inReplyTo = null, references = null;
        if (replyTo != null && !replyTo.isBlank()) {
            Message orig = gmail.users().messages().get(USER, replyTo)
                    .setFormat("metadata")
                    .setMetadataHeaders(List.of("Message-ID", "References", "Subject", "From"))
                    .execute();
            threadId = orig.getThreadId();
            String origMsgId = header(orig.getPayload(), "Message-ID");
            String origRefs = header(orig.getPayload(), "References");
            String origFrom = header(orig.getPayload(), "From");
            String origSubj = header(orig.getPayload(), "Subject");
            if (to == null || to.isBlank()) to = origFrom;
            if (subject == null || subject.isBlank()) subject = "Re: " + stripRe(origSubj);
            inReplyTo = origMsgId;
            references = (origRefs != null && !origRefs.isBlank() ? origRefs + " " : "") + (origMsgId == null ? "" : origMsgId);
        }

        if (to == null || to.isBlank()) {
            logError("draft requires --to <recipients> (or --reply-to <messageId> to infer it)");
            System.exit(2);
        }

        MimeMessage mime = buildMime(email, to, cc, bcc, subject, body, html, a.attachments, inReplyTo, references);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mime.writeTo(buffer);
        String raw = Base64.encodeBase64URLSafeString(buffer.toByteArray());

        Message message = new Message().setRaw(raw);
        if (threadId != null && !threadId.isBlank()) message.setThreadId(threadId);

        Draft created = gmail.users().drafts().create(USER, new Draft().setMessage(message)).execute();

        if (json) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("draftId", created.getId());
            out.put("messageId", created.getMessage() != null ? created.getMessage().getId() : null);
            out.put("threadId", threadId);
            emitJson(out);
        } else {
            System.out.println("SUCCESS: draft created");
            System.out.println("draftId:   " + created.getId());
            if (created.getMessage() != null) System.out.println("messageId: " + created.getMessage().getId());
            if (threadId != null) System.out.println("threadId:  " + threadId);
        }
    }

    private static void cmdAttachment(Args a, String messageId, String attachmentId) throws IOException {
        // Look up the originating part to recover its filename (for a sensible default output name).
        Message m = gmail.users().messages().get(USER, messageId).setFormat("full").execute();
        List<Map<String, Object>> atts = new ArrayList<>();
        collectAttachments(m.getPayload(), atts);
        String filename = null;
        for (Map<String, Object> at : atts) {
            if (attachmentId.equals(at.get("attachmentId"))) { filename = String.valueOf(at.get("filename")); break; }
        }

        MessagePartBody body = gmail.users().messages().attachments()
                .get(USER, messageId, attachmentId).execute();
        byte[] data = Base64.decodeBase64(body.getData());

        String outArg = a.get("out");
        Path outPath = (outArg != null && !outArg.isBlank())
                ? Paths.get(outArg)
                : Paths.get(filename != null && !filename.isBlank() ? filename : ("attachment-" + attachmentId + ".bin"));

        Files.write(outPath, data);

        if (json) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("path", outPath.toAbsolutePath().toString());
            out.put("bytes", data.length);
            out.put("filename", filename);
            emitJson(out);
        } else {
            System.out.println("SUCCESS: wrote " + data.length + " bytes to " + outPath.toAbsolutePath());
        }
    }

    // ---- MIME / body helpers ------------------------------------------------

    private static String resolveBody(Args a) throws IOException {
        if (a.has("body-stdin")) {
            return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String bodyFile = a.get("body-file");
        if (bodyFile != null && !bodyFile.isBlank()) {
            return Files.readString(Paths.get(bodyFile), StandardCharsets.UTF_8);
        }
        String body = a.get("body");
        return body != null ? body : "";
    }

    private static MimeMessage buildMime(String from, String to, String cc, String bcc,
                                         String subject, String body, boolean html,
                                         List<String> attachments,
                                         String inReplyTo, String references) throws Exception {
        Properties props = new Properties();
        Session session = Session.getInstance(props, null);
        MimeMessage email = new MimeMessage(session);

        if (from != null && !from.isBlank()) email.setFrom(new InternetAddress(from));
        addRecipients(email, javax.mail.Message.RecipientType.TO, to);
        addRecipients(email, javax.mail.Message.RecipientType.CC, cc);
        addRecipients(email, javax.mail.Message.RecipientType.BCC, bcc);
        email.setSubject(subject == null ? "" : subject, "UTF-8");

        if (inReplyTo != null && !inReplyTo.isBlank()) email.setHeader("In-Reply-To", inReplyTo);
        if (references != null && !references.isBlank()) email.setHeader("References", references);

        // Auto-upgrade plain text to HTML to avoid RFC MTA line-length wrapping on plain-text messages.
        if (!html) {
            body = plainTextToHtml(body);
            html = true;
        }

        String contentType = "text/html; charset=utf-8";

        if (attachments == null || attachments.isEmpty()) {
            email.setContent(body, contentType);
        } else {
            MimeMultipart mp = new MimeMultipart();
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setContent(body, contentType);
            mp.addBodyPart(textPart);
            for (String path : attachments) {
                MimeBodyPart att = new MimeBodyPart();
                att.attachFile(path);
                mp.addBodyPart(att);
            }
            email.setContent(mp);
        }
        return email;
    }

    private static String plainTextToHtml(String text) {
        if (text == null || text.isEmpty()) return "<p></p>";
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        String[] paragraphs = escaped.split("\\n\\n+");
        StringBuilder sb = new StringBuilder();
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;
            sb.append("<p>").append(trimmed.replace("\n", "<br>")).append("</p>\n");
        }
        return sb.length() > 0 ? sb.toString() : "<p></p>";
    }

    private static void addRecipients(MimeMessage email, javax.mail.Message.RecipientType type, String csv) throws Exception {
        if (csv == null || csv.isBlank()) return;
        String normalized = csv.replace(';', ',');
        email.addRecipients(type, InternetAddress.parse(normalized));
    }

    private static String stripRe(String subject) {
        if (subject == null) return "";
        String s = subject.trim();
        while (s.regionMatches(true, 0, "Re:", 0, 3)) s = s.substring(3).trim();
        return s;
    }

    // ---- payload walking ----------------------------------------------------

    private static Map<String, Object> summary(Message m) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", m.getId());
        s.put("threadId", m.getThreadId());
        s.put("from", header(m.getPayload(), "From"));
        s.put("to", header(m.getPayload(), "To"));
        s.put("date", header(m.getPayload(), "Date"));
        s.put("subject", header(m.getPayload(), "Subject"));
        s.put("snippet", unescapeHtml(m.getSnippet()));
        s.put("labels", m.getLabelIds());
        return s;
    }

    private static String header(MessagePart part, String name) {
        if (part == null || part.getHeaders() == null) return null;
        for (MessagePartHeader h : part.getHeaders()) {
            if (h.getName() != null && h.getName().equalsIgnoreCase(name)) return h.getValue();
        }
        return null;
    }

    /**
     * Picks the body to emit from the collected plain/html parts (same preference the read/thread/search
     * paths used inline) and, when {@code stripQuotes} is set, trims the quoted reply history from it.
     */
    static String bodyText(StringBuilder plain, StringBuilder html, boolean preferHtml, boolean stripQuotes) {
        boolean useHtml = preferHtml ? html.length() > 0 : plain.length() == 0;
        String body = useHtml ? html.toString() : plain.toString();
        if (!stripQuotes) return body;
        return useHtml ? QuoteStripper.stripHtml(body) : QuoteStripper.stripPlain(body);
    }

    private static void collectText(MessagePart part, StringBuilder plain, StringBuilder html) {
        if (part == null) return;
        String mime = part.getMimeType();
        MessagePartBody body = part.getBody();
        if (mime != null && body != null && body.getData() != null) {
            if (mime.startsWith("text/plain")) plain.append(decode(body.getData()));
            else if (mime.startsWith("text/html")) html.append(decode(body.getData()));
        }
        if (part.getParts() != null) for (MessagePart p : part.getParts()) collectText(p, plain, html);
    }

    private static void collectAttachments(MessagePart part, List<Map<String, Object>> out) {
        if (part == null) return;
        String filename = part.getFilename();
        MessagePartBody body = part.getBody();
        if (filename != null && !filename.isEmpty() && body != null && body.getAttachmentId() != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("filename", filename);
            m.put("mimeType", part.getMimeType());
            m.put("attachmentId", body.getAttachmentId());
            m.put("size", body.getSize());
            out.add(m);
        }
        if (part.getParts() != null) for (MessagePart p : part.getParts()) collectAttachments(p, out);
    }

    private static String decode(String urlSafeBase64) {
        return new String(Base64.decodeBase64(urlSafeBase64), StandardCharsets.UTF_8);
    }

    /** Gmail returns the {@code snippet} field HTML-entity-encoded; decode the common/numeric entities. */
    private static String unescapeHtml(String s) {
        if (s == null || s.indexOf('&') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i + 1);
                if (semi > i && semi - i <= 10) {
                    String ent = s.substring(i + 1, semi);
                    String rep = switch (ent) {
                        case "amp" -> "&";
                        case "lt" -> "<";
                        case "gt" -> ">";
                        case "quot" -> "\"";
                        case "apos", "#39" -> "'";
                        case "nbsp" -> " ";
                        default -> null;
                    };
                    if (rep == null && ent.startsWith("#")) {
                        try {
                            int code = (ent.startsWith("#x") || ent.startsWith("#X"))
                                    ? Integer.parseInt(ent.substring(2), 16)
                                    : Integer.parseInt(ent.substring(1));
                            rep = new String(Character.toChars(code));
                        } catch (Exception ignore) { rep = null; }
                    }
                    if (rep != null) { out.append(rep); i = semi + 1; continue; }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String resolveLabelId(String label) throws IOException {
        ListLabelsResponse resp = gmail.users().labels().list(USER).execute();
        if (resp.getLabels() != null) {
            for (Label l : resp.getLabels()) {
                if (label.equals(l.getId())) return l.getId();
            }
            for (Label l : resp.getLabels()) {
                if (l.getName() != null && l.getName().equalsIgnoreCase(label)) return l.getId();
            }
        }
        return label; // fall through; let the API decide
    }

    // ---- batch commands -----------------------------------------------------

    /**
     * R1: batch-read many messages under a single auth. Ids come from positionals,
     * {@code --ids-file <path>} (one per line) and/or {@code --ids-stdin}. Internally a chunked
     * {@code gmail.batch()} resolves them in {@code ceil(N/100)} round trips; a failing id becomes a
     * per-item error entry and does not abort the rest. Output is a JSON array echoing each input id.
     */
    private static void cmdReadBatch(Args a) throws IOException {
        if (a.has("raw")) {
            logError("read-batch does not support --raw (raw bytes can't be combined into one JSON response).");
            logError("Use 'read <messageId> --raw' per message instead.");
            System.exit(2);
        }
        List<String> ids = collectTokens(positionalArgs(a, 1), a.get("ids-file"), a.has("ids-stdin"));
        if (ids.isEmpty()) {
            logError("read-batch requires message ids (positionals, --ids-file <path>, or --ids-stdin).");
            System.exit(2);
        }
        boolean headersOnly = a.has("headers-only");
        boolean preferHtml = a.has("html");
        boolean stripQuotes = a.has("strip-quotes");

        List<BatchItem<Message>> fetched = batchGetMessages(ids, "full", null);
        List<Map<String, Object>> items = new ArrayList<>(fetched.size());
        for (BatchItem<Message> item : fetched) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", item.id);
            if (item.value != null) {
                e.put("ok", true);
                e.putAll(readMap(item.value, headersOnly, preferHtml, stripQuotes));
            } else {
                e.put("ok", false);
                e.put("error", item.error);
            }
            items.add(e);
        }

        if (json) {
            emitJsonStreaming(items);
        } else {
            int i = 1;
            for (Map<String, Object> e : items) {
                boolean ok = Boolean.TRUE.equals(e.get("ok"));
                System.out.println("===== [" + (i++) + "] id=" + e.get("id")
                        + (ok ? "" : "  ERROR: " + e.get("error")) + " =====");
                if (!ok) continue;
                System.out.println("From:    " + nz(e.get("from")));
                System.out.println("To:      " + nz(e.get("to")));
                if (e.get("cc") != null) System.out.println("Cc:      " + e.get("cc"));
                System.out.println("Date:    " + nz(e.get("date")));
                System.out.println("Subject: " + nz(e.get("subject")));
                if (!headersOnly) { System.out.println(); System.out.println(nz(e.get("body"))); System.out.println(); }
            }
            logInfo(items.size() + " message(s)");
        }
    }

    /**
     * R1: batch-read many threads under a single auth. Ids as for {@link #cmdReadBatch}. Output is a
     * JSON array, one entry per input threadId, each carrying its messages (or a per-item error).
     */
    private static void cmdThreadBatch(Args a) throws IOException {
        List<String> ids = collectTokens(positionalArgs(a, 1), a.get("ids-file"), a.has("ids-stdin"));
        if (ids.isEmpty()) {
            logError("thread-batch requires thread ids (positionals, --ids-file <path>, or --ids-stdin).");
            System.exit(2);
        }

        boolean stripQuotes = a.has("strip-quotes");
        List<BatchItem<com.google.api.services.gmail.model.Thread>> fetched = batchGetThreads(ids);
        List<Map<String, Object>> items = new ArrayList<>(fetched.size());
        for (BatchItem<com.google.api.services.gmail.model.Thread> item : fetched) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("threadId", item.id);
            if (item.value != null) {
                e.put("ok", true);
                e.put("messages", threadMessageMaps(item.value, stripQuotes));
            } else {
                e.put("ok", false);
                e.put("error", item.error);
            }
            items.add(e);
        }

        if (json) {
            emitJsonStreaming(items);
        } else {
            for (Map<String, Object> e : items) {
                boolean ok = Boolean.TRUE.equals(e.get("ok"));
                System.out.println("########## thread=" + e.get("threadId")
                        + (ok ? "" : "  ERROR: " + e.get("error")) + " ##########");
                if (!ok) continue;
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> msgs = (List<Map<String, Object>>) e.get("messages");
                int i = 1;
                for (Map<String, Object> s : msgs) {
                    System.out.println("===== message " + (i++) + " (id=" + s.get("id") + ") =====");
                    System.out.println("From:    " + nz(s.get("from")));
                    System.out.println("Date:    " + nz(s.get("date")));
                    System.out.println("Subject: " + nz(s.get("subject")));
                    System.out.println();
                    System.out.println(nz(s.get("body")));
                    System.out.println();
                }
            }
            logInfo(items.size() + " thread(s)");
        }
    }

    /**
     * R2: run many search queries under a single auth (Gmail has no multi-query API; the win is
     * amortizing startup+auth). Queries come from positionals, {@code --queries-file <path>} and/or
     * {@code --queries-stdin}. Each query runs {@code messages.list} then a batched metadata (or full,
     * with {@code --bodies}) fetch. Output is a JSON array keyed by query → its result summaries; a
     * failing query becomes a per-item error entry and does not abort the rest.
     */
    private static void cmdSearchBatch(Args a) throws IOException {
        List<String> queries = collectTokens(positionalArgs(a, 1), a.get("queries-file"), a.has("queries-stdin"));
        if (queries.isEmpty()) {
            logError("search-batch requires queries (positionals, --queries-file <path>, or --queries-stdin).");
            System.exit(2);
        }
        long max = a.getLong("max", 20L);
        boolean bodies = a.has("bodies");
        boolean preferHtml = a.has("html");
        boolean includeSpamTrash = a.has("include-spam-trash");
        boolean stripQuotes = a.has("strip-quotes");
        String label = a.get("label");
        String labelId = (label != null && !label.isBlank()) ? resolveLabelId(label) : null;

        List<Map<String, Object>> items = new ArrayList<>(queries.size());
        for (String q : queries) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("query", q);
            try {
                List<Map<String, Object>> results = searchSummaries(q, max, labelId, includeSpamTrash, bodies, preferHtml, stripQuotes);
                e.put("ok", true);
                e.put("results", results);
            } catch (Exception ex) {
                e.put("ok", false);
                e.put("error", ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
            items.add(e);
        }

        if (json) {
            emitJsonStreaming(items);
        } else {
            for (Map<String, Object> e : items) {
                boolean ok = Boolean.TRUE.equals(e.get("ok"));
                System.out.println("########## query: " + e.get("query")
                        + (ok ? "" : "  ERROR: " + e.get("error")) + " ##########");
                if (!ok) continue;
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) e.get("results");
                if (results.isEmpty()) { System.out.println("    (no messages)"); continue; }
                int i = 1;
                for (Map<String, Object> s : results) {
                    if (s.get("error") != null) {
                        System.out.println("    [" + (i++) + "] id=" + s.get("id") + "  ERROR: " + s.get("error"));
                        continue;
                    }
                    System.out.println("    [" + (i++) + "] id=" + s.get("id") + "  thread=" + s.get("threadId") + "  " + nz(s.get("subject")));
                    System.out.println("        > " + nz(s.get("snippet")));
                    if (bodies && s.get("body") != null) System.out.println("        " + s.get("body"));
                }
            }
            logInfo(queries.size() + " query(ies)");
        }
    }

    // ---- batch helpers ------------------------------------------------------

    /** One entry of a batched fetch: the input id plus either a value (success) or an error message. */
    static final class BatchItem<T> {
        final String id;
        T value;
        String error;
        BatchItem(String id) { this.id = id; }
    }

    /**
     * Fetches many messages via chunked {@code gmail.batch()} (≤100 sub-requests/HTTP batch).
     * Preserves input order (including duplicate ids); per-id failures are captured inline rather than
     * aborting the batch. {@code metadataHeaders} applies only to {@code format="metadata"}.
     */
    private static List<BatchItem<Message>> batchGetMessages(List<String> ids, String format, List<String> metadataHeaders) throws IOException {
        List<BatchItem<Message>> items = new ArrayList<>(ids.size());
        for (String id : ids) items.add(new BatchItem<>(id));

        final int CHUNK = 100;
        for (int start = 0; start < items.size(); start += CHUNK) {
            int end = Math.min(start + CHUNK, items.size());
            BatchRequest batch = gmail.batch();
            for (int i = start; i < end; i++) {
                final BatchItem<Message> item = items.get(i);
                Gmail.Users.Messages.Get get = gmail.users().messages().get(USER, item.id).setFormat(format);
                if (metadataHeaders != null && !metadataHeaders.isEmpty()) get.setMetadataHeaders(metadataHeaders);
                get.queue(batch, new JsonBatchCallback<Message>() {
                    @Override public void onSuccess(Message message, HttpHeaders h) { item.value = message; }
                    @Override public void onFailure(GoogleJsonError e, HttpHeaders h) { item.error = errMsg(e); }
                });
            }
            logInfo("batch messages.get: " + (end - start) + " request(s)");
            batch.execute();
        }
        return items;
    }

    /** Fetches many threads (format=full) via chunked {@code gmail.batch()}. See {@link #batchGetMessages}. */
    private static List<BatchItem<com.google.api.services.gmail.model.Thread>> batchGetThreads(List<String> ids) throws IOException {
        List<BatchItem<com.google.api.services.gmail.model.Thread>> items = new ArrayList<>(ids.size());
        for (String id : ids) items.add(new BatchItem<>(id));

        final int CHUNK = 100;
        for (int start = 0; start < items.size(); start += CHUNK) {
            int end = Math.min(start + CHUNK, items.size());
            BatchRequest batch = gmail.batch();
            for (int i = start; i < end; i++) {
                final BatchItem<com.google.api.services.gmail.model.Thread> item = items.get(i);
                gmail.users().threads().get(USER, item.id).setFormat("full")
                        .queue(batch, new JsonBatchCallback<com.google.api.services.gmail.model.Thread>() {
                            @Override public void onSuccess(com.google.api.services.gmail.model.Thread t, HttpHeaders h) { item.value = t; }
                            @Override public void onFailure(GoogleJsonError e, HttpHeaders h) { item.error = errMsg(e); }
                        });
            }
            logInfo("batch threads.get: " + (end - start) + " request(s)");
            batch.execute();
        }
        return items;
    }

    static String errMsg(GoogleJsonError e) {
        if (e == null) return "unknown error";
        String m = e.getMessage();
        if (m != null && !m.isBlank()) return m;
        return "HTTP " + e.getCode();
    }

    /**
     * Runs one query ({@code messages.list}) then a single chunked {@code gmail.batch()} to hydrate the
     * stubs — metadata headers by default, or full payload + decoded body when {@code bodies} is set
     * (R3/R4). Shared by {@code list}/{@code search} and {@code search-batch}.
     */
    private static List<Map<String, Object>> searchSummaries(String query, long max, String labelId,
                                                             boolean includeSpamTrash, boolean bodies, boolean preferHtml,
                                                             boolean stripQuotes) throws IOException {
        Gmail.Users.Messages.List req = gmail.users().messages().list(USER).setMaxResults(max);
        if (query != null && !query.isBlank()) req.setQ(query);
        if (includeSpamTrash) req.setIncludeSpamTrash(true);
        if (labelId != null) req.setLabelIds(List.of(labelId));

        ListMessagesResponse resp = req.execute();
        List<Message> stubs = resp.getMessages() == null ? List.of() : resp.getMessages();
        List<String> ids = new ArrayList<>(stubs.size());
        for (Message stub : stubs) ids.add(stub.getId());

        List<BatchItem<Message>> fetched = bodies
                ? batchGetMessages(ids, "full", null)
                : batchGetMessages(ids, "metadata", List.of("From", "To", "Cc", "Date", "Subject"));

        List<Map<String, Object>> summaries = new ArrayList<>(fetched.size());
        for (BatchItem<Message> item : fetched) {
            if (item.value == null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("id", item.id);
                err.put("error", item.error);
                summaries.add(err);
                continue;
            }
            Map<String, Object> s = summary(item.value);
            if (bodies) {
                StringBuilder plain = new StringBuilder(), html = new StringBuilder();
                collectText(item.value.getPayload(), plain, html);
                s.put("body", bodyText(plain, html, preferHtml, stripQuotes));
            }
            summaries.add(s);
        }
        return summaries;
    }

    /** Builds the per-message map that {@code read}/{@code read-batch} emit (headers + attachments + optional body). */
    private static Map<String, Object> readMap(Message m, boolean headersOnly, boolean preferHtml, boolean stripQuotes) {
        MessagePart payload = m.getPayload();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", m.getId());
        out.put("threadId", m.getThreadId());
        out.put("labels", m.getLabelIds());
        out.put("snippet", unescapeHtml(m.getSnippet()));
        out.put("from", header(payload, "From"));
        out.put("to", header(payload, "To"));
        out.put("cc", header(payload, "Cc"));
        out.put("date", header(payload, "Date"));
        out.put("subject", header(payload, "Subject"));

        List<Map<String, Object>> attachments = new ArrayList<>();
        collectAttachments(payload, attachments);
        out.put("attachments", attachments);

        if (!headersOnly) {
            StringBuilder plain = new StringBuilder(), html = new StringBuilder();
            collectText(payload, plain, html);
            out.put("body", bodyText(plain, html, preferHtml, stripQuotes));
        }
        return out;
    }

    /** Maps each message of a thread to its summary + decoded plain (fallback html) body. */
    private static List<Map<String, Object>> threadMessageMaps(com.google.api.services.gmail.model.Thread t, boolean stripQuotes) {
        List<Message> msgs = t.getMessages() == null ? List.of() : t.getMessages();
        List<Map<String, Object>> out = new ArrayList<>(msgs.size());
        for (Message m : msgs) {
            Map<String, Object> s = summary(m);
            StringBuilder plain = new StringBuilder(), html = new StringBuilder();
            collectText(m.getPayload(), plain, html);
            s.put("body", bodyText(plain, html, false, stripQuotes));
            out.add(s);
        }
        return out;
    }

    /** Positionals after the command word (index {@code from} onward), or empty. */
    static List<String> positionalArgs(Args a, int from) {
        return a.positionals.size() > from ? a.positionals.subList(from, a.positionals.size()) : List.of();
    }

    /** Gathers tokens (ids or queries) from positionals + an optional file + optional stdin; skips blank and #-comment lines. */
    static List<String> collectTokens(List<String> positionals, String file, boolean stdin) throws IOException {
        List<String> out = new ArrayList<>();
        if (positionals != null) for (String p : positionals) addToken(out, p);
        if (file != null && !file.isBlank()) {
            for (String line : Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)) addToken(out, line);
        }
        if (stdin) {
            String all = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : all.split("\\R", -1)) addToken(out, line);
        }
        return out;
    }

    private static void addToken(List<String> out, String line) {
        if (line == null) return;
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("#")) return;
        out.add(t);
    }

    // ---- output / logging ---------------------------------------------------

    /** Streams a JSON array to stdout element-by-element so large N stays memory-bounded. */
    static void emitJsonStreaming(List<Map<String, Object>> items) throws IOException {
        Gson gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
        JsonWriter w = new JsonWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        w.setIndent("  ");
        w.beginArray();
        for (Map<String, Object> item : items) {
            gson.toJson(item, Map.class, w);
        }
        w.endArray();
        w.flush();
        System.out.println();
    }

    static void emitJson(Object o) {
        System.out.println(new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create().toJson(o));
    }

    static String nz(Object o) { return o == null ? "" : o.toString(); }

    private static void requirePositional(List<String> pos, int index, String msg) {
        if (pos.size() <= index) {
            logError(msg);
            System.exit(2);
        }
    }

    /**
     * Fails fast (exit 2, before any auth) when a batch command was given no input source at all.
     * {@code kind} is "ids" (read-batch/thread-batch) or "queries" (search-batch); the actual token
     * collection + empty-after-parse check happens later in the command itself.
     */
    private static void requireBatchInput(Args a, List<String> pos, String kind) {
        boolean any = pos.size() > 1 || a.get(kind + "-file") != null || a.has(kind + "-stdin");
        if (!any) {
            logError("ids".equals(kind)
                    ? "read-batch/thread-batch require ids (positionals, --ids-file <path>, or --ids-stdin)."
                    : "search-batch requires queries (positionals, --queries-file <path>, or --queries-stdin).");
            System.exit(2);
        }
    }

    static void logInfo(String message) {
        if (verbose) System.err.println("[INFO] " + message);
    }

    static void logError(String message) {
        System.err.println("[ERROR] " + message);
    }

    private static void printUsage() {
        String u = """
                uskoag-gmailcli - read, search and draft Gmail (no send)

                Usage:
                  uskoag-gmailcli <command> [args] --email <you@gmail.com> [--json] [-v]

                Commands:
                  auth | login                          Run/verify OAuth, print profile
                  reauth | relogin                      Forget the stored token and re-consent (needed once for label writes)
                  profile | whoami                      Print account profile
                  listprofiles | accounts [--probe]     List configured accounts (client type, token stores);
                                                        --probe calls getProfile to verify live login (no browser)
                  labels                                List labels
                  label-create <name>                   Create a label   [--text-color <hex> --bg-color <hex>] [--hide|--show]
                  label-update <id|name>                Rename/recolor a label   [--name <new>] [colors] [--hide|--show]
                  label-delete <id|name>                Delete a (user) label
                  label   <labels> <id...>              Add label(s) to messages (or threads with --threads)   [--create-missing]
                  unlabel <labels> <id...>              Remove label(s) from messages (or threads with --threads)
                  modify  <id...> [--add <labels>] [--remove <labels>] [--threads] [--create-missing]
                  list [-q "<query>"] [-l <label>] [-n <max>] [--include-spam-trash] [--bodies] [--strip-quotes]
                  search "<query>" [-l <label>] [-n <max>] [--bodies] [--strip-quotes]
                  read <messageId> [--html] [--headers-only] [--raw] [--strip-quotes]
                  thread <threadId> [--strip-quotes]
                  read-batch <id...> | --ids-file <path> | --ids-stdin   [--html] [--headers-only] [--strip-quotes]
                  thread-batch <id...> | --ids-file <path> | --ids-stdin   [--strip-quotes]
                  search-batch <query...> | --queries-file <path> | --queries-stdin
                        [-l <label>] [-n <max>] [--include-spam-trash] [--bodies] [--strip-quotes]
                  drafts [-n <max>]
                  draft-read <draftId>
                  draft --to <a,b> [--cc ..] [--bcc ..] [-s <subject>]
                        (-b "<body>" | --body-file <path> | --body-stdin) [--html]
                        [--attach <file> ...] [--reply-to <messageId>] [--thread <threadId>]
                  attachment <messageId> <attachmentId> [--out <path>]

                Global flags:
                  --email, -e <email>     Gmail account (required)
                  (--app-key/-k is gone: a secret on argv is readable by other processes and lands
                   in shell history and AI transcripts. Use uskoag-wallet, or the hidden prompt.)
                  --json                  Machine-readable JSON output
                  --verbose, -v           Log progress to stderr

                Gmail search examples (-q / search):
                  "from:alice@x.com is:unread"   "subject:invoice newer_than:7d"   "has:attachment label:work"

                Bulk / batch notes:
                  --bodies                Inline the decoded body (plain; html with --html) in list/search output
                  --strip-quotes          Drop quoted reply history from the body (read/thread/list/search + batch
                                          variants) so re-reading a thread doesn't re-read every prior message's
                                          text at each level. Heuristic: cuts at "On ... wrote:" / "-----Original
                                          Message-----" / Outlook From:+Sent:+To: headers (plain text), or strips
                                          blockquote/gmail_quote containers (html). Aliases: --no-quotes, --no-history.
                  read-batch/thread-batch Many ids in ONE auth via gmail.batch(); a bad id is a per-item error,
                                          not a fatal exit. Output is a JSON array echoing each input id.
                  search-batch            Many queries in ONE auth; JSON array keyed by query. One query per line
                                          via --queries-file/--queries-stdin (blank and #-comment lines ignored).

                Label notes:
                  <labels>                Comma-separated names or ids; names match case-insensitively. System ids
                                          (INBOX, UNREAD, STARRED, IMPORTANT, SPAM, TRASH, CATEGORY_*) work too.
                  label/unlabel/modify    Ids from positionals, --ids-file <path>, or --ids-stdin. Many ids per
                                          auth via gmail.batch(); a bad id is a per-item error, not a fatal exit.
                  --create-missing        For added labels, auto-create any that don't exist (else it's an error).
                  --text-color/--bg-color Must be set together, and only from Gmail's fixed palette (else API error).
                """;
        System.err.println(u);
    }

    // ---- tiny arg parser ----------------------------------------------------

    /** Parses global + command flags in any position; everything else is a positional. */
    static final class Args {
        final Map<String, String> values = new LinkedHashMap<>();
        final List<String> positionals = new ArrayList<>();
        final List<String> attachments = new ArrayList<>();

        Args(String[] argv) {
            for (int i = 0; i < argv.length; i++) {
                String tok = argv[i];
                if (REPEATABLE_ATTACH.equals(tok)) {
                    if (i + 1 >= argv.length) throw new IllegalArgumentException("Missing value for " + tok);
                    attachments.add(argv[++i]);
                } else if (VALUE_FLAGS.containsKey(tok)) {
                    if (i + 1 >= argv.length) throw new IllegalArgumentException("Missing value for " + tok);
                    values.put(VALUE_FLAGS.get(tok), argv[++i]);
                } else if (BOOL_FLAGS.containsKey(tok)) {
                    values.put(BOOL_FLAGS.get(tok), "true");
                } else if (tok.startsWith("-") && tok.length() > 1 && !isNegativeNumber(tok)) {
                    throw new IllegalArgumentException("Unknown flag: " + tok);
                } else {
                    positionals.add(tok);
                }
            }
        }

        private static boolean isNegativeNumber(String s) {
            return s.matches("-\\d+");
        }

        String get(String name) { return values.get(name); }
        boolean has(String name) { return "true".equals(values.get(name)); }
        long getLong(String name, long def) {
            String v = values.get(name);
            if (v == null) return def;
            try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return def; }
        }
    }
}
