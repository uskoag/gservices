package uskoag.gservices;

import java.io.IOException;
import java.util.Comparator;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Resolution order, in strict order: an installed wallet, then the hidden console prompt that already
 * exists, then a dialog, then a clear failure naming what to start.
 *
 * <p>It never silently degrades into a mode that puts a key back into a log, and it never makes the
 * wallet a prerequisite — a jar with no wallet on its classpath behaves exactly as it does today.
 */
public final class Credentials {

    private Credentials() {
    }

    /** The highest-priority source that says it is available, if any beyond the app-key default. */
    public static Optional<CredentialSource> discovered() {
        return ServiceLoader.load(CredentialSource.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(CredentialSource::available)
                .max(Comparator.comparingInt(CredentialSource::priority));
    }

    public static ServiceAccess access(AccessSpec spec) throws IOException {
        return access(spec, () -> AppKeyPrompt.ask(spec.appName()));
    }

    public static ServiceAccess access(AccessSpec spec, Supplier<String> appKeyFallback) throws IOException {
        var source = discovered();
        if (source.isPresent()) return source.get().access(spec);
        return new AppKeyCredentialSource(appKeyFallback).access(spec);
    }

    /** For a {@code status}-style verb: which route this invocation would take, without taking it. */
    public static String describeRoute() {
        return discovered().map(CredentialSource::name).orElse("app-key (no wallet installed)");
    }
}
