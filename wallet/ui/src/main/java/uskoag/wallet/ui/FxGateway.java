package uskoag.wallet.ui;

import javafx.application.Platform;
import uskoag.wallet.daemon.ApprovalGateway;
import uskoag.wallet.daemon.WalletCore;
import uskoag.wallet.wire.ApprovalAnswer;
import uskoag.wallet.wire.ApprovalAsk;

/** The engine's route to a person: a real window, on the FX thread, with the worker blocked behind it. */
public final class FxGateway implements ApprovalGateway {

    private final WalletCore core;

    public FxGateway(WalletCore core) {
        this.core = core;
    }

    @Override
    public ApprovalAnswer ask(ApprovalAsk ask) {
        return ApprovalWindow.ask(ask, core.settings.destructiveOps, core.settings.destructiveMinutes);
    }

    @Override
    public void unlockNeeded(String because) {
        Platform.runLater(() -> UnlockWindow.show(core, null, because));
    }
}
