package uskoag.gservices.docs;

import java.nio.file.*;
import java.util.List;

/** read <doc> --revision <spec> — the document as it was then, from Drive's per-revision export. */
public final class VerbRevRead {

    private VerbRevRead() {}

    static void run(String spec, List<String> a) throws Exception {
        var fmt = Args.valOr(a, "--format", "md").toLowerCase();
        if (Args.flag(a, "--md")) fmt = "md";
        if (Args.flag(a, "--html")) fmt = "html";
        if (Args.flag(a, "--txt")) fmt = "txt";
        if (Args.flag(a, "--docx")) fmt = "docx";
        var out = Args.val(a, "--out", "-o");
        RevExport.refresh = Args.flag(a, "--refresh");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "read --revision");
        var rev = RevSpec.resolve(id, spec);
        var data = RevExport.bytes(id, rev.n(), fmt);
        if (fmt.equals("docx") && out == null) out = "r" + rev.n() + ".docx";
        if (out == null) {
            System.out.println("<!-- document id=" + id + " revision=" + rev.n() + " as of " + rev.when() + " by " + rev.who()
                    + " — Drive export: tabs are concatenated, see the help -->");
            System.out.print(new String(data, java.nio.charset.StandardCharsets.UTF_8));
            return;
        }
        var p = Path.of(out);
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.write(p, data);
        Out.success("wrote " + p + " (revision " + rev.n() + ", " + data.length + " bytes)");
    }
}
