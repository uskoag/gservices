package uskoag.wallet.daemon;

import uskoag.wallet.wire.AccessGrant;
import uskoag.wallet.wire.AccessRequest;
import uskoag.wallet.wire.AccountInfo;
import uskoag.wallet.wire.GApi;
import uskoag.wallet.wire.OrgInfo;
import uskoag.wallet.wire.Profiles;
import uskoag.wallet.wire.WalletStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Everything the wallet is, assembled: keyring, policy, tokens, grants, audit. No UI, no transport. */
public final class WalletCore {

    public final Keyring keyring = new Keyring();
    public final WalletSettings settings = WalletSettings.load();
    public final PolicyEngine policy = new PolicyEngine(keyring, settings);
    public final TokenCache tokens = new TokenCache();
    public final Grants grants = new Grants();
    public final Audit audit = new Audit();

    private ApprovalGateway gateway = new HeadlessGateway();
    private volatile int proxyPort;

    public void gateway(ApprovalGateway g) {
        this.gateway = g;
    }

    public ApprovalGateway gateway() {
        return gateway;
    }

    public void proxyPort(int port) {
        this.proxyPort = port;
    }

    public int proxyPortValue() {
        return proxyPort;
    }

    public void unlock(char[] passphrase) throws IOException {
        if (keyring.exists()) keyring.unlock(passphrase);
        else keyring.create(passphrase);
        audit.open(keyring.auditKey());
        Log.info("unlocked: " + keyring.data().orgs().size() + " org(s), "
                + keyring.data().credentials().size() + " account(s), "
                + keyring.data().rules().size() + " rule(s)");
    }

    /** Total, not gradual: every live handle dies here, so anything in flight stops mid-call. */
    public void lock() {
        grants.clear();
        tokens.clear();
        audit.close();
        keyring.lock();
        Log.info("locked");
    }

    public WalletStatus status() {
        var notes = new ArrayList<String>();
        if (!Dpapi.available()) notes.add("DPAPI unavailable, keyring is passphrase-only: " + Dpapi.unavailableReason());
        if (!gateway.interactive()) notes.add("no display, approvals will be denied rather than asked");
        return new WalletStatus("1.0", keyring.unlocked(), keyring.exists(),
                keyring.unlocked() ? keyring.data().credentials().size() : -1,
                0, proxyPort, notes);
    }

    public List<OrgInfo> orgs() {
        if (!keyring.unlocked()) return List.of();
        return keyring.data().orgs().stream()
                .map(o -> new OrgInfo(o.id, o.label, o.clientId, o.domains(),
                        (int) keyring.data().credentials().stream()
                                .filter(c -> o.id.equalsIgnoreCase(c.orgId)).count(),
                        o.addedAt))
                .toList();
    }

    public List<AccountInfo> accounts() {
        if (!keyring.unlocked()) return List.of();
        return keyring.data().credentials().stream()
                .map(c -> new AccountInfo(c.account, c.orgId, c.refreshToken != null, c.scopes(),
                        Profiles.known().stream().filter(p -> c.covers(Profiles.scopes(p))).toList(),
                        c.lastUsed, c.addedAt))
                .toList();
    }

    /**
     * Issues a handle for one tool run. Nothing is authorised here beyond naming the credential — every
     * individual call is still classified and policed at the proxy, where the request actually is.
     */
    public AccessGrant access(AccessRequest req) {
        if (!keyring.unlocked()) {
            gateway.unlockNeeded(req.appName() + " is asking for " + req.account());
            return AccessGrant.failed("wallet is locked, unlock it and retry");
        }
        var found = resolve(req.account());
        if (found.isEmpty()) return AccessGrant.failed(noAccount(req.account()));

        var cred = found.get();
        if (!cred.covers(req.scopes()) && !widen(cred, req.scopes())) {
            return AccessGrant.failed(cred.account + " has not consented to what " + req.appName()
                    + " needs (" + String.join(", ", cred.missing(req.scopes())) + "). Run: uskoag-walletcli login "
                    + cred.account);
        }

        var api = GApi.of(req.api());
        var g = grants.issue(cred.account, req.profile(), req.appName(), api.alias,
                req.session(), req.pid(), req.peerCommand());
        return new AccessGrant(g.token(), "http://127.0.0.1:" + proxyPort + "/g/" + api.alias + "/",
                cred.account, g.correlationCode(), 0L, null);
    }

    /**
     * A tool asking for scopes this account never consented to is the failure the old store hid: it
     * keyed tokens by app-key and never by scopes, so a widened request silently reused the narrow
     * token and died later on an opaque 403. Here it is visible, and fixable in one consent.
     */
    private boolean widen(CredentialRecord cred, List<String> wanted) {
        var missing = cred.missing(wanted);
        if (!gateway.interactive()) {
            Log.warn(cred.account + " is missing scopes " + missing + " and there is no display to ask on");
            return false;
        }
        if (!gateway.consentNeeded(cred.account, missing)) return false;
        try {
            var org = keyring.org(cred.orgId).orElseThrow(() ->
                    new IOException("no OAuth client stored for org '" + cred.orgId + "'"));
            var fresh = OAuthRunner.consent(cred.account, org, cred.unionWith(wanted), 8888);
            cred.refreshToken = fresh.refreshToken;
            cred.scopes = fresh.scopes;
            keyring.save();
            tokens.clear();
            Log.info("widened consent for " + cred.account + " to " + cred.scopes().size() + " scope(s)");
            return true;
        } catch (Exception e) {
            Log.error("could not widen consent for " + cred.account, e);
            return false;
        }
    }

    /**
     * The named account, else the only one there is. Email is the token's real identity, but it is not
     * something anyone should have to type when there is no ambiguity.
     */
    Optional<CredentialRecord> resolve(String account) {
        var all = keyring.data().credentials();
        if (account != null && !account.isBlank()) return keyring.find(account);
        return all.size() == 1 ? Optional.of(all.getFirst()) : Optional.empty();
    }

    private String noAccount(String asked) {
        var all = keyring.data().credentials();
        if (asked != null && !asked.isBlank()) {
            return "no credential for '" + asked + "'. Add its org and log in: uskoag-walletcli org add"
                    + " <org> --file credentials.json, then uskoag-walletcli login " + asked;
        }
        if (all.isEmpty()) return "the wallet has no accounts yet. Start with: uskoag-walletcli org add"
                + " <org> --file credentials.json";
        return "several accounts are stored, so the tool has to say which: pass --email. Known: "
                + all.stream().map(c -> c.account).toList();
    }
}
