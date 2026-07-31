package uskoag.wallet.daemon;

import uskoag.wallet.wire.Json;
import uskoag.wallet.wire.WalletPaths;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * The keyring on disk and in memory. Two layers, both keyed on the same typed passphrase, so there is
 * still only one thing to type: AES-GCM under a PBKDF2 key, then DPAPI with entropy so the file is
 * inert on any other machine.
 */
public final class Keyring {

    private static final byte[] MAGIC = {'U', 'K', 'W', 'K', '1'};
    private static final int FLAG_DPAPI = 0x1, SALT_LEN = 32, HEADER = 8 + SALT_LEN;

    private SecretKey master;
    private byte[] salt;
    private char[] passphrase;
    private KeyringData data;

    public boolean exists() {
        return Files.exists(WalletPaths.keyringFile());
    }

    public boolean unlocked() {
        return data != null;
    }

    public KeyringData data() {
        if (data == null) throw new IllegalStateException("wallet is locked");
        return data;
    }

    public synchronized void lock() {
        if (passphrase != null) Arrays.fill(passphrase, '\0');
        passphrase = null;
        master = null;
        data = null;
    }

    /** First run: mint a fresh keyring, including the audit's own column key. */
    public synchronized void create(char[] phrase) throws IOException {
        salt = Aes.salt();
        master = Aes.derive(phrase, salt);
        passphrase = phrase.clone();
        data = new KeyringData();
        data.auditKeyB64 = Base64.getEncoder().encodeToString(Aes.randomKey().getEncoded());
        save();
    }

    public synchronized void unlock(char[] phrase) throws IOException {
        var raw = Files.readAllBytes(WalletPaths.keyringFile());
        if (raw.length < HEADER || !Arrays.equals(Arrays.copyOf(raw, MAGIC.length), MAGIC)) {
            throw new IOException("not a wallet keyring: " + WalletPaths.keyringFile());
        }
        var flags = raw[MAGIC.length];
        salt = Arrays.copyOfRange(raw, 8, HEADER);
        var payload = Arrays.copyOfRange(raw, HEADER, raw.length);
        if ((flags & FLAG_DPAPI) != 0) payload = Dpapi.unprotect(payload, entropy(phrase, salt));

        master = Aes.derive(phrase, salt);
        var json = new String(Aes.decrypt(payload, master), StandardCharsets.UTF_8);
        data = Json.to(json, KeyringData.class);
        passphrase = phrase.clone();
        if (data.auditKeyB64 == null) {
            data.auditKeyB64 = Base64.getEncoder().encodeToString(Aes.randomKey().getEncoded());
            save();
        }
    }

    public synchronized void save() throws IOException {
        if (data == null || master == null) throw new IllegalStateException("wallet is locked");
        var plain = Json.of(data).getBytes(StandardCharsets.UTF_8);
        var payload = Aes.encrypt(plain, master);
        var flags = 0;
        if (Dpapi.available()) {
            payload = Dpapi.protect(payload, entropy(passphrase, salt));
            flags |= FLAG_DPAPI;
        }
        var out = new byte[HEADER + payload.length];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        out[MAGIC.length] = (byte) flags;
        System.arraycopy(salt, 0, out, 8, salt.length);
        System.arraycopy(payload, 0, out, HEADER, payload.length);

        Files.createDirectories(WalletPaths.home());
        var tmp = WalletPaths.keyringFile().resolveSibling("keyring.bin.tmp");
        Files.write(tmp, out);
        Files.move(tmp, WalletPaths.keyringFile(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Changing the passphrase re-derives the master key and rewrites this one file. Nothing else moves:
     * the audit's column key lives inside the keyring rather than being derived from the passphrase, so
     * a year of history stays readable across as many passphrase changes as you like.
     */
    public synchronized void changePassphrase(char[] current, char[] fresh) throws IOException {
        if (data == null) throw new IllegalStateException("unlock the wallet before changing its passphrase");
        if (!Arrays.equals(passphrase, current)) throw new IOException("current passphrase does not match");
        if (fresh == null || fresh.length < 8) throw new IOException("the new passphrase is too short");

        var previous = passphrase;
        salt = Aes.salt();
        master = Aes.derive(fresh, salt);
        passphrase = fresh.clone();
        save();
        Arrays.fill(previous, '\0');
    }

    /** The audit's column key, available only while unlocked. */
    public SecretKey auditKey() {
        return new SecretKeySpec(Base64.getDecoder().decode(data().auditKeyB64), "AES");
    }

    public Optional<CredentialRecord> find(String account, String profile) {
        return data().credentials().stream()
                .filter(c -> c.account.equalsIgnoreCase(account) && c.profile.equalsIgnoreCase(profile))
                .findFirst();
    }

    /** Bound to the passphrase and never written anywhere, which is the whole point of the parameter. */
    private static byte[] entropy(char[] phrase, byte[] salt) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(new String(phrase).getBytes(StandardCharsets.UTF_8));
            md.update("uskoag-wallet-dpapi-entropy".getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (Exception e) {
            throw new IllegalStateException("could not derive DPAPI entropy", e);
        }
    }
}
