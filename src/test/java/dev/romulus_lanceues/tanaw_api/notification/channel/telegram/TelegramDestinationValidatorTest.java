package dev.romulus_lanceues.tanaw_api.notification.channel.telegram;

import dev.romulus_lanceues.tanaw_api.notification.channel.InvalidDestinationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TelegramDestinationValidator SSRF Guard and Format Tests")
class TelegramDestinationValidatorTest {

    private final TelegramDestinationValidator validator = new TelegramDestinationValidator();

    @Nested
    @DisplayName("Valid Telegram Destination Chat IDs")
    class ValidChatIds {

        @ParameterizedTest
        @ValueSource(strings = {
                "123456789",
                "1",
                "987654321012345",
                "9223372036854775807",
                "12345678901234567890", // 20 digits
                "-1001234567890",
                "-1",
                "-10012345678901234567" // 20 digits with minus sign
        })
        @DisplayName("Accepts valid positive and negative numeric Telegram chat IDs")
        void acceptsValidNumericChatIds(String chatId) {
            assertThatCode(() -> validator.validate(chatId))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "@valid_channel",
                "@channel123",
                "@group_name",
                "@a1234", // exactly 5 chars after @
                "@username_with_32_characters_123", // exactly 32 chars after @
                "@TanawAlertsBot"
        })
        @DisplayName("Accepts valid public channel and group usernames starting with @")
        void acceptsValidUsernames(String username) {
            assertThatCode(() -> validator.validate(username))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Null, Blank, and Whitespace Rejection")
    class NullBlankAndWhitespaceRejection {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Rejects null, empty, or whitespace-only destinations")
        void rejectsNullOrBlank(String destination) {
            assertThatThrownBy(() -> validator.validate(destination))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("null or blank");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                " 123456789",
                "123456789 ",
                "123 456789",
                "@valid_user ",
                " @valid_user",
                "@valid user"
        })
        @DisplayName("Rejects destinations containing leading, trailing, or embedded whitespace")
        void rejectsDestinationsWithWhitespace(String destination) {
            assertThatThrownBy(() -> validator.validate(destination))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("whitespace");
        }
    }

    @Nested
    @DisplayName("SSRF and Dangerous URL Syntax Rejection")
    class SsrfAndDangerousUrlRejection {

        @ParameterizedTest
        @ValueSource(strings = {
                "https://api.telegram.org/bot123/sendMessage",
                "http://localhost:8080",
                "http://169.254.169.254/latest/meta-data",
                "ftp://evil.com/payload",
                "file:///etc/passwd",
                "gopher://attacker.com"
        })
        @DisplayName("Rejects full URLs and SSRF attack vectors")
        void rejectsUrlsAndSsrfVectors(String destination) {
            assertThatThrownBy(() -> validator.validate(destination))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("URL syntax");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "12345/67890",
                "12345?query=1",
                "12345#fragment",
                "12345&param=2",
                "-100123/456",
                "@channel/extra"
        })
        @DisplayName("Rejects dangerous URL separator and query/fragment characters")
        void rejectsDangerousUrlCharacters(String destination) {
            assertThatThrownBy(() -> validator.validate(destination))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("URL syntax");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "127.0.0.1:8080",
                "localhost:9000",
                "user:pass@host",
                "attacker.com:443"
        })
        @DisplayName("Rejects ports, IP addresses, and user-info vectors")
        void rejectsPortsAndUserInfo(String destination) {
            assertThatThrownBy(() -> validator.validate(destination))
                    .isInstanceOf(InvalidDestinationException.class);
        }
    }

    @Nested
    @DisplayName("Invalid Username Syntax")
    class InvalidUsernameSyntax {

        @ParameterizedTest
        @ValueSource(strings = {
                "@abc", // 3 chars (< 5)
                "@abcd", // 4 chars (< 5)
                "@this_is_a_very_long_username_that_exceeds_the_limit_of_thirty_two_characters_123", // 66 chars (> 32)
                "@user-name", // dash not allowed
                "@user.name", // dot not allowed
                "@user$name", // symbol not allowed
                "@user!name",
                "username_without_at" // missing @
        })
        @DisplayName("Rejects usernames violating Telegram syntax requirements")
        void rejectsInvalidUsernames(String username) {
            assertThatThrownBy(() -> validator.validate(username))
                    .isInstanceOf(InvalidDestinationException.class);
        }
    }

    @Nested
    @DisplayName("Invalid Numeric Chat IDs")
    class InvalidNumericChatIds {

        @ParameterizedTest
        @ValueSource(strings = {
                "0", // cannot be zero
                "-0", // cannot be negative zero
                "+123456789", // explicit plus not allowed
                "123456789012345678901", // 21 digits (exceeds 20)
                "-123456789012345678901", // 21 digits
                "123a456", // alphanumeric
                "-abc", // invalid signed string
                "--123456" // double minus
        })
        @DisplayName("Rejects non-numeric, zero, or oversized numeric chat IDs")
        void rejectsInvalidNumericIds(String chatId) {
            assertThatThrownBy(() -> validator.validate(chatId))
                    .isInstanceOf(InvalidDestinationException.class);
        }
    }
}
