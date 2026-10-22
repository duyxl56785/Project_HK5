package com.projecthk5.sellerservice.store;

/** Internal service inputs, not an approved HTTP wire or an authorization source. */
public final class StoreInputs {
    private StoreInputs() { }

    public record Create(String name, String slug) {
        public Create {
            var details = new StoreDetails(name, slug);
            name = details.name();
            slug = details.slug();
        }
    }

    public record Update(String name, String slug, Long expectedVersion) {
        public Update {
            requireVersion(expectedVersion);
            var details = new StoreDetails(name, slug);
            name = details.name();
            slug = details.slug();
        }
    }

    public record SetOpen(Boolean open, Long expectedVersion) {
        public SetOpen {
            requireVersion(expectedVersion);
            if (open == null) {
                throw new StoreRuleException(StoreRuleException.Code.INVALID_INPUT, "Store open must be explicit");
            }
        }
    }

    private static void requireVersion(Long version) {
        if (version == null || version < 0) {
            throw new StoreRuleException(StoreRuleException.Code.INVALID_INPUT,
                    "A nonnegative expected version is required");
        }
    }
}
