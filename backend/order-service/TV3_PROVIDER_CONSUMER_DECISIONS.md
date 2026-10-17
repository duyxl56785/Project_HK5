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
| C02 | **Proposed** authoritative `inventorySku` resolution from TV2 Product; `productId`, `sku`, `storeId` and `sellerId` are distinct opaque identifiers. **TV4 explicitly does not provide** Product→SKU/Store mapping, SKU namespace or Seller/Store ownership from Inventory. Never infer `productId == sku`. | **TV2 Product/Store:** authoritative source, mapping cardinality/uniqueness and `inventorySku` exposure. **TV4 Inventory:** validates compatible SKU namespace after TV2 decision. **TV3:** consumer DTO approval. **TV1:** service identity. | PENDING |
| C03 | **TV3 Order-side proposal:** store a durable, exact ordered/canonicalized reservation line set (authoritative `sku`, positive `long quantity`, reference Store only if approved), linked to `checkoutAttempt.id` and step-specific `operationId`, before each corresponding remote Inventory call; record attempted vs confirmed vs unknown outcomes separately. Proposed location: new **Order-owned checkout inventory operation journal and line table**, in Order PostgreSQL, via reviewed Flyway V2+ only; not in Inventory module or as a cross-DB FK. See transaction boundary below. | **TV3** owns schema, unique constraints, transactional write/recovery and consumer tests. **TV2** owns authoritative SKU/Store mapping. **TV4** validates exact line-set/payload canonicalization and Inventory idempotency contract. **TV1** reviews internal auth; **TV5** reviews lifecycle-dependent amounts/status. | PENDING |
| C04 | Create a **distinct, durable and retry-stable `operationId`** for each `RESERVE`, `RELEASE` and `CONSUME` step, at most 128 chars, separate from Order/attempt correlation ID; same operationId + canonical payload may replay, **same ID with different payload must be rejected** by Inventory's payload hash. Do not generate a new operationId on retry. | **TV3:** generate/persist/reuse ID and immutable payload in Order journal; **TV4:** confirms Inventory's hash and conflict semantics and which fields participate in canonical payload; tests must cover same/different payload and concurrent calls. | PENDING |
| C05 | **Proposal, not approved:** request `RESERVE` after successful Cart claim plus backend-authoritative Product/Store/price/SKU validation and Finance gating, after durable reservation intent commit; do not open network call under Order DB transaction. Inventory RESERVE transfers available→reserved. | **TV3** Checkout boundary / Order local DB; **TV2** Product/Store truth; **TV4** approves Inventory lifecycle trigger; **TV5** approves Finance/COD business gating. | PENDING |
| C06 | **No settled consume/release milestone.** Proposed discussion: `RELEASE` only after an approved cancellation/failure transition AND verified reservation-not-consumed; `CONSUME` only on an owner-approved fulfillment/COD transition, potentially after `DELIVERED` and COD confirmation, **not assumed**. Both send the persisted SKU/qty line set with their own stable operationId; never release merely because of unknown timeout. | **TV3** proposes Order-side state boundary; **TV4** approves Fulfillment/Inventory timing; **TV5** confirms COD/`DELIVERED`/payment/settlement truth source; **TV1** may review event auth; every step waits for joint decision. | PENDING |
| C07 | Unknown Inventory timeout/response loss is **not** proof of success or failure. Keep attempt in blocked/reconciliation state with immutable line set + step operationId; **no blind retry as reconciliation** or compensating `RELEASE` without approved authoritative status/evidence. Inventory currently has **no reservation lookup by orderId** and no status endpoint for this outcome. Define lookup by operationId or an owner-approved audited reconciliation path before live adapter. | **TV4 Inventory:** authoritative status/read model or explicit alternative with consistency guarantee. **TV3:** persisted journal + safe retry/worker/ops runbook and recovery boundary. **TV1:** internal auth; **TV5:** cancellation/COD decisions. **TV2:** SKU ownership source. | PENDING (live integration BLOCKED) |
| C08 | No business policy fallback. Keep finance gate false until written fee, currency scale/rounding, House Store, snapshot, COD and settlement decisions. | TV5/TV4/TV3 approval. | BLOCKED |
| C09 | Keep Catalog and Inventory adapters fail-closed until provider/consumer contract tests pass. Preserve caller identity; return documented status/errors, do not infer HTTP statuses from placeholder adapters. | TV1/TV2/TV4 review service-pair auth, timeouts, permissions and status/error contract. | BLOCKED |

