package com.projecthk5.cartservice.service;

import com.projecthk5.cartservice.api.CartDtos.*;
import com.projecthk5.cartservice.catalog.CartCatalogPort;
import com.projecthk5.cartservice.entity.*;
import com.projecthk5.cartservice.repository.CartRepository;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CartService {
    private final CartRepository repository;
    private final CartCatalogPort catalog;
    public CartService(CartRepository repository, CartCatalogPort catalog) { this.repository=repository; this.catalog=catalog; }

    @Transactional
    public CartView view(String customerId) {
        return repository.findByCustomerId(customerId).map(this::response)
            .orElse(new CartView(customerId, 0, false, List.of(), List.of()));
    }
    @Transactional
    public CartView add(String customerId, AddItem request) {
        // Reject invalid direct-service callers before consulting external Catalog.
        if (request == null || request.productId() == null || request.productId().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Product ID required");
        if (request.quantity() < 1 || request.quantity() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quantity must be 1..1000");
        // Resolve authoritative store/seller; client cannot forge these fields.
        var product = catalog.resolve(request.productId());
        if (product == null || !request.productId().equals(product.productId())
                || product.storeId() == null || product.storeId().isBlank()
                || product.sellerId() == null || product.sellerId().isBlank())
            throw new IllegalStateException("Untrusted product catalog response");
        if (!product.available()) throw new IllegalStateException("Product unavailable");
        if (product.unitPrice() == null || product.unitPrice().signum()<0) throw new IllegalStateException("Invalid product price");
        // The current cart_items.unit_price_snapshot column is NUMERIC(19,2).
        // Reject a Catalog price that PostgreSQL would round or overflow BEFORE
        // mutating the cart; this is storage safety, not an M01 business policy.
        CartAmountStorageBounds.requireExactNonnegative(product.unitPrice());
        Cart cart = repository.findForUpdate(customerId).orElseGet(() -> new Cart(customerId));
        cart.ensureEditable();
        CartItem old = cart.getItems().stream().filter(it -> it.getProductId().equals(request.productId())).findFirst().orElse(null);
        if (old == null) cart.getItems().add(new CartItem(cart, product.productId(), product.storeId(), product.sellerId(), request.quantity(),product.unitPrice()));
        else {
            if (!old.getStoreId().equals(product.storeId()) || !old.getSellerId().equals(product.sellerId()) || old.getUnitPriceSnapshot().compareTo(product.unitPrice()) != 0)
                throw new IllegalStateException("Product ownership or price changed; remove and re-add");
            if (old.getQuantity() + request.quantity() > 1000) throw new IllegalStateException("Maximum quantity exceeded");
            old.setQuantity(old.getQuantity()+request.quantity());
        }
        cart.touch(); return response(repository.saveAndFlush(cart));
    }
    @Transactional
    public CartView update(String customerId,String productId,int quantity) {
        if (quantity < 1 || quantity > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quantity must be 1..1000");
        Cart cart = locked(customerId); cart.ensureEditable();
        CartItem item = findItem(cart,productId); item.setQuantity(quantity);
        cart.touch(); return response(repository.saveAndFlush(cart));
    }
    @Transactional
    public CartView remove(String customerId,String productId) {
        Cart cart = locked(customerId); cart.ensureEditable();
        CartItem item=findItem(cart,productId);cart.getItems().remove(item);
        cart.touch();return response(repository.saveAndFlush(cart));
    }
    @Transactional
    public CartView claim(String customerId,String key, Long revision) {
        Cart cart=locked(customerId); cart.claim(key,revision);return response(repository.saveAndFlush(cart));
    }
    @Transactional
    public void release(String customerId,String key) {
        Cart cart=locked(customerId);cart.release(key);repository.saveAndFlush(cart);
    }
    @Transactional
    public void finalizeCheckout(String customerId,String key) {
        Cart cart=locked(customerId);cart.finalizeCheckout(key);repository.saveAndFlush(cart);
    }
    private Cart locked(String customerId) { return repository.findForUpdate(customerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Cart not found")); }
    private CartItem findItem(Cart cart,String productId) { return cart.getItems().stream().filter(i -> i.getProductId().equals(productId)).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not in cart")); }
    private record GroupKey(String sellerId,String storeId) {}
    private CartView response(Cart c) {
        List<Item> items=c.getItems().stream().map(i -> new Item(i.getProductId(),i.getStoreId(),i.getSellerId(),i.getQuantity(),i.getUnitPriceSnapshot())).toList();
        Map<GroupKey,List<Item>> groups=items.stream().collect(Collectors.groupingBy(i -> new GroupKey(i.sellerId(),i.storeId()),LinkedHashMap::new,Collectors.toList()));
        List<StoreGroup> storeGroups=groups.values().stream().map(list -> new StoreGroup(list.get(0).storeId(),list.get(0).sellerId(),list)).toList();
        return new CartView(c.getCustomerId(),c.getRevision(),c.getCheckoutClaim()!=null,items,storeGroups);
    }
}
