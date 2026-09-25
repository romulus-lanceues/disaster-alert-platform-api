package dev.romulus_lanceues.tanaw_api.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthCookieHelper Unit Tests")
class AuthCookieHelperTest {

    private JwtProperties jwtProperties;
    private AuthCookieHelper cookieHelper;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties(
                "tanaw-api",
                "tanaw-client",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                "dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="
        );
        cookieHelper = new AuthCookieHelper(jwtProperties);
    }

    @Nested
    @DisplayName("createRefreshTokenCookie")
    class CreateRefreshTokenCookie {

        @Test
        @DisplayName("should create cookie with all expected security attributes")
        void shouldCreateCookie_withExpectedAttributes() {
            String rawToken = "sample-raw-refresh-token";

            ResponseCookie cookie = cookieHelper.createRefreshTokenCookie(rawToken);

            assertThat(cookie.getName()).isEqualTo("refresh_token");
            assertThat(cookie.getValue()).isEqualTo(rawToken);
            assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.isSecure()).isTrue();
            assertThat(cookie.getSameSite()).isEqualTo("Strict");
            assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
        }
    }

    @Nested
    @DisplayName("clearRefreshTokenCookie")
    class ClearRefreshTokenCookie {

        @Test
        @DisplayName("should create cookie with maxAge 0 to clear client cookie")
        void shouldCreateCookie_withMaxAgeZero() {
            ResponseCookie cookie = cookieHelper.clearRefreshTokenCookie();

            assertThat(cookie.getName()).isEqualTo("refresh_token");
            assertThat(cookie.getValue()).isEmpty();
            assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.isSecure()).isTrue();
            assertThat(cookie.getSameSite()).isEqualTo("Strict");
            assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        }
    }
}
