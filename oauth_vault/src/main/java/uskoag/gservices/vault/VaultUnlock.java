package uskoag.gservices.vault;

/**
 * Result of {@link PassphraseVault#unlock}.
 *
 * <p>{@link UnlockStatus#WRONG_PASSPHRASE} is worth its own case: because the passphrase encrypts the token
 * rather than being compared against a stored copy of itself, a typo would otherwise be indistinguishable
 * from "this identity was never authorized", and the two need opposite remedies.
 */
public record VaultUnlock(PassphraseVault vault, UnlockStatus status, String reason) {

    public static VaultUnlock opened(PassphraseVault v, boolean created) {
        return new VaultUnlock(v, created ? UnlockStatus.CREATED : UnlockStatus.UNLOCKED, null);
    }

    public static VaultUnlock of(UnlockStatus s, String reason) { return new VaultUnlock(null, s, reason); }

    public boolean ok() { return vault != null; }

    public String describe() {
        return switch (status) {
            case UNLOCKED -> "vault unlocked";
            case CREATED -> "vault created";
            case WRONG_PASSPHRASE -> "wrong passphrase - the stored logins could not be decrypted";
            case DECLINED -> "no passphrase given";
            case FAILED -> "vault error: " + reason;
        };
    }
}
