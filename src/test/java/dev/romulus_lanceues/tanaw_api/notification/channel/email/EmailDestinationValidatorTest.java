package dev.romulus_lanceues.tanaw_api.notification.channel.email;

import dev.romulus_lanceues.tanaw_api.notification.channel.InvalidDestinationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EmailDestinationValidator Unit Tests")
class EmailDestinationValidatorTest {

    private EmailDestinationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EmailDestinationValidator();
    }

    @Nested
    @DisplayName("Valid Email Destinations")
    class ValidEmailDestinations {

        @ParameterizedTest
        @ValueSource(strings = {
                "user@example.com",
                "first.last@example.com",
                "user+alert@example.com",
                "user+earthquake-alert@sub.domain.org",
                "user123@domain.co.uk",
                "alerts@phivolcs.dost.gov.ph",
                "disaster-response@agency.gov",
                "emergency_contact@crisis-management.net",
                "user.name+tag123@mail.server.example.com"
        })
        @DisplayName("valid email addresses pass validation")
        void validEmailsPass(String validEmail) {
            assertThatCode(() -> validator.validate(validEmail))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("exact 64-char local-part is accepted when total length <= 254")
        void exact64CharLocalPartIsAccepted() {
            String localPart = "a".repeat(64);
            String email = localPart + "@example.com";
            assertThatCode(() -> validator.validate(email))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("exact 254-char total email length is accepted")
        void exact254CharTotalEmailIsAccepted() {
            // 64 chars local-part + '@' (1) + domain (189) = 254
            String localPart = "a".repeat(64);
            String domain = "b".repeat(60) + "." + "c".repeat(60) + "." + "d".repeat(62) + ".com";
            String email = localPart + "@" + domain;
            // 64 + 1 + 60 + 1 + 60 + 1 + 62 + 4 = 253 chars
            assertThatCode(() -> validator.validate(email))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Null, Blank, and Whitespace Handling")
    class NullBlankAndWhitespaceHandling {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("null or blank email destination throws InvalidDestinationException")
        void nullOrBlankEmailThrows(String blankEmail) {
            assertThatThrownBy(() -> validator.validate(blankEmail))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("Email destination must not be null or blank");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                " user@example.com",
                "user@example.com ",
                "user @example.com",
                "user@ example.com",
                "user@ex ample.com",
                "user\t@example.com"
        })
        @DisplayName("email with leading, trailing, or embedded whitespace throws InvalidDestinationException")
        void whitespaceInEmailThrows(String emailWithWhitespace) {
            assertThatThrownBy(() -> validator.validate(emailWithWhitespace))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("Email destination must not contain whitespace");
        }
    }

    @Nested
    @DisplayName("CRLF and Control Character Injection Prevention")
    class CrlfAndControlCharacterInjection {

        @ParameterizedTest
        @ValueSource(strings = {
                "user\r\n@example.com",
                "user\r@example.com",
                "user\n@example.com",
                "user@example.com\r\nBcc:victim@target.com",
                "user@example.com\nTo:injected@target.com",
                "user@example.com\rSubject:Injected",
                "user\0@example.com",
                "user\u001F@example.com",
                "user\u007F@example.com"
        })
        @DisplayName("CRLF, null bytes, and control characters throw InvalidDestinationException")
        void crlfAndControlCharsThrow(String maliciousEmail) {
            assertThatThrownBy(() -> validator.validate(maliciousEmail))
                    .isInstanceOf(InvalidDestinationException.class);
        }
    }

    @Nested
    @DisplayName("Structural and Syntax Validation")
    class StructuralAndSyntaxValidation {

        @ParameterizedTest
        @ValueSource(strings = {
                "plainaddress",
                "@missing-local.com",
                "missing-domain@",
                "missing-domain@.com",
                "user@@example.com",
                "user@domain@example.com",
                ".leadingdot@example.com",
                "trailingdot.@example.com",
                "consecutive..dots@example.com",
                "user@.leadingdotdomain.com",
                "user@trailingdotdomain.com.",
                "user@consecutive..dotsdomain.com",
                "user@localhost",
                "user@nodotdomain",
                "user@domain..com",
                "user@domain.c",
                "user@domain.123",
                "user@[127.0.0.1]"
        })
        @DisplayName("structurally invalid emails throw InvalidDestinationException")
        void structurallyInvalidEmailsThrow(String invalidEmail) {
            assertThatThrownBy(() -> validator.validate(invalidEmail))
                    .isInstanceOf(InvalidDestinationException.class);
        }
    }

    @Nested
    @DisplayName("Length Limits")
    class LengthLimits {

        @Test
        @DisplayName("local-part exceeding 64 characters throws InvalidDestinationException")
        void localPartExceeding64CharsThrows() {
            String excessiveLocalPart = "a".repeat(65);
            String email = excessiveLocalPart + "@example.com";

            assertThatThrownBy(() -> validator.validate(email))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("local-part exceeds maximum length of 64");
        }

        @Test
        @DisplayName("total email exceeding 254 characters throws InvalidDestinationException")
        void totalLengthExceeding254CharsThrows() {
            String domain = "b".repeat(60) + "." + "c".repeat(60) + "." + "d".repeat(60) + "." + "e".repeat(63) + ".com";
            String email = "user@" + domain;

            assertThatThrownBy(() -> validator.validate(email))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("exceeds maximum length of 254");
        }
    }
}
