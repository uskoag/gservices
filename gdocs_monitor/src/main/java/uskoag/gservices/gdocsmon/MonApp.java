package uskoag.gservices.gdocsmon;

import javafx.application.*;
import javafx.stage.Stage;

/** The daemon: loopback server, poller, tray icon and one window. Single instance through the port file. */
public final class MonApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        if (Client.alive()) { Home.log("another daemon is already answering; this one exits"); Platform.exit(); System.exit(0); }
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> Home.log("uncaught on " + t.getName() + ": " + e));
        Runnable exit = () -> {
            try { java.nio.file.Files.deleteIfExists(Home.PORT); } catch (Exception ignored) {}
            Home.log("daemon exit");
            Platform.exit();
            System.exit(0);
        };
        MonWindow.build(stage);
        State.onChange = () -> { MonWindow.refresh(); AskBox.sweep(); };
        Server.exit = exit;
        Server.start();
        Tray.install(() -> Platform.runLater(MonWindow::show), exit);
        Poller.start(exit);
        State.note("daemon started");
        MonWindow.refresh();
    }
}
