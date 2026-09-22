package dev.romulus_lanceues.tanaw_api.shared.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PasswordEncoder Unit Tests")
class PasswordEncoderTest {

    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        SecurityConfig securityConfig = new SecurityConfig();
        passwordEncoder = securityConfig.passwordEncoder();
    }

    @Test
    @DisplayName("shouldReturnTrue_whenRawPasswordMatchesEncodedHash")
    void shouldReturnTrue_whenRawPasswordMatchesEncodedHash() {

        String rawPassword = "P@ssword123!";

        String encodedHash = passwordEncoder.encode(rawPassword);
        boolean matches = passwordEncoder.matches(rawPassword, encodedHash);

        assertThat(matches).isTrue();
    }

    @Test
    @DisplayName("shouldProduceDifferentValueFromRaw_whenPasswordIsEncoded")
    void shouldProduceDifferentValueFromRaw_whenPasswordIsEncoded() {

        String rawPassword = "P@ssword123!";

        String encodedHash = passwordEncoder.encode(rawPassword);

        assertThat(encodedHash)
                .isNotBlank()
                .isNotEqualTo(rawPassword)
                .startsWith("$argon2id$");
    }

    @Test
    @DisplayName("shouldReturnFalse_whenRawPasswordDoesNotMatchEncodedHash")
    void shouldReturnFalse_whenRawPasswordDoesNotMatchEncodedHash() {

        String rawPassword = "P@ssword123!";
        String wrongPassword = "WrongPassword123!";

        String encodedHash = passwordEncoder.encode(rawPassword);
        boolean matches = passwordEncoder.matches(wrongPassword, encodedHash);

        assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("shouldGenerateDifferentHashes_whenSamePasswordIsEncodedMultipleTimes")
    void shouldGenerateDifferentHashes_whenSamePasswordIsEncodedMultipleTimes() {

        String rawPassword = "P@ssword123!";

        String firstHash = passwordEncoder.encode(rawPassword);
        String secondHash = passwordEncoder.encode(rawPassword);

        assertThat(firstHash).isNotEqualTo(secondHash);
        assertThat(passwordEncoder.matches(rawPassword, firstHash)).isTrue();
        assertThat(passwordEncoder.matches(rawPassword, secondHash)).isTrue();
    }
}
