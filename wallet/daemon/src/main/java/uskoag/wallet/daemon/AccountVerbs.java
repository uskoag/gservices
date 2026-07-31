package uskoag.wallet.daemon;

import uskoag.wallet.wire.Asks;
import uskoag.wallet.wire.Json;
import uskoag.wallet.wire.Profiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Adding, consenting, importing, exporting and forgetting accounts — the inventory, made explicit. */
public final class AccountVerbs {

    private final WalletCore core;

    public AccountVerbs(WalletCore core) {
        this.core = core;
    }

    /** Uploading a credentials.json is a first-class action, from the UI or the CLI, either way. */
    public String add(Asks.AddCredentials req) throws IOException {
        require();
        var rec = core.keyring.find(req.account(), req.profile())
                .orElseGet(() -> {
                    var fresh = new CredentialRecord(req.account(), req.profile());
                    core.keyring.data().credentials().add(fresh);
                    return fresh;
                });
        var secrets = ClientJson.parse(req.credentialsJson());
        rec.credentialsJson = req.credentialsJson();
        rec.clientId = secrets.get("client_id");
        rec.clientSecret = secrets.get("client_secret");
        core.keyring.save();
        if (core.settings.backupCredentialsJson) {
            CredentialsBackup.save(req.account(), req.profile(), req.credentialsJson());
        }
        return Json.of(Asks.Done.yes("credentials.json stored for " + req.account() + " / " + req.profile()
                + (rec.refreshToken == null ? " — now run login" : "")));
    }

    /** The consent flow runs here and nowhere else, so a refresh token is never born in a client. */
    public String login(Asks.Login req) throws IOException {
        require();
        var existing = core.keyring.find(req.account(), req.profile());
        var credentialsJson = existing.map(c -> c.credentialsJson).orElse(null);
        if (credentialsJson == null) {
            return Json.of(Asks.Done.no("no credentials.json for " + req.account() + " / " + req.profile()
                    + " — upload one first"));
        }
        var scopes = req.scopes() == null || req.scopes().isEmpty() ? Profiles.scopes(req.profile()) : req.scopes();
        var fresh = OAuthRunner.consent(req.account(), req.profile(), credentialsJson, scopes,
                req.port() <= 0 ? 8888 : req.port());
        existing.ifPresent(c -> core.keyring.data().credentials().remove(c));
        core.keyring.data().credentials().add(fresh);
        core.keyring.save();
        core.tokens.clear();
        return Json.of(Asks.Done.yes("consented: " + req.account() + " / " + req.profile()));
    }

    /**
     * Migration in place of rotation. Once the old stores are gone the app-keys that leaked into
     * transcripts protect nothing, and no account has to consent again.
     */
    public String importOld(Asks.Import req) throws IOException {
        require();
        var root = req.root() != null && !req.root().isBlank()
                ? Path.of(req.root()) : Profiles.legacyRoot(req.profile());
        var scopes = req.scopes() == null || req.scopes().isEmpty() ? Profiles.scopes(req.profile()) : req.scopes();
        var wanted = req.accounts() == null || req.accounts().isEmpty()
                ? ImportOldStore.accounts(root) : req.accounts();

        var taken = new ArrayList<String>();
        var skipped = new ArrayList<String>();
        for (var account : wanted) {
            try {
                var rec = ImportOldStore.read(root, account, req.appKey(), req.profile(), scopes);
                if (rec == null) {
                    skipped.add(account + " (no token for that app-key)");
                    continue;
                }
                core.keyring.find(account, req.profile()).ifPresent(core.keyring.data().credentials()::remove);
                core.keyring.data().credentials().add(rec);
                if (core.settings.backupCredentialsJson) {
                    CredentialsBackup.save(account, req.profile(), rec.credentialsJson);
                }
                taken.add(account);
            } catch (Exception e) {
                skipped.add(account + " (" + e.getMessage() + ")");
            }
        }
        core.keyring.save();
        if (req.deleteOld()) deleteOldStores(root, taken, req.appKey());
        return Json.of(Map.of("imported", taken, "skipped", skipped, "root", root.toString()));
    }

    /**
     * Two verbs on purpose. The token-only form is short-lived and safe to relay; the raw form is the
     * escape hatch, deliberately awkward and recorded — a tool that forbids its owner gets bypassed by
     * its owner, and then there is neither security nor a record.
     */
    public String export(Asks.Export req) throws IOException {
        require();
        var cred = core.keyring.find(req.account(), req.profile())
                .orElseThrow(() -> new IOException("no such credential: " + req.account() + " / " + req.profile()));
        core.audit.record(new AuditEvent(System.currentTimeMillis(), req.profile(), req.account(), "wallet",
                req.raw() ? "EXPORT RAW CREDENTIAL" : "export access token", uskoag.wallet.wire.Tier.DESTRUCTIVE,
                Verdict.ALLOW, 1, "cli", ProcessHandle.current().pid(), req.account(), "wallet export"));
        if (!req.raw()) {
            return Json.of(Map.of("accessToken", core.tokens.accessToken(cred), "expiresInSeconds", 3600));
        }
        Log.warn("RAW CREDENTIAL EXPORTED for " + cred.key());
        return Json.of(Map.of("account", cred.account, "profile", cred.profile, "clientId", cred.clientId,
                "clientSecret", cred.clientSecret, "refreshToken", cred.refreshToken, "scopes", cred.scopes));
    }

    public String forget(Asks.Forget req) throws IOException {
        require();
        var gone = core.keyring.data().credentials()
                .removeIf(c -> c.account.equalsIgnoreCase(req.account()) && c.profile.equalsIgnoreCase(req.profile()));
        if (gone) {
            core.keyring.save();
            core.tokens.clear();
        }
        return Json.of(gone ? Asks.Done.yes("forgotten") : Asks.Done.no("no such credential"));
    }

    private void require() throws IOException {
        if (!core.keyring.unlocked()) throw new IOException("wallet is locked");
    }

    private static void deleteOldStores(Path root, List<String> accounts, String appKey) {
        var dirName = "tokens_" + Md5.hex(appKey);
        for (var account : accounts) {
            var dir = root.resolve(account).resolve(dirName);
            if (!Files.isDirectory(dir)) continue;
            try (var paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException ignored) {
                    }
                });
                Log.info("deleted old token store " + dir);
            } catch (IOException e) {
                Log.warn("could not delete " + dir + " — " + e);
            }
        }
    }
}
