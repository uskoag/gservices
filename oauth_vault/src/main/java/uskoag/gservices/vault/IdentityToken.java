package uskoag.gservices.vault;

import com.google.api.client.auth.oauth2.Credential;

/**
 * A stored login for one identity.
 *
 * <p>{@link TokenStatus#MISSING} and {@link TokenStatus#UNREADABLE} are deliberately distinct: nobody having
 * logged this identity in is normal and callers should carry on quietly, whereas a token that exists and
 * will not open is a fault - the passphrase was right and this particular entry is corrupt or was written
 * under a different key.
 */
public record IdentityToken(String identity, Credential credential, TokenStatus status, String reason) {

    public static IdentityToken authorized(String id, Credential c) {
        return new IdentityToken(id, c, TokenStatus.AUTHORIZED, null);
    }

    public static IdentityToken missing(String id) { return new IdentityToken(id, null, TokenStatus.MISSING, null); }

    public static IdentityToken unreadable(String id, String why) {
        return new IdentityToken(id, null, TokenStatus.UNREADABLE, why);
    }

    public boolean ok() { return credential != null; }

    public String describe() {
        return switch (status) {
            case AUTHORIZED -> identity + ": authorized";
            case MISSING -> identity + ": not logged in";
            case UNREADABLE -> identity + ": stored login unreadable - " + reason;
        };
    }
}
