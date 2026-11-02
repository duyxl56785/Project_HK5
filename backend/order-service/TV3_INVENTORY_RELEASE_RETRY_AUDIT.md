# TV3 — Inventory RELEASE / retry / multi-SKU safety audit (22 October 2026)

Owner: TV3 (Order). Scope: independent safety evidence and regression coverage only.
Issue: #3 / #4; merged PR #54 + PR #58. This audit does **not** implement
live Product, Store, Auth, Inventory HTTP caller or Fulfillment/COD integration.

## Existing source facts

1. `InventoryOperationIntent` canonicalizes unique exact-case immutable
   `sku, quantity(long > 0)` lines by SKU, fingerprints the type+all canonical
   lines using the same length-prefixed SHA-256 algorithm as TV4 Inventory
   provider. `operationId` and CheckoutAttempt UUID are stored independently.
2. Order PostgreSQL `inventory_operation_journal` contains
   `operation_id` (PK), `checkout_attempt_id` (FK),
   `operation_type`, `canonical_lines` JSONB, `payload_fingerprint`,
   `state`, `send_attempts` and diagnostics. T1 persists exact bytes/lines
   before dispatch; the same ID/payload is idempotent, conflicting replay fails.
   T2 is committed separately. PR #58 fences callbacks by `send_attempts`.
3. `InventoryOperationCaller.dispatchOnce(operationId)` always reloads that
   exact durable tuple. Retry after UNKNOWN preserves ID/type/SKU/quantity;
   timeout/5xx/invalid/mismatched response stays UNKNOWN. The current
   `UnconfiguredInventoryOperationTransport` is fail-closed: no live requests.
4. TV4 Inventory server `InventoryOperationService.apply` uses a
   DB-backed operationId ledger and atomic multi-SKU stock transaction.
   Its existing PostgreSQL tests cover RESERVE/CONSUME/RELEASE retry
   (one SKU), changed-payload conflict, concurrent idempotency and
   rollback when a later SKU fails. These are provider-module tests,
   not an Order-to-Inventory HTTP acceptance test.
5. Current old `CheckoutService` still invokes
   `InventoryPort.Reservation(productId,int)` through a disabled adapter;
   no authoritative `ProductId → inventorySku`/auth positive integration
   or persisted reservation-to-release execution path is wired.

## TV3 regression test coverage restored from closed PR #60

- `InventoryOperationJournalPostgres16IT.multiSkuReleaseCandidateRetainsExactLongQuantitiesAndIndependentOperationId`:
  actual Order PG16 round-trip of RESERVE and separate candidate RELEASE,
  **three canonical SKUs**, distinct types/IDs/fingerprints, quantity
  **2,147,483,648** (beyond int), exact line counts and idempotent RELEASE T1.
  Changed quantity with reused RELEASE ID is rejected without changing saved
  lines. This proves durability/identity only, **not permission to release**.
- `InventoryOperationJournalPostgres16IT.unknownMultiSkuReservePreservesStableIdAndNeverCreatesImplicitRelease`:
  UNKNOWN + explicit retry retain same persisted ID/fingerprint/three lines,
  only one journal operation row, different send generations, no RELEASE
  implicitly recorded by the journal.
- `InventoryOperationCallerTest.multiSkuTimeoutAndRetryKeepExactlySameRequestAndNoImplicitRelease`:
  timeout then replay-success uses identical persisted Order wire request,
  including 3 sorted SKU quantities and distinct outcome generations.
  No auto-created release, no re-minted RESERVE.
- `InventoryOperationCallerTest.multiSkuMismatchedProviderSkuSetRecordsUnknownRatherThanConfirming`:
  unexpected SKU from purported provider reply cannot be CONFIRMED;
  stays UNKNOWN. This test does **not** verify individual quantities in
  provider response because the current Inventory response has balances,
  not original per-operation quantity echo.

## Additional exact-main regression tests (not from old PR #60)

