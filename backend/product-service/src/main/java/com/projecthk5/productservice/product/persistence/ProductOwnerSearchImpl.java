package com.projecthk5.productservice.product.persistence;

import com.projecthk5.productservice.product.ProductOwnerPage;
import com.projecthk5.productservice.product.ProductOwnerPage.Row;
import com.projecthk5.productservice.product.ProductOwnerQuery;
import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.productservice.product.ProductStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Store restriction applies to count and rows in SQL, before pagination; no entity/private-field loading. */
public class ProductOwnerSearchImpl implements ProductOwnerSearch {
    private final EntityManager entityManager;

    public ProductOwnerSearchImpl(EntityManager entityManager) { this.entityManager = entityManager; }

    @Override
    public ProductOwnerPage listOwned(UUID storeId, ProductOwnerQuery filter) {
        if (storeId == null || filter == null) {
            throw new ProductRuleException(ProductRuleException.Code.INVALID_INPUT, "Store and owner-list criteria are required");
        }
        Integer isolation = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || isolation == null || isolation != TransactionDefinition.ISOLATION_REPEATABLE_READ
                && isolation != TransactionDefinition.ISOLATION_SERIALIZABLE) {
            throw new ProductPersistenceException(ProductPersistenceException.Code.UNAVAILABLE,
                    "Owner count and rows require one local snapshot transaction");
        }
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> count = cb.createQuery(Long.class);
            Root<ProductEntity> counted = count.from(ProductEntity.class);
            count.select(cb.count(counted)).where(restrictions(cb, counted, filter));
            long total = bind(entityManager.createQuery(count), storeId, filter).getSingleResult();
            if (filter.offset() >= total) return new ProductOwnerPage(List.of(), total, filter.page(), filter.pageSize());

            CriteriaQuery<Row> rows = cb.createQuery(Row.class);
            Root<ProductEntity> product = rows.from(ProductEntity.class);
            rows.select(cb.construct(Row.class, product.get("id"), product.get("storeId"), product.get("categoryId"),
                    product.get("inventorySku"), product.get("name"), product.get("unitPrice"), product.get("currency"),
                    product.get("status"), product.get("version")))
                    .where(restrictions(cb, product, filter))
                    .orderBy(cb.asc(product.get("name")), cb.asc(product.get("id")));
            List<Row> loaded = bind(entityManager.createQuery(rows), storeId, filter)
                    .setFirstResult(filter.offset()).setMaxResults(filter.pageSize()).getResultList();
            return new ProductOwnerPage(loaded, total, filter.page(), filter.pageSize());
        } catch (RuntimeException failure) {
            throw new ProductPersistenceException(ProductPersistenceException.Code.UNAVAILABLE,
                    "Product owner list is unavailable", failure);
        }
    }

    private Predicate restrictions(CriteriaBuilder cb, Root<ProductEntity> product, ProductOwnerQuery filter) {
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(cb.equal(product.get("storeId"), cb.parameter(UUID.class, "store")));
        if (filter.status() != null) conditions.add(cb.equal(product.get("status"), cb.parameter(ProductStatus.class, "status")));
        if (filter.term() != null) {
            var term = cb.lower(cb.parameter(String.class, "term"));
            // SKU's C collation preserves exact identity; search alone needs Unicode case folding like name.
            var searchableSku = ((HibernateCriteriaBuilder) cb).collate(product.get("inventorySku"), "\"default\"");
            conditions.add(cb.or(cb.like(cb.lower(product.get("name")), term, '\\'),
                    cb.like(cb.lower(searchableSku), term, '\\')));
        }
        return cb.and(conditions.toArray(Predicate[]::new));
    }

    private <T> TypedQuery<T> bind(TypedQuery<T> query, UUID storeId, ProductOwnerQuery filter) {
        query.setParameter("store", storeId);
        if (filter.status() != null) query.setParameter("status", filter.status());
        if (filter.term() != null) {
            String literal = filter.term().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            query.setParameter("term", "%" + literal + "%");
        }
        return query;
    }
}
