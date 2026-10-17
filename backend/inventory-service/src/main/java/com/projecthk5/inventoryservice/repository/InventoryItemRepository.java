package com.projecthk5.inventoryservice.repository;

import com.projecthk5.inventoryservice.model.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.sku in :skus order by i.sku")
    List<InventoryItem> findAllBySkuForUpdate(@Param("skus") Collection<String> skus);

    Optional<InventoryItem> findBySku(String sku);
}
