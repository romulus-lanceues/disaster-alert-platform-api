package dev.romulus_lanceues.tanaw_api.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DestinationMasker Unit Tests")
class DestinationMaskerTest {

    private final DestinationMasker masker = new DestinationMasker();

    @Nested
    @DisplayName("Discord Masking Tests")
    class DiscordMaskingTests {

        @Test
        @DisplayName("Masks Discord webhook URL, showing only channel type and last 4 chars of token")
        void masksDiscordWebhook() {
            String webhook = "https://discord.com/api/webhooks/1234567890/tokenABC1234";
            String hint = masker.mask(NotificationChannel.DISCORD, webhook);

            assertThat(hint).isEqualTo("Discord webhook ...1234");
        }

        @Test
        @DisplayName("Handles short token safely")
        void handlesShortDiscordToken() {
            String webhook = "https://discord.com/api/webhooks/1234567890/abc";
            String hint = masker.mask(NotificationChannel.DISCORD, webhook);

            assertThat(hint).isEqualTo("Discord webhook ...abc");
        }

        @Test
        @DisplayName("Handles malformed Discord webhook gracefully without crashing")
        void handlesMalformedDiscordWebhookGracefully() {
            String raw = "not-a-webhook";
            String hint = masker.mask(NotificationChannel.DISCORD, raw);

            assertThat(hint).isEqualTo("Discord webhook ...hook");
        }
    }

    @Nested
    @DisplayName("Email Masking Tests")
    class EmailMaskingTests {

        @Test
        @DisplayName("Masks email username preserving first letter and domain")
        void masksStandardEmail() {
            String email = "john.doe@example.com";
            String hint = masker.mask(NotificationChannel.EMAIL, email);

            assertThat(hint).isEqualTo("j***@example.com");
        }

        @Test
        @DisplayName("Masks single letter email username")
        void masksSingleLetterEmail() {
            String email = "a@domain.org";
            String hint = masker.mask(NotificationChannel.EMAIL, email);

            assertThat(hint).isEqualTo("a***@domain.org");
        }

        @Test
        @DisplayName("Handles email without @ symbol gracefully")
        void handlesInvalidEmail() {
            String email = "invalid-email";
            String hint = masker.mask(NotificationChannel.EMAIL, email);

            assertThat(hint).isEqualTo("i***");
        }
    }

    @Nested
    @DisplayName("Telegram Masking Tests")
    class TelegramMaskingTests {

        @Test
        @DisplayName("Preserves Telegram handle starting with @")
        void preservesTelegramHandle() {
            String telegram = "@tanaw_alerts";
            String hint = masker.mask(NotificationChannel.TELEGRAM, telegram);

            assertThat(hint).isEqualTo("@tanaw_alerts");
        }

        @Test
        @DisplayName("Masks numeric Telegram chat ID showing prefix and suffix")
        void masksNumericChatId() {
            String chatId = "9876543210";
            String hint = masker.mask(NotificationChannel.TELEGRAM, chatId);

            assertThat(hint).isEqualTo("Telegram chat ...3210");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("Returns empty string for null or blank destinations")
        void handlesNullOrBlank(String destination) {
            assertThat(masker.mask(NotificationChannel.DISCORD, destination)).isEmpty();
            assertThat(masker.mask(null, destination)).isEmpty();
        }
    }
}
