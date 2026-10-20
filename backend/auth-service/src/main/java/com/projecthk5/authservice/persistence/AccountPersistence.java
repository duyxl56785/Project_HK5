package com.projecthk5.authservice.persistence;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AccountPersistence {
    private final EntityManager entityManager;

    public AccountPersistence(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void create(Account account) {
        entityManager.persist(account);
        entityManager.persist(new ProfileProvisioningTask(account.getId(), account.getCreatedAt()));
        entityManager.flush();
    }
}
