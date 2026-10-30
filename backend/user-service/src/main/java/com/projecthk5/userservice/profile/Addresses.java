package com.projecthk5.userservice.profile;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Addresses extends JpaRepository<UserAddress, UUID> {
    List<UserAddress> findByAccountId(String accountId);
}
