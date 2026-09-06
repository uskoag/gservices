package uskoag.gservices.vault;

import java.util.List;

/** What a bundle carries once opened. {@code clientId} lets an import refuse a token from a different client. */
public record BundleContent(String account, String clientId, String created, List<BundleEntry> entries) {}
