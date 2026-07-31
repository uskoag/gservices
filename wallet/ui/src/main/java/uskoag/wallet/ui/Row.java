package uskoag.wallet.ui;

import uskoag.wallet.wire.TokenInfo;

/**
 * One line of the accounts tree. {@code token} is null for org, account and placeholder rows, which is
 * what the buttons check before acting.
 */
public record Row(String kind, String account, String text, TokenInfo token) {

    @Override
    public String toString() {
        return text == null ? "" : text;
    }
}
