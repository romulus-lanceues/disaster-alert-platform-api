package dev.romulus_lanceues.tanaw_api.auth;

import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserAlreadyExistsException;
import dev.romulus_lanceues.tanaw_api.user.UserRepository;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenSessionService refreshTokenSessionService;
    private final JwtProperties jwtProperties;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenService accessTokenService,
            RefreshTokenSessionService refreshTokenSessionService,
            JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.refreshTokenSessionService = refreshTokenSessionService;
        this.jwtProperties = jwtProperties;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing-mitigation");
    }

    @Transactional
    public void register(RegisterRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String normalizedEmail = request.email() != null ? request.email().trim().toLowerCase() : "";
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException("Email is already registered: " + normalizedEmail);
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Registered new user with id: {}", savedUser.getId());
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String normalizedEmail = request.email() != null ? request.email().trim().toLowerCase() : "";
        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);

        if (userOptional.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new InvalidCredentialsException();
        }

        User user = userOptional.get();
        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());

        if (!passwordMatches || user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        String accessToken = accessTokenService.generateToken(user);
        String rawRefreshToken = refreshTokenSessionService.issueRefreshToken(user);

        log.info("User logged in successfully with id: {}", user.getId());

        return new AuthResult(
                accessToken,
                rawRefreshToken,
                jwtProperties.accessTokenDuration().toSeconds()
        );
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token is required");
        }

        RefreshTokenRotationResult rotationResult = refreshTokenSessionService.rotate(rawRefreshToken);
        String accessToken = accessTokenService.generateToken(rotationResult.user());

        log.info("Refreshed access token for user id: {}", rotationResult.user().getId());

        return new AuthResult(
                accessToken,
                rotationResult.newRawToken(),
                jwtProperties.accessTokenDuration().toSeconds()
        );
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            try {
                refreshTokenSessionService.revokeCurrent(rawRefreshToken);
                log.info("Revoked session during logout");
            } catch (Exception e) {
                log.warn("Failed to revoke session during logout: {}", e.getMessage());
            }
        }
    }
}
