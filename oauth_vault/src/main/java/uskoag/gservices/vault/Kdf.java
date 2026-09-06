package uskoag.gservices.vault;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Passphrase to key, and a verifier that proves a passphrase without storing one.
 *
 * <p>The iteration count is high enough that guessing from the on-disk salt is expensive and low enough
 * that unlocking at launch is imperceptible. The verifier exists so a wrong passphrase can be reported as
 * wrong rather than as an absent login.
 */
public final class Kdf {

    public static final int ITERATIONS = 210_000;
    static final String VERIFIER_PLAINTEXT = "uskoag-gservices vault v1";

    private Kdf() {}

    public static byte[] derive(char[] passphrase, byte[] salt) throws Exception {
        var spec = new PBEKeySpec(passphrase, salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally { spec.clearPassword(); }
    }

    public static byte[] salt() {
        var s = new byte[16];
        new SecureRandom().nextBytes(s);
        return s;
    }

    public static String makeVerifier(byte[] key) throws Exception {
        return Base64.getEncoder().encodeToString(seal(key, VERIFIER_PLAINTEXT.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * The GCM tag is the proof, not the plaintext. Comparing the decrypted text against a constant would
     * make the constant part of the on-disk format: change it - as happened when this moved out of
     * kk-yt_live_chats - and every existing vault reports a correct passphrase as wrong. Authenticated
     * decryption succeeding already establishes the key, whatever wrote the verifier and whatever it says.
     */
    public static boolean verifies(byte[] key, String verifier) {
        try {
            open(key, Base64.getDecoder().decode(verifier));
            return true;
        } catch (Exception e) { return false; }
    }

    /** 12-byte IV prepended to AES-256-GCM ciphertext. */
    public static byte[] seal(byte[] key, byte[] plain) throws Exception {
        var iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        var c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        var ct = c.doFinal(plain);
        var out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return out;
    }

    public static byte[] open(byte[] key, byte[] all) throws Exception {
        var c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
            new GCMParameterSpec(128, Arrays.copyOfRange(all, 0, 12)));
        return c.doFinal(Arrays.copyOfRange(all, 12, all.length));
    }

    public static String hex(byte[] b) {
        var sb = new StringBuilder(b.length * 2);
        for (var x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }
}
