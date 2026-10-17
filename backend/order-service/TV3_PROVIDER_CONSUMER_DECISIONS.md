# TV3 — Checkout Product/Inventory/Finance consumer decisions (PR #28)

**Owner:** TV3 · **Refs:** Issue #3; PR #28; TV2/TV4 follow-ups on 18/10/2026.
**Status:** DRAFT / NEEDS OWNER APPROVAL. This document records a **proposal for review**, not an adopted wire contract, not a rollout instruction, and not runtime implementation. Keep current adapters fail-closed.

## Baseline confirmed from current source

- Order `CatalogPort.verify(productId)` currently returns `productId, sellerId, storeId, available, sellerApproved, storeOpen, houseStore, unitPrice`; it has **no SKU/currency field**.
- Order `InventoryPort.reserve(orderId, List<Reservation(productId,int)>)` and `release(orderId)` **cannot be safely connected** to TV4 Inventory API as-is.
- Inventory module (PR #31 merged to main) supports `POST /api/inventory/operations` accepting `operationId` (string, max 128), `type` (`RESERVE`, `RELEASE`, `CONSUME`, etc.), `items[]: {sku: string, quantity: long}`. A replay must retain the **same operationId, type, line set and quantities**. It has no current reservation lookup by orderId and no verified Store scope.
- TV2 reports current Product service lacks authoritative Product/Store endpoints. Proposed `GET /internal/products/{productId}/checkout-snapshot` is **DRAFT only**, not an available API.
- Auth contract #24 is HS256 JWT current/previous keyring; any internal route's caller credentials, exposure and error mapping require TV1 approval; never expose `/internal/**` on public Gateway.
- TV3 Finance policy remains disabled: `HK5_FINANCE_POLICY_APPROVED=false`. No fee/currency/rounding can be inferred from existing test fixtures.

## Consumer proposal and open decisions

| ID | TV3 proposal for owner/consumer review | Required decision / owner | Status |
|---|---|---|---|
| C01 | TV3 accepts TV2's **proposed** checkout snapshot route/fields as a discussion basis, using backend-derived IDs, statuses and current price, never request headers/body as authority. | TV2 Product endpoint/DTO/status truth source; TV1 internal auth/error semantics. | PENDING |
| C02 | Add an **authoritative** `inventorySku` resolved on the Product side (either approved snapshot extension or separately approved lookup). Keep `productId`, `sellerId`, `storeId` and `sku` as distinct opaque IDs. | TV2/TV4 agree uniqueness, mapping cardinality, Store namespace, source-of-truth; TV3 approves consumer DTO evolution. | PENDING |
| C03 | Order persists the **exact confirmed reservation line set** (SKU, positive long quantity, Store/reference where approved) and the step-specific `operationId` values durably **before** sending the first Inventory request, so a crash/retry can use the same payload. Do not persist cross-service foreign keys. | TV3 design with TV4 on payload canonicalization, DB constraints and state-machine; only new V2+ Flyway migration after review. | PENDING |
| C04 | Use **different stable IDs for RESERVE / RELEASE / CONSUME**; keep `checkoutAttempt.id` / `orderId` as correlation IDs, **not** reuse as the Inventory operation ID across steps. IDs must remain stable across restarts and retries and fit max length 128. | TV3/TV4 approve operationId generation/persistence, retry and concurrency rules; not implemented in current InventoryPort. | PENDING |
| C05 | Propose `RESERVE` only after Cart claim and verified Catalog+Finance snapshot; a prior successful reservation should not be repeated with a different payload. | TV3/TV4/TV5 confirm order status/lifecycle and when checkout is allowed. | PENDING |
| C06 | `CONSUME` and `RELEASE` require explicit terminal/event transitions supported by TV4 COD/Fulfillment; **no event timing chosen yet**. Never release on a mere timeout/unknown commit. | TV4/TV5 and TV1 confirm COD paid / delivery / cancel / settlement truth source. | PENDING |
| C07 | Unknown outcome (Inventory request timeout or Order DB commit lost): keep attempt blocked and preserve line set/operation IDs; reconcile by authoritative reservation state before any remote compensation. No `release(orderId)` adapter based on assumptions. | TV4 defines whether/how an authenticated status lookup by `operationId` or correlation ID can exist, and when it becomes consistent; TV3 designs auditable worker/manual recovery. | BLOCKED |
| C08 | No business policy fallback. Keep finance gate false until written fee, currency scale/rounding, House Store, snapshot, COD and settlement decisions. | TV5/TV4/TV3 approval. | BLOCKED |
| C09 | Keep Catalog and Inventory adapters fail-closed until provider/consumer contract tests pass. Preserve caller identity; return documented status/errors, do not infer HTTP statuses from placeholder adapters. | TV1/TV2/TV4 review service-pair auth, timeouts, permissions and status/error contract. | BLOCKED |

## Why implementation is currently blocked

The current CheckoutService generates reservations from **productId**, not validated SKU, and InventoryPort's `release(orderId)` has **no persisted lines**. Directly forwarding those calls to TV4 would risk applying stock operations to the wrong product or an unrecoverable reservation. Adding a fake product-to-SKU mapping, enabling Finance gate for tests, or calling the Inventory API without a durable per-step operation ID is **not an acceptable fix**. Existing Cart/Checkout unit and PostgreSQL tests use mocks for Product/Inventory adapters.

**Not approved / not implemented:** Catalog HTTP adapter, Inventory HTTP adapter, persistent reservation journal, consumer-specific V2 migration, status lookup/reconciliation worker, authenticated real-service E2E, Kafka outbox relay. Existing V1 migrations and shared contracts remain unchanged.

## Proposed acceptance & evidence matrix (future execution only)

| Case | Expected check | Current status |
|---|---|---|
| Product availability / price / Seller / Store changes between Cart and Checkout | Refuse stale snapshot, no reservation; server-derived identities only | NOT_RUN live |
| 1 Customer, 2 Stores, COD checkout | 1 Order, 2 SellerOrders, correct SKU/qty/fee snapshots in separate service DBs | NOT_RUN live |
| Identical idempotency key/payload concurrent and retried | Same one Order and at most one effective RESERVE; replay returns response | NOT_RUN live |
| Same key with changed payload | HTTP 409, no second Inventory operation | NOT_RUN live |
| Inventory timeout before/after reserve commit | Reuse unchanged step operationId and original line set; reconcile without extra stock movement | NOT_RUN live |
| Order save succeeds but response is lost | Return committed Order on retry, do not release Cart/Inventory | PASS in existing *PostgreSQL mock-port regression* only; NOT_RUN live |
| RELEASE and CONSUME repeated/concurrent | Exactly-once effective inventory transition under approved lifecycle | NOT_RUN live |
| Auth JWT/Gateway and service-pair credential | Valid authorized calls pass; forged/expired/missing credentials fail closed | NOT_RUN live |

## Existing tested evidence (not new results)

At HEAD `89c20bc`, review evidence under `backend/order-service/verification/tv5-fixes/final-green/`: **56 unit PASS + 11 PostgreSQL16 integration PASS, 0 fail/error/skip**, with 13 JUnit XML suites and raw Gradle log. TV1/TV2 reviewed this code snapshot. These tests do **not** establish live Product/Inventory/Gateway/Finance/COD/E2E PASS. A docs-only follow-up commit changes documentation, not those historical test results; tests on a new post-merge integration SHA remain NOT_RUN until executed.

## Owner responses requested

- **TV2 @duyxl56785:** confirm the Product snapshot route and backend authoritative data, whether SKU will be returned and how Store→Seller ownership/status can be verified without cross-DB joins. No Catalog provider today.
- **TV4 @phamthecuong2022006-alt:** confirm Inventory SKU namespace, operationId/payload/idempotency rules, permitted lifecycle, secure reservation status/reconcile contract and exact RELEASE line set.
- **TV5 @nguyenminhduy090-alt:** confirm signed-off money rules, House policy, scale/rounding, COD truth source, refunds/settlement, and who owns each status transition.
- **TV1 @lexuanbao258-collab:** confirm internal service authentication, Gateway allowlist and error/status behavior.
- **TV3:** after owner decisions, implement only Cart/Order consumer interfaces/adapters/journal and **new** migration if necessary, run Java21 unit, PostgreSQL16/Flyway, concurrency, provider-consumer and 2-Store E2E with commit SHA + raw logs. Request re-review only with those results.

**Do not merge PR #28 or close Issue #3 based on this proposal.**
