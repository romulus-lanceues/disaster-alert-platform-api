package dev.romulus_lanceues.tanaw_api.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.romulus_lanceues.tanaw_api.user.User;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AccessTokenService {

    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    @Autowired
    public AccessTokenService(JwtProperties jwtProperties, Clock clock, JwtDecoder jwtDecoder) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.jwtDecoder = jwtDecoder;

        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.secret());
        SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

        JWKSource<SecurityContext> jwkSource = new ImmutableSecret<>(secretKey);
        this.jwtEncoder = new NimbusJwtEncoder(jwkSource);
    }

    public AccessTokenService(JwtProperties jwtProperties, Clock clock) {
        this(jwtProperties, clock, createJwtDecoder(jwtProperties, clock));
    }

    public static JwtDecoder createJwtDecoder(JwtProperties jwtProperties, Clock clock) {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.secret());
        SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setClock(clock);

        JwtIssuerValidator issuerValidator = new JwtIssuerValidator(jwtProperties.issuer());

        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(jwtProperties.audience())
        );

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                issuerValidator,
                audienceValidator
        ));
        return decoder;
    }


    public String generateToken(UUID userId, long authenticationVersion) {

        Instant now = clock.instant();
        Instant expiresAt = now.plus(jwtProperties.accessTokenDuration());

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("av", authenticationVersion)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public String generateToken(User user) {
        Objects.requireNonNull(user, "user must not be null");
        return generateToken(user.getId(), user.getAuthenticationVersion());
    }

    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }

}
