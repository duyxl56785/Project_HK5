# TV3 — durable Checkout draft + Inventory T1/T2

Refs #3, #10, #16. **Draft / owner review only. Checkout remains PARTIAL. Five live acceptance tests: NOT_RUN, 0 executed.**

## Integration boundary

This branch starts directly at `main@faffda5207b87dd362ce1ce402c5501ab3233a1f`, after merges #138 and #139. It is not stacked on #138. The imported patch is exactly the 16-file `backend/order-service` delta from #138 source `a589ca20c3b2e63630d24fef0ee2c96b86ac88da` to local candidate `dfe903230c0d2c41ac59b412fd0f249b9f951da2` / tree `e7a45238a336f1ad52b5e61471302864df2f86e8`.

Before applying, the Order source on main was identical to #138's source. Patch check/application had no conflicts. All 16 resulting files match the candidate byte-for-byte. The shipping replay guard from #138 stays in the base; no frontend/Auth/Gateway/Finance/Inventory source is replaced. V1–V4 remain byte-identical to main. V5 was unused in the Order migration namespace at this base.

`provenance.json` records the exact source commit/tree and source hashes. Source-only commit is created after tests from the identical staged tested tree; a subsequent evidence-only commit adds this review package. The artifacts do not claim that historical 329 PASS, #138 CI or main CI ran this new integrated HEAD.

## Functional source

- `domain/CheckoutDraft.java`: immutable versioned business snapshot including root/child/item IDs, Product/SKU/quantity, shipping, Cart revisions, House facts, frozen BigDecimal totals/fees and exact outbox payload/event ID.
- `service/CheckoutDraftJournal.java`, `service/OrderWriteService.java`, `service/CheckoutService.java`: persist/commit draft before Inventory I/O; bind to customer/fingerprint/processing attempt, enforce immutability, verify payload SHA256 on load. Draft is private storage, not a placed Order or recovery authorization.
- `ports/InventoryHttpAdapter.java`: persist RESERVE intent T1 before sending; dispatch the saved tuple once from atomic PENDING claim; T2 records verified success/rejection, preserves uncertainty. RESERVE refuses an ambient DB transaction. Existing non-RESERVE HTTP path and TV4 DISPATCHED cutoff remain unchanged.
- `service/InventoryOperationJournal.java`, `service/InventoryOperationCaller.java`: UNKNOWN quarantined across both claim APIs/generic caller, atomic PostgreSQL predicate, send-generation fencing, stale response rejection. Positive NotSent may return only the current IN_FLIGHT generation to PENDING; UNKNOWN has no reopen/resolution API.
- `db/migration/V5__checkout_order_drafts.sql`: additive table, FK to existing attempt, unique draft per attempt, version/JSON/hash-shape checks. No backfill, GRANT, runtime switch or automatic recovery.

Paths above are relative to `src/main/java/com/projecthk5/orderservice/`, except migration under `src/main/resources/`. Exact full paths appear in `provenance.json`.

## Test evidence

See [results.json](results.json) for executed commands, per-group counts and commit binding; [logs](logs/) for sanitized console logs and [junit](junit/) for suite/testcase outcomes. These are fresh local executions on the integrated source. Count tests once from XML; child JVM halt cases are already included in the Order PostgreSQL total.

Source-only commit: **`f2529ffb8036898d9eee847917912128689d8f43`**, tested tree **`0b4a376956f13bb623c3b5c2dc190786142825d7`**. Java 21.0.8+9, Gradle 8.10, actual PostgreSQL versions 16.6 and 16.15 (the existing `16-alpine` tests resolve to 16.15).

| Fresh suite | PASS | FAIL / SKIP |
|---|---:|---:|
| Order unit | 156 | 0 / 0 |
| Order PostgreSQL | 103 | 0 / 0 |
| Cart unit | 11 | 0 / 0 |
| Cart PostgreSQL | 13 | 0 / 0 |
| Inventory unit | 27 | 0 / 0 |
| Inventory PostgreSQL | 19 | 0 / 0 |
| **Total, both commands exit 0** | **329** | **0 / 0** |

