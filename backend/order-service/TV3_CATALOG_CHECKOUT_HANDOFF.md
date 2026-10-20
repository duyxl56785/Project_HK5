# TV3 — C01/E01 Catalog batch consumer handoff (candidate, fail-closed)

**Owner:** TV3, `tranvanchinhbg2006-alt`. **Status:** DESIGN-CANDIDATE + PURE VALIDATION TESTS ONLY.
Refs Issue #2 / #3 / #4 / #5 / #12, TV2's `catalog.checkout.v1` / v0.2 discussion,
TV1 `TV1-S01-OD09-20261018-v1`, merged PR #54/#56/#58, TV3 PR #60.

## Why this unblocks parallel work

Previously Order `CatalogPort.verify(productId)` returned single-product Product/Store facts,
with **no inventorySku or currency**, and Checkout's legacy `InventoryPort.Reservation`
still used `productId`. Positive Product→SKU mapping and Store-current authority
are **not production-provided** by TV2 yet. These interfaces remain **unchanged
and fail-closed**; silently casting productId to SKU would corrupt inventory.

New `CatalogCheckoutBatchPreflight` is a **pure, unwired consumer-side validator**
with **10 JUnit tests**. It allows TV2 to implement its trusted Product API and
TV4 to test SKU/Inventory independently while TV3 validates the consumer
invariants. There is **no HTTP adapter, no verified caller/pair token, no
Spring bean, no route, no side effect, no data migration and no new flag**.

## Candidate input and result, subject to exact TV2+TV3+TV1 C01/E01 signoff

- `Expected(productId,storeId,sellerId,unitPrice,currency)` is a comparison-only
  Cart expectation. **Never infer** SKU, House, ownership, price or authority from it.
- `BatchResponse(contractVersion,allReady,items)`; version
  `catalog.checkout.v1`, batch 1–100 **distinct** Product IDs. Strict 1:1
  matching result in request order is an explicit **candidate** requiring TV2
  confirmation; TV2 may instead propose id-keyed reordering in a reviewed V2.
- Each `ItemResult(productId,code,snapshot)` must be READY and have a
  trusted `VerifiedProduct(productId,inventorySku,storeId,sellerId,unitPrice,
  currency,available,sellerApproved,storeOpen,houseStore)`.
  Source must be **TV2-authoritative current Product/Store/Seller**. A valid
  mock record is not proof of authoritative source or transaction freshness.
- All rows must match expectations exactly by productId/Store/Seller, numeric
  price and explicit currency. Every SKU must be nonblank, canonical and
  unique within returned batch (TV2 still owes **global** uniqueness/immutability
  at Product database level); all statuses must be ready. Wrong version,
  duplicate/extra/missing/partial rows, invalid SKU, missing currency, stale
  price/status, `allReady=false` or unrepresentable PostgreSQL `NUMERIC(19,2)`
  amount rejects the **entire** batch before any Inventory operation.
- `BigDecimal.compareTo` checks exact numeric price parity; it does **not**
  approve M01 Product unit cap, VND policy, scale-zero calculations, fees,
  House classification, or settlement. `OrderAmountStorageBounds` is **only
  current DB precision safety**, not business price policy.
- This Java value object is **NOT** the final HTTP/JSON DTO. Path
  `POST /internal/catalog/checkout/validate`, canonical UUID serialization,
  errors (401/403/503 vs verified PRODUCT_NOT_FOUND), current actor/delegation,
  result ordering, source freshness and read→commit race still need named
  provider/consumer approvals. Do not turn `IllegalStateException` here
  into a public API mapping without reviewed privacy/error rules.

## Handoff blockers and owner-specific acceptance

| Owner | Can complete now | Dependency before positive integration |
| --- | --- | --- |
| **TV2 Product/Catalog** | Product Flyway V2+ immutable **globally unique** SKU, current Store/Seller facts/House source candidate + tests | Actual production Product DB/API + source versions, signed money/auth, exact DTO and error semantics |
| **TV3 Order/Cart** | Consumer pure validation and negative tests in this PR, existing W1 idempotency/journal/UNKNOWN/attempt fencing in merged PRs, PR #60 multi-SKU regression | TV2 authoritative Product→SKU endpoint, TV1 pair/delegated auth, TV4 provider, TV5 M01, common read→commit/compensation rules |
| **TV4 Inventory** | SKU-only request, stable per-step operationId, multi-SKU atomic PG and duplicate/replay provider tests, pair-filter default OFF | Authenticated actor/system provenance, confirmed RESERVE→eligible RELEASE/CONSUME lifecycle & current Product source |
| **TV1 Auth/Gateway** | Versioned per-pair method/path/operation 401/403/503 negative tests, current Account→Store/Seller principal contract | Provider code/secret provisioning/rotation and authorized positive service-pair E2E |
| **TV5 Finance/QA** | Expected monetary/currency/House mismatch and COD safety matrix, test-only acceptance | **Named business approver**, same-version M01/MD01–MD10/COD/settlement signed decisions and real E2E |

## When and how to wire after the gate

1. TV2 posts exact production Product API+UNIQUE SKU+current Store/Seller
   code HEAD + provider tests, TV1 signs pair security and exact DTO/errors.
2. TV5 + named delegated business owner sign applicable currency/precision/
   fee/House/COD slice, TV2/TV3 agree snapshot freshness/read→commit.
3. TV3 adds a **separate reviewed** CatalogPort V2+ HTTP adapter, full Cart
   identity checks and Order processing that validates **all** lines
   before journal T1 and Inventory RESERVE. Do not combine READY subset.
4. TV4+TV3 verify Inventory same-ID replay, timeout UNKNOWN, exact multi-SKU
   RESERVE/CONSUME/RELEASE and approved actor-specific operation policy;
   TV3 builds Order status/outbox and authorized release with 2-Store E2E.
5. Until then `UnconfiguredCatalogAdapter`,
   `UnconfiguredInventoryAdapter` and
   `UnconfiguredInventoryOperationTransport` remain fail-closed,
   `HK5_FINANCE_POLICY_APPROVED=false`,
   `INVENTORY_ORDER_PAIR_ENABLED=false`. **Issue #3 remains OPEN.**

## Evidence

Run from `backend`: `./gradlew :order-service:test --tests '*CatalogCheckoutBatchPreflightTest'`,
followed by Order full unit & PostgreSQL16 via normal GitHub Backend readiness
(which executes tagged `:order-service:postgresIntegrationTest` in CI).
Record exact SHA/build exit/count; never attribute historical runs to this source.
