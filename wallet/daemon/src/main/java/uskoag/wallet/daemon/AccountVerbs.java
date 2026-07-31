package uskoag.wallet.daemon;

import uskoag.wallet.wire.Asks;
import uskoag.wallet.wire.Json;
import uskoag.wallet.wire.Profiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Orgs, accounts, consent, migration, export — the inventory, made explicit. */
public final class AccountVerbs {

    private final WalletCore core;

    public AccountVerbs(WalletCore core) {
        this.core = core;
    }

    /**
     * Uploading a credentials.json is a first-class action, from the UI or the CLI, either way — and it
     * lands on the org rather than on a tool, because that is what an OAuth client belongs to.
     */
    public String addOrg(Asks.AddOrg req) throws IOException {
        require();
        var org = core.keyring.org(req.id()).orElseGet(() -> {
            var fresh = new OrgRecord(req.id());
            core.keyring.data().orgs().add(fresh);
            return fresh;
        });
        var secrets = ClientJson.parse(req.credentialsJson());
        org.label = req.label() == null || req.label().isBlank() ? req.id() : req.label();
        org.credentialsJson = req.credentialsJson();
        org.clientId = secrets.get("client_id");
        org.clientSecret = secrets.get("client_secret");
        if (req.domains() != null) req.domains().forEach(d -> {
            if (!d.isBlank() && !org.domains().contains(d.toLowerCase())) org.domains().add(d.toLowerCase());
        });
        core.keyring.save();
        if (core.settings.backupCredentialsJson) CredentialsBackup.save(org.id, req.credentialsJson());
        return Json.of(Asks.Done.yes("org '" + org.id + "' stored"
                + (org.domains().isEmpty() ? "" : " for " + String.join(", ", org.domains()))
                + ". Now: uskoag-walletcli login <email>"));
    }

    /** The consent flow runs here and nowhere else, so a refresh token is never born in a client. */
    public String login(Asks.Login req) throws IOException {
        require();
        var existing = core.keyring.find(req.account());
        var org = core.keyring.orgFor(req.org() != null ? req.org()
                        : existing.map(CredentialRecord::orgId).orElse(null), req.account())
                .orElseThrow(() -> new IOException("cannot tell which org '" + req.account()
                        + "' belongs to. Pass --org, or add one: uskoag-walletcli org add <id>"
                        + " --file credentials.json --domains " + domainOf(req.account())));

        // Union, never replacement: widening for one tool must not revoke what another already relies on.
        var wanted = req.scopes() == null || req.scopes().isEmpty() ? Profiles.allScopes() : req.scopes();
        var union = existing.map(c -> c.unionWith(wanted)).orElse(wanted);

        var fresh = OAuthRunner.consent(req.account(), org, union, req.port() <= 0 ? 8888 : req.port());
        existing.ifPresent(core.keyring.data().credentials()::remove);
        core.keyring.data().credentials().add(fresh);
        org.learn(req.account());
        core.keyring.save();
        core.tokens.clear();
        return Json.of(Asks.Done.yes("consented: " + req.account() + " under org '" + org.id + "', "
                + union.size() + " scope(s), ready for: " + readyFor(fresh)));
    }

