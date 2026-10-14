package dev.romulus_lanceues.tanaw_api.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DiscordDestinationValidator SSRF Guard Tests")
class DiscordDestinationValidatorTest {

    private final DiscordDestinationValidator validator = new DiscordDestinationValidator();

    @Nested
    @DisplayName("Valid Webhook URLs")
    class ValidWebhookUrls {

        @ParameterizedTest
        @ValueSource(strings = {
                "https://discord.com/api/webhooks/123456789012345678/token_ABC-123",
                "https://discordapp.com/api/webhooks/987654321/secret-token_xyz",
                "https://ptb.discord.com/api/webhooks/112233445566/my_valid_token",
                "https://canary.discord.com/api/webhooks/998877665544/canary_token",
                "https://ptb.discordapp.com/api/webhooks/1234567890/token",
                "https://canary.discordapp.com/api/webhooks/1234567890/token"
        })
        @DisplayName("Accepts valid Discord webhook URLs on all allowed hosts")
        void acceptsValidUrls(String url) {
            assertThatCode(() -> validator.validate(url))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("SSRF and Protocol Security Checks")
    class SsrfAndProtocolSecurityChecks {

        @Test
        @DisplayName("Rejects non-HTTPS schemes (e.g. http://)")
        void rejectsHttpScheme() {
            assertThatThrownBy(() -> validator.validate("http://discord.com/api/webhooks/123/token"))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("HTTPS");
        }

        @Test
        @DisplayName("Rejects dangerous pseudo schemes (file, ftp, gopher)")
        void rejectsNonHttpSchemes() {
            assertThatThrownBy(() -> validator.validate("file:///etc/passwd"))
                    .isInstanceOf(InvalidDestinationException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://attacker.com/api/webhooks/123/token",
                "https://localhost/api/webhooks/123/token",
                "https://127.0.0.1/api/webhooks/123/token",
                "https://169.254.169.254/latest/meta-data/",
                "https://internal-service.local/api/webhooks/123/token",
                "https://discord.com.attacker.com/api/webhooks/123/token",
                "https://attacker-discord.com/api/webhooks/123/token",
                "https://fake-ptb.discord.com.evil.io/api/webhooks/123/token"
        })
        @DisplayName("Rejects unallowed hosts preventing SSRF attacks")
        void rejectsDisallowedHosts(String url) {
            assertThatThrownBy(() -> validator.validate(url))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("host");
        }

        @Test
        @DisplayName("Rejects user info embedded in URI")
        void rejectsUserInfoInUri() {
            assertThatThrownBy(() -> validator.validate("https://user:pass@discord.com/api/webhooks/123/token"))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("user-info");
        }

        @Test
        @DisplayName("Rejects non-standard ports (e.g. 8443 or 8080)")
        void rejectsNonStandardPorts() {
            assertThatThrownBy(() -> validator.validate("https://discord.com:8443/api/webhooks/123/token"))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("port");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://discord.com/api/webhooks/123/token?wait=true",
                "https://discord.com/api/webhooks/123/token?thread_id=456",
                "https://discordapp.com/api/webhooks/123/token?foo=bar&baz=qux"
        })
        @DisplayName("Rejects query parameters in webhook URL")
        void rejectsQueryParameters(String url) {
            assertThatThrownBy(() -> validator.validate(url))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("query parameters");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://discord.com/api/webhooks/123/token#section",
                "https://discord.com/api/webhooks/123/token#ref"
        })
        @DisplayName("Rejects URL fragments in webhook URL")
        void rejectsFragments(String url) {
            assertThatThrownBy(() -> validator.validate(url))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("fragments");
        }
    }

    @Nested
    @DisplayName("Path and Structure Validation")
    class PathAndStructureValidation {

        @ParameterizedTest
        @ValueSource(strings = {
                "https://discord.com/",
                "https://discord.com/api/v10/users/@me",
                "https://discord.com/oauth2/authorize",
                "https://discord.com/api/webhooks",
                "https://discord.com/api/webhooks/",
                "https://discord.com/api/webhooks/not-numeric/token",
                "https://discord.com/api/webhooks/12345/",
                "https://discord.com/api/webhooks/12345/token/extra/path",
                "https://discord.com/api/webhooks/12345/token with spaces"
        })
        @DisplayName("Rejects paths that do not strictly match /api/webhooks/{numeric_id}/{token}")
        void rejectsInvalidPaths(String url) {
            assertThatThrownBy(() -> validator.validate(url))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("path");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "not-a-valid-uri:::///"})
        @DisplayName("Rejects null, empty, blank or malformed URLs")
        void rejectsNullEmptyOrMalformed(String url) {
            assertThatThrownBy(() -> validator.validate(url))
                    .isInstanceOf(InvalidDestinationException.class);
        }
    }
}
