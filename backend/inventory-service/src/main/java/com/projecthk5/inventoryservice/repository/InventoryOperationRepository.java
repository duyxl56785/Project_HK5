package com.projecthk5.inventoryservice.repository;

import com.projecthk5.inventoryservice.model.InventoryOperation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryOperationRepository extends JpaRepository<InventoryOperation, String> {
}
