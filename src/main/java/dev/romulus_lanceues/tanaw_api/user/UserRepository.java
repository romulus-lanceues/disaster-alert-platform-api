package dev.romulus_lanceues.tanaw_api.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByEmailAndStatus(String email, UserStatus status);

    @Query("""
            SELECT new dev.romulus_lanceues.tanaw_api.user.UserAuthState(
                u.status,
                u.authenticationVersion
            )
            FROM User u
            WHERE u.id = :userId
            """)
    Optional<UserAuthState> findAuthState(@Param("userId") UUID userId);
}

