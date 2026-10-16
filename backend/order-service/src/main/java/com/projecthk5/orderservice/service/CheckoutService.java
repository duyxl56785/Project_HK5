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
    public CheckoutService(CartPort carts,CatalogPort catalog,InventoryPort inventory,IdempotencyCoordinator keys,
                           OrderWriteService writer,OrderRepository orders,ObjectMapper json){
        this.carts=carts;this.catalog=catalog;this.inventory=inventory;this.keys=keys;this.writer=writer;this.orders=orders;this.json=json;
    }
    public CheckoutResult checkout(String customerId,String bearer,String key,CheckoutRequest req) {
        if (key == null || key.isBlank() || key.length()>128 || !key.equals(key.trim()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key required (1-128 characters)");
        // Validate duplicates before external work, not merely after JWT authentication.
        CheckoutRules.requireSameItems(req.expectedItems(),req.expectedItems().stream()
            .map(i -> new CartPort.Item(i.productId(),"","",i.quantity(),null)).toList());
        var fingerprint=RequestFingerprint.compute(req);
        IdempotencyCoordinator.BeginResult started;
        try {started=keys.begin(customerId,key,fingerprint);}
        catch(DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Checkout key is being processed",e);}
        CheckoutAttempt attempt=started.attempt();
        if(attempt.getState()==CheckoutAttempt.State.SUCCEEDED){
            Order prior=orders.findById(attempt.getOrderId()).orElseThrow(() -> new IllegalStateException("Placed order missing"));
            try {carts.finalizeCart(bearer,key);} catch(Exception ignored) { /* safe to retry on next replay */ }
            return asResult(prior);
        }
        if(!started.created() || attempt.getState()!=CheckoutAttempt.State.PROCESSING || attempt.getOrderId()!=null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Checkout already processing or requires reconciliation");
        // A brand-new attempt is the only state allowed to proceed. If there is an
        // existing PROCESSING request, begin() deliberately does not return it as new.
        if(!attempt.getFingerprint().equals(fingerprint)) throw new IllegalStateException("Fingerprint mismatch");
        UUID orderId=UUID.randomUUID();
        boolean claimStarted=false;
        boolean reserveStarted=false;
        try {
            claimStarted=true;
            CartPort.CartSnapshot cart=carts.claim(bearer,key,req.expectedRevision());
            if(!customerId.equals(cart.customerId())) throw new IllegalStateException("Cart customer mismatch");
            CheckoutRules.requireSameItems(req.expectedItems(),cart.items());
            Order order=prepareOrder(orderId,customerId,cart.items());
            List<InventoryPort.Reservation> reservation=cart.items().stream()
                 .map(i -> new InventoryPort.Reservation(i.productId(),i.quantity())).toList();
            reserveStarted=true;inventory.reserve(orderId,reservation);
            String eventJson=eventPayload(order);
            writer.savePlaced(order,attempt.getId(),eventJson);
            // Order and idempotency are committed atomically; retry cannot create another.
            try {carts.finalizeCart(bearer,key);} catch(Exception ignored) { /* documented manual/retry reconciliation */ }
            return asResult(order);
        } catch(Exception error){
            boolean uncertain=false;
            if(reserveStarted){
                try {inventory.release(orderId);} catch(Exception releaseError){uncertain=true;}
            }
            if(claimStarted){
                try {carts.release(bearer,key);} catch(Exception releaseError){uncertain=true;}
            }
            keys.fail(attempt.getId(),uncertain);
            throw error;
        }
    }
    private Order prepareOrder(UUID id,String customer,List<CartPort.Item> items){
        Order order=new Order(id,customer);
        Map<String,List<Prepared>> grouped=new LinkedHashMap<>();
        for (CartPort.Item item:items){
            CatalogPort.ProductSnapshot product=catalog.verify(item.productId());
            if(!product.available() || !product.sellerApproved() || !product.storeOpen())
                throw new IllegalStateException("Product, seller or store not available");
            if(!product.productId().equals(item.productId()) || !product.storeId().equals(item.storeId()) || !product.sellerId().equals(item.sellerId()))
                throw new IllegalStateException("Catalog changed since cart creation");
            if (item.unitPriceSnapshot() == null || product.unitPrice().compareTo(item.unitPriceSnapshot()) != 0)
                throw new IllegalStateException("Product price changed since adding to cart");
            BigDecimal amount=MoneyRules.lineTotal(product.unitPrice(),item.quantity());
            grouped.computeIfAbsent(product.storeId(),id2 -> new ArrayList<>()).add(new Prepared(product,item.quantity(),amount));
        }
        for(var entry:grouped.entrySet()){
            List<Prepared> lines=entry.getValue();
            Prepared first=lines.get(0);
            if(lines.stream().anyMatch(i -> !i.product.sellerId().equals(first.product.sellerId()) || i.product.houseStore()!=first.product.houseStore()))
                throw new IllegalStateException("Inconsistent store ownership");
            BigDecimal gross=lines.stream().map(Prepared::total).reduce(BigDecimal.ZERO,BigDecimal::add);
            SellerOrder so=new SellerOrder(order,first.product.sellerId(),entry.getKey(),gross,MoneyRules.fee(gross,first.product.houseStore()));
            lines.forEach(line -> so.getItems().add(new OrderItem(so,line.product.productId(),line.quantity,line.product.unitPrice(),line.total)));
            order.addSellerOrder(so);
        }
        return order;
    }
    private record Prepared(CatalogPort.ProductSnapshot product,int quantity,BigDecimal total){}
    private String eventPayload(Order order){
        try {return json.writeValueAsString(Map.of("eventId",UUID.randomUUID().toString(),"orderId",order.getId().toString(),"customerId",order.getCustomerId(),"sellerOrderCount",order.getSellerOrders().size()));}
        catch(JsonProcessingException e){throw new IllegalStateException("Unable to write outbox event",e);}
    }
    private CheckoutResult asResult(Order order){return new CheckoutResult(order.getId(),order.getStatus().name(),order.getTotal(),order.getSellerOrders().size());}
}
