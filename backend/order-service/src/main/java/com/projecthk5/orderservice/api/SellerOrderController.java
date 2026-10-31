package com.projecthk5.orderservice.api;

import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle;
import com.projecthk5.orderservice.ports.StoreDirectory;
import com.projecthk5.orderservice.repository.SellerOrderRepository;
import com.projecthk5.orderservice.service.FinanceReport;
import com.projecthk5.orderservice.service.FulfillmentService;
import com.projecthk5.security.Actor;
import com.projecthk5.security.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** SELLER fulfillment and finance for the Store the caller manages now; Store id never comes from the client. */
@RestController
@RequestMapping("/api/seller")
public class SellerOrderController {
    private final SellerOrderRepository sellerOrders;
    private final FulfillmentService fulfillment;
    private final StoreDirectory stores;
    private final OrderViews views;
    private final FinanceReport finance;

    public SellerOrderController(SellerOrderRepository sellerOrders, FulfillmentService fulfillment,
                                 StoreDirectory stores, OrderViews views, FinanceReport finance) {
        this.sellerOrders = sellerOrders;
        this.fulfillment = fulfillment;
        this.stores = stores;
        this.views = views;
        this.finance = finance;
    }

    public record ActionRequest(@Size(max = 500) String reason) {}

    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public List<OrderViews.StoreOrder> list(@RequestParam(required = false) SellerOrderLifecycle.Status status) {
        String storeId = myStore();
        return views.stores(sellerOrders.findForStore(storeId, status, PageRequest.of(0, 200)));
    }

    @GetMapping("/orders/{id}")
    @Transactional(readOnly = true)
    public OrderViews.StoreOrder one(@PathVariable UUID id) {
        String storeId = myStore();
        SellerOrder so = sellerOrders.findById(id).filter(s -> s.getStoreId().equals(storeId))
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));
        return views.stores(List.of(so)).get(0);
    }

    @PostMapping("/orders/{id}/{action}")
    @Transactional
    public OrderViews.StoreOrder act(@PathVariable UUID id, @PathVariable String action,
                                     @Valid @RequestBody(required = false) ActionRequest body) {
        SellerOrder so = fulfillment.sellerAction(myStore(), id, action, body == null ? null : body.reason());
        return views.stores(List.of(so)).get(0);
    }

    @GetMapping("/finance")
    @Transactional(readOnly = true)
    public FinanceReport.StoreSummary myFinance() {
        StoreDirectory.Store store = stores.managedBy(Actor.require("SELLER").id());
        return finance.forStore(store.storeId(), store.name());
    }

    private String myStore() {
        return stores.managedBy(Actor.require("SELLER").id()).storeId();
    }
}
