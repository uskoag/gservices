package uskoag.wallet.cli;

import uskoag.wallet.wire.Asks;
import uskoag.wallet.wire.Profiles;
import uskoag.wallet.wire.WalletClient;

import java.nio.file.Files;
import java.nio.file.Path;

/** Adding, consenting, migrating and exporting accounts from a terminal. */
public final class AccountCommands {

    private AccountCommands() {
    }

    public static int add(WalletClient client, Args a) throws Exception {
        var email = a.at(1);
        var file = a.get("file", null);
        if (email == null || file == null) {
            System.err.println("usage: uskoag-walletcli add <email> --profile <p> --file <credentials.json>");
            return 1;
        }
        var json = Files.readString(Path.of(file));
        return WalletCli.out(client.callRaw("addcredentials",
                new Asks.AddCredentials(email, WalletCli.profileOf(a), json)));
    }

    public static int login(WalletClient client, Args a) throws Exception {
        var email = a.at(1);
        if (email == null) {
            System.err.println("usage: uskoag-walletcli login <email> --profile <p>");
            return 1;
        }
        var profile = WalletCli.profileOf(a);
        System.err.println("A browser window will open for consent. The wallet keeps what comes back.");
        return WalletCli.out(client.callRaw("login",
                new Asks.Login(email, profile, Profiles.scopes(profile), a.num("port", 8888))));
    }

    /**
     * The migration that replaces rotation: one old app-key, typed once and never passed as a flag,
     * moves every account that key can decrypt into the keyring without a single re-consent.
     */
    public static int importOld(WalletClient client, Args a) throws Exception {
        var profile = WalletCli.profileOf(a);
        var appKey = a.secret("old app-key to import with");
        if (appKey == null) {
            System.err.println("No console available. The old app-key is a secret and is never taken as a flag.");
            return 3;
        }
        var root = a.get("root", Profiles.legacyRoot(profile).toString());
        System.err.println("importing from " + root);
        var reply = client.callRaw("import", new Asks.Import(root, appKey, profile,
                Profiles.scopes(profile), a.list("accounts"), a.has("delete-old")));
        System.out.println(reply);
        if (a.has("delete-old")) {
            System.err.println("Old token stores deleted. That app-key now decrypts nothing,"
                    + " so wherever it has leaked it protects nothing.");
        } else {
            System.err.println("Old token stores kept. Re-run with --delete-old once you have verified"
                    + " the accounts work, and the leaked app-key stops mattering.");
        }
        return 0;
    }

    public static int export(WalletClient client, Args a) throws Exception {
        var email = a.at(1);
        if (email == null) {
            System.err.println("usage: uskoag-walletcli export <email> --profile <p> [--raw]");
            return 1;
        }
        if (a.has("raw")) {
            System.err.println("EXPORTING A RAW CREDENTIAL — refresh token and client secret."
                    + " This is recorded in the audit.");
        }
        return WalletCli.out(client.callRaw("export",
                new Asks.Export(email, WalletCli.profileOf(a), a.has("raw"))));
    }

    public static int forget(WalletClient client, Args a) throws Exception {
        var email = a.at(1);
        if (email == null) {
            System.err.println("usage: uskoag-walletcli forget <email> --profile <p>");
            return 1;
        }
        return WalletCli.out(client.callRaw("forget", new Asks.Forget(email, WalletCli.profileOf(a))));
    }
}
