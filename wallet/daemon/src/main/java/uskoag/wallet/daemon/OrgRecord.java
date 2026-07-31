package uskoag.wallet.daemon;

import java.util.ArrayList;
import java.util.List;

/**
 * One OAuth client, which in practice means one organisation.
 *
 * <p>A {@code credentials.json} is a Cloud project's client, not a tool's and not a person's. One per
 * org is not a preference, it is what Google leaves available: an unverified app hits the hundred-user
 * cap and the unverified-app screen, and a Workspace admin can refuse a foreign client ID outright
 * through App Access Control. So the client secret belongs here, once, and every account in the org
 * mints its tokens from it.
 *
 * <p>{@code domains} exists so nobody has to name the org by hand — an address ending in one of them
 * resolves to this record.
 */
public final class OrgRecord {

    String id, label, credentialsJson, clientId, clientSecret;
    List<String> domains = new ArrayList<>();
    long addedAt = System.currentTimeMillis();

    public OrgRecord() {
    }

    public OrgRecord(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public List<String> domains() {
        if (domains == null) domains = new ArrayList<>();
        return domains;
    }

    public boolean covers(String email) {
        if (email == null) return false;
        var at = email.indexOf('@');
        if (at < 0) return false;
        var domain = email.substring(at + 1).toLowerCase();
        return domains().stream().anyMatch(d -> d.equalsIgnoreCase(domain));
    }

    /** Remembers the domain of an account added under this org, so the next one needs no flag. */
    public void learn(String email) {
        if (email == null) return;
        var at = email.indexOf('@');
        if (at < 0) return;
        var domain = email.substring(at + 1).toLowerCase();
        if (!domains().contains(domain)) domains().add(domain);
    }
}
