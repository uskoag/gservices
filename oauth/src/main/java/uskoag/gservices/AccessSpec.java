package uskoag.gservices;

import java.util.List;

/**
 * What a tool needs in order to talk to one Google service.
 *
 * <p>{@code profile} names a credential; it is not a secret and never unlocks one. Tools declare their
 * own as a compiled-in constant, which is what the app-key used to be minus the secrecy.
 *
 * @param api             one of drive, sheets, slides, docs, gmail, youtube
 * @param profile         compiled-in credential name, e.g. {@code gsheets}
 * @param appName         human label, for the audit and the consent screen
 * @param account         the email, or null to let the source pick its default
 * @param legacyCredsRoot where this tool's per-email token directories live; used only by the app-key
 *                        fallback, since a wallet keeps everything in one keyring instead
 */
public record AccessSpec(String api, String profile, String appName, String account,
                         List<String> scopes, String legacyCredsRoot) {

    public static AccessSpec of(String api, String profile, String appName, String account, List<String> scopes) {
        return new AccessSpec(api, profile, appName, account, scopes, null);
    }

    public AccessSpec legacyRoot(String root) {
        return new AccessSpec(api, profile, appName, account, scopes, root);
    }
}
