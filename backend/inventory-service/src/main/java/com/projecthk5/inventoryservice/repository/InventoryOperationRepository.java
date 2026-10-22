package com.projecthk5.inventoryservice.repository;

import com.projecthk5.inventoryservice.model.InventoryOperation;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryOperationRepository extends JpaRepository<InventoryOperation, String> {
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO inventory_operation (operation_id, operation_type, request_hash, created_at)
            VALUES (:operationId, :operationType, :requestHash, CURRENT_TIMESTAMP)
            ON CONFLICT (operation_id) DO NOTHING
            """, nativeQuery = true)
    int claimOperationId(@Param("operationId") String operationId,
                         @Param("operationType") String operationType,
                         @Param("requestHash") String requestHash);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO inventory_operation (operation_id, operation_type, request_hash, reservation_id, created_at)
            VALUES (:operationId, :operationType, :requestHash, :reservationId, CURRENT_TIMESTAMP)
            ON CONFLICT (operation_id) DO NOTHING
            """, nativeQuery = true)
    int claimReservationOperation(@Param("operationId") String operationId,
                                  @Param("operationType") String operationType,
                                  @Param("requestHash") String requestHash,
                                  @Param("reservationId") String reservationId);
}