## TV4 follow-up confirmation — 18/10/2026 (HEAD before this update: `34d451d`)

TV4 replied on [PR #28](https://github.com/duyxl56785/Project_HK5/pull/28#issuecomment-6237872079). **Confirmed only within current Inventory implementation** (PR #31 merged to main): the operations endpoint receives `sku`, positive `long quantity`, operation `type` and `operationId`; idempotency replays an unchanged operationId/payload and rejects reused IDs with changed payload via payload hashing. Inventory **does not** provide authoritative Product↔SKU/Store mapping, a reservation line-set by `orderId`, or a status lookup that proves unknown request outcome. This is **NOT APPROVAL** of the shared consumer API or COD/Fulfillment timing.

**TV3 proposals requiring owner acceptance; all PENDING:**

1. **Reservation persistence — C03 (TV3 owns; TV2/TV4 dependencies).** Proposed Order-service **V2+** tables: `checkout_inventory_operations` (attempt/order correlation ID, separate step operationId, operation type, canonical payload digest, request/response/unknown state, audit timestamps) and `checkout_inventory_operation_lines` (exact immutable SKU and quantity per operation, order/reference Store only when trusted). Columns, constraints, enum/status names and SQL are *design proposals, not implemented migrations*. In a short **Order DB transaction T1**, validate preconditions and persist step intent, immutable line set and operationId; **commit T1 before HTTP** to Inventory. After a response, use a separate **T2 local transaction** to record confirmed outcome and proceed. A lost response leaves the durable outcome UNKNOWN; transaction rollback in Order cannot undo remote inventory. Never hold an Order DB transaction open across network calls.
2. **Idempotency — C04 (TV3 owns IDs/journal, TV4 confirms hash).** Generate/persist **one distinct, stable operationId per step**; retain exact type+SKU/quantity line payload across retry/crash. `checkoutAttempt.id` and `orderId` are correlation only. Do not assume a retry with the same operationId proves a reservation state until TV4 supplies approved status/evidence semantics.
3. **Lifecycle — C05/C06 (TV3 proposes; TV4+TV5 approve).** `RESERVE` is proposed after successful Cart claim, authoritative Product/Store/SKU/price check and Finance gate, with durable intent. `CONSUME` and `RELEASE` timing is **not decided**; TV4 owns Fulfillment/Inventory transition constraints, and **TV5 must approve COD / DELIVERED boundary** and settlement truth source where relevant. Do not implement automatic consume/release before that decision.
4. **Unknown outcome — C07 (TV4 supplies authoritative source, TV3 implements reconciliation).** TV4 must propose an authenticated reservation/operation-status lookup with consistency requirements **or** an explicitly owner-approved audited reconciliation process; TV3 then designs retry/worker behavior. Until available, leave attempts blocked/manual audit and adapters fail-closed. **Blind reserve retry or release after timeout is not reconciliation.**

**Open approval dependencies:** TV2 owns Product/Store identity and persistent SKU mapping; TV4 owns Inventory operation/status semantics and Fulfillment timing; TV5 owns approved COD/DELIVERED and finance boundary; TV1 owns service-to-service security; TV3 owns Order-side journal/transaction boundary. No owner has approved the joint contract in this comment. All C02–C07 remain **PENDING**; C08/C09 remain **BLOCKED**. Source Java/API/contracts/Flyway V1 remain untouched.

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
