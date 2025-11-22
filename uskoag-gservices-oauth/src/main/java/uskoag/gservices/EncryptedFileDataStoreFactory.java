package uskoag.gservices;

import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.FileDataStoreFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Thread-safe encrypted file data store factory that creates {@link EncryptedFileDataStore} instances.
 *
 * This factory wraps {@link FileDataStoreFactory} and adds transparent AES-GCM encryption
 * to protect OAuth tokens from infostealer malware that scans filesystem for credentials.
 *
 * The appKey is used to derive a 256-bit AES key via SHA-256 hashing.
 *
 * <p>Usage example:</p>
 * <pre>
 * var factory = new EncryptedFileDataStoreFactory(tokensDir, "MyApp-UniqueKey-v1.0");
 * var flow = new GoogleAuthorizationCodeFlow.Builder(...)
 *     .setDataStoreFactory(factory)
 *     .build();
 * </pre>
 */
public class EncryptedFileDataStoreFactory extends AbstractDataStoreFactory {

    private final FileDataStoreFactory wrappedFactory;
    private final SecretKey encryptionKey;

    /**
     * @param dataDirectory data directory for storing encrypted token files
     * @param appKey application-specific key used for encryption (should be unique per app)
     * @throws IOException if unable to create or access the data directory
     */
    public EncryptedFileDataStoreFactory(File dataDirectory, String appKey) throws IOException {
        this.wrappedFactory = new FileDataStoreFactory(dataDirectory);
        this.encryptionKey = deriveEncryptionKey(appKey);
    }

    /**
     * Derives a 256-bit AES encryption key from the appKey using SHA-256.
     *
     * This ensures:
     * - Consistent key derivation (same appKey = same key)
     * - Proper key size for AES-256
     * - One-way transformation (cannot reverse to get appKey from key)
     */
    private static SecretKey deriveEncryptionKey(String appKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(appKey.getBytes(UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by Java spec, should never happen
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    protected <V extends Serializable> DataStore<V> createDataStore(String id) throws IOException {
        // Create underlying byte[] DataStore from wrapped factory
        // The EncryptedFileDataStore will handle serialization and encryption
        DataStore<byte[]> wrappedStore = wrappedFactory.getDataStore(id);

        // Wrap it with encryption layer
        return new EncryptedFileDataStore<>(this, wrappedStore, encryptionKey);
    }

    /**
     * Returns the data directory where encrypted files are stored.
     */
    public File getDataDirectory() {
        return wrappedFactory.getDataDirectory();
    }
}
