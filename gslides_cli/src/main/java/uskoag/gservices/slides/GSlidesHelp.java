package uskoag.gservices.slides;

import java.io.PrintStream;

public final class GSlidesHelp {

    private GSlidesHelp() {}

    static void print(PrintStream out) { out.print(Res.text("gslides-help.txt")); }
}
