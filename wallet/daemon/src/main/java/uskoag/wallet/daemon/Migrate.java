package uskoag.wallet.daemon;

/**
 * Carries an older keyring forward rather than refusing it.
 *
 * <p>The version guard exists because Gson will silently parse an old shape into the new one and leave
 * the moved fields unset, which looks like a working wallet and is not. Refusing is the safe default;
 * migrating explicitly is better still, and it is only better because it is explicit — each step below
 * names exactly what it fills in, so nothing is left to inference.
 */
public final class Migrate {

    private Migrate() {
    }

    /** True if the data was changed and should be written back at the new version. */
    public static boolean forward(KeyringData data) {
        var changed = false;
        if ("2".equals(data.version)) {
            two2three(data);
            data.version = KeyringData.VERSION;
            changed = true;
        }
        return changed;
    }

    /**
     * v2 held one union token per account with no group. v3 holds one token per scope group, so each
     * expires on its own. An existing union token cannot be split — its scopes were granted together in
     * one consent — so it becomes a single {@code legacy} token carrying exactly what it really has, and
     * sorts last so any properly scoped token granted later is preferred over it.
     */
    private static void two2three(KeyringData data) {
        for (var c : data.credentials()) {
            if (c.group == null || c.group.isBlank()) c.group = AccountVerbs.LEGACY;
            if (c.order == 0) c.order = 1000;
        }
        Log.info("keyring migrated 2 -> 3: " + data.credentials().size()
                + " token(s) kept as 'legacy'. Re-consent into groups when convenient.");
    }
}
