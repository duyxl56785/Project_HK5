# TV5 — Finance regression after integration of PR #41

Refs #5/#12/#28/#37/#41. Checkpoint 2026-10-18, Asia/Bangkok.
**PARTIAL / WAITING_REVIEW / OWNER_CONFIRMATION.** This report supersedes only the
integration/test checkpoint, not the historical raw evidence or human contract decisions.

## GitHub and source verified

- PR41 merged by `duyxl56785` into PR28 branch at `55bf5ff086633eddd37c245abc809f5e0ce57be8`,
  2026-10-18T05:16:55Z. PR41 head aaf9d31 is an ancestor; whole-tree diff aaf9d31→55bf5ff is empty.
  No push, reviewer request or reopen on merged PR41.
- PR28 remains OPEN, base main, checked head55bf5ff. Its three review threads, including TV5 JWT,
  are resolved. All168 changed files listed through paginated API; latest reviews/commits/CI read.
- PR37 remains OPEN at9c28338, with no new owner response after
  [TV5's versioned money request](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6245987112).
- A separate TV5 branch starts from exact55bf5ff. New delta is Finance tests/QA/docs/evidence only,
  not Checkout/Inventory/Auth production source, shared model, migrations or flag changes.

## New executions by TV5

Java21.0.7, Gradle8.14.3, Docker29.8.1, Testcontainers PostgreSQL16-alpine, Windows.
Process-only `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC -Dapi.version=1.44`.

```powershell
gradle -p backend :cart-service:build :order-service:build :cart-service:postgresIntegrationTest :order-service:postgresIntegrationTest --rerun-tasks --no-daemon --continue --console=plain
```

| Execution | Source | UTC start → finish | Unit PASS | PG PASS | FAIL/errors/skip | Build / exit |
|---|---|---|---:|---:|---|---|
| Parent after integration | 55bf5ff086633eddd37c245abc809f5e0ce57be8 | 05:30:33 →05:31:25 | 72 (Cart6/Order66) | 13 (Cart1/Order12) | 0/0/0 | 51s /0 |
| Supplementary Finance coverage | cd2c0beaba814a947690fb5e7000d73b1748246c | 05:32:14 →05:32:52 | 73 (Cart6/Order67) | 17 (Cart1/Order16) | 0/0/0 | 37s /0 |

Both executions have16 tasks. Do not add overlapping runs into one acceptance total. These are new
TV5 runs, not TV2's checks, prior TV5 PR41 executions, report V2 or an assumed CI result. JWT environment
and decoder tests, Finance rules/config tests and PG replay/scanner regressions actually reran.

Raw supplementary run.json records HEAD at launch55bf5ff because two test files were modified then;
they were committed unchanged ascd2c0be after PASS. source-binding.json explicitly records that derived
binding and both working-tree and Git-blob hashes; no raw run record was rewritten to hide this detail.

## Added coverage and exact meaning

| New case | Evidence / expected result | What it does not prove |
|---|---|---|
| Large quantity (1 unit) | BigDecimal 0.01×Integer.MAX_VALUE=21474836.47; 99999999999999999.99×2=199999999999999999.98 exactly; helper fee remains exact before persistence. | No approved business upper bound, no guarantee the result fits DB. |
| Zero and maximum (2 PG invocations) | Real V1 NUMERIC(19,2), JPA commit + fresh JDBC read preserve0.00 and99999999999999999.99 with exact gross/fee/derived net. | Zero Catalog price eligibility and the new currency policy are not approved; derived net is not a snapshot. |
| Parent aggregate overflow (1 PG) | Each Store60000000000000000.00 fits, combined parent120000000000000000.00 fails SQLSTATE22003; transaction rolls back Order/children/items/outbox, attempt staysPROCESSING with no orderId. | Not settlement atomicity or proof of external Inventory compensation/reconciliation. Expected DB rejection is a PASS assertion, not a hidden test failure. |
| Financial successful replay (1 PG) | Real attempt/writer/repository, two Stores100+200; replay twice under deliberately different fixture fee policy retains300 total,5 fee, same child rows and exactly one placement outbox event. Catalog/Inventory have no calls, Cart finalize twice. | No production settlement/revenue ledger, Auth or service-to-service E2E; ports and alternate policy are fixtures. |

No previously passing assertion was weakened, no test deleted/disabled and no production bug fix claimed.
The five additions close independent QA gaps. Existing settlement eligibility truth-table and Store guard
tests were already complete for their pure-helper scope; rewriting them would not prove absent APIs.

## New finding FIN-06 — aggregate bound is checked only by the database

**Owner:** TV3 Checkout/Order model + TV5 M01 policy; TV2 for Product price bounds.
**State:** BLOCKED for release of live Checkout, not corrected by this QA patch.

MoneyRules.lineTotal and fee use arbitrary-precision BigDecimal, and prepareOrder sums Store subtotals
without validating current NUMERIC(19,2) capacity. The new PG case proves SQLSTATE22003 at savePlaced.
Checkout calls Inventory.reserve **before** OrderWriteService.savePlaced, so an approved/enabled live
path with excessive amounts could reserve stock before DB rejection and enter UNKNOWN reconciliation.
Current adapters and policy remain fail-closed; this is not a claim that a production reservation occurred.

Required owner action after M01 approval: validate exact unit/line/Store/parent currency+scale+precision
before RESERVE; reject known-invalid amounts without side effects, use reviewed V2+ changes if bounds
change, then add consumer tests asserting no reserve/write on rejected aggregate. No unilateral Checkout
or schema edit was made. DB transaction rollback remains proven, and external recovery remains unproven.

FIN-01 scale loss, FIN-02 missing currency/rate/House/policy/net snapshots, FIN-03 absent production
COD/atomic unique settlement, FIN-04 Store/live authority and FIN-05 rounding sign-off remain open.
New tests do not close those findings or introduce new business constraints.

## Current contract status / implementation permission

[Full versioned technical proposals, signers, evidence and permitted modules](../docs/TV5_FINANCE_CONTRACT_SIGNOFF.md)
remain the reference; TV5-FIN-v0.1 has **not** gained human signatures merely because PR41 merged.

| Contract | Overall state | Accountable owner / missing written agreement | Allowed after relevant agreement |
|---|---|---|---|
| S01 | PENDING | TV2 Store +TV1 boundary/current authority; TV3/TV4 consumer, TV5 Finance scope | TV2 Store source, verified Store-scoped consumers |
| C01 | REVISE | TV2/TV3 DTO/identifier/batch wire, TV1 pair auth, TV5 currency and TV4 SKU | Secured authoritative Catalog+consumer contract |
| E01 | REVISE | TV2/TV3 HTTP/envelope/missing-vs-unavailable, TV1 privacy/security | Error mapping and no partial Checkout |
| M01 | PENDING | TV5 human/business decision maker, TV2 prices, TV3 snapshots/bounds, TV4 COD/refund, TV1 enable authority | Reviewed monetary V2+ and compatible consumers |
| Inventory/C06 | PENDING /REVISE C06 | TV2 mapping, TV3/TV4 state/journal/retry, TV1 scope, TV5 COD separation | Exact-line per-step journal/recovery and restricted operations |
| Security pairs | PENDING; OD-09 baseline DECIDED | TV1 concrete pairs/actor checks +TV2/TV3/TV4 producers/consumers | Caller-specific allowlists; never broad Inventory token |
| Finance/COD settlement | PENDING | TV5/TV4/TV3 delivery+paid source, snapshots, atomic unique settlement, TV2 Store/House authority, TV1 actor/query scope and business approvals | Production settlement only after all its prerequisites |

Evidence: [TV2 proposals](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6238333126),
[TV3 consumer](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6238558286),
[TV4 six-contract response](https://github.com/duyxl56785/Project_HK5/issues/4#issuecomment-6244373601),
[TV5 explicit candidate/request](https://github.com/duyxl56785/Project_HK5/pull/28#pullrequestreview-5561540775).
No new APPROVED row; fee5%/House0/net and DELIVERED+CODPAID existing requirements remain distinct
from full signed M01/wire/runtime acceptance. VND/scale0/HALF_UP stage/bounds are still proposals.

## QA matrix / work remaining

- [x] Verify41 integration, latest heads/reviews/threads/CI and current source; preserve old worktree/cache.
- [x] Fresh parent72unit+13PG regression and supplementary73unit+17PG, original logs/XML/timestamps.
- [x] Five independent money-bound/rollback/replay cases, FIN-06 owner and evidence.
- [x] Update QA/contract checkpoint; separate pure guards, mock ports, real PG and live tests.
- [ ] M01 and six-contract same-version human signatures; no automatic repeated reminder.
- [ ] Complete currency/rate/House/net snapshots and pre-reserve bounds after ownership agreement.
- [ ] COD ADMIN current authorization; DELIVERED+PAID atomic settlement, concurrent uniqueness,
  recovery and Store-scoped read/list/aggregate: **NOT_RUN/BLOCKED**, no runtime implementation exists.
- [ ] Live Catalog/Inventory/Auth/Gateway and one-Customer/two-Store E2E: **NOT_RUN/BLOCKED**.
- [ ] Issue5/12 whole acceptance: **not DONE**. No production settlement or week2–3 feature work.

Independent QA gaps in the current implemented Finance surface are covered. Next meaningful work
requires owner responses or a changed runtime source; do not manufacture an API/settlement implementation
or duplicate guards to avoid waiting. State: **WAITING_REVIEW / OWNER_CONFIRMATION**.

## Artifact verification

[43 artifacts and manifest](evidence/finance-followup-20261018/README.md): original logs/XML/run metadata,
derived source binding, Docker preservation and exit codes. Raw bytes retained with scoped -text;
code/docs diff-check separately PASS. No secrets detected in the public bundle. Both executions preserve
all3 pre-existing containers and2 named volumes. No prune/down-v/delete against existing Docker data.
Final delivery checks must verify all43 committed Git blobs against the SHA256 manifest.
