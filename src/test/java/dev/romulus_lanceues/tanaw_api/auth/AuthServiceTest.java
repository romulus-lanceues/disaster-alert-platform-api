package dev.romulus_lanceues.tanaw_api.auth;

import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserAlreadyExistsException;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccessTokenService accessTokenService;

    @Mock
    private RefreshTokenSessionService refreshTokenSessionService;

    private JwtProperties jwtProperties;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties(
                "tanaw-api",
                "tanaw-client",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                "dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="
        );

        given(passwordEncoder.encode(anyString())).willReturn("$argon2id$v=19$m=16384,t=2,p=1$dummyhash");

        authService = new AuthService(
                userRepository,
                passwordEncoder,
                accessTokenService,
                refreshTokenSessionService,
                jwtProperties
        );
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("should trim and lowercase email, encode password, and save active user")
        void shouldTrimAndLowercaseEmail_andSaveActiveUser() {
            RegisterRequest request = new RegisterRequest("  Alice@Example.COM  ", "SecurePass123!");
            given(userRepository.existsByEmail("alice@example.com")).willReturn(false);
            given(passwordEncoder.encode("SecurePass123!")).willReturn("encoded-hash");

            User savedUser = User.builder()
                    .id(UUID.randomUUID())
                    .email("alice@example.com")
                    .passwordHash("encoded-hash")
                    .status(UserStatus.ACTIVE)
                    .build();
            given(userRepository.save(any(User.class))).willReturn(savedUser);

            authService.register(request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            then(userRepository).should().save(userCaptor.capture());

            User captured = userCaptor.getValue();
            assertThat(captured.getEmail()).isEqualTo("alice@example.com");
            assertThat(captured.getPasswordHash()).isEqualTo("encoded-hash");
            assertThat(captured.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("should throw UserAlreadyExistsException when email is already registered")
        void shouldThrowUserAlreadyExistsException_whenEmailAlreadyExists() {
            RegisterRequest request = new RegisterRequest("existing@example.com", "SecurePass123!");
            given(userRepository.existsByEmail("existing@example.com")).willReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining("existing@example.com");

            then(userRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should return AuthResult when credentials are valid and user is active")
        void shouldReturnAuthResult_whenCredentialsAreValid() {
            LoginRequest request = new LoginRequest("  User@Example.COM  ", "ValidPass123!");
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .email("user@example.com")
                    .passwordHash("stored-hash")
                    .status(UserStatus.ACTIVE)
                    .build();

            given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("ValidPass123!", "stored-hash")).willReturn(true);
            given(accessTokenService.generateToken(user)).willReturn("valid-access-token");
            given(refreshTokenSessionService.issueRefreshToken(user)).willReturn("valid-refresh-token");

            AuthResult result = authService.login(request);

            assertThat(result.accessToken()).isEqualTo("valid-access-token");
            assertThat(result.rawRefreshToken()).isEqualTo("valid-refresh-token");
            assertThat(result.expiresIn()).isEqualTo(900L);
        }

        @Test
        @DisplayName("should perform dummy hash compare and throw InvalidCredentialsException when email is unknown")
        void shouldPerformDummyHashCompare_andThrow_whenEmailUnknown() {
            LoginRequest request = new LoginRequest("unknown@example.com", "SomePass123!");
            given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid email or password");

            then(passwordEncoder).should().matches(eq("SomePass123!"), eq("$argon2id$v=19$m=16384,t=2,p=1$dummyhash"));
            then(accessTokenService).shouldHaveNoInteractions();
            then(refreshTokenSessionService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should throw InvalidCredentialsException when password does not match")
        void shouldThrowInvalidCredentialsException_whenPasswordDoesNotMatch() {
            LoginRequest request = new LoginRequest("user@example.com", "WrongPass123!");
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .email("user@example.com")
                    .passwordHash("stored-hash")
                    .status(UserStatus.ACTIVE)
                    .build();

            given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("WrongPass123!", "stored-hash")).willReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid email or password");

            then(accessTokenService).shouldHaveNoInteractions();
            then(refreshTokenSessionService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should throw InvalidCredentialsException when user account is disabled")
        void shouldThrowInvalidCredentialsException_whenUserAccountDisabled() {
            LoginRequest request = new LoginRequest("user@example.com", "CorrectPass123!");
            User disabledUser = User.builder()
                    .id(UUID.randomUUID())
                    .email("user@example.com")
                    .passwordHash("stored-hash")
                    .status(UserStatus.DISABLED)
                    .build();

            given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(disabledUser));
            given(passwordEncoder.matches("CorrectPass123!", "stored-hash")).willReturn(true);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid email or password");

            then(accessTokenService).shouldHaveNoInteractions();
            then(refreshTokenSessionService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("should rotate session and return new tokens when refresh token is valid")
        void shouldRotateSessionAndReturnNewTokens_whenRefreshTokenValid() {
            String rawToken = "raw-refresh-token";
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .email("user@example.com")
                    .status(UserStatus.ACTIVE)
                    .build();

            RefreshTokenRotationResult rotationResult = new RefreshTokenRotationResult("new-raw-refresh-token", user);
            given(refreshTokenSessionService.rotate(rawToken)).willReturn(rotationResult);
            given(accessTokenService.generateToken(user)).willReturn("new-access-token");

            AuthResult result = authService.refresh(rawToken);

            assertThat(result.accessToken()).isEqualTo("new-access-token");
            assertThat(result.rawRefreshToken()).isEqualTo("new-raw-refresh-token");
            assertThat(result.expiresIn()).isEqualTo(900L);
        }

        @Test
        @DisplayName("should throw InvalidRefreshTokenException when refresh token is null")
        void shouldThrowInvalidRefreshTokenException_whenRefreshTokenIsNull() {
            assertThatThrownBy(() -> authService.refresh(null))
                    .isInstanceOf(InvalidRefreshTokenException.class)
                    .hasMessage("Refresh token is required");
        }

        @Test
        @DisplayName("should throw InvalidRefreshTokenException when refresh token is blank")
        void shouldThrowInvalidRefreshTokenException_whenRefreshTokenIsBlank() {
            assertThatThrownBy(() -> authService.refresh("   "))
                    .isInstanceOf(InvalidRefreshTokenException.class)
                    .hasMessage("Refresh token is required");
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("should revoke current session when token is provided")
        void shouldRevokeCurrentSession_whenTokenProvided() {
            String rawToken = "raw-token-to-revoke";

            authService.logout(rawToken);

            then(refreshTokenSessionService).should().revokeCurrent(rawToken);
        }

        @Test
        @DisplayName("should do nothing when token is null or blank")
        void shouldDoNothing_whenTokenIsNullOrEmpty() {
            authService.logout(null);
            authService.logout("   ");

            then(refreshTokenSessionService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should not throw when revocation throws exception")
        void shouldNotThrow_whenRevocationThrows() {
            willThrow(new RuntimeException("Database error")).given(refreshTokenSessionService).revokeCurrent("token");

            authService.logout("token");

            then(refreshTokenSessionService).should().revokeCurrent("token");
        }
    }
}
