package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Paragraph layout a verb sets on top of whatever style the paragraph has: alignment, keepWithNext
 * (the paragraph stays on the page of the one after it, so a caption never lands apart from its picture)
 * and keepLinesTogether. A null field is left untouched, and only the fields given go in the field mask.
 */
record ParaFmt(String align, Boolean keepWithNext, Boolean keepLinesTogether) {

    static final ParaFmt NONE = new ParaFmt(null, null, null);

    boolean any() { return align != null || keepWithNext != null || keepLinesTogether != null; }

    /** --align <v> and the keep flags. shortFlags adds --center/--left/--right/--justify, which `image insert` cannot take: --left and --right are its crop fractions. */
    static ParaFmt of(List<String> a, boolean shortFlags) {
        var v = Args.val(a, "--align");
        if (shortFlags) for (var n : List.of("center", "left", "right", "justify"))
            if (Args.flag(a, "--" + n)) { if (v != null && !v.equals(n)) Out.die("give one alignment, not --align " + v + " and --" + n); v = n; }
        return new ParaFmt(v == null ? null : api(v), keep(a, "--keep-with-next", "--no-keep-with-next"), keep(a, "--keep-lines-together", "--no-keep-lines-together"));
    }

    private static Boolean keep(List<String> a, String on, String off) {
        var yes = Args.flag(a, on);
        var no = Args.flag(a, off);
        if (yes && no) Out.die("give " + on + " or " + off + ", not both");
        return yes ? Boolean.TRUE : no ? Boolean.FALSE : null;
    }

    static String api(String v) {
        return switch (v.strip().toLowerCase()) {
            case "center", "centre" -> "CENTER";
            case "left", "start" -> "START";
            case "right", "end" -> "END";
            case "justify", "justified" -> "JUSTIFIED";
            default -> { Out.die("unknown alignment \"" + v + "\" (center, left, right, justify)"); yield null; }
        };
    }

    String mask() {
        var f = new ArrayList<String>();
        if (align != null) f.add("alignment");
        if (keepWithNext != null) f.add("keepWithNext");
        if (keepLinesTogether != null) f.add("keepLinesTogether");
        return String.join(",", f);
    }

    ParagraphStyle style() {
        return new ParagraphStyle().setAlignment(align).setKeepWithNext(keepWithNext).setKeepLinesTogether(keepLinesTogether);
    }

    Request request(Range r) {
        return new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest().setParagraphStyle(style()).setFields(mask()).setRange(r));
    }

    String describe() {
        var p = new ArrayList<String>();
        if (align != null) p.add(align.equals("START") ? "left" : align.equals("END") ? "right" : align.toLowerCase());
        if (keepWithNext != null) p.add(keepWithNext ? "keep-with-next" : "no keep-with-next");
        if (keepLinesTogether != null) p.add(keepLinesTogether ? "keep-lines-together" : "no keep-lines-together");
        return String.join(", ", p);
    }
}
