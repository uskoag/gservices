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

    /** Higher wins when several are on the classpath. The app-key default sits at zero. */
    default int priority() {
        return 0;
    }

    ServiceAccess access(AccessSpec spec) throws IOException;
}
