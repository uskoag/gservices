package uskoag.wallet.wire;

import java.util.List;

/** One organisation's OAuth client, as seen from outside the wallet. The secret never appears here. */
public record OrgInfo(String id, String label, String clientId, List<String> domains, int accounts, long addedAt) {
}
