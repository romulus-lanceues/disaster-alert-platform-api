package dev.romulus_lanceues.tanaw_api.notification.destination;

import dev.romulus_lanceues.tanaw_api.notification.NotificationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

@Component
public class DestinationCrypto {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    @Autowired
    public DestinationCrypto(NotificationProperties notificationProperties) {
        this(notificationProperties.crypto().key(), new SecureRandom());
    }

    public DestinationCrypto(String key) {
        this(key, new SecureRandom());
    }

    DestinationCrypto(String key, SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "SecureRandom must not be null");
        this.secretKey = parseSecretKey(key);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertextWithTag = cipher.doFinal(plaintextBytes);

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertextWithTag.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertextWithTag);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (GeneralSecurityException ex) {
            throw new CryptoException("Encryption failed", ex);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }

        try {
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(ciphertext);
            } catch (IllegalArgumentException ex) {
                throw new CryptoException("Malformed Base64 ciphertext", ex);
            }

            int minLength = IV_LENGTH_BYTES + (TAG_LENGTH_BITS / Byte.SIZE);
            if (decoded.length < minLength) {
                throw new CryptoException("Ciphertext is truncated or invalid; insufficient length for IV and tag");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byteBuffer.get(iv);

            byte[] encryptedBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedBytes);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new CryptoException("Decryption failed: corrupted data or invalid key", ex);
        }
    }

    private static SecretKey parseSecretKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Encryption key must not be null or blank");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Encryption key must be a valid Base64-encoded string", ex);
        }

        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "Encryption key must be exactly 256 bits (32 bytes) when Base64 decoded, but got "
                            + (keyBytes.length * 8) + " bits (" + keyBytes.length + " bytes)"
            );
        }

        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}
