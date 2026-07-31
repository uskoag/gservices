package uskoag.wallet.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import uskoag.wallet.daemon.Log;
import uskoag.wallet.daemon.Wallet;

/**
 * uskoag-wallet.exe. The only process on this machine that ever holds a refresh token.
 *
 * <p>The engine runs in-process rather than in a separate daemon, because an approval dialog that lives
 * somewhere other than the thing needing the answer is an approval that cannot be given. Closing the
 * window hides it; the wallet keeps serving from the tray.
 */
public final class WalletApp extends Application {

    private final Wallet wallet = new Wallet();

    @Override
    public void start(Stage ignored) throws Exception {
        Platform.setImplicitExit(false);
        wallet.core.gateway(new FxGateway(wallet.core));
        wallet.start();

        Tray.install(() -> MainWindow.show(wallet.core), this::lock);
        UnlockWindow.show(wallet.core, () -> {
            MainWindow.show(wallet.core);
            Tray.note("uskoag wallet", "Unlocked. Tools on this machine can now reach Google through it.");
        }, null);
    }

    private void lock() {
        wallet.core.lock();
        MainWindow.hide();
        Tray.note("uskoag wallet", "Locked. Everything in flight has stopped.");
        UnlockWindow.show(wallet.core, () -> MainWindow.show(wallet.core), "Locked from the tray.");
    }

    @Override
    public void stop() {
        Log.info("shutting down");
        wallet.stop();
    }
}
