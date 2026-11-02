package com.projecthk5.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.api.CheckoutDtos.*;
import com.projecthk5.orderservice.domain.*;
import com.projecthk5.orderservice.entity.*;
import com.projecthk5.orderservice.ports.*;
import com.projecthk5.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckoutService {
    private final CartPort carts;
    private final CatalogPort catalog;
    private final InventoryPort inventory;
    private final IdempotencyCoordinator keys;
    private final OrderWriteService writer;
    private final OrderRepository orders;
    private final ObjectMapper json;
    private final MoneyRules money;

    public CheckoutService(CartPort carts, CatalogPort catalog, InventoryPort inventory,
                           IdempotencyCoordinator keys, OrderWriteService writer,
                           OrderRepository orders, ObjectMapper json, MoneyRules money) {
        this.carts=carts; this.catalog=catalog; this.inventory=inventory; this.keys=keys;
        this.writer=writer; this.orders=orders; this.json=json; this.money=money;
    }

    public CheckoutResult checkout(String customerId, String bearer, String key, CheckoutRequest req) {
        return checkout(customerId, bearer, key, req, null);
    }

    public CheckoutResult checkout(String customerId, String bearer, String key, CheckoutRequest req, Order.Shipping shipping) {
        // Cart/Inventory HTTP must not occur under a caller's DB transaction; T1 needs a committed attempt FK.
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Checkout cannot run inside an outer database transaction");
        if (key == null || key.isBlank() || key.length()>128 || !key.equals(key.trim()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key required (1-128 characters)");
        if (req == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Checkout request required");
        try { CheckoutRules.requireValidExpectedItems(req.expectedItems()); }
        catch (IllegalStateException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid expectedItems",invalid);
        }
        String fingerprint=RequestFingerprint.compute(req);
        IdempotencyCoordinator.BeginResult started;
        try { started=keys.begin(customerId,key,fingerprint); }
        catch (DataIntegrityViolationException duplicate) {
            // The DB unique(customer_id,idempotency_key) arbitrates concurrent requests.
            throw new ResponseStatusException(HttpStatus.CONFLICT,"CHECKOUT_IN_PROGRESS_RETRY_LATER",duplicate);
        }
        CheckoutAttempt attempt=started.attempt();
        if (attempt.getState()==CheckoutAttempt.State.SUCCEEDED) {
            Order placed=orders.findByIdAndCustomerId(attempt.getOrderId(), customerId)
                .orElseThrow(() -> new IllegalStateException("Placed order needs reconciliation"));
            // A legacy idempotency fingerprint covers Cart revision/items but not the
            // separately supplied shipping address. Never replay a committed order
            // as a successful response to the same key with different shipping.
            // Compare persisted, controller-normalized facts without altering
            // historical fingerprints or re-running any external side effects.
            if (!Objects.equals(placed.getShipping(), shipping))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_PAYLOAD_MISMATCH");
            try { carts.finalizeCart(bearer,key); } catch (Exception ignored) {
                // Committed outcome is authoritative; leave cart cleanup for reconciliation.
            }
            return asResult(placed);
        }
        if (!started.created() || attempt.getState()!=CheckoutAttempt.State.PROCESSING)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"CHECKOUT_REQUIRES_RECONCILIATION");
        if (!attempt.getFingerprint().equals(fingerprint))
            throw new IllegalStateException("Fingerprint mismatch");

        // Durable attempt UUID is also the order/reservation correlation ID: repeated
        // calls or worker reconciliation can never manufacture a different order ID.
        UUID orderId=attempt.getId();
        boolean claimRequested=false, claimConfirmed=false, claimOwned=false;
        boolean reserveRequested=false, writeRequested=false;
        List<InventoryPort.Reservation> reservation=List.of();
        try {
            claimRequested=true;
            CartPort.CartSnapshot cart=carts.claim(bearer,key,req.expectedRevision());
            // A null reply cannot prove whether the remote Cart claim committed.
            // Keep the attempt for reconciliation instead of blindly releasing it.
            if (cart == null) throw new IllegalStateException("Cart claim outcome not confirmed");
            claimConfirmed=true;
            claimOwned=cart.claimed();
            if (!claimOwned) throw new IllegalStateException("Cart claim was not acquired");
            if (!customerId.equals(cart.customerId())) throw new IllegalStateException("Cart customer mismatch");
            CheckoutRules.requireSameItems(req.expectedItems(),cart.items());
            PreparedOrder prepared=prepareOrder(orderId,customerId,cart.items());
            Order order=prepared.order();
            order.ship(shipping);
            // Reserve exactly the verified lines, including the immutable SKU from Catalog.
            reservation=order.getSellerOrders().stream()
                 .flatMap(so -> so.getItems().stream())
                 .map(i -> new InventoryPort.Reservation(i.getProductId(),i.getQuantity(),i.getSku())).toList();
            // Serialize the deterministic Order facts before external Inventory side
            // effects. A local serialization failure is known-safe and can release
            // the confirmed Cart claim without inventing an UNKNOWN reservation.
            String outboxPayload=eventPayload(order);
            // Preserve the exact business graph and event ID before any external stock effect.
            // A restart can inspect it without repricing or minting new child/item IDs.
            writer.prepareDraft(CheckoutDraft.capture(order,fingerprint,req.expectedRevision(),cart.revision(),
                prepared.houseStores(),outboxPayload));
            // Any error/timeout after this point has UNKNOWN outcome. Never
            // automatically compensate after reserve or uncertain DB commit.
            reserveRequested=true;
            inventory.reserve(orderId,reservation);
            writeRequested=true;
            writer.savePlaced(order,attempt.getId(),outboxPayload);
            try { carts.finalizeCart(bearer,key); } catch (Exception ignored) {
                // Order is committed; finalize may be retried on same-key replay.
            }
            return asResult(order);
        } catch (Exception error) {
            if (writeRequested) {
                // Query can prove success, but failure to query proves nothing.
                try {
                    Optional<Order> committed=orders.findByIdAndCustomerId(orderId, customerId);
                    if (committed.isPresent()) {
                        try { carts.finalizeCart(bearer,key); } catch (Exception ignored) { }
                        return asResult(committed.get());
                    }
                } catch (Exception readFailure) {
                    error.addSuppressed(readFailure);
                }
            }
            // A definitive Inventory rejection proves no stock moved, so the Cart claim can be released.
            boolean reserveRejected=reserveRequested && !writeRequested && error instanceof InventoryPort.Rejected;
            boolean uncertain=(reserveRequested && !reserveRejected) || writeRequested
                || (claimRequested && !claimConfirmed && !(error instanceof CartPort.Rejected));
            if (!uncertain && claimConfirmed && claimOwned) {
                try { carts.release(bearer,key); }
                catch (Exception releaseFailure) {
                    uncertain=true;
                    error.addSuppressed(releaseFailure);
                }
            }
            // Mark unsafe attempts for durable operator reconciliation; never
            // silently reopen cart/refund stock if reservation/commit may exist.
            try { keys.fail(attempt.getId(),uncertain); }
            catch (Exception markFailure) { error.addSuppressed(markFailure); }
            if (uncertain)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "CHECKOUT_OUTCOME_UNKNOWN_RECONCILIATION_REQUIRED",error);
            // shortcut: Inventory stops at the first short SKU, so one line is named; report all of them there to list every line.
            if (error instanceof InventoryPort.Rejected rejected && "INSUFFICIENT_STOCK".equals(rejected.code()))
                for (InventoryPort.Reservation line:reservation)
                    if (line.sku()!=null && line.sku().equals(rejected.sku()))
                        throw new CheckoutItemsRejected(List.of(new CheckoutItemsRejected.Item(line.productId(),CheckoutItemsRejected.Reason.OUT_OF_STOCK)));
            throw error;
        }
    }

    private PreparedOrder prepareOrder(UUID id,String customer,List<CartPort.Item> items) {
        Order order=new Order(id,customer);
        Map<String,Boolean> houseStores=new LinkedHashMap<>();
        Map<String,List<Prepared>> grouped=new LinkedHashMap<>();
        // S01: authoritative Catalog facts cannot assign the same Seller to two
        // Stores within one checkout. Reject the whole batch before Inventory.
        Map<String,String> storeForSeller=new HashMap<>();
        List<CheckoutItemsRejected.Item> refused=new ArrayList<>();
        for (CartPort.Item item:items) {
            CatalogPort.ProductSnapshot product;
            // CatalogHttpAdapter throws IllegalStateException only for a definite "gone"; an outage stays a 503.
            try { product=catalog.verify(item.productId()); }
            catch (IllegalStateException gone) {
                refused.add(new CheckoutItemsRejected.Item(item.productId(),CheckoutItemsRejected.Reason.PRODUCT_UNAVAILABLE));
                continue;
            }
            CheckoutItemsRejected.Reason problem=problem(item,product);
            if (problem!=null) {
                refused.add(new CheckoutItemsRejected.Item(item.productId(),problem));
                continue;
            }
            String previouslyVerifiedStore=storeForSeller.putIfAbsent(product.sellerId(),product.storeId());
            if (previouslyVerifiedStore!=null && !previouslyVerifiedStore.equals(product.storeId()))
                throw new IllegalStateException("Catalog assigned one Seller to multiple Stores");
            // Storage exactness is independent of M01 currency, rates or business caps.
            // Reject known-invalid unit and line amounts before any Inventory call.
            OrderAmountStorageBounds.requireExactNonnegative(product.unitPrice());
            BigDecimal amount=MoneyRules.lineTotal(product.unitPrice(),item.quantity());
            OrderAmountStorageBounds.requireExactNonnegative(amount);
            grouped.computeIfAbsent(product.storeId(),ignore -> new ArrayList<>())
                .add(new Prepared(product,item.quantity(),amount));
        }
        if (!refused.isEmpty()) throw new CheckoutItemsRejected(refused);
        for (var entry:grouped.entrySet()) {
            List<Prepared> lines=entry.getValue();
            Prepared first=lines.get(0);
            if (lines.stream().anyMatch(i -> !i.product.sellerId().equals(first.product.sellerId())
                || i.product.houseStore()!=first.product.houseStore()))
                throw new IllegalStateException("Inconsistent store ownership");
            BigDecimal gross=lines.stream().map(Prepared::total).reduce(BigDecimal.ZERO,BigDecimal::add);
            OrderAmountStorageBounds.requireExactNonnegative(gross);
            BigDecimal fee=money.fee(gross,first.product.houseStore());
            OrderAmountStorageBounds.requireExactNonnegative(fee);
            if (fee.compareTo(gross)>0) throw new IllegalArgumentException("Platform fee exceeds Store subtotal");
            // Net is currently derived, not persisted; guard it for future V2+ snapshots.
            OrderAmountStorageBounds.requireExactNonnegative(gross.subtract(fee));
            SellerOrder so=new SellerOrder(order,first.product.sellerId(),entry.getKey(),gross,fee);
            lines.forEach(line -> so.getItems().add(new OrderItem(so,line.product.productId(),
                line.quantity,line.product.unitPrice(),line.total,line.product.sku(),line.product.name())));
            order.addSellerOrder(so);
            houseStores.put(entry.getKey(),first.product.houseStore());
        }
        // Two Store subtotals may fit NUMERIC(19,2) individually while the parent
        // aggregate does not. The caller has not set reserveRequested yet.
        OrderAmountStorageBounds.requireExactNonnegative(order.getTotal());
        return new PreparedOrder(order,Map.copyOf(houseStores));
    }
    private static CheckoutItemsRejected.Reason problem(CartPort.Item item,CatalogPort.ProductSnapshot product) {
        if (!product.available()) return CheckoutItemsRejected.Reason.PRODUCT_UNAVAILABLE;
        if (!product.sellerApproved() || !product.storeOpen()) return CheckoutItemsRejected.Reason.STORE_UNAVAILABLE;
        if (!product.productId().equals(item.productId()) || !product.storeId().equals(item.storeId())
                || !product.sellerId().equals(item.sellerId())) return CheckoutItemsRejected.Reason.CATALOG_CHANGED;
        if (item.unitPriceSnapshot()==null || product.unitPrice().compareTo(item.unitPriceSnapshot())!=0)
            return CheckoutItemsRejected.Reason.PRICE_CHANGED;
        return null;
    }
    private record PreparedOrder(Order order,Map<String,Boolean> houseStores){}
    private record Prepared(CatalogPort.ProductSnapshot product,int quantity,BigDecimal total){}
    private String eventPayload(Order order) {
        try { return json.writeValueAsString(Map.of("eventId",UUID.randomUUID().toString(),
            "orderId",order.getId().toString(),"customerId",order.getCustomerId(),
            "sellerOrderCount",order.getSellerOrders().size())); }
        catch(JsonProcessingException e){throw new IllegalStateException("Unable to write outbox event",e);}
    }
    private CheckoutResult asResult(Order order){
        return new CheckoutResult(order.getId(),order.getStatus().name(),order.getTotal(),order.getSellerOrders().size());
    }
}
