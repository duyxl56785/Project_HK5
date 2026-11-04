# TV3 durable reservation-owner map — isolated Order candidate (04/11/2026)

Refs #3, #4, #10, #11, #16, #18, #155 and provider PR #69.

**Status: IMPLEMENTATION_CANDIDATE / NOT_INTEGRATED / NOT_RUN.**
This branch prepares Order-owned immutable T1 facts. It does not wire a V3 HTTP
request, move the Inventory V3 candidate migration into default Flyway, enable
`INVENTORY_ORDER_PAIR_ENABLED`, change COD/settlement, or run an UNKNOWN fault.

## What this branch actually adds

- `ReservationOwnerSnapshot`: one opaque reservation ID per frozen SellerOrder;
  frozen root Checkout attempt/Order UUID, Customer, Store/Seller, canonical sorted
  Product/SKU/long quantity lines, versioned provider owner fingerprint and a
  separate Order snapshot checksum. Same SKU, Product or owner mismatch fails closed.
- `ReservationOwnerCandidateJournal.prepareInsideDraftT1`: uses the persisted
  immutable `CheckoutDraft` and locked PROCESSING attempt, reuses an already
  committed reservation ID for exact replay and refuses mismatched pre-existing
  rows. `Propagation.MANDATORY` prevents an accidental independent transaction.
- `db/candidate-migration/V6__checkout_reservation_owner_candidate.sql`:
  an additive PostgreSQL schema with FK to the committed Checkout attempt (not
  a future SellerOrder row), uniqueness per SellerOrder/attempt/Store and bounded
  checksums. **Excluded from normal Order Flyway locations.**
- JUnit pure-domain and PostgreSQL16 candidate tests for two Store bindings,
  fingerprint encoding, malformed SKU, T1 rollback and immutable replay.

The provider owner fingerprint is the Java candidate's
`reservation-owner-v1` encoding in #69, including reservationId, StoreId,
SellerId, SellerOrderId and sorted SKU/quantity lines. This is NOT the legacy
aggregate `InventoryOperationIntent` fingerprint, and the proxy byte SHA256
from #170/#175 proves neither owner identity nor provider COMMIT.

## Important integration boundary: T1 is NOT wired yet

The current `OrderWriteService.prepareDraft` calls
`CheckoutDraftJournal.prepare` with `REQUIRES_NEW`. The new journal is a
**candidate service, not called from Checkout/Inventory production paths**.

After all owners approve a V3 Order implementation, the intended T1 integration
must call `prepareInsideDraftT1(draft)` **inside that same
`CheckoutDraftJournal.prepare` REQUIRES_NEW transaction**, after writing the
saved draft and before return/commit. It must not be invoked after
`OrderWriteService.prepareDraft` returns, as that would create two separate
commits and violate the pre-HTTP atomicity requirement. Do not merely enable
a feature flag; migration, store facts, pair auth and recovery must be approved.

This candidate does **not** persist per-RESERVE/CONSUME/RELEASE operation IDs or
authorize V3 HTTP; the final V3 T1/T2 operation journal design still needs an
approved additive migration, exact fingerprint/type/lines and send generation.
The current legacy `InventoryOperationJournal` remains unchanged and quarantines
UNKNOWN without automatic reopening or blind compensation.

## How to verify in an isolated Windows worktree

Run in Windows PowerShell **from the repository root** (do not delete shared
worktrees or uncommitted files):

```powershell
Set-Location D:\Project_HK5
git fetch origin
git worktree add D:\Project_HK5_OWNERMAP origin/feat/tv3-reservation-owner-map-candidate-20261104
Set-Location D:\Project_HK5_OWNERMAP
git status --short
git rev-parse HEAD
.\backend\gradlew.bat -p backend :order-service:test --tests '*ReservationOwnerSnapshotTest' --rerun-tasks --no-daemon --console=plain
.\backend\gradlew.bat -p backend :order-service:postgresIntegrationTest --tests '*ReservationOwnerCandidatePostgresIT' --rerun-tasks --no-daemon --console=plain
```

PostgreSQL tests require working Docker/Testcontainers and Java 21. Record
actual exit status, JUnit XML, local SHA/branch and counts. A class-init or
Docker failure is **BLOCKED_ENV/NOT_RUN**, never a PostgreSQL PASS. Unit PASS
alone does not establish transaction or distributed recovery acceptance.

Before a separate real integration PR, rehearse Order V5→candidate V6
against a disposable PostgreSQL16 DB with baseline and candidate migration
locations. Verify a fresh default main startup does **not** apply V6.
Do not use any shared Compose DB or delete existing Docker volumes.

## Owner-gated acceptance still missing

- **TV2**: authoritative Product→SKU/current Store+Seller facts; delegated
  current actor and scoped pair/lookup authorization; exact approved API path.
- **TV3/TV4**: implement per-step durable V3 operation journal/transport and
  agree `lookup(operationId,reservationId)` response/errors. Lookup absence
  means UNKNOWN, never definitive no-commit or permission for a new operation ID.
- **TV4**: provider canonical fingerprint/commit receipt and bounded recovery.
- **TV5**: partial cancellation, finance/COD/reversal/PII retention, return
  settlement boundary and full QA acceptance.
- **TV3/TV4/TV5/TV2**: signed isolated fault fixture with authentic provider
  receipts; lost-response LIVE NOT_RUN and claim-conflict BLOCKED_DESIGN.

**Evidence at authoring time:** code/SQL/tests committed on a separate branch;
Java unit, PG16, migration rehearsal, browser E2E and live HTTP: **NOT_RUN**.
Do not mark Issues #3/#4/#10/#11/#16/#18 or #155 complete from this candidate.
