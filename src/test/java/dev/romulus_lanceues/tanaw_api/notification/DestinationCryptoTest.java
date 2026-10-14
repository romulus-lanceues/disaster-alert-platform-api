package dev.romulus_lanceues.tanaw_api.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DestinationCrypto Unit Tests")
class DestinationCryptoTest {

    // 32-byte (256-bit) Base64 encoded test keys (44 chars with Base64 padding)
    private static final String VALID_BASE64_KEY = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";
    private static final String ANOTHER_BASE64_KEY = "YW5vdGhlci1zZWN1cmUtMjU2LWJpdC1rZXktMTIzNDU=";

    private final DestinationCrypto crypto = new DestinationCrypto(VALID_BASE64_KEY);

    @Nested
    @DisplayName("Key Initialization Tests")
    class KeyInitializationTests {

        @Test
        @DisplayName("Accepts valid 32-byte Base64 encoded key")
        void acceptsValidBase64Key() {
            DestinationCrypto instance = new DestinationCrypto(VALID_BASE64_KEY);
            assertThat(instance).isNotNull();
        }

        @Test
        @DisplayName("Rejects raw non-Base64 key")
        void rejectsRawNonBase64Key() {
            assertThatThrownBy(() -> new DestinationCrypto("not-valid-base64!@#$%^"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("valid Base64-encoded string");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "dG9vLXNob3J0", // "too-short" (9 bytes)
                "dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng==" // 40 bytes (320 bits)
        })
        @DisplayName("Rejects Base64 keys that decode to length other than 32 bytes (256 bits)")
        void rejectsKeysWithInvalidByteLengths(String invalidKey) {
            assertThatThrownBy(() -> new DestinationCrypto(invalidKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("256 bits (32 bytes)");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("Rejects null, empty, or blank keys")
        void rejectsNullOrBlankKeys(String invalidKey) {
            assertThatThrownBy(() -> new DestinationCrypto(invalidKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null or blank");
        }
    }

    @Nested
    @DisplayName("Encryption & Decryption Roundtrip Tests")
    class RoundtripTests {

        @Test
        @DisplayName("Encrypts and decrypts webhook destination successfully")
        void encryptsAndDecryptsSuccessfully() {
            String original = "https://discord.com/api/webhooks/123456789012345678/aBcDeFgHiJkLmNoPqRsTuVwXyZ";

            String encrypted = crypto.encrypt(original);
            assertThat(encrypted).isNotNull().isNotEqualTo(original);

            String decrypted = crypto.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(original);
        }

        @Test
        @DisplayName("Produces distinct ciphertexts for identical plaintexts due to random IVs")
        void producesDistinctCiphertextsDueToRandomIv() {
            String original = "https://discord.com/api/webhooks/1234567890/token";

            String encrypted1 = crypto.encrypt(original);
            String encrypted2 = crypto.encrypt(original);

            assertThat(encrypted1).isNotEqualTo(encrypted2);
            assertThat(crypto.decrypt(encrypted1)).isEqualTo(original);
            assertThat(crypto.decrypt(encrypted2)).isEqualTo(original);
        }

        @Test
        @DisplayName("Returns null when encrypting null")
        void encryptNullReturnsNull() {
            assertThat(crypto.encrypt(null)).isNull();
        }

        @Test
        @DisplayName("Returns null when decrypting null")
        void decryptNullReturnsNull() {
            assertThat(crypto.decrypt(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Tampering and Security Tests")
    class SecurityTests {

        @Test
        @DisplayName("Fails decryption if ciphertext is tampered (GCM auth tag verification failure)")
        void failsWhenCiphertextIsTampered() {
            String original = "https://discord.com/api/webhooks/1234567890/token";
            String encrypted = crypto.encrypt(original);

            byte[] decoded = Base64.getDecoder().decode(encrypted);
            // Flip the last byte (auth tag)
            decoded[decoded.length - 1] ^= 0x01;
            String tampered = Base64.getEncoder().encodeToString(decoded);

            assertThatThrownBy(() -> crypto.decrypt(tampered))
                    .isInstanceOf(CryptoException.class)
                    .hasMessageContaining("Decryption failed");
        }

        @Test
        @DisplayName("Fails decryption if IV is tampered")
        void failsWhenIvIsTampered() {
            String original = "https://discord.com/api/webhooks/1234567890/token";
            String encrypted = crypto.encrypt(original);

            byte[] decoded = Base64.getDecoder().decode(encrypted);
            // Flip the first byte (IV)
            decoded[0] ^= 0x01;
            String tampered = Base64.getEncoder().encodeToString(decoded);

            assertThatThrownBy(() -> crypto.decrypt(tampered))
                    .isInstanceOf(CryptoException.class);
        }

        @Test
        @DisplayName("Fails decryption when decrypting with a different key")
        void failsDecryptionWithWrongKey() {
            String original = "https://discord.com/api/webhooks/1234567890/token";
            String encrypted = crypto.encrypt(original);

            DestinationCrypto wrongKeyCrypto = new DestinationCrypto(ANOTHER_BASE64_KEY);

            assertThatThrownBy(() -> wrongKeyCrypto.decrypt(encrypted))
                    .isInstanceOf(CryptoException.class);
        }

        @Test
        @DisplayName("Fails decryption on malformed base64 or truncated payload")
        void failsOnTruncatedPayload() {
            byte[] tooShort = new byte[10]; // Less than 12-byte IV
            String encoded = Base64.getEncoder().encodeToString(tooShort);

            assertThatThrownBy(() -> crypto.decrypt(encoded))
                    .isInstanceOf(CryptoException.class)
                    .hasMessageContaining("truncated");

            byte[] missingFullTag = new byte[20]; // 12-byte IV + only 8 bytes (tag requires 16 bytes)
            String missingTagEncoded = Base64.getEncoder().encodeToString(missingFullTag);

            assertThatThrownBy(() -> crypto.decrypt(missingTagEncoded))
                    .isInstanceOf(CryptoException.class)
                    .hasMessageContaining("truncated");
        }

        @Test
        @DisplayName("Fails decryption on invalid Base64 input")
        void failsOnInvalidBase64() {
            assertThatThrownBy(() -> crypto.decrypt("not-valid-base64!@#$"))
                    .isInstanceOf(CryptoException.class);
        }
    }
}
