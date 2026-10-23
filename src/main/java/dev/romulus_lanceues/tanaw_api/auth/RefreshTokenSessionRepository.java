package dev.romulus_lanceues.tanaw_api.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, UUID> {

    Optional<RefreshTokenSession> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshTokenSession r JOIN FETCH r.user WHERE r.tokenHash = :tokenHash")
    Optional<RefreshTokenSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("UPDATE RefreshTokenSession r SET r.revokedAt = :revokedAt WHERE r.familyId = :familyId AND r.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("UPDATE RefreshTokenSession r SET r.revokedAt = :revokedAt WHERE r.tokenHash = :tokenHash AND r.revokedAt IS NULL")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash, @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("UPDATE RefreshTokenSession r SET r.revokedAt = :revokedAt WHERE r.user.id = :userId AND r.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

}
