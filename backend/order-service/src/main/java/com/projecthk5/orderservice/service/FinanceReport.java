package com.projecthk5.orderservice.service;

import com.projecthk5.orderservice.entity.SellerOrder;
import com.projecthk5.orderservice.repository.SellerOrderRepository;
import com.projecthk5.orderservice.ports.StoreDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Finance over delivered Store orders, using only the amounts snapshotted at checkout
 * (gross, platform fee, net), never a recalculation with today's policy.
 */
@Service
public class FinanceReport {
    private final SellerOrderRepository sellerOrders;
    private final StoreDirectory stores;

    public FinanceReport(SellerOrderRepository sellerOrders, StoreDirectory stores) {
        this.sellerOrders = sellerOrders;
        this.stores = stores;
    }

    public record Row(UUID storeOrderId, UUID orderId, BigDecimal gross, BigDecimal platformFee, BigDecimal sellerNet,
                      String codStatus, Instant deliveredAt, Instant codCollectedAt, Instant settledAt) {}
    public record Totals(long deliveredOrders, BigDecimal gross, BigDecimal platformFee, BigDecimal sellerNet,
                         BigDecimal codCollected, BigDecimal codPending, BigDecimal settledNet, BigDecimal payableNet) {}
    public record StoreSummary(String storeId, String storeName, Totals totals, List<Row> rows) {}
    public record Platform(Totals totals, List<StoreSummary> stores) {}

    public StoreSummary forStore(String storeId, String storeName) {
        List<SellerOrder> delivered = sellerOrders.findDelivered(storeId);
        return new StoreSummary(storeId, storeName, totals(delivered), delivered.stream().map(FinanceReport::row).toList());
    }

    public Platform platform() {
        List<SellerOrder> delivered = sellerOrders.findDelivered(null);
        Map<String, List<SellerOrder>> byStore = delivered.stream()
                .collect(Collectors.groupingBy(SellerOrder::getStoreId, LinkedHashMap::new, Collectors.toList()));
        Map<String, String> names = names(byStore.keySet());
        List<StoreSummary> perStore = byStore.entrySet().stream()
                .map(e -> new StoreSummary(e.getKey(), names.get(e.getKey()), totals(e.getValue()),
                        e.getValue().stream().map(FinanceReport::row).toList()))
                .sorted(Comparator.comparing((StoreSummary s) -> s.totals().gross()).reversed())
                .toList();
        return new Platform(totals(delivered), perStore);
    }

    static Totals totals(List<SellerOrder> delivered) {
        BigDecimal gross = BigDecimal.ZERO, fee = BigDecimal.ZERO, net = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO, pending = BigDecimal.ZERO, settled = BigDecimal.ZERO, payable = BigDecimal.ZERO;
        for (SellerOrder so : delivered) {
            gross = gross.add(so.getGross());
            fee = fee.add(so.getPlatformFee());
            net = net.add(so.getSellerNet());
            if (so.getCodStatus() == SellerOrder.CodStatus.COLLECTED) {
                collected = collected.add(so.getGross());
                if (so.getSettledAt() != null) settled = settled.add(so.getSellerNet());
                else payable = payable.add(so.getSellerNet());
            } else {
                pending = pending.add(so.getGross());
            }
        }
        return new Totals(delivered.size(), gross, fee, net, collected, pending, settled, payable);
    }

    private static Row row(SellerOrder so) {
        return new Row(so.getId(), so.getOrder().getId(), so.getGross(), so.getPlatformFee(), so.getSellerNet(),
                so.getCodStatus().name(), so.getDeliveredAt(), so.getCodCollectedAt(), so.getSettledAt());
    }

    private Map<String, String> names(Set<String> storeIds) {
        try {
            return stores.facts(storeIds).stream().collect(Collectors.toMap(StoreDirectory.Store::storeId, StoreDirectory.Store::name));
        } catch (RuntimeException unavailable) {
            return Map.of();
        }
    }
}
