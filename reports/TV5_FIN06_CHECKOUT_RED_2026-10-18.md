# TV5 — FIN-06 Checkout-side RED diagnostic and updated contract checkpoint

2026-10-18, Asia/Bangkok. Refs #5/#12/#28/#37/#42.
**KNOWN RED / OWNER IMPLEMENTATION BLOCKED / NOT FINANCE DONE.**

## Current source, owner response and completed integration

- PR42 has been merged by the owner into PR28 at9d54c17. PR28 now has merge head
  `28dd0cfeab988386723896af74b8c6b9364228a2`, with main524e4f3 as second parent.
  Cart/Order trees remain identical to the tested patch35297ac; no old suite is presented as a new run.
- [TV3 FIN-06 disposition](https://github.com/duyxl56785/Project_HK5/pull/42#issuecomment-6246202960):
  **CONFIRMED / FIX NOT_IMPLEMENTED**, implementation proposal only AFTER applicable contract sign-off.
  This is not permission for TV5 to edit Checkout production. No production source/migration/flag changed here.
- [TV3 main-merge QA handoff](https://github.com/duyxl56785/Project_HK5/pull/28#issuecomment-6246374469):
  independently verified docs/QA.md at28dd0cf is exact main524e4f3 content plus the two TV5 18/10 sections
  from9d54c17. Exact byte union, no duplicated sections and15 relative file links **PASS**.
  Derived static evidence: qa-union.json. This is QA content confirmation, not PR approval.
- PR37 head `63668b870c6e2e65e86c28b8c79715f2d0a9e7c8` adds an internal Catalog source fix and tests;
  candidate wire response v0.2 atc375951 and TV3 reply are now part of the decision evidence.
- Own JWT thread atPR28 remains resolved; two other threads also resolved, none changed in this task.

## Execution evidence — new tests actually run

Java21.0.7 / Gradle8.14.3 / Docker29.8.1 / Testcontainers PostgreSQL16-alpine on Windows.
Process-only `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC -Dapi.version=1.44`.

| Execution and source | UTC start → finish | PASS | FAIL | Error / skip | Exit / build |
|---|---|---:|---:|---|---|
| Checkout diagnostic at28dd0cf + test-only delta committed unchanged as5fa10ce | 06:16:34 →06:17:03 | 1 | **2** | 0/0 | **1 /28s** |
| Targeted internal CatalogBatchValidatorTest at63668b8 | 06:17:00 →06:17:14 | **50** | 0 | 0/0 | 0 /13s |

### Diagnostic command

```powershell
gradle -p backend --init-script ../tests/finance/fin06-regression.init.gradle :order-service:fin06Regression --rerun-tasks --no-daemon --console=plain
```

[Executable source and invocation notes](../tests/finance/README.md). An explicit init script adds only
this invocation's source set/task; shared build and normal suites are unchanged. The task is deliberately
RED until the owner implements safe preflight. No @Disabled, assertion weakening or removal of old tests.
Default CI/build does not run this separate diagnostic; green default CI **cannot close FIN-06/FIN-01**.

Spring loads the real application/YAML, real Checkout/IdempotencyCoordinator/writer/repository and actual
Flyway/JPA/PostgreSQL. Only Cart/Catalog/Inventory ports are mocked. The manually constructed caller gets
a synthetic approved MoneyRules fixture; the production-injected MoneyRules remains closed (positive
control explicitly verifies that). No environment/deploy policy flag enabled, no real stock reservation.

### Actual observations

| Test | Actual result / evidence | Required next action |
|---|---|---|
| Positive control | PASS: exact100+200 two-Store fixture commits300 total/5 fee; replay reserves/writes only once, one placement outbox event; productionMoney rejects pricing. | Confirms the diagnostic harness reaches real transaction boundaries; no settlement/live-E2E claim. |
| FIN-06 pre-reserve aggregate rejection | **FAIL**: each subtotal60000000000000000.00 fits current NUMERIC(19,2), combined120000000000000000.00 does not. InventoryPort.reserve and writer.savePlaced both invoked; DB22003 rolls back Order/children/outbox. Cart is not released and attempt becomesNEEDS_RECONCILIATION. Four assertions fail (no reserve/no write/safe Cart release/known FAILED state). | TV3 preflight before reserveRequested/Inventory, with TV5 M01 compatibility. Do not change UNKNOWN handling to blind compensation. FAILED state/Cart-release expectations are provisional acceptance from TV3's proposal, not signed wire/status contract. |
| FIN-01 incompatible precision/replay | **FAIL**: fixture helper scale3 permits1.001+2.001; initial Checkout total3.002; writer commits scale2 DB rows; successful replay total3.00. Reserve/write occur and one Order exists, instead of preflight rejection. Five assertions fail; unit/PG initialization did not fail. | TV3 reject unsupported storage precision or implement signed compatible representation before reserve. M01 business scale remains pending; no implicit VND/scale0 selection from the fixture. |

These are **2 failing test invocations**, not9 separate tests (nine aggregated assertions fail).
Do not add the Catalog50 or historical Cart/Order73+17 into one acceptance number. The prior73unit+17PG
fromcd2c0be is historical/equivalent Cart/Order content, not a fresh default-suite run on28dd0cf.
Default Cart/Order unit/PG suites **NOT_RUN in this task**: no source changes there; targeted new diagnostics
were run instead. CI readiness success reported at28dd0cf is separate; tagged PG diagnostic is not in it.

### Catalog command and exact scope

```powershell
gradle -p backend :product-service:test --tests com.projecthk5.productservice.catalog.CatalogBatchValidatorTest --rerun-tasks --no-daemon --console=plain
```

Read and verified new internal LookupResult FOUND/ABSENT/UNAVAILABLE: omission/null/unavailable cannot
become PRODUCT_NOT_FOUND, incomplete IDs and conflicting same-Store facts fail the batch, and any rejected
item withholds all usable snapshots.50 unit invocations PASS. This does not prove authenticated HTTP,
published JSON `allReady`/decimal strings, source persistence, SKU uniqueness or a live Catalog adapter.
TV2's response document still describes the pre-fix list-only prototype as a known gap: owner should
update that paragraph to current63668b8 facts while preserving candidate/runtime distinctions.

## Current DB facts and concrete fix handoff (proposal, not source edit)

Migration `backend/order-service/src/main/resources/db/migration/V1__checkout_baseline.sql` stores
orders.total, seller_orders.gross/platform_fee, order_items.unit_price_snapshot/line_total asNUMERIC(19,2).
The storage capacity is17 integer digits +2 decimals, not a newly selected business cap. MoneyRules accepts
scale0..4; BigDecimal arithmetic itself does not bound these columns. CheckoutService.prepareOrder
returns the assembled Order, then checkout lines76–79 setreserveRequested, reserve, and write.

TV3's proposed implementation point is inside prepareOrder or a preflight immediately after trusted
Catalog checks and **before reserveRequested=true**. Validate exact unit/quantity/line/Store/fee/net/parent
against both the signed policy and actual schema. A technical storage check can require nonnegative
`amount.setScale(2, UNNECESSARY)` with precision<=19, applied to every persisted amount; a signed policy may
be stricter. This is pseudocode guidance only: M01 currency/rounding/rate/bounds, shared snapshots and
error wire still require owner agreement. Never use a NUMERIC cast as preflight, silently round, edit V1,
change fee calculation or make a blanket RELEASE after an UNKNOWN remote/write outcome.

Owner acceptance checklist after authorization:

- [ ] TV3 implements the approved guard at the pre-reserve point on a separate reviewed source patch.
- [ ] FIN-06 and FIN-01 diagnostic tests become GREEN with assertions preserved; positive control staysPASS.
- [ ] Prove no reserve/write/order/fee/event on known-invalid amounts; Cart release only when known safe.
- [ ] Rerun existing replay/scanner/unknown-commit/rollback and applicable security+PG regressions on fix SHA.
- [ ] Promote applicable diagnostics into agreed normal acceptance gate; no misleading default-build success.

## Contract sign-off matrix — latest evidence, no substituted human signatures

| Contract / state | Concrete latest proposal / current fact | Owner and required signers | Evidence / missing agreement | Permitted modules after applicable signatures |
|---|---|---|---|---|
| **S01 PENDING** | Store on Seller DB/service, local Seller FK/unique Store/slug, Product logical Store ID; trusted current ownership/House source, no client authority. | TV2 +TV1 boundary; TV3/TV4 consumers, TV5 Finance scope | TV2 response v0.2 below; TV1 final boundary/current provider still absent. | TV2 Store/provider; verified Store-scoped consumer adapters. |
| **C01 REVISE** | Candidate catalog.checkout.v1 v0.2: POST batch1–100 distinct IDs, canonical lowercase UUID strings for Product/Store/Seller (not JWT.sub); prices plain decimal strings, explicit currency, allReady/items complete/order-matching, reject whole Checkout before reserve. | TV2 provider +TV3 consumer; TV1 caller/privacy, TV5 currency, TV4 SKU | TV3 accepts technical candidate for further sign-off, not full approval. Positive HTTP/source/freshness absent. | Secured authoritative Catalog and reviewed Cart/Order DTO V2+ consumers, not a fixture adapter. |
| **E01 REVISE** | Candidate200 evaluated batch,400 invalid,401 caller/delegation,403 scope,503 unavailable; top-level code/message/errors. Explicit absent only →notfound. New63668b8 fixes internal absence/READY-subset handling. | TV2/TV3 and TV1 security/privacy; TV5 money disclosure | 50unitPASS proves internal fix, not HTTP/pair contract. TV1 signature pending. | Versioned HTTP/error mapping, deny/default and no-partial-side-effect consumer tests. |
| **M01 PENDING** | TV5-FIN-v0.1 VND integral, scale0 calculation/exact.00 NUMERIC19,2 storage,17-digit bound, HALF_UP once per Store fee; rate5%/House0%, net/subtotal/currency/House/rate/policy snapshots. | Human TV5 +business decision maker; TV2 prices, TV3 bounds/snapshots, TV4 COD/refund, TV1 enable authority | TV2 now explicitly technically compatible; TV3 conditional acceptance only. No complete same-version human signatures for rate/stage/adjustments/enablement. | Reviewed monetary schema/DTO/validation after consent; no deploy flag change now. |
| **C06 /Inventory PENDING; C06 REVISE** | Product-owned exact immutable global SKU target; per-step durable IDs/exact lines, T1/T2, bounded retry/manual hold; CONSUME after committed dispatch, RELEASE only proven pre-dispatch cancel. | TV2 mapping, TV3 journal, TV4 lifecycle, TV1 scope, TV5 COD separation | State/event naming conflict and operational budget/current authority still open; no consumer journal/wire. | Restricted operations, journal/recovery after own sub-contract approval. |
| **Security pairs PENDING** | OD-09 baselineDECIDED, actual pair/method/path/operation +delegated/current actor; no broad Inventory credential to Checkout. | TV1 +TV2/TV3/TV4 pairs, TV5 ADMIN COD/Finance scope | No concrete executable pair approval/live proof. | Caller-specific allowlists/current identity adapters; no invented trust headers. |
| **Finance/COD/Settlement PENDING** | DELIVERED AND CODPAID; current ADMIN confirmation once; approved immutable snapshots; unique sellerOrder settlement +atomic eligibility/insert, verified Store reads. | TV5/TV4/TV3, TV2 Store/House provider, TV1 actor/query policy and business owner | No runtime settlement/payment/current Store Finance source or complete policy/event/transaction signatures. | Production settlement only after all required contracts and explicit scope; not now. |

Evidence: [TV2 candidate v0.2](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246199255),
[TV3 technical response](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246255122),
[TV4 six-contract checkpoint](https://github.com/duyxl56785/Project_HK5/issues/4#issuecomment-6244373601),
[TV3 FIN-06 owner response](https://github.com/duyxl56785/Project_HK5/pull/42#issuecomment-6246202960).
**No new full APPROVED contract.** Existing5%/House0/net/eligibility requirements are retained, not an
approval of VND/scale0/shipping/discount/tax/refund or permission to enable policy.

## Checklist and boundaries

- [x] Read latest28/37/42 heads/reviews/threads/CI/owner response; verify42 integrated, QA union intact.
- [x] Reproduce Checkout-side FIN-06 and precision/replay through real PostgreSQL with original FAIL evidence.
- [x] Positive control PASS; new Catalog50unitPASS on exact changed source; no unchanged full-suite rerun.
- [x] Provide explicit runnable diagnostics, fix location/acceptance, current contract matrix and artifact hashes.
- [ ] Source fix/diagnostic GREEN — **BLOCKED TV3 authorization and applicable signatures**.
- [ ] Production settlement/concurrent uniqueness/Store Finance authorization — **NOT_RUN/BLOCKED**.
- [ ] Live Catalog/Inventory/Auth/Gateway/COD two-Store E2E — **NOT_RUN/BLOCKED**.
- [ ] Finance/Issue5/12 DONE — criteria unmet. No Approve/Merge/Close or policy flag change.

## Artifact integrity

[11-artifact evidence bundle](evidence/fin06-checkout-20261018/README.md) retains original failing/passing
logs, JUnit XML, timestamps, exit codes and Docker inventory. source-binding.json is explicitly derived
metadata tying unchanged diagnostic files to5fa10ce; raw records were not rewritten after commit.
Code/docs diff-check separately PASS; raw CRLF artifacts remain byte-exact. Secret-pattern scan found
no generated credentials/deploy token in this bundle; the literal key/Bearer string are synthetic fixtures.
All3 old containers and2 named volumes remain present. Final delivery must verify11 committed blob hashes.

Next meaningful work needs TV3's authorized validator and human sign-off, or a changed source/review.
No additional production or duplicate-test work is invented while waiting.
