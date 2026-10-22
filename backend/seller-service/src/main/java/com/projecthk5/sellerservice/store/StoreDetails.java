package com.projecthk5.sellerservice.store;

/** The existing Store name/slug rules, shared by domain snapshots and service inputs. */
public record StoreDetails(String name, String slug) {
    public StoreDetails {
        if (name == null || name.isBlank() || name.strip().length() > 255
                || slug == null || slug.length() > 128 || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new StoreRuleException(StoreRuleException.Code.INVALID_INPUT, "Invalid store details");
        }
        name = name.strip();
    }
}
