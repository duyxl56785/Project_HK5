package com.projecthk5.authservice.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String normalizedEmail);

    default Optional<Account> findByEmailNormalized(String email) {
        return findByEmail(Account.normalizeEmail(email));
    }
}
