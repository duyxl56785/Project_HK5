package com.projecthk5.orderservice.api;

import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle;
import com.projecthk5.orderservice.ports.StoreDirectory;
import com.projecthk5.orderservice.repository.SellerOrderRepository;
import com.projecthk5.orderservice.service.FinanceReport;
import com.projecthk5.orderservice.service.FulfillmentService;
import com.projecthk5.orderservice.service.ReturnRequestService;
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
    private final ReturnRequestService returns;

    public SellerOrderController(SellerOrderRepository sellerOrders, FulfillmentService fulfillment,
                                 StoreDirectory stores, OrderViews views, FinanceReport finance,
                                 ReturnRequestService returns) {
        this.sellerOrders = sellerOrders;
        this.fulfillment = fulfillment;
        this.stores = stores;
        this.views = views;
        this.finance = finance;
        this.returns = returns;
    }

    public record ActionRequest(@Size(max = 500) String reason) {}
    public record ReturnActionRequest(@Size(max = 128) String receiptReference) {}

    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public List<OrderViews.StoreOrder> list(@RequestParam(required = false) SellerOrderLifecycle.Status status) {
        StoreDirectory.Store store = myStore();
        return views.stores(sellerOrders.findForStore(store.storeId(), status, PageRequest.of(0, 200))).stream()
                .map(order -> forSeller(store, order)).toList();
    }

    @GetMapping("/orders/{id}")
    @Transactional(readOnly = true)
    public OrderViews.StoreOrder one(@PathVariable UUID id) {
        StoreDirectory.Store store = myStore();
        SellerOrder so = sellerOrders.findById(id).filter(s -> s.getStoreId().equals(store.storeId()))
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));
        return forSeller(store, views.stores(List.of(so)).get(0));
    }

    @PostMapping("/orders/{id}/{action}")
    @Transactional
    public OrderViews.StoreOrder act(@PathVariable UUID id, @PathVariable String action,
                                     @Valid @RequestBody(required = false) ActionRequest body) {
        StoreDirectory.Store store = myStore();
        requireAllowed(store.sellerApproved(), action);
        SellerOrder so = fulfillment.sellerAction(store.storeId(), id, action, body == null ? null : body.reason());
        return forSeller(store, views.stores(List.of(so)).get(0));
    }

    /** Seller Return actions are Store-scoped and deliberately separate from fulfillment actions. */
    @GetMapping("/returns")
    @Transactional(readOnly = true)
    public List<ReturnRequestService.SellerReturnView> returns() {
        return returns.forStore(myStore().storeId());
    }

    @PostMapping("/returns/{id}/{action}")
    @Transactional
    public ReturnRequestService.SellerReturnView returnAction(@PathVariable UUID id, @PathVariable String action,
            @Valid @RequestBody(required = false) ReturnActionRequest body) {
        StoreDirectory.Store store = myStore();
        return returns.sellerAction(store.storeId(), Actor.require("SELLER").id(), id, action,
                body == null ? null : body.receiptReference());
    }

    @GetMapping("/finance")
    @Transactional(readOnly = true)
    public FinanceReport.StoreSummary myFinance() {
        StoreDirectory.Store store = myStore();
        if (!store.sellerApproved()) throw ApiException.forbidden("Người bán đang bị tạm ngừng nên không thể xem tài chính");
        return finance.forStore(store.storeId(), store.name());
    }

    /** A suspended Seller finishes accepted orders and may decline pending ones, but takes on nothing new. */
    static void requireAllowed(boolean sellerApproved, String action) {
        if (!sellerApproved && "accept".equals(action)) {
            throw ApiException.forbidden("Người bán đang bị tạm ngừng nên không thể nhận đơn mới");
        }
    }

    private static OrderViews.StoreOrder forSeller(StoreDirectory.Store store, OrderViews.StoreOrder order) {
        return store.sellerApproved() ? order : order.withoutFinance();
    }

    private StoreDirectory.Store myStore() {
        return stores.managedBy(Actor.require("SELLER").id());
    }
}
