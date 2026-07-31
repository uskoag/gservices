package uskoag.wallet.daemon;

import uskoag.wallet.wire.ApprovalAnswer;
import uskoag.wallet.wire.ApprovalAsk;

/**
 * How the engine reaches a person. The UI module supplies the JavaFX implementation; the engine itself
 * stays headless so it can be built and tested without a display.
 */
public interface ApprovalGateway {

    ApprovalAnswer ask(ApprovalAsk ask);

    /** Raise the unlock window because work arrived while the wallet was locked. */
    void unlockNeeded(String because);

    default boolean interactive() {
        return true;
    }
}