- `multiSkuUnknownIntentRejectsSameIdDifferentTypeOrCheckoutAttempt`:
  PostgreSQL16 journal UNKNOWN row cannot be hijacked by a different RELEASE
  type or another CheckoutAttempt with identical operationId; row/state/immutable
  fingerprint/lines are unchanged, with no Inventory provider side effects.
- `twoWorkersReclaimOneUnknownMultiSkuOperationOnlyOnce`:
  concurrent Order PostgreSQL claim race after UNKNOWN yields exactly one
  new generation 2, never two sends, and retains canonical three-SKU payload.
- `multiSkuReleaseCandidateDisabledTransportDoesNotConfirmOrChangeIntent`:
  caller rejects positive dispatch when the injected pair transport guarantees
  NotSent; candidate RELEASE lines stay a syntactic wire proposal, never
  proof of prior reservation, authorized cancellation, held balance or remote mutation.

**Source lineage:** PR #60 was closed by TV3 without merge on 20 Oct 2026.
GitHub records an owner CLOSE event but no explicit reason; do not invent a
technical rejection. This new branch starts from current main, selectively
restores its four non-merged test methods (2 unit + 2 PostgreSQL), and adds
the three Order-scoped methods above. No production code or shared API changed.
PR #75 Unicode SKU work and PR #77 Catalog preflight remain distinct drafts.

## Blockers: RELEASE correctness is NOT established by a journal alone

**DO NOT claim fully correct automatic stock return.** Current source has
no approved, wired rule proving all of these before RELEASE:
- Confirmed committed RESERVE for the *same* checkout/SellerOrder,
  linked to authoritative Product-owned SKU and exact quantities;
- Genuine cancellation/rejection authorized by verified actor + current
  Seller/Store ownership and SellerOrder state **before DISPATCHED**;
- Amount still held after any CONSUME/partial release; historical immutable
  RESERVE lines alone are not proof of remaining physical reserved stock;
- A **distinct stable RELEASE operationId** committed once with durable
  `sku,long quantity` intent before HTTP, and a replay/reconciliation policy
  for RELEASE UNKNOWN and 409/5xx/provider-unavailable cases;
- TV1/TV4 signed Order→Inventory restricted service-pair credential plus
  authenticated customer/system provenance; TV2 current SKU/Store facts;
  TV5/owner Finance and C06/COD/refund policy.

Current `InventoryOperationJournal.prepare(RELEASE)` validates immutable
record syntax, but does **not** verify existence of prior successful RESERVE,
available held stock, authorized lifecycle or absence of CONSUME. A candidate
RELEASE entry with good JSON/FK **does not mean it is safe or permitted to
send**. Do not wire any positive HTTP adapter or scheduled compensator
based only on these tests. A late result from an older retry is fenced on
Order (PR #58), but this is not distributed exactly-once by itself.
`GET /api/inventory/{sku}` balance cannot prove a specific operation
outcome. Do not mint a fresh operationId after timeout or blindly RELEASE.

## Verification and handoff

Official GitHub Backend readiness executes Order unit tests and tagged
`:order-service:postgresIntegrationTest` on disposable Testcontainers
PostgreSQL16. **All new-head tests are NOT_RUN until GitHub Actions
finishes successfully and actual logs are inspected**. Do not count
historical PR #54/#58 unit or PG results as new test results.

Only TV3 Order test code and this audit document change in this PR.
Order/Inventory runtime unchanged; no V1/V2 migration edits, credentials,
Finance flag, Inventory pair flag, other-owner service edits, merged
source replacement or Issue #3 closure.

Next: TV4 confirm provider-side *multi-SKU* RESERVE→RELEASE, exact same-ID
replay, 409/timeout UNKNOWN, rollback/underflow and concurrent claims with
real PostgreSQL (prefer additional TV4-owned tests); TV2 ship SKU source;
TV1/TV4 approve per-pair authorization; TV3 then build reviewed
release-eligibility orchestration + durable link/status/outbox and positive
HTTP adapter under a separate contract-gated PR.
