package uskoag.gservices;

import java.io.IOException;

/**
 * The seam that lets a wallet exist without this library knowing anything about one.
 *
 * <p>{@link AppKeyCredentialSource} is the default and stays the default permanently, so a shaded jar
 * handed to someone with no wallet installed works exactly as it does today. A wallet, when installed,
 * contributes its own implementation through {@link java.util.ServiceLoader} and is preferred. Nothing
 * here compiles against the wallet; the dependency arrow points the other way.
 */
public interface CredentialSource {

    String name();

    /** Cheap enough to call on every invocation — a client asks this before every access. */
    boolean available();

    /**
     * Whether a request made right now would be served without stopping to ask a person for anything.
     *
     * <p>Distinct from {@link #available()}, which asks whether this source exists at all. A wallet is
     * available while it is locked, and not ready. The difference only matters to work nobody is
     * watching: a scheduled refresh must not raise a passphrase box at three in the morning, and when
     * it cannot run it has to say so rather than look like a refresh that ran and found nothing.
     * Foreground callers ignore this and simply ask — being prompted is the whole point of them.
     */
    default boolean ready() {
        return available();
    }

    /** Higher wins when several are on the classpath. The app-key default sits at zero. */
    default int priority() {
        return 0;
    }

    /**
     * The accounts this source can serve, when it can enumerate them at all.
     *
     * <p>Empty means "cannot say", never "none". The app-key default has no inventory to offer, and a
     * wallet that is locked or not running cannot be asked for one. So a caller listing accounts has to
     * report which of the two it is — {@link #ready()} tells it apart — rather than printing an empty
     * list: an empty inventory of a credential store reads as loss, and a tool that says "no accounts"
     * about a wallet holding six of them has reported a fault that does not exist.
     */
    default java.util.List<String> accounts() {
        return java.util.List.of();
    }


    ServiceAccess access(AccessSpec spec) throws IOException;
}
