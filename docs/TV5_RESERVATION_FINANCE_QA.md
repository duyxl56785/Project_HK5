> **Current clarification — 30/10/2026:** The 22–23/10 tables below are historical.
> Their Product-price signature/persistence PENDING entries were superseded by the
> [canonical 25/10 acceptance receipt](TV5_FINANCE_CONTRACT_SIGNOFF.md#verified-product-price-slice-acceptance--25102026).
> MD01, MD02 and Product-unit MD03 already have the scoped human business receipt
> [#84](https://github.com/duyxl56785/Project_HK5/pull/84#issuecomment-6364386964),
> [Level A design review](https://github.com/duyxl56785/Project_HK5/pull/84#pullrequestreview-5637148973)
> and [Level B internal persistence review](https://github.com/duyxl56785/Project_HK5/pull/88#pullrequestreview-5639341936);
> #84/#88 are MERGED. Do not request those signatures again or block the approved
> Product price scope on unrelated MD04–MD10. Quantity/aggregate caps, full Finance,
> House/commission/rounding/COD/refund/settlement and runtime authorization remain
> separate PENDING/BLOCKED gates. This clarification records existing decisions;
> it adds no signature, business policy, implementation permission or flag change.

> **Historical 23/10 checkpoint:** [63PR/allreviewers/currentQA and ownerhuman decisions](../reports/TV5_REVIEW_ACCEPTANCE_2026-10-23.md). Prior22/10snapshots below retain history;69oldCRsuperseded,68/70/73/75MERGED. M01PENDING,FinanceproductionBLOCKED.

# TV5 — reservation / Finance QA handoff, 22/10/2026 R2

Refs #5/#12/#69/#70/#71. **Technical QA verified; shared contract and business decisions PENDING.**
TV5 Nguyễn Minh Duy is proposer/Finance consumer, not designated final business approver.
Latest tested Inventory candidate4eeeb3843f2795168ca03cdd3cd2e8be9f825ec6 is Java-only, Draft, no authorized HTTP route.
[Fresh audit and exact-source evidence](../reports/TV5_PR_REVIEW_FOLLOWUP_2026-10-22_R2.md).

## 1. Verified provider invariants and test mapping

All methods below are in `InventoryReservationLedgerCandidatePostgresIT` at69head4eeeb384.
14candidate invocations (including three SKU-padding parameters) are part of34PG, not additional tests.
These are actual TV5-executed PostgreSQL tests, not merely a proposed acceptance matrix.

| QA ID | Requirement / actual method | Result | Limits |
|---|---|---|---|
|RV-Q01|Owner tuple cannot spend another hold: `wrongStoreAndSellerOrderCannotUseAnotherReservationsHold`|PASS|Compared supplied identifiers, not verified principal/current Store authorization|
|RV-Q02|Exact operationId tuple binds owner/type/payload: `sameOperationIdWithChangedOwnerTypeOrPayloadConflictsWithoutStockChange`|PASS|Does not define HTTP409/error envelope without agreed wire|
|RV-Q03|Exact retry after lost response / two Store budget isolation: `twoStoresWithSameSkuKeepIndependentOwnerBudgetsAndLookupSurvivesLostResponse`|PASS|Fixture isolation is not authoritative global Product/SKU source|
|RV-Q04|Partial held budget and multi-SKU rollback: `multiSkuPartialBudgetFailureRollsBackEarlierLineAndOperationClaim`, `failedMultiSkuReserveRollsBackReservationAndOperationLedger`|PASS|Atomic provider SQL, not distributed Order/Inventory atomicity or partial-refund policy|
|RV-Q05|Concurrent same RESERVE/RELEASE operationId replay: `concurrentSameReserveOperationIdReplaysWinnerWithoutDuplicatingOwnerOrStock`, `concurrentSameReleaseOperationIdReplaysWithoutReturningStockTwice`|PASS|Not live caller T1/recovery runtime|
|RV-Q06|Concurrent distinct RELEASE IDs cannot overspend: `concurrentDistinctReleaseIdsCannotSpendTheSameOwnerBudgetTwice`; changed retry conflict: `concurrentReleaseRetryWithChangedPayloadConflictsAfterWinnerCommits`|PASS|No automatic permission to release on UNKNOWN or refund money|
|RV-Q07|Initial SKU lines immutable: `reservationOwnerCannotChangeInitialSkuLinesWithANewOperationId`|PASS|Does not approve mutable Product mapping or new reservation on ambiguous outcome|
|RV-Q08|Reject STOCK adjustments +noncanonical SKU before mutation: `reservationCandidateRejectsStockAdjustmentTypesBeforeCreatingLedgerRows`; padded-SKU parameterized test|PASS|Exact case/composition retained;70Product boundary aligns, no global persistence uniqueness proof|
|RV-Q09|Default Flyway/V2 Hibernate compatibility: `InventoryDefaultFlywayPostgresIT.defaultFlywayLocationAppliesOnlyApprovedV1AndV2AndJpaValidationStarts`|PASS|Candidate V3 opt-in in tests, not approved production rollout|

Provider schema requires `initial = consumed + released + held`, holds/reservation owner budget and operation journal update atomically.
Existing19PG regressions and27unit also ran; do not report34candidate tests.
Keep original logs/XML private, exact byte hashes in [derived69receipt](../reports/evidence/tv5-audit-20261022-r2/pr69.json).

## 2. TV5 conditional technical position — not a business vote

1. A committed Inventory operation is evidence of that stock operation only; it must not be converted into COD PAID, SellerOrder DELIVERED or settlement eligibility.
2. UNKNOWN/absence is not DEFINITIVE_NO_COMMIT. Same immutable ID/payload must be retained; no blind compensating RELEASE or replacement operationId.
3. Partial CONSUME/RELEASE can be correct held-budget arithmetic while partial delivery/cancellation/refund remains unapproved. The candidate test is not a business signoff for those actions.
4. Settlement must consume committed authoritative SellerOrder/payment snapshots under the approved policy and trusted Store identity, not current Catalog price or client-supplied House flag.
   Current pure guard tests do not implement atomic unique settlement, reporting, Store-scoped SQL or current ADMIN authorization.
5. Candidate lookup compares operationId/reservationId, not authenticated caller. TV1 must approve exact allowed callers/roles, original delegated actor and recovery-system provenance before exposure.
6. Timestamp clarification: lookup `committedAt` is loaded from `created_at`, set with SQL `CURRENT_TIMESTAMP` at operation insert.
   Per [PostgreSQL16 timestamp semantics](https://www.postgresql.org/docs/16/functions-datetime.html#FUNCTIONS-DATETIME-CURRENT), it is transaction-start time, not exact commit wall-clock time. TV4/TV3 must choose/document name and semantics before wire freeze;
   Finance must not use it for revenue cutoff, payment confirmation or delivery time. No production timestamp consumer exists in this candidate.

These technical invariants may inform owners' contract, but do not authorize a route, rollout, cancellation/refund policy, fee schedule or Finance flag.

## 3. Owner decisions still required (do not repeat existing unchanged questions)

| Decision | Proposal / completion evidence required | Owner / confirmers | Status | Implementation permitted now |
|---|---|---|---|---|
|Reservation lifecycle/C06|Persist Order T1 reservation→SellerOrder tuple before call; exact RESERVE/dispatch CONSUME/cancel RELEASE boundary, outbox/recovery and ambiguous outcome semantics; same-version producer/consumer reply|TV3+TV4, TV1 security; TV5 consumes resulting facts|PENDING exact wire/runtime; existing principles acknowledged|Candidate tests and reviews only; no Checkout wiring|
|Lookup timestamp/status/errors|Document `created_at`/transaction-time meaning vs proposed `committedAt`; scoped COMMITTED/UNKNOWN, exact HTTP/status/error choices; no absent→no-commit inference|TV4 provider +TV3 consumer; TV1 exposure|NEEDS_OWNER / PENDING wire freeze|No HTTP exposure or financial cutoff implementation|
|Trusted Product/SKU/Store facts|Exact immutable Product→inventorySku and ownership source, current verified Store/Seller status; global uniqueness enforcement;71oneSeller/oneStore fixture consistency is not the source|TV2; TV3/TV4 consumers; TV1 current authority|Technical SKU boundary aligned70/69; production source/runtime PENDING|No mock→production or positive-pair claim|
|COD/payment/delivery source|Explicit source/action/transaction/event version, eligible states, PAID audit/amount/currency, duplicate/replay conflict, event ordering and committed delivery; current ADMIN/operator/system proof|TV4+TV3; TV1 auth; TV5 Finance consumer +business signer|Existing technical replies retained; authoritative runtime/business PENDING|No settlement trigger, PAID fabrication or self-approved COD policy|
|M01 Product slice|Latest Product unit1..1billionVND candidate, exact positive integral string/BigDecimal; authorized signer/version/scope +applicable schema/S01/security gates|TV1/project authority designates signer; TV5 proposer; TV2/TV3 confirm own responsibilities|PENDING BUSINESS APPROVAL; separate fromMD04–10|Do not alter price persistence/validation policy based on agent proposal|
|Finance snapshots/House/fee/refund/cutoff|Authorized House assignment source, commission basis/rate/rounding stage, immutable currency/gross/rate/fee/net/version, adjustments/refund and reporting definitions|Business signer+TV5; TV2 facts, TV3 snapshot, TV4 COD, TV1 authorization|MD04–10 PENDING; proposed5%/House0/net formula not final approval|Rules/tests/docs only; Finance production BLOCKED, flagfalse|

Existing M01 authority request6254401376 and owner COD/current-authority requests onIssue12 remain active; no duplicate broad reminder needed.
S01 baseline one-Store-per-Seller is already recorded; these questions concern concrete source/wire/runtime, not reopening agreed architecture.
Reply format: `Contract ID | version | own responsibility | APPROVED/REVISE/PENDING | evidence URL | remaining gates`.
Agent cannot supply another owner's or a business signer's acceptance.

## 4. Future acceptance matrix — NOT_RUN / BLOCKED

| ID | Scenario required after contract/authorized runtime | Status / dependency |
|---|---|---|
|RV-F01|Actual Order T1 commit then timeout/restart, same operationId reconcile against real Inventory without double reserve|NOT_RUN; TV3/TV4 runtime and signed C06|
|RV-F02|Current caller/actor/Store mismatch denied; forged headers/suspended actor/async provenance; authenticated positive allowed path|NOT_RUN; TV1/TV2 current authority +approved pair wiring|
|RV-F03|DELIVERED without PAID, PAID without DELIVERED, wrong Store, replay and concurrent settle|BLOCKED; authoritative COD/delivery facts +approvedM01 +TV5production persistence scope|
|RV-F04|Committed unique sellerOrder settlement and Store-scoped query with exact immutable money snapshots|BLOCKED; same approved schema/policy/security/provider prerequisites|
|RV-F05|Partial delivery/cancel/refusal/return/refund state and money reconciliation, duplicate/out-of-order events|BLOCKED; TV3/TV4/business state/action/event/refund decisions|
|RV-F06|Two Store React→Gateway→services→PostgreSQL UAT with realCatalog/Inventory/COD|NOT_RUN; actual positive providers/pair/routes/frontend, not fixtures|

NormalCI does not imply these scenarios ran. No issue DONE, production gate enablement or live E2E conclusion follows from RV-Q01–Q09.

## 23/10 owner-response delta (not a new contract signature)

TV1scopedAPPROVED69/4ee now supersedes oldCR; owner keepsDraft/V3opt-in. TV3's SKU consumer boundary fix75 is merged; new77pureCatalogpreflight and78Order-local recovery tests restore closed-unmerged62/60 content under explicit new heads.79activeCheckoutguard is separate from unwired77. Current technical approvals are recorded in the linkedreport, not converted to C06/M01 signatures.
TV5today verified77money/currency/House-consistency scope16unitPASS,78Order108unit+29PG (13journalPGsubset),79Checkout19mockedunit, andmain3originalFINPGdiagnostics. These do not prove Inventoryprovider ownership, remainingheld, authorizedcancellation, COD/paymenttruth or atomicsettlement.
Existingoperationlookup timestamp question and authority/CODrequests remainpending; no sourcechange/readiness inferred from extra proposals. Human71formalreview is actionable without more code. TV1realAuthruntime is still pending per6354324181; don't reopen completed sourceprep review or wire positiveadapters yourself. Finance/pairflagsOFF, M01PENDING andIssue5/12PARTIAL.
