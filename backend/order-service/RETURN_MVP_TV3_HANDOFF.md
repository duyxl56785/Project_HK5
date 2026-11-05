# TV3 W4 — Return MVP handoff (#155)

**Source base:** `main@00426dc3d933d02ec9545be7aea43b8a448f0829` after #171/#172/#176.  
**Branch:** `feat/tv3-return-mvp-w4-20261105`.  
**Business approval:** [TV2 decision 05/11/2026](https://github.com/duyxl56785/Project_HK5/issues/155#issuecomment-6650087223).  
**Status as authored:** SOURCE AND TESTS COMMITTED / **GRADLE, POSTGRES, FRONTEND AND LIVE E2E NOT_RUN**. This is NOT a merge-ready release statement.

## Implemented in the TV3 scope

1. Order Flyway `V6__customer_return_requests.sql` creates a durable Return row, immutable buyer key+request fingerprint, SellerOrder ownership FK, status constraints and unique active-return partial index. A Java/PG UTF-16 length rule checks at most 500 UTF-16 units (non-BMP emoji count as two).
2. `ReturnRequestService` serializes buyer create and Finance settle through the **same** `SellerOrderRepository.lockById` row lock. Only original buyer, COD, `DELIVERED`, `settled_at IS NULL`, within **7 days from `delivered_at` UTC** and no active Return may create. `COLLECTED` COD is allowed while unsettled.
3. Buyer `POST /api/orders/{orderId}/store-orders/{storeOrderId}/returns`: authenticated CUSTOMER, required `Idempotency-Key` 1–128 and `{\"reason\":\"...\"}` required; first response HTTP 201, exact-key+normalized-reason replay HTTP 200 with same request ID, different store/order/reason same key HTTP 409. The persisted key spans all StoreOrders of a Customer, not merely one Store. Wrong buyer 404, invalid input 400. GET `/api/orders/{orderId}/returns` lists only authenticated buyer's requests.
4. On accepted create, one notification to the right Store is queued in the existing transactional outbox; no shipping address, key or reason is sent in event. The Order/Store money snapshot remains unchanged.
5. `FulfillmentService.settle` checks `REQUESTED/APPROVED/RECEIVED` under the same SellerOrder lock before settlement, returning 409 `SETTLEMENT_RETURN_OPEN`. `collectCod` remains allowed, and `REJECTED` unblocks settlement.
6. The buyer's current Order History card shows a Return form/status without rebuilding Checkout. Retry after a lost network response preserves the original body and key for that mounted form. New requests after an audited REJECTED Return are permitted inside the 7-day window.

## Test sources committed but not yet executed

- `ReturnRequestServiceRulesTest`: deterministic 7-day UTC cutoff inclusive, COD and settled eligibility, UTF-16 500 units, key and fingerprint.
- `FulfillmentPostgres16IT` new tests: two-Store scoped ownership, seller-role denial, successful buyer create/read, same-key replay and changed-body 409, deadline, UTF-16 SQL, REJECTED→new request, RECEIVED settlement block, Return/settle concurrent race, and no Inventory RELEASE.
- `ReturnRequestPanel.dom.test.mjs`: happy-dom UI visibility/states plus lost-response same-key retry.
- `frontend/e2e/return-request.spec.ts`: *real* disposable demo-stack Playwright: Customer order, Seller delivery, buyer form Return, same key and changed key API behavior. No HTTP interception or fake server replies. **This test is not yet run**; its scenario is limited to customer Return, not Stock/Finance/UAT-final.

## REQUIRED Windows PowerShell verification — separate worktree

Commands run in **Windows PowerShell**, not ChatGPT or a shared worktree. Keep `D:\Project_HK5` intact. Ensure no dirty shared state is overwritten and `D:\Project_HK5_RETURN` does not already exist.

```powershell
Set-Location D:\Project_HK5
git status --short
git fetch origin
git worktree add --detach D:\Project_HK5_RETURN origin/feat/tv3-return-mvp-w4-20261105
Set-Location D:\Project_HK5_RETURN
git rev-parse HEAD
java -version
docker info

.\backend\gradlew.bat -p backend :order-service:test --tests '*ReturnRequestServiceRulesTest' --rerun-tasks --no-daemon --console=plain
.\backend\gradlew.bat -p backend :order-service:postgresIntegrationTest --tests '*FulfillmentPostgres16IT' --rerun-tasks --no-daemon --console=plain

Set-Location D:\Project_HK5_RETURN\frontend
npm.cmd ci
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
```

Record **full HEAD, Gradle exit codes, individual JUnit XML, Node test summary, TS build exit code and SHA256 evidence**. Stop and repair at that exact HEAD if any test fails. No claim of PASS until actual logs have been reviewed. `docker info` failure means Testcontainers and PG test **BLOCKED_ENV**, not PASS.

Playwright `frontend/e2e/return-request.spec.ts` must only run against an **approved, disposable, isolated demo stack**, with fake users/data and the correct approved pair/Finance config. Do not activate flags or run `scripts/demo/up.mjs` blindly on the team's live or shared demo, and do not prune any shared volume. Once the isolated environment is authorized and running:

```powershell
Set-Location D:\Project_HK5_RETURN\frontend
npm.cmd run e2e -- --grep "delivered buyer requests one Return"
```

## TV4/TV2/TV5 integration and ownership — NOT yet supplied by this branch

- **TV4** implements SellerStore-scoped GET/action routes for `REQUESTED→APPROVED/REJECTED`, `APPROVED→RECEIVED` with verified current StoreDirectory owner, rollback/replay and exactly-once physical receipt/Inventory restock. The database requires `reviewed_by/reviewed_at` for `APPROVED/REJECTED` and `received_by/received_at` for `RECEIVED`. Keep `seller_orders.status=DELIVERED`. Do not `RELEASE` already-consumed inventory.
- **TV2** exposes read-only Admin GET for Return requests and tests 403/404 current authority. Admin may NOT approve/receive returns. Buyer GET is already provided here.
- **TV5** independently executes RET-01..06, including exact-head PostgreSQL concurrent settlement proof, 2-Store isolation, refund/Finance boundaries and privacy. `RECEIVED` blocks settlement until an *approved separate Finance decision*; no automatic refund or payout reversal.
- No UNKNOWN fault test, default flag change, V3 Inventory owner-map promotion, or production retention authorization is implied.

**PR gate:** Do not mark this branch ready or merge until unit/PG/frontend/E2E evidence is tied to its final exact HEAD, independent TV4/TV5 review is recorded, and CI is green. Do not close #155 or #18 solely because source and test files exist.
