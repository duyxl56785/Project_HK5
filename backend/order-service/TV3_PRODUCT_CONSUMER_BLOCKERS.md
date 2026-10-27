# TV3 Product -> Cart -> Checkout -> Inventory: local consumer increment

Base: main `1833dd4e5baf61683adaea34a54b6bb9a239e43d`, inspected 27/10/2026.
Dedicated branch: `feat/tv3-catalog-sku-reserve-intent`. The user authorized new
branches/PRs on 27/10/2026, superseding the former NO_SAFE_EXISTING_PR_FOR_CHANGE
publication restriction. PR77/78 have merged; PR86 remains frontend-only.
Refs Issues #3/#10/#16. Draft scope is only the Order-local mapping and tests below;
live provider integration remains BLOCKED. No owner-file edit or flag change.

## Executable change and exact boundary

`catalog/CatalogReservationPlan.reserveIntent` joins claimed Cart quantities to
provider-owned SKUs by Product identity, after binding expectations to that Cart
and running the complete existing `CatalogCheckoutBatchPreflight`. Cart order
may differ from request order; Catalog order still follows the signed-off-at-design
ordered candidate. No partial READY subset, Product-ID fallback, SKU normalization,
default currency or remapping on journal replay is permitted.

The returned immutable `InventoryOperationIntent` can be saved through the existing
Order journal. New PostgreSQL tests prove exact SKU/quantity round-trip, stable-ID
replay/conflict, and actual disabled transport remaining BLOCKED. Quantities use
Cart's existing 1..1000 bound; provider long quantities do not relax Cart validation.

This is an Order-local mapping implementation, **not a live integration**. It has
no Spring bean, positive HTTP adapter, actor verification, journal side effect or
network call. It is not injected into CheckoutService. Source records in the new
tests are fixtures, not current Product/Seller/House authority.

## Trace of the still-disabled legacy path

- `service/CheckoutService.java`, reservation construction after `prepareOrder`:
  `new InventoryPort.Reservation(i.productId(), i.quantity())`.
- `ports/InventoryPort.java`: `Reservation(String productId, int quantity)` and
  orderId correlation, not SKU/step-level operationId. This cannot be adapted
  directly to TV4's SKU-only operations endpoint.
- Order `CatalogPort.ProductSnapshot` lacks inventorySku/currency; Cart
  `CartCatalogPort.ProductSnapshot` also lacks both. Cart V1 DTO/storage lacks
  currency. Do not invent a default or retrofit a shared DTO/migration without review.
- `UnconfiguredCatalogAdapter`, `UnconfiguredCartCatalog`,
  `UnconfiguredInventoryAdapter` and `UnconfiguredInventoryOperationTransport`
  remain unchanged and fail-closed. The legacy reservation construction remains
  to be replaced during approved orchestration work; this patch does not disguise
  Product IDs as SKUs or claim that line has been fixed in live Checkout.

## Provider evidence and approval scope

PR90 latest inspected HEAD: `0f9c0c84a310bcfcf86f06592b64aed6690258dd`, OPEN.
Its latest main synchronization preserves Product production source relative to
`273a44db9a24e5aac69e7d9f9e8e0208c9f963b7`. TV1/TV5 formal approvals inspected at
273a44d cover INTERNAL CATALOG BATCH SOURCE ONLY, not C01/E01,
positive HTTP, current actor/House authority, freshness or runtime flags.
PR77 helper and PR78 regression are now merged; their former Draft notes are history.

Already real on main: ProductEntity.inventorySku and Flyway V2 global UNIQUE SKU,
SKU immutability, Product price/currency persistence; Seller StoreCurrentSource
reads current local Store/Seller ownership/status/version. Missing House is not false.

PR90 adds ProductCatalogSnapshotSource/ProductRepository.findCatalogRows and
CurrentStoreFactsSource. Source is deliberately unregistered, depends on verified
current Store/Seller/House facts, and distinguishes confirmed ABSENT from UNAVAILABLE.
It is not a callable Catalog API. ProductSecurityConfiguration still denies
unconfigured routes. Never import Product repositories into Order or join Seller DB.

## Concrete blockers and decision owners

