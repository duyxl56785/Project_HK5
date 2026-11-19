package com.projecthk5.orderservice.service;

import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.entity.CheckoutAttempt;
import com.projecthk5.orderservice.ports.CartPort;
import com.projecthk5.orderservice.ports.CatalogPort;
import com.projecthk5.orderservice.ports.InventoryPort;
import com.projecthk5.orderservice.ports.VoucherPort;
import com.projecthk5.orderservice.ports.VoucherPort.ReleaseReason;
import com.projecthk5.orderservice.ports.VoucherPort.State;
import com.projecthk5.orderservice.repository.OrderRepository;
import com.projecthk5.orderservice.service.VoucherCheckoutJournal.Attempt;
import com.projecthk5.orderservice.service.VoucherCheckoutJournal.Lease;
import com.projecthk5.orderservice.service.VoucherCheckoutJournal.Phase;
import com.projecthk5.orderservice.service.VoucherCheckoutJournal.Progress;
import com.projecthk5.security.ApiException;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** C2 recovery reuses the immutable Order draft and the same Seller/Inventory operation IDs. */
@Service
public class VoucherCheckoutSaga {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(VoucherCheckoutSaga.class);
    private final VoucherCheckoutJournal journal;
    private final CheckoutDraftJournal drafts;
    private final VoucherPort vouchers;
    private final InventoryPort inventory;
    private final CartPort carts;
    private final CatalogPort catalog;
    private final OrderWriteService writer;
    private final OrderRepository orders;
    private final boolean enabled;

    public VoucherCheckoutSaga(VoucherCheckoutJournal journal, CheckoutDraftJournal drafts, VoucherPort vouchers,
                                InventoryPort inventory, CartPort carts, CatalogPort catalog, OrderWriteService writer,
                                OrderRepository orders, @Value("${hk5.voucher-c2.enabled:false}") boolean enabled) {
        this.journal = journal;
        this.drafts = drafts;
        this.vouchers = vouchers;
        this.inventory = inventory;
        this.carts = carts;
        this.catalog = catalog;
        this.writer = writer;
        this.orders = orders;
        this.enabled = enabled;
    }

    public boolean enabled() { return enabled; }

    public com.projecthk5.orderservice.domain.VoucherSnapshot quote(String store, String customer, String code,
                                                                   long merchandise) {
        if (!enabled) throw ApiException.conflict("VOUCHER_C2_DISABLED", "Voucher checkout is disabled");
        try {
            return vouchers.quote(UUID.fromString(store), customer, code, merchandise);
        } catch (VoucherPort.Rejected reject) {
            throw refused(reject.code());
        }
    }

    public Order place(UUID attemptId) {
        return place(attemptId, CheckoutAttempt.State.PROCESSING);
    }

    public Order place(UUID attemptId, CheckoutAttempt.State checkoutState) {
        run(attemptId);
        Attempt attempt = journal.find(attemptId).orElseThrow(() -> checkoutState == CheckoutAttempt.State.FAILED
                ? refused("VOUCHER_CHECKOUT_NOT_PLACED") : pending());
        if (attempt.phase() == Phase.ABORTED) throw refused(attempt.failure());
        if (attempt.phase() != Phase.PLACED) throw pending();
        return orders.findByIdAndCustomerId(attemptId, attempt.customer()).orElseThrow(VoucherCheckoutSaga::pending);
    }

    @Scheduled(fixedDelayString = "${hk5.voucher-c2.scan-ms:5000}",
            initialDelayString = "${hk5.voucher-c2.scan-ms:5000}")
    public void recover() {
        for (UUID attempt : journal.due()) run(attempt);
    }

    private void run(UUID id) {
        try {
            journal.acquire(id).ifPresent(lease -> {
                try { advance(lease); }
                finally { journal.relinquish(lease); }
            });
        } catch (RuntimeException error) {
            // Journal state, never a network exception, decides whether Checkout can release or return success.
            LOG.warn("Voucher checkout {} awaits reconciliation ({})", id, error.getClass().getSimpleName());
        }
    }

    private void advance(Lease lease) {
        Attempt attempt = current(lease);
        if (attempt.phase() == Phase.REDEEM) {
            redeem(lease, attempt);
            attempt = current(lease);
        }
        if (attempt.phase() == Phase.INVENTORY) {
            placeAfterInventory(lease, attempt);
            attempt = current(lease);
        }
        settleSeller(lease, attempt);
        if (!attempt.cartDone()) {
            journal.renew(lease);
            carts.recover(attempt.customer(), attempt.key(), attempt.revision(), attempt.phase() == Phase.PLACED);
            journal.cartDone(lease);
        }
    }

