package uskoag.gservices.gdocsmon;

import java.awt.*;
import java.awt.image.BufferedImage;

/** AWT tray (JavaFX has none), icon drawn in code: a page with a speech bubble; the bubble turns amber while something waits for approval. */
final class Tray {

    private static TrayIcon icon;
    private static Boolean shownPending;

    private Tray() {}

    static void install(Runnable open, Runnable quit) {
        if (!SystemTray.isSupported()) { Home.log("no system tray"); return; }
        var menu = new PopupMenu();
        menu.add(item("Open monitor", open));
        menu.addSeparator();
        menu.add(item("Quit", quit));
        icon = new TrayIcon(image(false), "gdocs monitor", menu);
        icon.setImageAutoSize(true);
        icon.addActionListener(e -> open.run());
        try { SystemTray.getSystemTray().add(icon); } catch (AWTException e) { Home.log("tray add failed: " + e); }
    }

    static void pending(boolean any, String tip) {
        if (icon == null) return;
        icon.setToolTip(tip);
        if (shownPending != null && shownPending == any) return;
        shownPending = any;
        icon.setImage(image(any));
    }

    static void message(String title, String text) {
        if (icon != null) icon.displayMessage(title, Ev.clip(text, 240), TrayIcon.MessageType.INFO);
    }

    private static MenuItem item(String text, Runnable r) {
        var m = new MenuItem(text);
        m.addActionListener(e -> { try { r.run(); } catch (Throwable t) { Home.log("tray item " + text + " failed: " + t); } });
        return m;
    }

    static BufferedImage image(boolean pending) {
        var img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x1A, 0x73, 0xE8));
        g.fillRoundRect(3, 1, 20, 27, 4, 4);
        g.setColor(Color.WHITE);
        for (var y = 7; y <= 19; y += 4) g.fillRect(7, y, 12, 2);
        g.setColor(pending ? new Color(0xE6, 0x8A, 0x00) : new Color(0x2E, 0x7D, 0x32));
        g.fillOval(15, 14, 16, 13);
        g.fillPolygon(new int[]{18, 22, 17}, new int[]{24, 25, 31}, 3);
        g.setColor(Color.WHITE);
        for (var x = 18; x <= 26; x += 4) g.fillOval(x, 19, 3, 3);
        g.dispose();
        return img;
    }
}
