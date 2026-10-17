package com.projecthk5.inventoryservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "inventory_operation")
public class InventoryOperation {
    @Id
    @Column(name = "operation_id", length = 128, nullable = false)
    private String operationId;

    @Column(name = "operation_type", length = 24, nullable = false)
    private String operationType;

    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected InventoryOperation() {}

    public InventoryOperation(String operationId, String operationType, String requestHash) {
        this.operationId = operationId;
        this.operationType = operationType;
        this.requestHash = requestHash;
        this.createdAt = OffsetDateTime.now();
    }

    public String getOperationId() { return operationId; }
    public String getOperationType() { return operationType; }
    public String getRequestHash() { return requestHash; }
}
