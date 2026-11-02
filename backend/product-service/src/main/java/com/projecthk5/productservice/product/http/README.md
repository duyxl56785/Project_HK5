# Product management HTTP preparation

Private preparation on top of ProductApplicationService (#89). There is **no controller, route, Spring handler/authority bean, JWT adapter or live HTTP acceptance** in this package. The existing Product deny-default security remains in force. No C01/E01 Catalog contract is changed.

`ProductManagementHandler` supports create, edit, submit, approve, reject, suspend, deactivate and owner-read by ID. Each operation obtains the exact current actor from `ProductManagementAuthority` before input parsing or Product lookup. It then uses the existing application service/policy and REQUIRED transactions. A role/owner in JSON is never accepted. A valid-looking actor string alone does not supply authorization: the future ProductAccessPolicy must verify current roles and Seller/Store ownership/status.

Prepared JSON shapes (subject to TV1/consumer review before any HTTP registration):

| Operation | Exact fields |
| --- | --- |
| Create | storeId, categoryId, sku, name, unitPrice, currency |
| Edit | categoryId, name, unitPrice, currency, expectedVersion |
| Submit/approve/deactivate | expectedVersion |
| Reject/suspend | expectedVersion, reason |

Product ID is a separate typed resource argument, not mutable body identity. UUIDs use complete standard text; unitPrice is an explicit canonical decimal **string** in 1..1000000000 with exact currency VND, no default/coercion/rounding. expectedVersion is a canonical nonnegative Java-long decimal **string**, never a JavaScript Number. Duplicate/unknown/null/missing/trailing JSON fields, malformed Unicode and NUL are rejected. Domain remains responsible for name/SKU/state/reason rules and canonicalization.

Response allowlist: id, storeId, categoryId, sku, name, unitPrice, currency, status, version. Price/version remain exact strings. Raw entities, reviewReason and ownerReference are not serialized. Error mapping uses fixed sanitized messages; it is preparation, not ControllerAdvice or an approved wire/error contract. Exceptions must leave the proxied application transaction; do not swallow a failed transaction or reinterpret an uncertain write as confirmed success.

The Product UI projection (#95) has corresponding scalar fields, but this package does not provide its Store-scoped list/reload receipt adapter. Before runtime integration, TV1/TV2 must supply verified current identity/role and current ProductAccessPolicy, reviewed paths/methods/DTO/errors, Gateway security and negative HTTP tests. The host must obtain a complete authoritative Store-scoped reload before issuing a UI reload receipt. No cached array/version/role is proof of freshness or ownership.

Still absent: Store-scoped Product listing, image upload/association, public eligibility/search HTTP, House authority, cross-service freshness, live JWT/Gateway/PostgreSQL/Checkout E2E. PostgreSQL tests here use real Product V1/V2 but **test-only identity/Store-policy fixtures**. They do not verify Auth or Seller HTTP integration.

Verification: `backend/gradlew.bat -p backend :product-service:build --rerun-tasks --no-daemon --console=plain`, Java21/Docker/PostgreSQL16.6 Testcontainers. No migration, shared CI, Auth/Gateway or Finance/Inventory flag is changed.
