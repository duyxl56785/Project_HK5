package com.projecthk5.productservice.product.persistence;

import com.projecthk5.productservice.catalog.CatalogCandidatePage;
import com.projecthk5.productservice.catalog.CatalogCandidatePage.Candidate;
import com.projecthk5.productservice.catalog.CatalogSearchQuery;
import com.projecthk5.productservice.category.Category;
import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.productservice.product.ProductStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Spring Data fragment for PRIVATE Product candidates; no Seller/Store eligibility or public total is implied. */
public class ProductCatalogSearchImpl implements ProductCatalogSearch {
    private final EntityManager entityManager;

    public ProductCatalogSearchImpl(EntityManager entityManager) { this.entityManager = entityManager; }

    @Override
    public CatalogCandidatePage searchCandidates(CatalogSearchQuery filter) {
        if (filter == null) throw new ProductRuleException(ProductRuleException.Code.INVALID_INPUT, "Search criteria are required");
        Integer isolation = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || isolation == null || isolation != TransactionDefinition.ISOLATION_REPEATABLE_READ
                && isolation != TransactionDefinition.ISOLATION_SERIALIZABLE) {
            throw new ProductPersistenceException(ProductPersistenceException.Code.UNAVAILABLE,
                    "Candidate count and rows require one local snapshot transaction");
        }
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> count = cb.createQuery(Long.class);
            Root<ProductEntity> counted = count.from(ProductEntity.class);
            count.select(cb.count(counted)).where(restrictions(cb, count, counted, filter));
            long total = bind(entityManager.createQuery(count), filter).getSingleResult();
            if (filter.offset() >= total) {
                return new CatalogCandidatePage(List.of(), total, filter.page(), filter.pageSize());
            }
            CriteriaQuery<Candidate> rows = cb.createQuery(Candidate.class);
            Root<ProductEntity> product = rows.from(ProductEntity.class);
            rows.select(cb.construct(Candidate.class, product.get("id"), product.get("storeId"),
                    product.get("categoryId"), product.get("inventorySku"), product.get("name"),
                    product.get("unitPrice"), product.get("currency")))
                    .where(restrictions(cb, rows, product, filter)).orderBy(ordering(cb, product, filter));
            List<Candidate> loaded = bind(entityManager.createQuery(rows), filter)
                    .setFirstResult(filter.offset()).setMaxResults(filter.pageSize()).getResultList();
            return new CatalogCandidatePage(loaded, total, filter.page(), filter.pageSize());
        } catch (RuntimeException failure) {
            throw new ProductPersistenceException(ProductPersistenceException.Code.UNAVAILABLE,
                    "Product candidate search is unavailable", failure);
        }
    }

    private Predicate restrictions(CriteriaBuilder cb, AbstractQuery<?> query, Root<ProductEntity> product,
                                    CatalogSearchQuery filter) {
        Root<Category> category = query.from(Category.class);
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(cb.equal(product.get("categoryId"), category.get("id")));
        conditions.add(cb.equal(product.get("status"), cb.parameter(ProductStatus.class, "active")));
        if (filter.name() != null) {
            conditions.add(cb.like(cb.lower(product.get("name")), cb.lower(cb.parameter(String.class, "name")), '\\'));
        }
        if (filter.categoryId() != null) conditions.add(cb.equal(product.get("categoryId"), cb.parameter(java.util.UUID.class, "category")));
        if (filter.minimumVnd() != null) conditions.add(cb.greaterThanOrEqualTo(product.get("unitPrice"), cb.parameter(BigDecimal.class, "min")));
        if (filter.maximumVnd() != null) conditions.add(cb.lessThanOrEqualTo(product.get("unitPrice"), cb.parameter(BigDecimal.class, "max")));
        return cb.and(conditions.toArray(Predicate[]::new));
    }

    private List<Order> ordering(CriteriaBuilder cb, Root<ProductEntity> product, CatalogSearchQuery filter) {
        Order primary = switch (filter.sort()) {
            case NAME_ASC -> cb.asc(product.get("name"));
            case PRICE_ASC -> cb.asc(product.get("unitPrice"));
            case PRICE_DESC -> cb.desc(product.get("unitPrice"));
        };
        return List.of(primary, cb.asc(product.get("id")));
    }

    private <T> TypedQuery<T> bind(TypedQuery<T> query, CatalogSearchQuery filter) {
        query.setParameter("active", ProductStatus.ACTIVE);
        if (filter.name() != null) {
            String literal = filter.name().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            query.setParameter("name", "%" + literal + "%");
        }
        if (filter.categoryId() != null) query.setParameter("category", filter.categoryId());
        if (filter.minimumVnd() != null) query.setParameter("min", filter.minimumVnd());
        if (filter.maximumVnd() != null) query.setParameter("max", filter.maximumVnd());
        return query;
    }
}
