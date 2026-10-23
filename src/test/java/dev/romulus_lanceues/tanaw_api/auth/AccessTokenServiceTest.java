package dev.romulus_lanceues.tanaw_api.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AccessTokenService Unit Tests")
class AccessTokenServiceTest {

    private static final String ISSUER = "https://auth.tanaw.dev";
    private static final String AUDIENCE = "tanaw-api";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private byte[] secretBytes;
    private String base64Secret;
    private JwtProperties jwtProperties;
    private Clock fixedClock;
    private Instant now;
    private AccessTokenService accessTokenService;

    @BeforeEach
    void setUp() {
        secretBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            secretBytes[i] = (byte) (i + 1);
        }
        base64Secret = Base64.getEncoder().encodeToString(secretBytes);

        jwtProperties = new JwtProperties(
                ISSUER,
                AUDIENCE,
                ACCESS_TOKEN_TTL,
                REFRESH_TOKEN_TTL,
                base64Secret
        );

        now = Instant.parse("2026-09-23T10:00:00Z");
        fixedClock = Clock.fixed(now, ZoneOffset.UTC);

        accessTokenService = new AccessTokenService(jwtProperties, fixedClock);
    }

    @Nested
    @DisplayName("generateToken")
    class GenerateToken {

        @Test
        @DisplayName("should issue HS256 token with iss, aud, sub, iat, exp, and av claims")
        void shouldIssueHs256TokenWithExpectedClaims() {
            UUID userId = UUID.randomUUID();
            long authVersion = 3L;

            String token = accessTokenService.generateToken(userId, authVersion);

            assertThat(token).isNotBlank();

            Jwt jwt = accessTokenService.decode(token);

            assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
            assertThat(jwt.getSubject()).isEqualTo(userId.toString());
            assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
            assertThat(jwt.getAudience()).containsExactly(AUDIENCE);
            assertThat(jwt.getIssuedAt()).isEqualTo(now);
            assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(ACCESS_TOKEN_TTL));
            assertThat(jwt.<Long>getClaim("av")).isEqualTo(authVersion);
        }

        @Test
        @DisplayName("should issue token using User entity")
        void shouldIssueTokenUsingUserEntity() {
            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .email("user@example.com")
                    .passwordHash("hashed")
                    .status(UserStatus.ACTIVE)
                    .authenticationVersion(7L)
                    .build();

            String token = accessTokenService.generateToken(user);

            assertThat(token).isNotBlank();

            Jwt jwt = accessTokenService.decode(token);
            assertThat(jwt.getSubject()).isEqualTo(userId.toString());
            assertThat(jwt.<Long>getClaim("av")).isEqualTo(7L);
        }
    }

    @Nested
    @DisplayName("decode and validation")
    class DecodeAndValidation {

        @Test
        @DisplayName("should reject expired token")
        void shouldRejectExpiredToken() {
            // Clock for issuing an expired token (issued in the past, expired well beyond 60s skew)
            Instant pastTime = now.minus(Duration.ofHours(2));
            Clock pastClock = Clock.fixed(pastTime, ZoneOffset.UTC);
            AccessTokenService pastService = new AccessTokenService(jwtProperties, pastClock);

            String expiredToken = pastService.generateToken(UUID.randomUUID(), 0L);

            // Using the current service (whose clock is 2 hours later) to decode should fail
            assertThatThrownBy(() -> accessTokenService.decode(expiredToken))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("should reject token signed with a different key")
        void shouldRejectTokenWithInvalidSignature() {
            byte[] differentKeyBytes = new byte[32];
            for (int i = 0; i < 32; i++) {
                differentKeyBytes[i] = (byte) (99 - i);
            }
            SecretKey differentKey = new SecretKeySpec(differentKeyBytes, "HmacSHA256");
            JWKSource<SecurityContext> jwkSource = new ImmutableSecret<>(differentKey);
            JwtEncoder foreignEncoder = new NimbusJwtEncoder(jwkSource);

            JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(ISSUER)
                    .audience(List.of(AUDIENCE))
                    .subject(UUID.randomUUID().toString())
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plus(Duration.ofMinutes(10)))
                    .claim("av", 0L)
                    .build();

            String tamperedToken = foreignEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

            assertThatThrownBy(() -> accessTokenService.decode(tamperedToken))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("should reject token with wrong issuer")
        void shouldRejectTokenWithWrongIssuer() {
            byte[] keyBytes = Base64.getDecoder().decode(base64Secret);
            SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
            JWKSource<SecurityContext> jwkSource = new ImmutableSecret<>(secretKey);
            JwtEncoder encoder = new NimbusJwtEncoder(jwkSource);

            JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer("https://wrong-issuer.com")
                    .audience(List.of(AUDIENCE))
                    .subject(UUID.randomUUID().toString())
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plus(Duration.ofMinutes(10)))
                    .claim("av", 0L)
                    .build();

            String badIssuerToken = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

            assertThatThrownBy(() -> accessTokenService.decode(badIssuerToken))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("should reject token with wrong audience")
        void shouldRejectTokenWithWrongAudience() {
            byte[] keyBytes = Base64.getDecoder().decode(base64Secret);
            SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
            JWKSource<SecurityContext> jwkSource = new ImmutableSecret<>(secretKey);
            JwtEncoder encoder = new NimbusJwtEncoder(jwkSource);

            JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(ISSUER)
                    .audience(List.of("wrong-audience"))
                    .subject(UUID.randomUUID().toString())
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plus(Duration.ofMinutes(10)))
                    .claim("av", 0L)
                    .build();

            String badAudienceToken = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

            assertThatThrownBy(() -> accessTokenService.decode(badAudienceToken))
                    .isInstanceOf(JwtException.class);
        }
    }
}
