# TV5 financial aggregate durability regression

Refs #5/#12. Test-only additions; no production Checkout, schema, API, settlement or policy changes.

## Coverage inventory before adding tests

| Module | Existing evidence/source | Remaining gap | Owner / action |
|---|---|---|---|
| Product price/persistence | Merged #88 T01–T15, ProductPrice and persistence tests | No uncovered variant established for the requested integer/VND/cap/rounding cases | TV2; do not duplicate existing tests or require another price-slice signature |
| Product lifecycle/current authority | #89 36 lifecycle PG cases; #97 handler unit/PG cases | Real Auth/current Seller/Store adapter absent | TV1/TV2; fixtures are not protected HTTP |
| Catalog validation/DB source | CatalogBatchValidatorTest; #90 46 PG cases | Requested missing/duplicate/invalid Product, absent/unavailable, exact SKU/Store/price and whole-batch rejection already covered | TV2; no duplicate test or invented C01/E01 wire |
| Search | #91 query tests and 35 PG cases | Public eligibility/authority/freshness is outside private candidate search | TV2/TV1/TV3 contract gate |
| Finance exactness/replay | FinanceSnapshotPostgresIT; CheckoutReplayPostgresIT; original FIN-01/06 diagnostic | Late outbox DB failure, outer rollback after a successful writer return, concurrent financial replay and same-key customer namespaces not covered together with the complete two-Store aggregate | **Four new TV5 cases below** |
| COD/settlement | Pure guards exist; production payment/settlement storage and authority do not | Cannot prove unique/atomic settlement or payment authorization without implementation and approved contracts | BLOCKED; do not create a fake settlement table or claim live acceptance |
| PR audit automation | Existing reusable audit logic is private under `.verification/`; no tracked CLI found | Shareable read-only collector and deterministic tests | Separate QA-tool task/PR; not mixed with these financial tests |

## New executable cases

[`FinanceDurabilityPostgresIT`](../../backend/order-service/src/test/java/com/projecthk5/orderservice/finance/FinanceDurabilityPostgresIT.java)
uses real Flyway/JPA/PostgreSQL 16.6 Testcontainers and the actual Spring transaction proxies.
[`FinanceTwoStoreFixture`](../../backend/order-service/src/test/java/com/projecthk5/orderservice/finance/FinanceTwoStoreFixture.java)
provides synthetic two-Store Order aggregates and an existing-format Checkout request; it grants no authority.

| ID | Behavior and evidence | Boundary |
|---|---|---|
| TV5-FD01 | A PostgreSQL trigger confirms two child orders/items already exist **inside** the transaction, then fails the outbox insert with SQLSTATE23514. Order/children/items/outbox and attempt success all roll back. Removing the test-only fault permits one clean local retry. | Only an explicitly proven local rollback is retried; no remote UNKNOWN compensation or new Inventory operation |
| TV5-FD02 | An outer REQUIRED transaction sees complete flushed money/outbox/success, then throws. Outside it, every placement row is absent and the original attempt remains PROCESSING. | Verifies propagation/atomicity; not a distributed transaction |
| TV5-FD03 | Four synchronized concurrent successful replays return the same Order ID/total/two-Store count; every stored fee/price/quantity row and the single placement event remain unchanged. Catalog/Inventory are never called. | Caller MoneyRules remains **false**; no synthetic approved Finance configuration |
| TV5-FD04 | Two customers use the same idempotency key and concurrently replay different two-Store totals/Order IDs. Each receives its own aggregate, with two distinct key namespace rows. | Customer-ID-scoped service/storage behavior with fixture identities, not verified JWT or Store-scoped Finance API |
| TV5-FD05 | A valid replay and a changed-quantity same-key replay start together: the valid caller receives the original two-Store result, the conflicting caller gets409 IDEMPOTENCY_PAYLOAD_MISMATCH. Every financial row, attempt fingerprint and exact outbox row/payload remain unchanged; only the valid caller finalizes Cart. | Adds mixed successful/conflicting contention with complete financial/outbox assertions, rather than duplicating the existing sequential generic mismatch case. MoneyRules stays false; no new policy or remote side-effect proof |

Fees are supplied historical fixture values, **not** calculated or approved commission/House policy.
The current Order schema does not persist complete currency/rate/House/settlement snapshots; these tests
do not claim otherwise. No fee, rounding, refund or COD decision is made here. M01 Product price approval
does not approve the full Finance policy. No Finance/Inventory flag is enabled.

## Commands

From this exact checkout with Java21 and Docker available:

```powershell
$env:JAVA_TOOL_OPTIONS='-Duser.timezone=UTC -Dapi.version=1.44'
.\backend\gradlew.bat -p backend :order-service:postgresIntegrationTest --tests '*FinanceDurabilityPostgresIT' --rerun-tasks --no-daemon --console=plain
```

Relevant existing regression plus the unchanged original diagnostic:

```powershell
$tv5Diagnostic=(Resolve-Path 'tests/finance/fin06-regression.init.gradle').Path
.\backend\gradlew.bat -p backend --init-script $tv5Diagnostic :order-service:test :order-service:postgresIntegrationTest :order-service:fin06Regression --rerun-tasks --no-daemon --console=plain
```

The initial focused execution produced **4 PASS/0 FAIL/0 skip**, exit0, against the two new test-source
fingerprints on main `ed85f62` before commit. A final commit-bound regression receipt follows after it runs;
do not relabel this precommit execution as a different SHA's fresh run. Logs/XML stay private and unchanged;
publish derived counts/source/commands/timestamps and original byte hashes only.

Test fault DDL creates/removes only its uniquely named trigger/function in this new disposable container.
It never connects to an existing DB, deletes an existing Docker volume or changes a production migration.
Live HTTP, current Auth/Store/House, Inventory reservation, COD/settlement and two-Store live E2E remain
**NOT_RUN/BLOCKED**. Product integration order remains TV2's #89 → check → #90 → check → #91 → check;
#94 remains separate. These tests do not perform or replace that integration.

27/10 follow-up: the TV5 branch is synchronized with actual merged main7d68e11. FD05 passed
on its first focused run (five durability cases); record it as **PREVENTIVE/REGRESSION**,
not a manufactured RED->GREEN. Existing generic mismatch, concurrent-success and customer
namespace cases remain intact. The original counts above are historical, not new-HEAD receipts.
