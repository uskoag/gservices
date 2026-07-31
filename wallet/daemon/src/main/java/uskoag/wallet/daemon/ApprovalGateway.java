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

    /**
     * Bring the wallet's own window forward. Called when a second launch finds this one already
     * running: the expectation when someone starts an app that is already up is that its window
     * appears, not that a second copy argues with the first.
     */
    default void showWindow() {
    }

    /**
     * A tool needs scopes this account has never granted. Answering yes opens a browser, so this is a
     * question and not a notification.
     */
    default boolean consentNeeded(String account, java.util.List<String> missingScopes) {
        return false;
    }

    default boolean interactive() {
        return true;
    }
}
