package com.projecthk5.authservice.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.*;

public interface RefreshFamilyRepository extends JpaRepository<RefreshFamily, UUID> {
    // Caller owns one transaction: lock first, then read/consume token, persist child or revoke, commit.
    @Transactional(propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from RefreshFamily f where f.id = "
            + "(select t.familyId from RefreshToken t where t.tokenHash = :hash)")
    Optional<RefreshFamily> lockByTokenHash(@Param("hash") String hash);

    @Transactional(propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from RefreshFamily f where f.id = :id")
    Optional<RefreshFamily> lockById(@Param("id") UUID id);
}
