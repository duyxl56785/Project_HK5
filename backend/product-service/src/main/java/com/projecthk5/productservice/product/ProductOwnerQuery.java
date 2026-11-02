package com.projecthk5.productservice.product;

/** Internal owner criteria; Store scope comes exclusively from verified current policy. */
public record ProductOwnerQuery(String term, ProductStatus status, int page, int pageSize) {
    public ProductOwnerQuery {
        if (page < 0 || pageSize < 1 || pageSize > 100 || (long) page * pageSize > Integer.MAX_VALUE
                || term != null && (term.length() > 255 || term.codePoints().anyMatch(
                        cp -> cp == 0 || cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE))) {
            throw new ProductRuleException(ProductRuleException.Code.INVALID_INPUT, "Invalid Product owner-list criteria");
        }
        term = term == null || term.isBlank() ? null : term.strip();
    }

    public int offset() { return (int) ((long) page * pageSize); }
}
