package uskoag.gservices;

import jakarta.mail.internet.MimeMessage;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Standalone smoke test for {@link GmailCli#buildMime} — no Google auth needed.
 * Covers prp/08-prp-inline-image-dropped-on-send-with-attachments.md: --inline alone and --attach alone
 * keep the original cid:+separate-part recipe (proven safe in every test); --inline combined with
 * --attach uses a different recipe (a data: URI embedded directly in the html) because every variant of
 * the original recipe was proven to break under Gmail's own compose window for that one combination —
 * see 08-prp.status.md for the full investigation. This only guards the MIME this tool BUILDS, not
 * Gmail's own compose-window handling of a draft, which is outside this process's reach.
 * Run: mvn exec:java -Dexec.mainClass="uskoag.gservices.MimeStructureSmokeTest" -Dexec.classpathScope=test
 */
public class MimeStructureSmokeTest {

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("mime-smoke");
        Path img = dir.resolve("test.png");
        Files.write(img, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3});
        Path doc1 = dir.resolve("doc1.txt");
        Files.writeString(doc1, "one");
        Path doc2 = dir.resolve("doc2.txt");
        Files.writeString(doc2, "two");
        Path doc3 = dir.resolve("doc3.txt");
        Files.writeString(doc3, "three");
        Path doc4 = dir.resolve("doc4.txt");
        Files.writeString(doc4, "four");

        boolean ok = true;
        ok &= checkInlineOnly(img);
        ok &= checkCombined("inline + 1 attach", List.of(doc1.toString()), img);
        ok &= checkCombined("inline + 4 attach",
                List.of(doc1.toString(), doc2.toString(), doc3.toString(), doc4.toString()), img);

        System.out.println(ok ? "ALL PASS" : "FAILURE — see above");
        if (!ok) System.exit(1);
    }

    /** --inline alone: unchanged recipe (cid: + separate MIME part), proven safe in every test. */
    private static boolean checkInlineOnly(Path img) throws Exception {
        String raw = build(List.of(), List.of(img.toString()));

        boolean hasContentId = raw.contains("Content-ID: <test.png>");
        boolean hasInlineDisposition = raw.contains("Content-Disposition: inline; filename=test.png");
        boolean hasImagePart = raw.contains("Content-Type: image/png; name=\"test.png\"");

        boolean pass = hasContentId && hasInlineDisposition && hasImagePart;
        System.out.println((pass ? "PASS" : "FAIL") + " - inline alone"
                + "  [Content-ID=" + hasContentId + " inline-disposition=" + hasInlineDisposition
                + " image-part=" + hasImagePart + "]");
        if (!pass) System.out.println(raw);
        return pass;
    }

    /** --inline + --attach: the data: URI recipe — no separate cid:-referenced MIME part for the image. */
    private static boolean checkCombined(String label, List<String> attachments, Path img) throws Exception {
        String raw = build(attachments, List.of(img.toString()));

        boolean hasDataUri = raw.contains("data:image/png;base64,");
        boolean noContentId = !raw.contains("Content-ID:");
        boolean noSeparateImagePart = !raw.contains("Content-Type: image/png; name=\"test.png\"");
        int attachCount = countOccurrences(raw, "Content-Disposition: attachment;");

        boolean pass = hasDataUri && noContentId && noSeparateImagePart && attachCount == attachments.size();
        System.out.println((pass ? "PASS" : "FAIL") + " - " + label
                + "  [data-uri=" + hasDataUri + " no-content-id=" + noContentId
                + " no-separate-image-part=" + noSeparateImagePart
                + " attachCount=" + attachCount + "/" + attachments.size() + "]");
        if (!pass) System.out.println(raw);
        return pass;
    }

    private static String build(List<String> attachments, List<String> inlines) throws Exception {
        MimeMessage mime = GmailCli.buildMime("from@example.com", "to@example.com", null, null,
                "Subject", "<p>Hello <img src=\"cid:test.png\"></p>", true,
                attachments, inlines, null, null);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        mime.writeTo(buf);
        return buf.toString("UTF-8");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) { count++; idx += needle.length(); }
        return count;
    }
}
