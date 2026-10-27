package com.projecthk5.orderservice.repository;

import com.projecthk5.orderservice.api.OrderHistoryDtos.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Scalar SQL projections: no paginated collection fetch, lazy loading or per-Order queries. */
@Repository
public class OrderHistoryRepository {
    private final JdbcTemplate db;
    public OrderHistoryRepository(JdbcTemplate db) { this.db = db; }

    public Page list(String customerId, int page, int size) {
        long total = db.queryForObject("select count(*) from orders where customer_id=?", Long.class, customerId);
        List<Summary> content = db.query("""
                select o.id,o.status,o.total,o.created_at,
                  (select count(*) from seller_orders s where s.order_id=o.id) as seller_order_count
                from orders o where o.customer_id=?
                order by o.created_at desc,o.id desc limit ? offset ?
                """, (rs, row) -> summary(rs), customerId, size, (long) page * size);
        return new Page(content, page, size, total, total / size + (total % size == 0 ? 0 : 1));
    }

    public Optional<Detail> detail(String customerId, UUID orderId) {
        var roots = db.query("""
                select o.id,o.status,o.total,o.created_at,0 as seller_order_count
                from orders o where o.id=? and o.customer_id=?
                """, (rs, row) -> summary(rs), orderId, customerId);
        if (roots.isEmpty()) return Optional.empty();
        Summary order = roots.get(0);
        // Scope the child query too; never accept a Store/Customer identity from the request.
        List<StoreOrder> stores = db.query("""
                select s.id as seller_order_id,s.store_id,s.gross,
                       i.id as item_id,i.product_id,i.quantity,i.unit_price_snapshot,i.line_total
                from orders o join seller_orders s on s.order_id=o.id
                left join order_items i on i.seller_order_id=s.id
                where o.id=? and o.customer_id=? order by s.id,i.id
                """, rs -> {
                    var grouped = new LinkedHashMap<UUID, StoreRows>();
                    while (rs.next()) {
                        UUID id = rs.getObject("seller_order_id", UUID.class);
                        StoreRows store = grouped.get(id);
                        if (store == null) {
                            store = new StoreRows(id, rs.getString("store_id"), decimal(rs, "gross"));
                            grouped.put(id, store);
                        }
                        if (rs.getObject("item_id") != null) {
                            store.items.add(new Item(rs.getString("product_id"), rs.getInt("quantity"),
                                    decimal(rs, "unit_price_snapshot"), decimal(rs, "line_total")));
                        }
                    }
                    return grouped.values().stream().map(s -> new StoreOrder(s.id, s.storeId, s.gross, s.items)).toList();
                }, orderId, customerId);
        return Optional.of(new Detail(order.orderId(), order.status(), order.total(), order.createdAt(), stores));
    }

    private static Summary summary(ResultSet rs) throws SQLException {
        return new Summary(rs.getObject("id", UUID.class), rs.getString("status"), decimal(rs, "total"),
                rs.getTimestamp("created_at").toInstant(), rs.getLong("seller_order_count"));
    }
    private static String decimal(ResultSet rs, String column) throws SQLException {
        return rs.getBigDecimal(column).toPlainString();
    }
    private static final class StoreRows {
        final UUID id;
        final String storeId;
        final String gross;
        final List<Item> items = new ArrayList<>();
        StoreRows(UUID id, String storeId, String gross) { this.id=id; this.storeId=storeId; this.gross=gross; }
    }
}