| Boundary | Existing evidence | Required decision/delivery before live connection | Decision owner |
|---|---|---|---|
| API / DTO C01 | Candidate POST `/internal/catalog/checkout/validate`, `catalog.checkout.v1`; 1..100 distinct IDs, ordered complete results, all-or-nothing READY. Internal Java records are not HTTP DTOs. | Final same-version request/response, canonical UUID strings, decimal-string price/currency, strict unknown/null fields and ordering; implemented authenticated route and consumer/provider contract tests. | TV2 provider + TV3 consumer; TV1 boundary/privacy |
| Errors E01 | Candidate complete evaluation 200; invalid/auth/unavailable mappings remain proposals; confirmed ABSENT differs from partial/stale/unavailable source. | Agree envelope/status/challenge and concealment of unauthorized/private Products. Test 400/401/403/503 and no READY subset; never map dependency failure to Product absence. | TV2 + TV3 + TV1 |
| Actor / service pair | OD09 design baseline; no approved positive Order->Catalog verifier. | Dedicated caller pair identity/credential rotation and POST-only allowlist, original verified customer Bearer/current actor, server-side scope/privacy. Expected Store/Seller IDs never grant authority. Cart add also needs an approved Cart->Catalog lookup path/actor; Checkout comparison endpoint cannot be assumed suitable. | TV1 + TV2 + TV3 |
| Product ID -> SKU | Product DB stores immutable unique inventorySku. PR90 internal source projects it correctly. | Owner integration/post-merge verification of PR90, plus approved reachable adapter preserving exact SKU. Order must never derive SKU from Product ID or regenerate it from Cart. | TV2 delivers source; TV3 consumer |
| Store/Seller/House authority | Main has same-database StoreCurrentSource with current statuses/owner/version. No authenticated cross-service provider or House assignment authority follows from that class. | TV2 delivers verified CurrentStoreFactsSource implementation using approved service boundary; complete current Store/Seller facts plus explicit privileged House fact. Unknown/missing House stays unavailable. No cross-DB join, ownerReference leakage or fixture `current=true` as proof. | TV2 provider + TV1 authority/privacy; TV5 House policy |
| Cart expectations / money | Cart V1 has Product/Store/Seller/price but no currency; new mapper refuses missing expected currency. | Versioned Cart/DTO/storage transition or another explicitly approved expectation source, including existing rows; no silent currency backfill. Full Checkout fee/House policy separate from Product price-slice approval. | TV3 Cart/Order + TV2 price/currency + TV5 applicable policy |
| Freshness / MD07 | No lease/quote redemption; last-verified-snapshot + Phase A/B is only a proposal. Two reads do not freeze Product/Store/Seller/House/Category. | Named accountable owner chooses tolerated observation/commit race or a real cross-source lease protocol; define phase-B changes and failure handling after RESERVE. No invented TTL or version string as guarantee. | TV1 + TV5/named business owner; TV2/TV3/TV4 implementation |
| Inventory continuation | Local mapper produces exact RESERVE intent; existing journal has T1/T2 and generation fencing; positive transport OFF. | Replace legacy Product-ID port only after above gates. Commit step ID/type/exact SKU/qty before HTTP; replay only saved tuple. Approve actor/pair, reservation ownership, remaining-held proof, C06 RELEASE/CONSUME boundaries and bounded UNKNOWN recovery. Never auto-release/new-ID retry after timeout. | TV3 orchestration + TV4 provider/lifecycle + TV1 auth; TV5 COD boundary |

## Next implementation sequence after decisions

1. TV2 integrates/verifies PR90 through its own approved process, delivers the actual
   Product/Store/House boundary and final C01/E01 response contract. TV1 supplies pair/actor rules.
2. TV3 owns the reviewed Cart expectation/port transition and authenticated consumer;
   use this mapper instead of positional quantities or Product-ID reservations.
3. Bind the stable RESERVE operation to the committed CheckoutAttempt/journal, send
   only through approved transport, and reconcile unchanged saved tuple on UNKNOWN.
4. Revalidate under the chosen freshness policy, then write Order/SellerOrders/outbox;
   preserve persisted-snapshot SUCCEEDED replay without repricing.
5. Run provider/consumer HTTP negative/positive tests and live two-Store DB/stock UAT.

## Validation commands and interpretation

From repository root with existing Java21/Gradle8.10/Docker and process-only UTC/API1.44:

```bash
bash backend/gradlew -p backend :order-service:test --tests '*CatalogCheckoutBatchPreflightTest' --tests '*CatalogReservationPlanTest' :order-service:postgresIntegrationTest --tests '*CatalogReservationPlanPostgresIT' --rerun-tasks --no-daemon --max-workers=2 --console=plain
bash backend/gradlew -p backend --init-script "$PWD/tests/finance/fin06-regression.init.gradle" :order-service:test :order-service:postgresIntegrationTest :order-service:fin06Regression --rerun-tasks --no-daemon --max-workers=2 --console=plain
```

Unit inputs are Catalog/Cart fixtures (no mocks needed); PG cases use actual Order
Flyway/JPA/journal/caller/default-disabled transport. No Product PostgreSQL or HTTP,
real reservation stock, Cart HTTP, actor authentication, COD or E2E acceptance is
claimed by these tests. Exact local patch/results are retained outside the checkout
at `/workspace/review-evidence/tv3-catalog-consumer/`.

Actual local results on the above base plus this uncommitted patch: focused
13 new mapper unit +16 existing preflight +3 new PG PASS, exit0; full Order
141 unit +32 PostgreSQL +3 unchanged FIN diagnostics PASS, 0 fail/error/skip,
exit0. The focused cases are subsets, never added to the full count. The PG
new-class log verifies PostgreSQL16.6/Flyway V1+V2; no Product provider was called.

Do not tick Checkout/Inventory/Issues #3/#10/#16 DONE, full ten-item P0 backlog DONE,
or claim production Product -> Cart -> Checkout works. Owner review is required;
publication of this Draft does not permit merge, positive adapters or runtime flags.