    private void redeem(Lease lease, Attempt attempt) {
        for (var step : journal.steps(attempt)) {
            var i = step.intent();
            if (step.progress() == Progress.RESERVED) continue;
            journal.renew(lease);
            if (step.progress() == Progress.PENDING)
                journal.step(lease, i.sellerOrderId(), Progress.PENDING, Progress.REDEEMING, null);
            try {
                var reservation = step.progress() == Progress.REDEEMING
                        ? vouchers.status(i).orElseGet(() -> vouchers.redeem(i))
                        : vouchers.redeem(i);
                if (reservation.state() != State.RESERVED) throw pending();
            } catch (VoucherPort.Rejected rejected) {
                journal.abort(lease, rejected.code());
                return;
            }
            journal.step(lease, i.sellerOrderId(), Progress.REDEEMING, Progress.RESERVED, null);
        }
        String changed = voucherCatalogChange(lease);
        if (changed == null) journal.startInventory(lease);
        else journal.abort(lease, changed);
    }

    /** A restart before Inventory must not use an expired, previously Promotion-free draft unchecked. */
    private String voucherCatalogChange(Lease lease) {
        var draft = drafts.load(lease.attemptId());
        for (var store : draft.sellerOrders()) {
            if (store.voucher() == null) continue;
            for (var line : store.items()) {
                journal.renew(lease);
                CatalogPort.ProductSnapshot facts;
                try { facts = catalog.verify(line.productId()); }
                catch (IllegalStateException gone) { return "VOUCHER_CHANGED"; }
                if (facts == null || facts.promotionApplied() == null) throw pending();
                if (facts.promotionApplied()) return "VOUCHER_PROMOTION_CONFLICT";
                if (!facts.available() || !facts.sellerApproved() || !facts.storeOpen()
                        || !line.productId().equals(facts.productId()) || !store.storeId().equals(facts.storeId())
                        || !store.sellerId().equals(facts.sellerId())
                        || !java.util.Objects.equals(line.sku(), facts.sku())
                        || facts.unitPrice() == null || line.unitPriceSnapshot().compareTo(facts.unitPrice()) != 0)
                    return "VOUCHER_CHANGED";
            }
        }
        return null;
    }

    private void placeAfterInventory(Lease lease, Attempt attempt) {
        var draft = drafts.load(attempt.id());
        var items = draft.sellerOrders().stream().flatMap(store -> store.items().stream())
                .map(item -> new InventoryPort.Reservation(item.productId(), item.quantity(), item.sku())).toList();
        journal.renew(lease);
        try {
            inventory.reserve(attempt.id(), items);
        } catch (InventoryPort.Rejected rejected) {
            journal.abort(lease, rejected.code());
            return;
        } catch (RuntimeException uncertain) {
            // Only positive Inventory ledger proof can advance a pending or UNKNOWN reserve.
            if (!inventory.confirmReserve(attempt.id(), items)) throw uncertain;
        }
        writer.saveVoucherPlaced(draft.restoreOrder(), draft.outboxPayload(), journal, lease);
    }

    private void settleSeller(Lease lease, Attempt attempt) {
        for (var step : journal.steps(attempt)) {
            if (step.progress() == Progress.RELEASED) continue;
            var i = step.intent();
            if (attempt.phase() == Phase.ABORTED) {
                journal.renew(lease);
                var outcome = vouchers.abort(i);
                requireRelease(outcome, ReleaseReason.CHECKOUT_FAILED);
                journal.step(lease, i.sellerOrderId(), step.progress(), Progress.RELEASED, outcome.releaseReason());
                continue;
            }
            if (attempt.phase() != Phase.PLACED) throw pending();
            Progress progress = step.progress();
            if (progress == Progress.RESERVED) {
                journal.renew(lease);
                var outcome = vouchers.status(i).orElseThrow(VoucherCheckoutSaga::pending);
                if (outcome.state() == State.RESERVED) outcome = vouchers.commit(i);
                if (outcome.state() == State.RELEASED && step.closure() != null) {
                    requireRelease(outcome, step.closure());
                    journal.step(lease, i.sellerOrderId(), progress, Progress.RELEASED, step.closure());
                    continue;
                }
                if (outcome.state() != State.COMMITTED) throw pending();
                journal.step(lease, i.sellerOrderId(), progress, Progress.COMMITTED, null);
                progress = Progress.COMMITTED;
            }
            if (progress != Progress.COMMITTED) throw pending();
            if (step.closure() != null) {
                journal.renew(lease);
                var outcome = vouchers.release(i, step.closure());
                requireRelease(outcome, step.closure());
                journal.step(lease, i.sellerOrderId(), progress, Progress.RELEASED, step.closure());
            }
        }
    }

    private Attempt current(Lease lease) {
        return journal.find(lease.attemptId()).orElseThrow(VoucherCheckoutSaga::pending);
    }

    private static void requireRelease(VoucherPort.Reservation reply, ReleaseReason reason) {
        if (reply.state() != State.RELEASED || reply.releaseReason() != reason) throw pending();
    }

    private static ApiException refused(String code) {
        return ApiException.conflict(code, "Checkout could not use the selected voucher or reserve stock");
    }

    private static ApiException pending() {
        return ApiException.unavailable("VOUCHER_CHECKOUT_PENDING", "Voucher checkout needs reconciliation. Retry the same request.");
    }
}
