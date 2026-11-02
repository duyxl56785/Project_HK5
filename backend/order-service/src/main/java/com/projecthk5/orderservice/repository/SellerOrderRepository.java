package com.projecthk5.orderservice.repository;

import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SellerOrderRepository extends JpaRepository<SellerOrder, UUID> {
    /** Row lock serializes concurrent seller/customer/admin actions on one Store order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SellerOrder s where s.id = :id")
    Optional<SellerOrder> lockById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"items", "order"})
    @Query("select s from SellerOrder s where s.storeId = :storeId and (:status is null or s.status = :status) "
        + "order by s.order.createdAt desc")
    List<SellerOrder> findForStore(@Param("storeId") String storeId,
                                   @Param("status") SellerOrderLifecycle.Status status, Pageable page);

    @EntityGraph(attributePaths = {"items", "order"})
    @Query("select s from SellerOrder s where (:status is null or s.status = :status) "
        + "and (:cod is null or s.codStatus = :cod) order by s.order.createdAt desc")
    List<SellerOrder> findForAdmin(@Param("status") SellerOrderLifecycle.Status status,
                                   @Param("cod") SellerOrder.CodStatus cod, Pageable page);

    /** Finance rows: delivered Store orders only; amounts are the immutable checkout snapshot. */
    @Query("select s from SellerOrder s where s.status = com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle.Status.DELIVERED "
        + "and (:storeId is null or s.storeId = :storeId) order by s.deliveredAt desc")
    List<SellerOrder> findDelivered(@Param("storeId") String storeId);

    @EntityGraph(attributePaths = {"items", "order"})
    @Query("select s from SellerOrder s where s.order.id = :orderId")
    List<SellerOrder> findByOrderId(@Param("orderId") UUID orderId);
}