    /**
     * Migration in place of rotation, across every tool at once. One account's tokens live in one
     * directory per tool, so picking a single profile was the wrong shape: the default here is all of
     * them, merged into one record per email with the union of whatever each one had granted.
     */
    public String importOld(Asks.Import req) throws IOException {
        require();
        var profiles = req.profiles() == null || req.profiles().isEmpty() ? Profiles.known() : req.profiles();
        var org = core.keyring.org(req.org()).orElseGet(() -> {
            var fresh = new OrgRecord(req.org());
            fresh.label = req.org();
            core.keyring.data().orgs().add(fresh);
            return fresh;
        });

        var tokenByAccount = new LinkedHashMap<String, String>();
        var scopesByAccount = new LinkedHashMap<String, LinkedHashSet<String>>();
        var perProfile = new LinkedHashMap<String, List<String>>();
        var visited = new ArrayList<Path>();

        for (var profile : profiles) {
            var root = req.root() != null && !req.root().isBlank()
                    ? Path.of(req.root()) : Profiles.legacyRoot(profile);
            if (!visited.contains(root)) visited.add(root);
            var wanted = req.accounts() == null || req.accounts().isEmpty()
                    ? ImportOldStore.accounts(root) : req.accounts();

            var hit = new ArrayList<String>();
            for (var account : wanted) {
                var found = ImportOldStore.read(root, account, req.appKey(), Profiles.scopes(profile));
                if (found == null) continue;
                // A later token supersedes an earlier one; they are all the same account, and the newest
                // refresh token is the one Google will still honour.
                tokenByAccount.put(account, found.refreshToken());
                scopesByAccount.computeIfAbsent(account, k -> new LinkedHashSet<>()).addAll(found.scopes());
                if (org.credentialsJson == null) adoptClient(org, found.credentialsJson());
                org.learn(account);
                hit.add(account);
            }
            perProfile.put(profile, hit);
        }

        for (var e : tokenByAccount.entrySet()) {
            core.keyring.find(e.getKey()).ifPresent(core.keyring.data().credentials()::remove);
            var rec = new CredentialRecord(e.getKey(), org.id);
            rec.refreshToken = e.getValue();
            rec.scopes = List.copyOf(scopesByAccount.get(e.getKey()));
            core.keyring.data().credentials().add(rec);
        }
        core.keyring.save();
        if (req.deleteOld()) deleteOldStores(visited, tokenByAccount.keySet(), req.appKey());

        return Json.of(Map.of(
                "org", org.id,
                "imported", tokenByAccount.keySet(),
                "perProfile", perProfile,
                "rootsScanned", visited.stream().map(Path::toString).toList()));
    }

    /**
     * Two verbs on purpose. The token-only form is short-lived and safe to relay; the raw form is the
     * escape hatch, deliberately awkward and recorded — a tool that forbids its owner gets bypassed by
     * its owner, and then there is neither security nor a record.
     */
    public String export(Asks.Export req) throws IOException {
        require();
        var cred = core.keyring.find(req.account())
                .orElseThrow(() -> new IOException("no such account: " + req.account()));
        core.audit.record(new AuditEvent(System.currentTimeMillis(), "wallet", req.account(), "wallet",
                req.raw() ? "EXPORT RAW CREDENTIAL" : "export access token", uskoag.wallet.wire.Tier.DESTRUCTIVE,
                Verdict.ALLOW, 1, "cli", ProcessHandle.current().pid(), req.account(), "wallet export"));
        var org = core.keyring.org(cred.orgId)
                .orElseThrow(() -> new IOException("no OAuth client stored for org '" + cred.orgId + "'"));
        if (!req.raw()) {
            return Json.of(Map.of("accessToken", core.tokens.accessToken(cred, org), "expiresInSeconds", 3600));
        }
        Log.warn("RAW CREDENTIAL EXPORTED for " + cred.account);
        return Json.of(Map.of("account", cred.account, "org", cred.orgId, "clientId", org.clientId,
                "clientSecret", org.clientSecret, "refreshToken", cred.refreshToken, "scopes", cred.scopes()));
    }

    public String forget(Asks.Forget req) throws IOException {
        require();
        var gone = core.keyring.data().credentials().removeIf(c -> c.account.equalsIgnoreCase(req.account()));
        if (gone) {
            core.keyring.save();
            core.tokens.clear();
        }
        return Json.of(gone ? Asks.Done.yes("forgotten") : Asks.Done.no("no such account"));
    }

    private static void adoptClient(OrgRecord org, String credentialsJson) {
        try {
            var secrets = ClientJson.parse(credentialsJson);
            org.credentialsJson = credentialsJson;
            org.clientId = secrets.get("client_id");
            org.clientSecret = secrets.get("client_secret");
        } catch (IOException e) {
            Log.warn("could not read the OAuth client during import - " + e);
        }
    }

    private static String readyFor(CredentialRecord rec) {
        var ready = Profiles.known().stream().filter(p -> rec.covers(Profiles.scopes(p))).toList();
        return ready.isEmpty() ? "(no full tool scope set yet)" : String.join(", ", ready);
    }

    private static String domainOf(String email) {
        var at = email == null ? -1 : email.indexOf('@');
        return at < 0 ? "<domain>" : email.substring(at + 1);
    }

    private void require() throws IOException {
        if (!core.keyring.unlocked()) throw new IOException("wallet is locked");
    }

    private static void deleteOldStores(List<Path> roots, java.util.Set<String> accounts, String appKey) {
        var dirName = "tokens_" + Md5.hex(appKey);
        for (var root : roots) {
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
                    Log.warn("could not delete " + dir + " - " + e);
                }
            }
        }
    }
}
