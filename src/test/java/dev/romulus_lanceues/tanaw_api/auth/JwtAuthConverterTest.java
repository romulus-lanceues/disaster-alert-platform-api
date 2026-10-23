package dev.romulus_lanceues.tanaw_api.auth;

import dev.romulus_lanceues.tanaw_api.user.UserAuthState;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthConverter Unit Tests")
class JwtAuthConverterTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JwtAuthConverter jwtAuthConverter;

    private Jwt createJwt(String subject, Object av) {
        Jwt.Builder builder = Jwt.withTokenValue("mock.jwt.token")
                .header("alg", "HS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900));

        if (subject != null) {
            builder.subject(subject);
        }
        if (av != null) {
            builder.claim("av", av);
        }
        return builder.build();
    }

    @Nested
    @DisplayName("Valid Token Conversion")
    class ValidTokenConversion {

        @Test
        @DisplayName("should accept valid token and return JwtAuthenticationToken with ROLE_USER")
        void shouldReturnJwtAuthenticationTokenWhenTokenIsValid() {
            UUID userId = UUID.randomUUID();
            long authVersion = 1L;
            Jwt jwt = createJwt(userId.toString(), authVersion);

            given(userRepository.findAuthState(userId))
                    .willReturn(Optional.of(new UserAuthState(true, authVersion)));

            JwtAuthenticationToken authentication = jwtAuthConverter.convert(jwt);

            assertThat(authentication).isNotNull();
            assertThat(authentication.getPrincipal()).isEqualTo(jwt);
            assertThat(authentication.getName()).isEqualTo(userId.toString());
            assertThat(authentication.isAuthenticated()).isTrue();
            assertThat(authentication.getAuthorities())
                    .containsExactly(new SimpleGrantedAuthority("ROLE_USER"));
        }

        @Test
        @DisplayName("should accept valid token when av claim is represented as Integer")
        void shouldAcceptTokenWhenAvClaimIsInteger() {
            UUID userId = UUID.randomUUID();
            Jwt jwt = createJwt(userId.toString(), 2);

            given(userRepository.findAuthState(userId))
                    .willReturn(Optional.of(new UserAuthState(true, 2L)));

            JwtAuthenticationToken authentication = jwtAuthConverter.convert(jwt);

            assertThat(authentication).isNotNull();
            assertThat(authentication.getAuthorities())
                    .containsExactly(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }

    @Nested
    @DisplayName("Account State and Version Validation")
    class AccountStateValidation {

        @Test
        @DisplayName("should reject token when user does not exist")
        void shouldRejectWhenUserDoesNotExist() {
            UUID userId = UUID.randomUUID();
            Jwt jwt = createJwt(userId.toString(), 1L);

            given(userRepository.findAuthState(userId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> jwtAuthConverter.convert(jwt))
                    .isInstanceOf(InvalidBearerTokenException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("should reject token when user is inactive")
        void shouldRejectWhenUserIsInactive() {
            UUID userId = UUID.randomUUID();
            Jwt jwt = createJwt(userId.toString(), 1L);

            given(userRepository.findAuthState(userId))
                    .willReturn(Optional.of(new UserAuthState(false, 1L)));

            assertThatThrownBy(() -> jwtAuthConverter.convert(jwt))
                    .isInstanceOf(InvalidBearerTokenException.class)
                    .hasMessageContaining("User account is inactive");
        }

        @Test
        @DisplayName("should reject token when authentication version mismatches")
        void shouldRejectWhenAuthenticationVersionMismatches() {
            UUID userId = UUID.randomUUID();
            long tokenVersion = 1L;
            long dbVersion = 2L;
            Jwt jwt = createJwt(userId.toString(), tokenVersion);

            given(userRepository.findAuthState(userId))
                    .willReturn(Optional.of(new UserAuthState(true, dbVersion)));

            assertThatThrownBy(() -> jwtAuthConverter.convert(jwt))
                    .isInstanceOf(InvalidBearerTokenException.class)
                    .hasMessageContaining("Authentication version mismatch");
        }
    }

    @Nested
    @DisplayName("Subject Validation")
    class SubjectValidation {

        @Test
        @DisplayName("should reject token when subject is not a valid UUID")
        void shouldRejectWhenSubjectIsNotValidUuid() {
            Jwt jwt = createJwt("invalid-uuid-string", 1L);

            assertThatThrownBy(() -> jwtAuthConverter.convert(jwt))
                    .isInstanceOf(InvalidBearerTokenException.class)
                    .hasMessageContaining("not a valid UUID");

            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should reject token when subject is null")
        void shouldRejectWhenSubjectIsNull() {
            Jwt jwt = createJwt(null, 1L);

            assertThatThrownBy(() -> jwtAuthConverter.convert(jwt))
                    .isInstanceOf(InvalidBearerTokenException.class)
                    .hasMessageContaining("JWT subject must not be null or blank");

            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should reject token when subject is blank")
        void shouldRejectWhenSubjectIsBlank() {
            Jwt jwt = createJwt("   ", 1L);

            assertThatThrownBy(() -> jwtAuthConverter.convert(jwt))
                    .isInstanceOf(InvalidBearerTokenException.class)
                    .hasMessageContaining("JWT subject must not be null or blank");

            then(userRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("Claim and Token Edge Cases")
    class ClaimAndTokenValidation {

        @Test
        @DisplayName("should reject token when av claim is missing")
        void shouldRejectWhenAvClaimIsMissing() {
            UUID userId = UUID.randomUUID();
            Jwt jwt = createJwt(userId.toString(), null);

            assertThatThrownBy(() -> jwtAuthConverter.convert(jwt))
                    .isInstanceOf(InvalidBearerTokenException.class)
                    .hasMessageContaining("missing authentication version (av) claim");

            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should reject token when av claim is not numeric")
        void shouldRejectWhenAvClaimIsNotNumeric() {
            UUID userId = UUID.randomUUID();
            Jwt jwt = createJwt(userId.toString(), "not-a-number");

            assertThatThrownBy(() -> jwtAuthConverter.convert(jwt))
                    .isInstanceOf(InvalidBearerTokenException.class)
                    .hasMessageContaining("Invalid authentication version (av) claim");

            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should reject when jwt is null")
        void shouldRejectWhenJwtIsNull() {
            assertThatThrownBy(() -> jwtAuthConverter.convert(null))
                    .isInstanceOf(InvalidBearerTokenException.class)
                    .hasMessageContaining("JWT must not be null");

            then(userRepository).shouldHaveNoInteractions();
        }
    }
}
