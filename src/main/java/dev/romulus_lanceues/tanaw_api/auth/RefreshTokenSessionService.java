package dev.romulus_lanceues.tanaw_api.auth;

import dev.romulus_lanceues.tanaw_api.user.User;
import dev.romulus_lanceues.tanaw_api.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenSessionService {

    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();


    @Transactional
    public String issueRefreshToken(User user) {

        Instant now = clock.instant();
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        Instant expiresAt = now.plus(jwtProperties.refreshTokenDuration());

        RefreshTokenSession session = RefreshTokenSession.builder()
                .user(user)
                .familyId(UUID.randomUUID())
                .tokenHash(tokenHash)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        refreshTokenSessionRepository.save(session);
        return rawToken;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RefreshTokenRotationResult rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token must not be blank");
        }

        Instant now = clock.instant();
        String tokenHash = hashToken(rawToken);

        RefreshTokenSession session = refreshTokenSessionRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found"));

        if (session.getRevokedAt() != null) {
            refreshTokenSessionRepository.revokeFamily(session.getFamilyId(), now);
            throw new InvalidRefreshTokenException("Refresh token has already been revoked");
        }

        if (!now.isBefore(session.getExpiresAt())) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        if (session.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new InvalidRefreshTokenException("User account is inactive");
        }

        String newRawToken = generateRawToken();
        String newTokenHash = hashToken(newRawToken);
        Instant expiresAt = now.plus(jwtProperties.refreshTokenDuration());

        RefreshTokenSession newSession = RefreshTokenSession.builder()
                .user(session.getUser())
                .familyId(session.getFamilyId())
                .tokenHash(newTokenHash)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        newSession = refreshTokenSessionRepository.save(newSession);

        session.rotateTo(newSession, now);
        refreshTokenSessionRepository.save(session);

        return new RefreshTokenRotationResult(newRawToken, session.getUser());
    }

    @Transactional
    public void revokeCurrent(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String tokenHash = hashToken(rawToken);
        refreshTokenSessionRepository.revokeByTokenHash(tokenHash, clock.instant());
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        if (userId == null) {
            return;
        }
        refreshTokenSessionRepository.revokeAllByUserId(userId, clock.instant());
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

}
