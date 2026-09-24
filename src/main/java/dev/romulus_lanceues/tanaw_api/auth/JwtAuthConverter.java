package dev.romulus_lanceues.tanaw_api.auth;

import dev.romulus_lanceues.tanaw_api.user.UserAuthState;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final GrantedAuthority ROLE_USER = new SimpleGrantedAuthority("ROLE_USER");
    private static final List<GrantedAuthority> DEFAULT_AUTHORITIES = List.of(ROLE_USER);

    private final UserRepository userRepository;

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        if (jwt == null) {
            throw new InvalidBearerTokenException("JWT must not be null");
        }

        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidBearerTokenException("JWT subject must not be null or blank");
        }

        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("JWT subject is not a valid UUID: " + subject, e);
        }

        Object avClaim = jwt.getClaim("av");
        if (avClaim == null) {
            throw new InvalidBearerTokenException("JWT is missing authentication version (av) claim");
        }

        long tokenAuthVersion;
        if (avClaim instanceof Number number) {
            tokenAuthVersion = number.longValue();
        } else {
            try {
                tokenAuthVersion = Long.parseLong(avClaim.toString());
            } catch (NumberFormatException e) {
                throw new InvalidBearerTokenException("Invalid authentication version (av) claim: " + avClaim, e);
            }
        }

        UserAuthState authState = userRepository.findAuthState(userId)
                .orElseThrow(() -> new InvalidBearerTokenException("User not found: " + userId));

        if (!authState.active()) {
            throw new InvalidBearerTokenException("User account is inactive");
        }

        if (authState.authenticationVersion() == null || tokenAuthVersion != authState.authenticationVersion()) {
            throw new InvalidBearerTokenException("Authentication version mismatch");
        }

        return new JwtAuthenticationToken(jwt, DEFAULT_AUTHORITIES);
    }
}
