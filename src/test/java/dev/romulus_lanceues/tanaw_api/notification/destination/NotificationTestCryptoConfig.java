package dev.romulus_lanceues.tanaw_api.notification.destination;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class NotificationTestCryptoConfig {

    public static final String TEST_AES_KEY = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    @Bean
    public DestinationCrypto destinationCrypto() {
        return new DestinationCrypto(TEST_AES_KEY);
    }

    @Bean
    public DestinationEncryptionConverter destinationEncryptionConverter(DestinationCrypto crypto) {
        return new DestinationEncryptionConverter(crypto);
    }
}
