package dev.romulus_lanceues.tanaw_api.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TestJwtFactory Unit Tests")
class TestJwtFactoryTest {

    private TestJwtFactory factory;
    private JwtDecoder decoder;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC);
        JwtProperties properties = new JwtProperties(
                TestJwtFactory.DEFAULT_ISSUER,
                TestJwtFactory.DEFAULT_AUDIENCE,
                TestJwtFactory.DEFAULT_TTL,
                TestJwtFactory.DEFAULT_TTL,
                TestJwtFactory.DEFAULT_SECRET
        );
        factory = new TestJwtFactory(properties, clock);
        decoder = AccessTokenService.createJwtDecoder(properties, clock);
    }

    @Test
    @DisplayName("valid token decodes successfully with matching claims")
    void validToken_decodesSuccessfully() {
        UUID userId = UUID.randomUUID();
        String token = factory.createValidToken(userId, 5L);

        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.<Long>getClaim("av")).isEqualTo(5L);
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(TestJwtFactory.DEFAULT_ISSUER);
        assertThat(jwt.getAudience()).containsExactly(TestJwtFactory.DEFAULT_AUDIENCE);
    }

    @Test
    @DisplayName("expired token fails with JwtValidationException")
    void expiredToken_failsValidation() {
        String token = factory.createExpiredToken();
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("wrong issuer token fails with JwtValidationException")
    void wrongIssuerToken_failsValidation() {
        String token = factory.createWrongIssuerToken();
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("wrong audience token fails with JwtValidationException")
    void wrongAudienceToken_failsValidation() {
        String token = factory.createWrongAudienceToken();
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("bad signature token fails with BadJwtException")
    void badSignatureToken_failsValidation() {
        String token = factory.createBadSignatureToken();
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(BadJwtException.class);
    }
}