| Invariant | Regression in source | Evidence group |
|---|---|---|
| Draft and journal commit visible before HTTP | `CheckoutDraftPostgresIT.completeBusinessSnapshotCommitsBeforeHttpAndMatchesPlacedRowsAndOutbox`; `CheckoutInventoryJournalPostgresIT.intentIsCommittedBeforeHttpAndSuccessfulReplayUsesNoNewSnapshotOrSend` | Order PostgreSQL |
| IDs/shipping/money/SKU/quantity/outbox survive crash | `CheckoutDraftPostgresIT.committedDraftSurvivesAbruptChildJvmHaltWithoutCompletingOrReleasing` at AFTER_DRAFT, AFTER_T1, AFTER_RESERVE; child exit 86 and independent read | Order PostgreSQL; 3 parameterized cases |
| Immutable draft/checksum/customer binding | `concurrentSameDraftIsOneRowAndChangedShippingCannotOverwriteIt`, `wrongCustomerFingerprintOrClosedAttemptCannotWriteDraft`, `corruptPayloadFailsClosedWithoutNetworkOrOrderCreation` | Order PostgreSQL |
| UNKNOWN cannot be reopened by disabled caller or RESERVE | `CheckoutInventoryJournalPostgresIT.unknownGenericDisabledTransportCannotReopenRowOrEnableHttpAdapter`, `unknownCannotBeClaimedThroughLegacyJournalApi`, `unresolvedUnknownCannotBeRedispatchedByEitherReserveEntryPoint` | Order PostgreSQL |
| Atomic PENDING claim/concurrent sender | `stalePendingReadCannotClaimAConcurrentlyCommittedUnknown`, `concurrentGenericCallersCannotBothSend`, `concurrentSendersCannotBothDispatchOneJournalEntry` | Order PostgreSQL |
| Stale generation and late success fenced | `InventoryOperationJournalPostgres16IT.staleGenerationCannotMutateALaterSendAfterPositiveNotSent`, `lateT2AfterCrashScannerMustNotOverwriteUnknown`; draft late HTTP success regression | Order PostgreSQL |
| Replay/deduplication and #138 shipping guard | `CheckoutReplayPostgresIT`; `CheckoutDraftPostgresIT.overlappingSameKeyCheckoutCreatesOneDraftOrderOutboxAndInventorySend`; Finance durability replay tests | Order PostgreSQL |
| No regression to Fulfillment/COD/Finance or adjacent modules | Existing Order suite plus Cart/Inventory suites listed in results | Unit + PostgreSQL, within fixture scope |

PostgreSQL tests use real disposable Testcontainers/Flyway/JPA. Several Order tests mock Cart/Catalog and use a loopback HTTP Inventory peer. HTTP sockets/timeouts are real, but the peer is a fixture: this does not prove physical Inventory stock, real Product authority, live JWT/current-authority or website/browser E2E. Unit/MockMvc/synthetic-token checks are distinguished from these tests.

[historical-red](historical-red/) retains the prior **6 FAIL / exit 1** regression evidence before P1/P2 fixes on `9a6d9f3`, with provenance. That is historical, not a new RED run. Fresh integrated GREEN results are separate. No source was changed to weaken assertions during integration.

## Migration rehearsal and deployment limits

[migration-rehearsal/REPORT.md](migration-rehearsal/REPORT.md) preserves the prior PostgreSQL **16.6 V4 → V5 rehearsal: 35/35 checkpoints PASS**, exit 0, from candidate `dfe9032`. V1–V5 bytes on this branch are identical. This is not a fresh 35-test run on the integrated commit and is not added to the test total. Report, raw harness log, checks, Flyway metrics, lock observations and hashes are published here. Row snapshots/pg_dump are deliberately kept local, never committed.

Observed V5 execution: 5 ms / migrate-call wall 47 ms on tiny fake data. FK creation waited for ShareRowExclusiveLock on `checkout_attempts`; controlled lock_timeout caused 55P03 and transactional rollback without repair. V5 gives a separately provisioned runtime role no implicit privileges: fixture-only SELECT+INSERT grant was required and explicitly tested. Owner must decide actual target roles/grants, DDL window, backup, PII retention and parent purge/FK policy; see [OWNER_DECISIONS.md](OWNER_DECISIONS.md). No DB rollout is authorized by these results.

## Publication and remaining gates

- Review source: TV4 `phamthecuong2022006-alt` (Inventory boundary/migration), TV5 `nguyenminhduy090-alt` (frozen Finance facts/UAT), TV2 `duyxl56785` (authority/integration). TV1 participates for current-authority, DB roles and PII controls. Listing owners here is not proof a GitHub review request succeeded.
- Require a fresh **Backend readiness CI SUCCESS at final PR HEAD** and independent owner review. Main/#138 historical CI is insufficient. Backend workflow is PR-triggered for backend changes; this feature branch is not a push-trigger branch.
- No recovery executor/resolution endpoint, network retry, implicit RELEASE/CONSUME or completion of UNKNOWN is added. Persisted draft does not confer authority to write an Order after restart.
- Five published live acceptance cases remain [on their existing handoff branch](https://github.com/duyxl56785/Project_HK5/tree/feat/tv3-two-store-acceptance-review), unchanged and NOT_RUN pending owner-confirmed isolated stack, accounts/two-Store SKUs/stock and real fault receipts. No browser E2E claim.
- Runtime Inventory/Finance/Auth gates unchanged. No new API/event/fingerprint format, production DB access, frontend changes, or Issue completion.

## Evidence integrity / sanitization

`SHA256SUMS` hashes the published files; run `sha256sum -c SHA256SUMS` in this directory. `provenance.json` maps raw local hashes to published hashes and explains transforms. Console logs have sensitive-field lines withheld if present. XML is an allowlisted projection of suite/testcase metadata and outcomes: system-out/err, properties, hostnames and stack traces are removed. Original raw files remain unchanged locally. No DB dumps, data snapshots, tokens/passwords, payload PII or compiled binaries are published. The original rehearsal manifest names excluded local artifacts to preserve provenance; those raw data files are not included in Git.

Generated console logs preserve three original Gradle trailing spaces at line 3 of `logs/order.log`, `logs/cart-inventory.log` and `historical-red/red.log`; `git diff --check` reports these evidence-only lines. They are retained to preserve raw hashes. Source and non-log artifacts pass whitespace checks. Raw Order output includes background Kafka connection warnings and datasource shutdown errors after disposable containers stop; these are not hidden and do not change the XML outcomes or exit code. No live Kafka/service stack was started to silence them.
