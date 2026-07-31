package uskoag.gservices.slides;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Straight concatenation, no transitions, even dimensions forced so libx264 accepts it. */
public final class VideoExport {

    private VideoExport() {}

    static void build(List<Path> slides, Path outDir, String title, double secondsPerSlide) throws Exception {
        if (slides.isEmpty()) { Out.error("no PNGs to build a video from"); return; }
        var out = outDir.resolve(title.replaceAll("[^A-Za-z0-9_-]", "_") + "_slideshow.mp4");

        var cmd = new ArrayList<String>();
        cmd.add("ffmpeg");
        for (var p : slides) {
            cmd.add("-loop"); cmd.add("1");
            cmd.add("-t"); cmd.add(String.format("%.2f", secondsPerSlide));
            cmd.add("-i"); cmd.add(p.toAbsolutePath().toString());
        }
        cmd.add("-filter_complex"); cmd.add(filter(slides.size()));
        cmd.add("-map"); cmd.add("[vout]");
        cmd.add("-c:v"); cmd.add("libx264");
        cmd.add("-pix_fmt"); cmd.add("yuv420p");
        cmd.add("-r"); cmd.add("30");
        cmd.add("-crf"); cmd.add("18");
        cmd.add("-y"); cmd.add(out.toAbsolutePath().toString());

        Out.info("ffmpeg: " + slides.size() + " slides at " + secondsPerSlide + "s each");
        var pb = new ProcessBuilder(cmd).directory(outDir.toFile());
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        var code = pb.start().waitFor();
        if (code != 0) {
            Out.error("ffmpeg exited " + code + " -- is ffmpeg on PATH? PNGs were still saved.");
            return;
        }
        Out.data(out.toAbsolutePath().toString());
    }

    private static String filter(int n) {
        var sb = new StringBuilder();
        for (var i = 0; i < n; i++)
            sb.append(String.format("[%d:v]scale='ceil(iw/2)*2':'ceil(ih/2)*2',setsar=1[v%d];", i, i));
        for (var i = 0; i < n; i++) sb.append(String.format("[v%d]", i));
        sb.append(String.format("concat=n=%d:v=1:a=0[vout]", n));
        return sb.toString();
    }
}
