package com.projecthk5.authservice.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String normalizedEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findLockedById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findSharedById(UUID id);

    default Optional<Account> findByEmailNormalized(String email) {
        return findByEmail(Account.normalizeEmail(email));
    }
}
