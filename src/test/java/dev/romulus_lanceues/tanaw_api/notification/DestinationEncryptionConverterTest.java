package dev.romulus_lanceues.tanaw_api.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@DisplayName("DestinationEncryptionConverter Unit Tests")
class DestinationEncryptionConverterTest {

    private final DestinationCrypto crypto = mock(DestinationCrypto.class);
    private final DestinationEncryptionConverter converter = new DestinationEncryptionConverter(crypto);

    @Test
    @DisplayName("Converts entity attribute (plaintext) to database column (ciphertext)")
    void convertsToDatabaseColumn() {
        String plaintext = "https://discord.com/api/webhooks/123/token";
        String ciphertext = "encrypted_base64_payload";

        given(crypto.encrypt(plaintext)).willReturn(ciphertext);

        String result = converter.convertToDatabaseColumn(plaintext);

        assertThat(result).isEqualTo(ciphertext);
        then(crypto).should().encrypt(plaintext);
    }

    @Test
    @DisplayName("Converts database column (ciphertext) to entity attribute (plaintext)")
    void convertsToEntityAttribute() {
        String ciphertext = "encrypted_base64_payload";
        String plaintext = "https://discord.com/api/webhooks/123/token";

        given(crypto.decrypt(ciphertext)).willReturn(plaintext);

        String result = converter.convertToEntityAttribute(ciphertext);

        assertThat(result).isEqualTo(plaintext);
        then(crypto).should().decrypt(ciphertext);
    }

    @Test
    @DisplayName("Handles null values gracefully")
    void handlesNullGracefully() {
        given(crypto.encrypt(null)).willReturn(null);
        given(crypto.decrypt(null)).willReturn(null);

        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
