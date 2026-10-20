# TV3 W2 — durable Inventory journal + fail-closed caller scaffold

**Owner:** TV3 (`tranvanchinhbg2006-alt`). **Status:** implementation proposal / NOT approved for live Inventory traffic.
**Based on:** PR #28 (merged Cart/Checkout W1); TV1 S01/OD-09 PR #48 (merged docs);
TV4 Inventory provider contract; PR #49 scoped Inventory pair gate (merged, default OFF);
TV4 PR #39 C06 still pending; TV2 Product/Store source-of-truth handoff pending.

## Implemented in this branch

- Flyway **V2** creates Order-owned `inventory_operation_journal` with globally unique step
  `operation_id`, checkout attempt correlation, enum RESERVE/CONSUME/RELEASE,
  canonical `sku,long quantity` JSON, immutable fingerprint, status, send count and
  bounded diagnostic code. Existing **V1 is untouched**.
- `InventoryOperationJournal.prepare(intent)`: an isolated **T1 / REQUIRES_NEW**
  PostgreSQL commit persists canonical lines/fingerprint before any outbound HTTP.
  An identical operation ID + identical tuple is an idempotent replay; a changed
  tuple fails without writing. Journal reload rehashes and detects corrupt payload.
- `claimForSend`: atomic compare-and-set from PENDING/UNKNOWN to IN_FLIGHT. Two
  concurrent Order workers cannot claim the same operation simultaneously.
- `InventoryOperationWire`: exact candidate `POST /api/inventory/operations`,
  `X-Internal-Token`, request `{operationId,type,items:[{sku,quantity}]}`;
  *never* productId-as-SKU or broad Inventory legacy token.
- `InventoryOperationCaller.dispatchOnce(operationId)`: **reconstructs the same tuple
  from journal** on every explicit retry, never generates a new operation ID.
  Matches echoed operation ID, type and SKU-set; only then independently commits
  T2 CONFIRMED. A **definite no-send** reverts PENDING, verified atomic rejection
  becomes REJECTED; timeout/5xx/unclassified error or response mismatch records
  **UNKNOWN** for reconciliation. An error committing T2 leaves IN_FLIGHT; the
  scanner later marks it UNKNOWN. No blind RELEASE or assumed stock rollback.
- `InventoryJournalReconciliationScanner`: marks abandoned IN_FLIGHT records
  UNKNOWN without network traffic or compensation.
- `UnconfiguredInventoryOperationTransport`: hard **OFF**, regardless of provider
  token/flag. No network adapter can mutate stock from this branch.

## What is **not** implemented, and must not be inferred from unit/PG tests

1. **No positive HTTP transport**: there is no safe approved Order `X-Internal-Token`
   provisioning/rotation, delegated original customer Bearer and current authority
   for RESERVE, or durable system provenance for CONSUME/RELEASE. Do **not**
   activate `INVENTORY_ORDER_PAIR_ENABLED` on Inventory (PR #49 defaults OFF).
2. Existing `CheckoutService` still uses legacy `InventoryPort.reserve(orderId,
   Reservation(productId,quantity))`, whose `productId` is **NOT** authoritative
   `inventorySku`. Do **not** attach the new journal or wire to Checkout until
   TV2 Product→SKU + current Store/Seller source, TV1/TV4 actor/credential scope,
   TV5 money approvals and TV3 versioned Order adapter are reviewed. Existing
   external Catalog/Inventory adapters stay fail-closed.
3. T1/T2 are Order DB local transactions, **not** a distributed transaction.
   Provider committed/response lost remains UNKNOWN. TV4 Inventory has no
   authoritative operation-status query; GET balance cannot prove operation outcome.
4. Automatic retries, jitter/backoff (TV4 proposed 250ms/1s/4s), deadline, alerting
   integration and manual operation resolution are **not** approved. This scaffold
   supports only explicit `dispatchOnce(id)`, allowing another invocation after
   UNKNOWN using the exact persisted tuple. Do not interpret it as automatic
   production retry scheduler or end-to-end recovery.
5. C06 versioned SellerOrder lifecycle/dispatch-outbox/COD actor rules and
   Release cutoff remain cross-owner REVISE/PENDING, not enabled.
6. Live two-Store COD, Catalog/Inventory/Auth pair, settlement, Kafka publisher and
   E2E NOT_RUN. Finance remains `HK5_FINANCE_POLICY_APPROVED=false`.

## Verification instructions — independent exact commit evidence required

From `backend` (Windows):

```powershell
.\gradlew.bat :order-service:test --tests '*InventoryOperationCallerTest' --rerun-tasks --no-daemon --console=plain
.\gradlew.bat :order-service:postgresIntegrationTest --tests '*InventoryOperationJournalPostgres16IT' --rerun-tasks --no-daemon --console=plain
.\gradlew.bat :cart-service:test :order-service:test :cart-service:postgresIntegrationTest :order-service:postgresIntegrationTest --rerun-tasks --no-daemon --console=plain
```

Real PostgreSQL integration requires Docker/Testcontainers; tagged PG is excluded
from ordinary Backend readiness `build`. Record PASS/FAIL/NOT_RUN with exact SHA.

## Owner decision/next steps

- TV2: post exact PR/commit for **production Product API that returns authoritative,
  immutable globally unique SKU plus current Store/Seller facts** (PR #52 covers
  Store persistence; it does not prove Product price/SKU/public Catalog).
- TV1/TV4: approve and test OD-09 delegated user/system provenance and per-pair
  credential verifier, rotation, HTTP filter ordering/path normalization.
- TV3: when above gates are signed, implement authenticated positive HTTP adapter,
  price/Catalog reconciliation and wire Checkout to committed journal T1/T2;
  implement backoff/deadline on a separately approved recovery policy and test
  crash-between-T1/HTTP/T2 plus duplicate/reorder concurrent requests.
- TV5: Finance sign-off (MD01–10 per scope), COD/settlement checks; PR #39
  old requested changes require TV5's own new review, not TV3 dismissal.
- TV1: PR #51 timezone fix review belongs to TV1; do not rewrite their Auth code.

**This PR is a safety-focused W2 increment.** No `main` push, merge,
cross-owner file edits, secret provisioning, live enablement or Issue closure.

## 20/10/2026 — explicit CI PG16 verification wiring (test-only build behavior)

To address TV1/TV4 feedback, Order module's `build.gradle` now includes tagged
`:order-service:postgresIntegrationTest` in the `check` dependency graph **only when
`GITHUB_ACTIONS=true`**. Existing backend-readiness workflow already provisions Docker
and PG via `docker compose`; the tagged suite itself uses disposable
`postgres:16-alpine` Testcontainers (not a team/business database). Local `build`
remains Docker-free and targeted manual `postgresIntegrationTest` stays available.

The PG test class adds verification of:
- committed CheckoutAttempt FK; T1 REQUIRES_NEW commit remains after caller rollback;
- T2 CONFIRMED REQUIRES_NEW remains after caller rollback;
- stale IN_FLIGHT→UNKNOWN; a late T2 must never overwrite UNKNOWN blindly.

The Gradle task emits `ORDER_PG16_TEST_SUMMARY tests/pass/fail/skipped` in CI log.
**Record the actual HEAD and CI run before claiming success**; existing readiness
at `b9c03a6` was unit-only and cannot prove these new PG checks. A green build still
does not prove live HTTP service-pair authorization, real Inventory stock mutation,
Order Checkout attachment or Finance/COD E2E; all remain BLOCKED/NOT_RUN.
