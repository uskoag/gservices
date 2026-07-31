package uskoag.gservices;

import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import java.util.Arrays;

/**
 * Where an app-key comes from when no wallet is installed: a hidden console prompt, then a dialog, and
 * never a command-line flag.
 *
 * <p>argv is readable by any process on Windows through {@code Win32_Process.CommandLine}, and it lands
 * in PSReadLine history and in AI transcripts. That is the accidental-disclosure path this whole
 * design exists to close, so there is deliberately no flag to fall back to.
 */
public final class AppKeyPrompt {

    private AppKeyPrompt() {
    }

    public static String ask(String appName) {
        var console = System.console();
        if (console != null) {
            var typed = console.readPassword("app-key for %s (input hidden): ", appName);
            if (typed == null) return null;
            var key = new String(typed).trim();
            Arrays.fill(typed, '\0');
            return key.isEmpty() ? null : key;
        }
        return dialog(appName);
    }

    /** Swing, not JavaFX, purely because it needs no Application lifecycle to show one modal box. */
    private static String dialog(String appName) {
        try {
            if (java.awt.GraphicsEnvironment.isHeadless()) return null;
            var field = new JPasswordField(24);
            var choice = JOptionPane.showConfirmDialog(null, field,
                    appName + " — app-key (no wallet found)",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) return null;
            var typed = field.getPassword();
            var key = new String(typed).trim();
            Arrays.fill(typed, '\0');
            return key.isEmpty() ? null : key;
        } catch (Throwable t) {
            return null;
        }
    }
}
