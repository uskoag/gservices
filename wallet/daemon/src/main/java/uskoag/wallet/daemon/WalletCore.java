package uskoag.wallet.daemon;

import uskoag.wallet.wire.AccessGrant;
import uskoag.wallet.wire.AccessRequest;
import uskoag.wallet.wire.AccountInfo;
import uskoag.wallet.wire.GApi;
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
        Log.info("unlocked — " + keyring.data().credentials().size() + " credential(s), "
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
        if (!gateway.interactive()) notes.add("no display — approvals will be denied rather than asked");
        return new WalletStatus("1.0", keyring.unlocked(), keyring.exists(),
                keyring.unlocked() ? keyring.data().credentials().size() : -1,
                0, proxyPort, notes);
    }

    public List<AccountInfo> accounts() {
        if (!keyring.unlocked()) return List.of();
        return keyring.data().credentials().stream()
                .map(c -> new AccountInfo(c.account, c.profile, c.clientSecret != null,
                        c.refreshToken != null, c.scopes, c.lastUsed, c.addedAt))
                .toList();
    }

    /**
     * Issues a handle for one tool run. Nothing is authorised here beyond naming the credential — every
     * individual call is still classified and policed at the proxy, where the request actually is.
     */
    public AccessGrant access(AccessRequest req) {
        if (!keyring.unlocked()) {
            gateway.unlockNeeded(req.appName() + " is asking for " + req.account());
            return AccessGrant.failed("wallet is locked — unlock it and retry");
        }
        var cred = resolve(req);
        if (cred.isEmpty()) {
            return AccessGrant.failed("no credential for account '" + req.account() + "' profile '"
                    + req.profile() + "' — add it in the wallet, or run: uskoag-walletcli login");
        }
        var api = GApi.of(req.api());
        var g = grants.issue(cred.get().account, req.profile(), req.appName(), api.alias,
                req.session(), req.pid(), req.peerCommand());
        var root = "http://127.0.0.1:" + proxyPort + "/g/" + api.alias + "/";
        return new AccessGrant(g.token(), root, cred.get().account, g.correlationCode(), 0L, null);
    }

    /**
     * An exact account plus profile, then the same account under any profile, then the sole credential
     * when there is only one. Multi-org reality is the normal case here, not an edge case.
     */
    Optional<CredentialRecord> resolve(AccessRequest req) {
        var all = keyring.data().credentials();
        if (req.account() != null && !req.account().isBlank()) {
            var exact = keyring.find(req.account(), req.profile());
            if (exact.isPresent()) return exact;
            return all.stream().filter(c -> c.account.equalsIgnoreCase(req.account())).findFirst();
        }
        var byProfile = all.stream().filter(c -> c.profile.equalsIgnoreCase(req.profile())).toList();
        if (byProfile.size() == 1) return Optional.of(byProfile.getFirst());
        return all.size() == 1 ? Optional.of(all.getFirst()) : Optional.empty();
    }
}
