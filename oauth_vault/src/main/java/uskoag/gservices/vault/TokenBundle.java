package uskoag.gservices.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A portable, password-protected copy of one or more logins: authorize on a trusted machine, run on another.
 *
 * <p>An ordinary zip rather than a bespoke encrypted format, so it can be carried, stored and inspected with
 * tools people already have. The cost is real and deliberate: zip's AES uses a far cheaper key derivation
 * than {@link Kdf}, so the password itself carries the strength here and callers should insist on a long one.
 *
 * <p>What travels is the refresh token, never the token store - a store file is keyed to one installation's
 * derived key, so copying it would carry that machine's key material too. The OAuth client does not travel
 * either; only its id is recorded, which is enough for an import to refuse a token minted by a different
 * client rather than let it fail later as an opaque {@code invalid_grant}.
 *
 * <p>Entry names and the filename stay readable without the password - zip never encrypts its central
 * directory - which is what lets a bundle be identified before anyone types anything.
 */
public final class TokenBundle {

    public static final String FORMAT = "uskoag-gservices token bundle v1", ENTRY = "logins.json";

    static final ObjectMapper OM = new ObjectMapper();
    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");

    private TokenBundle() {}

    /** Downloads, named for what it holds and the minute it was made. */
    public static Path defaultFile(String appName, List<String> identities) {
        var name = appName + "-logins-" + String.join("-", identities) + "-" + LocalDateTime.now().format(STAMP) + ".zip";
        var downloads = Path.of(System.getProperty("user.home"), "Downloads");
        return (Files.isDirectory(downloads) ? downloads : Path.of(System.getProperty("user.home"))).resolve(name);
    }

    public static void write(Path file, BundleContent c, char[] password) throws Exception {
        try {
            var body = OM.createObjectNode();
            body.put("format", FORMAT);
            body.put("account", c.account());
            body.put("clientId", c.clientId());
            body.put("created", c.created());
            var arr = body.putArray("tokens");
            for (var e : c.entries()) {
                var n = arr.addObject();
                n.put("identity", e.identity());
                n.put("refreshToken", e.refreshToken());
                if (e.accessToken() != null) n.put("accessToken", e.accessToken());
                if (e.expiresAtMs() != null) n.put("expiresAtMs", e.expiresAtMs());
                if (e.boundTo() != null) n.put("boundTo", e.boundTo());
            }
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.deleteIfExists(file);
            try (var zip = new ZipFile(file.toFile(), password)) {
                zip.addStream(new ByteArrayInputStream(OM.writerWithDefaultPrettyPrinter().writeValueAsBytes(body)),
                    encrypted(ENTRY));
            }
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    public static BundleContent read(Path file, char[] password) throws Exception {
        try (var zip = new ZipFile(file.toFile(), password)) {
            byte[] json;
            try (var in = zip.getInputStream(zip.getFileHeader(ENTRY))) {
                json = in.readAllBytes();
            } catch (NullPointerException missing) {
                throw new IllegalArgumentException(file + " is not a " + FORMAT + " (no " + ENTRY + " inside)");
            } catch (IOException bad) {
                throw new BadBundlePassword();   // zip4j fails the AES MAC while reading, not on open
            }
            var root = OM.readTree(json);
            if (!FORMAT.equals(root.path("format").asText()))
                throw new IllegalArgumentException(file + " is not a " + FORMAT);
            var entries = new ArrayList<BundleEntry>();
            for (var n : root.path("tokens")) {
                entries.add(new BundleEntry(n.path("identity").asText(), n.path("refreshToken").asText(),
                    n.path("accessToken").asText(null),
                    n.hasNonNull("expiresAtMs") ? n.path("expiresAtMs").asLong() : null,
                    n.path("boundTo").asText(null)));
            }
            return new BundleContent(root.path("account").asText("default"), root.path("clientId").asText(null),
                root.path("created").asText(""), entries);
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    /** What can be told without the password: the name, the size, that it is a zip of ours. */
    public static String describe(Path file) {
        try (var zip = new ZipFile(file.toFile())) {
            var names = new ArrayList<String>();
            for (var h : zip.getFileHeaders()) names.add(h.getFileName());
            return file.getFileName() + "  (" + Files.size(file) + " bytes, entries: " + names + ")";
        } catch (Exception e) {
            return file.getFileName() + "  (unreadable as a zip: " + e.getMessage() + ")";
        }
    }

    /** AES-256; zip's other option, ZipCrypto, is broken and must never be written. */
    static ZipParameters encrypted(String name) {
        var p = new ZipParameters();
        p.setFileNameInZip(name);
        p.setEncryptFiles(true);
        p.setEncryptionMethod(EncryptionMethod.AES);
        p.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        return p;
    }

    public static String now() { return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(); }
}
