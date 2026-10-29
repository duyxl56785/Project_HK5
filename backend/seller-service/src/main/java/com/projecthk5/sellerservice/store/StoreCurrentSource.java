package com.projecthk5.sellerservice.store;

import com.projecthk5.sellerservice.seller.domain.SellerStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Trusted same-database facts only. No HTTP route, House inference, recipient or actor authorization. */
@Component
@Transactional(readOnly = true, propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
public class StoreCurrentSource {
    private final StoreRepository stores;

    public StoreCurrentSource(StoreRepository stores) { this.stores = Objects.requireNonNull(stores); }

    public record Facts(UUID storeId, UUID sellerId, StoreStatus storeStatus, SellerStatus sellerStatus,
                        long storeVersion, long sellerVersion) {
        public Facts {
            if (storeId == null || sellerId == null || storeStatus == null || sellerStatus == null
                    || storeVersion < 0 || sellerVersion < 0) {
                throw new StoreRuleException(StoreRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                        "Incomplete current Store/Seller facts");
            }
        }

        public boolean eligible() { return storeStatus == StoreStatus.OPEN && sellerStatus == SellerStatus.APPROVED; }
    }

    public Optional<Facts> findById(UUID id) {
        requireCurrentRead();
        if (id == null) throw invalid();
        return stores.findFactsById(id);
    }

    public Optional<Facts> findBySellerId(UUID sellerId) {
        requireCurrentRead();
        if (sellerId == null) throw invalid();
        return stores.findFactsBySellerId(sellerId);
    }

    public Optional<Facts> findBySlug(String slug) {
        requireCurrentRead();
        if (slug == null || slug.length() > 128 || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw invalid();
        return stores.findFactsBySlug(slug);
    }

    public record OwnedFacts(UUID storeId, UUID sellerId, StoreStatus storeStatus, SellerStatus sellerStatus,
                             long storeVersion, long sellerVersion, String ownerReference) {
        public OwnedFacts {
            new Facts(storeId,sellerId,storeStatus,sellerStatus,storeVersion,sellerVersion);
            if(ownerReference==null || ownerReference.isBlank() || ownerReference.codePointCount(0,ownerReference.length())>128)
                throw new StoreRuleException(StoreRuleException.Code.AUTHORIZATION_UNAVAILABLE,"Incomplete Store owner mapping");
        }
    }

    public java.util.List<OwnedFacts> findBatch(java.util.List<UUID> ids) {
        requireCurrentRead();
        if(ids==null || ids.isEmpty() || ids.size()>100 || ids.stream().anyMatch(Objects::isNull)
                || new java.util.HashSet<>(ids).size()!=ids.size())throw invalid();
        return java.util.List.copyOf(stores.findOwnedFactsByIds(ids));
    }
    private void requireCurrentRead() {
        Integer isolation = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if (isolation != null && isolation != TransactionDefinition.ISOLATION_READ_COMMITTED) {
            throw new StoreRuleException(StoreRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                    "Current facts require a READ_COMMITTED transaction, not a retained snapshot");
        }
    }

    private StoreRuleException invalid() {
        return new StoreRuleException(StoreRuleException.Code.INVALID_INPUT, "Store lookup requires a valid key");
    }
}
