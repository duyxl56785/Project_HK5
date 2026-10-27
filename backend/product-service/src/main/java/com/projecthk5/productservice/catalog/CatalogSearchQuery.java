package com.projecthk5.productservice.catalog;

import com.projecthk5.productservice.product.ProductPrice;
import com.projecthk5.productservice.product.ProductRuleException;
import java.math.BigDecimal;
import java.util.UUID;

/** Internal Product-candidate criteria, not a public HTTP/C01 contract. Price filters are explicitly VND. */
public record CatalogSearchQuery(String name, UUID categoryId, BigDecimal minimumVnd, BigDecimal maximumVnd,
                                 Sort sort, int page, int pageSize) {
    public static final int MAX_PAGE_SIZE = 100;
    public enum Sort { NAME_ASC, PRICE_ASC, PRICE_DESC }

    public CatalogSearchQuery {
        if (page < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE || (long) page * pageSize > Integer.MAX_VALUE
                || name != null && (name.length() > 255 || name.codePoints().anyMatch(
                        cp -> cp == 0 || cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE))) {
            throw invalid();
        }
        name = name == null || name.isBlank() ? null : name.strip();
        sort = sort == null ? Sort.NAME_ASC : sort;
        if (minimumVnd != null) ProductPrice.validate(minimumVnd, "VND");
        if (maximumVnd != null) ProductPrice.validate(maximumVnd, "VND");
        if (minimumVnd != null && maximumVnd != null && minimumVnd.compareTo(maximumVnd) > 0) throw invalid();
    }

    public int offset() { return (int) ((long) page * pageSize); }

    private static ProductRuleException invalid() {
        return new ProductRuleException(ProductRuleException.Code.INVALID_INPUT, "Invalid Product candidate search criteria");
    }
}
