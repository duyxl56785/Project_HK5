package com.projecthk5.sellerservice.web;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SellerOutboxRepository extends JpaRepository<SellerOutbox, UUID> {
    @Query("select o from SellerOutbox o where o.deliveredAt is null order by o.createdAt")
    List<SellerOutbox> pending(Pageable page);
}
