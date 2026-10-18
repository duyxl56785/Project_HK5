package com.projecthk5.authservice.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.*;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String hash);

    // Lock the family inside this statement too, so callers cannot bypass logout/rotation serialization.
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true)
    @Query(value = """
            WITH locked_family AS MATERIALIZED (
                SELECT f.* FROM refresh_families f
                WHERE f.id = (SELECT family_id FROM refresh_tokens WHERE id = :id)
                FOR UPDATE
            )
            UPDATE refresh_tokens t SET used_at = :now
            FROM locked_family f
            WHERE t.id = :id AND t.family_id = f.id
              AND t.used_at IS NULL AND t.revoked_at IS NULL
              AND t.created_at <= :now AND t.expires_at > :now
              AND f.revoked_at IS NULL AND f.created_at <= :now AND f.expires_at > :now
            """, nativeQuery = true)
    int consume(@Param("id") UUID id, @Param("now") Instant now);
}
