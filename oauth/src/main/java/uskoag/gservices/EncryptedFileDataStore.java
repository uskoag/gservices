package uskoag.gservices;

import com.google.api.client.util.store.AbstractDataStore;
import com.google.api.client.util.store.DataStore;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Set;

/**
 * Thread-safe encrypted DataStore implementation that wraps another DataStore
 * and encrypts/decrypts values using AES-GCM.
 *
 * This protects OAuth tokens from infostealer malware that scans filesystem
 * for plaintext credentials. The appKey is used to derive the encryption key.
 *
 * @param <V> serializable type of the mapped value
 */
public class EncryptedFileDataStore<V extends Serializable> extends AbstractDataStore<V> {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits authentication tag

    private final DataStore<byte[]> wrappedStore;
    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom;

    /**
     * @param factory data store factory
     * @param wrappedStore underlying data store (stores encrypted bytes)
     * @param encryptionKey AES encryption key derived from appKey
     */
    protected EncryptedFileDataStore(
            EncryptedFileDataStoreFactory factory,
            DataStore<byte[]> wrappedStore,
            SecretKey encryptionKey) {
        super(factory, wrappedStore.getId());
        this.wrappedStore = wrappedStore;
        this.encryptionKey = encryptionKey;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public Set<String> keySet() throws IOException {
        return wrappedStore.keySet();
    }

    @Override
    public java.util.Collection<V> values() throws IOException {
        Set<String> keys = keySet();
        java.util.List<V> values = new java.util.ArrayList<>(keys.size());
        for (String key : keys) {
            V value = get(key);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    @Override
    public V get(String key) throws IOException {
        byte[] encryptedData = wrappedStore.get(key);
        if (encryptedData == null) {
            return null;
        }
        try {
            return deserialize(decrypt(encryptedData));
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to decrypt data for key: " + key, e);
        }
    }

    @Override
    public DataStore<V> set(String key, V value) throws IOException {
        try {
            byte[] serializedData = serialize(value);
            byte[] encryptedData = encrypt(serializedData);
            wrappedStore.set(key, encryptedData);
            return this;
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to encrypt data for key: " + key, e);
        }
    }

    @Override
    public DataStore<V> clear() throws IOException {
        wrappedStore.clear();
        return this;
    }

    @Override
    public DataStore<V> delete(String key) throws IOException {
        wrappedStore.delete(key);
        return this;
    }

    @Override
    public EncryptedFileDataStoreFactory getDataStoreFactory() {
        return (EncryptedFileDataStoreFactory) super.getDataStoreFactory();
    }

    /**
     * Encrypts data using AES-GCM with a random IV prepended to the ciphertext.
     * Format: [12-byte IV][ciphertext with authentication tag]
     */
    private byte[] encrypt(byte[] plaintext) throws GeneralSecurityException {
        // Generate random IV for each encryption (never reuse IV with same key)
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);

        byte[] ciphertext = cipher.doFinal(plaintext);

        // Prepend IV to ciphertext: [IV][ciphertext]
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

        return result;
    }

    /**
     * Decrypts data encrypted by encrypt() method.
     * Expects format: [12-byte IV][ciphertext with authentication tag]
     */
    private byte[] decrypt(byte[] encryptedData) throws GeneralSecurityException {
        if (encryptedData.length < GCM_IV_LENGTH) {
            throw new GeneralSecurityException("Invalid encrypted data: too short");
        }

        // Extract IV from beginning
        byte[] iv = Arrays.copyOfRange(encryptedData, 0, GCM_IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(encryptedData, GCM_IV_LENGTH, encryptedData.length);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);

        return cipher.doFinal(ciphertext);
    }

    /**
     * Serializes value to bytes using Java serialization.
     * Could be enhanced with custom serialization for better compatibility.
     */
    @SuppressWarnings("unchecked")
    private byte[] serialize(V value) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos)) {
            oos.writeObject(value);
        }
        return baos.toByteArray();
    }

    /**
     * Deserializes value from bytes using Java deserialization.
     */
    @SuppressWarnings("unchecked")
    private V deserialize(byte[] data) throws IOException {
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais)) {
            return (V) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to deserialize data", e);
        }
    }
}
