package uskoag.gservices.vault;

/**
 * One identity's login, flattened to what another machine can rebuild a credential from.
 *
 * <p>{@code boundTo} is free text naming whatever sub-identity the token actually speaks for - a YouTube
 * channel, a send-as alias - carried so a human can verify the bundle before installing it.
 */
public record BundleEntry(String identity, String refreshToken, String accessToken, Long expiresAtMs, String boundTo) {}
