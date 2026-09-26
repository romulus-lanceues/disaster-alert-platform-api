package dev.romulus_lanceues.tanaw_api.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class TestJwtFactory {

    public static final String DEFAULT_ISSUER = "tanaw-api";
    public static final String DEFAULT_AUDIENCE = "user";
    public static final String DEFAULT_SECRET = "dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng==";
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final JwtEncoder validEncoder;
    private final JwtEncoder badSignatureEncoder;

    public TestJwtFactory(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;

        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.secret());
        SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        JWKSource<SecurityContext> jwkSource = new ImmutableSecret<>(secretKey);
        this.validEncoder = new NimbusJwtEncoder(jwkSource);

        byte[] badKeyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            badKeyBytes[i] = (byte) (99 - i);
        }
        SecretKey badSecretKey = new SecretKeySpec(badKeyBytes, "HmacSHA256");
        JWKSource<SecurityContext> badJwkSource = new ImmutableSecret<>(badSecretKey);
        this.badSignatureEncoder = new NimbusJwtEncoder(badJwkSource);
    }

    public TestJwtFactory() {
        this(new JwtProperties(
                DEFAULT_ISSUER,
                DEFAULT_AUDIENCE,
                DEFAULT_TTL,
                Duration.ofDays(7),
                DEFAULT_SECRET
        ), Clock.systemUTC());
    }

    public String createValidToken(UUID userId, long authenticationVersion) {
        Instant now = clock.instant();
        return createToken(validEncoder, jwtProperties.issuer(), List.of(jwtProperties.audience()),
                userId.toString(), now, now.plus(jwtProperties.accessTokenDuration()),
                authenticationVersion);
    }

    public String createValidToken(UUID userId) {
        return createValidToken(userId, 0L);
    }

    public String createValidToken() {
        return createValidToken(UUID.randomUUID(), 0L);
    }

    public String createExpiredToken(UUID userId, long authenticationVersion) {
        Instant now = clock.instant();
        return createToken(validEncoder, jwtProperties.issuer(), List.of(jwtProperties.audience()),
                userId.toString(), now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)),
                authenticationVersion);
    }

    public String createExpiredToken() {
        return createExpiredToken(UUID.randomUUID(), 0L);
    }

    public String createWrongIssuerToken(UUID userId, long authenticationVersion) {
        Instant now = clock.instant();
        return createToken(validEncoder, "https://wrong-issuer.com", List.of(jwtProperties.audience()),
                userId.toString(), now, now.plus(jwtProperties.accessTokenDuration()),
                authenticationVersion);
    }

    public String createWrongIssuerToken() {
        return createWrongIssuerToken(UUID.randomUUID(), 0L);
    }

    public String createWrongAudienceToken(UUID userId, long authenticationVersion) {
        Instant now = clock.instant();
        return createToken(validEncoder, jwtProperties.issuer(), List.of("wrong-audience"),
                userId.toString(), now, now.plus(jwtProperties.accessTokenDuration()),
                authenticationVersion);
    }

    public String createWrongAudienceToken() {
        return createWrongAudienceToken(UUID.randomUUID(), 0L);
    }

    public String createBadSignatureToken(UUID userId, long authenticationVersion) {
        Instant now = clock.instant();
        return createToken(badSignatureEncoder, jwtProperties.issuer(), List.of(jwtProperties.audience()),
                userId.toString(), now, now.plus(jwtProperties.accessTokenDuration()),
                authenticationVersion);
    }

    public String createBadSignatureToken() {
        return createBadSignatureToken(UUID.randomUUID(), 0L);
    }

    private String createToken(JwtEncoder encoder, String issuer, List<String> audience,
                               String subject, Instant issuedAt, Instant expiresAt,
                               long authenticationVersion) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("av", authenticationVersion)
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
