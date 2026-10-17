package com.projecthk5.cartservice.repository;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.projecthk5.cartservice.entity.Cart;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByCustomerId(String customerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.customerId = :customerId")
    Optional<Cart> findForUpdate(@Param("customerId") String customerId);
}
