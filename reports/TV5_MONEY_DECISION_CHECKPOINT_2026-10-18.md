# TV5 — money decision packet, current diagnostic and cross-owner QA

2026-10-18, Asia/Bangkok. Refs #5/#12/#28/#37/#38/#39/#40/#43.
**PARTIAL / DIAGNOSTIC RED / HUMAN DECISION PENDING.** No PR approval/merge in this task.

## Verified PR/source checkpoint

| PR | Observed head / state | Current finding or action |
|---|---|---|
| 28 | ee15851047c6f191f0fdbbd828f87f598db4ff44 /OPEN | Adds only unwired OrderAmountStorageBounds +6 tests; still no pre-reserve fix. Main QA union intact. |
| 37 | 42cc4e73a816895dd3d64ab831ec32f5e9af5feb /OPEN | Source unchanged since63668b8; updated provider/freshness proposal, not a working endpoint or human policy approval. |
| 38 | 7a8d18eb54c3d134964a282ddd7612283dcea66c /OPEN | Reviewed docs corrections; provider src/tests unchanged since9b8ae35. |
| 39 | ec2ae5af5162aa93482b9439d1b39dbb7abb3bec /OPEN | Legacy IDs/meanings and supplemental counts repaired; shared state/event/retry still pending. |
| 40 | e91312c8d897b8faf12fed151022508025719de1 /OPEN | Prior publication ledger unchanged:49 public hashes,3 original XML withheld with explicit derived summaries; no original credentials republished. |
| 41/42 | MERGED into TV3 | aaf9d31/35297ac ancestors ofee15851 verified. No push/reviewer request on closed PRs. |
| 43 | Draft, TV5 branch synced by mergec4d517e | Original Java/init/assertions and all11 original artifact hashes unchanged; new evidence below. |

QA source union atPR28 remains exact main524e4f3 plus the two TV5 18/10 Finance sections from9d54c17.
No conflict reopened/redone. Normal readiness CI is separate from explicit tagged PostgreSQL and diagnostic
tasks; a success there does not prove these tasks ran. No old unit/PG run is substituted for a new SHA.

## Execution actually performed now

Source **c4d517e0dbb2d25cd7f9e1ee8e762d9fdb75c5da**, TV5 merge of unchanged diagnostic with owner
head **ee15851**. Java21.0.7, Gradle8.14.3, Docker29.8.1, PostgreSQL16-alpine/Testcontainers,
process-only `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC -Dapi.version=1.44`.
UTC **07:56:26 →07:57:08**, build41s/6tasks, overall **exit1 / BUILD FAILED**.

```powershell
gradle -p backend --init-script ../tests/finance/fin06-regression.init.gradle :order-service:test --tests com.projecthk5.orderservice.domain.OrderAmountStorageBoundsTest :order-service:fin06Regression --rerun-tasks --no-daemon --continue --console=plain
```

| Suite | PASS | FAIL | Error / skip | Meaning |
|---|---:|---:|---|---|
| OrderAmountStorageBoundsTest, official Gradle/JUnit runner | **6** | 0 | 0/0 | Existing NUMERIC19,2 representation utility only, not signed M01 or Checkout wiring. TV3's prior stub/standalone runs remain separate. |
| CheckoutMoneyPreflightRegressionTest, real Order PostgreSQL/mock remote ports | **1** | **2** | 0/0 | Positive controlPASS; FIN-06 and FIN-01 remainRED. Not a fixPASS or liveE2E. |

Current failing observations unchanged: reserve/writer precede parent overflow DB rejection and attempt
becomesNEEDS_RECONCILIATION; first3.002 vs replay3.00 for incompatible precision. No assertions lowered,
tests disabled, raw failure edited or policy flag enabled. Utility is not referenced in CheckoutService.
After TV3's authorized implementation, run these same acceptance tests on the exact fix SHA, then affected
tagged PG/replay/scanner/security suites; retain both RED and GREEN. A utility testPASS is insufficient.

Full unchanged Cart/Order suites, Catalog, Inventory runtime tests **NOT_RUN in this task**. Source comparisons
establish unchanged Catalog Java since the separately recorded50unitPASS at63668b8 and Inventory Java/tests
since prior17unit+15PG at9b8ae35; neither is labeled a fresh result at42cc4e7/7a8d18e. No counts summed
across snapshots, executors or report V2. Live providers/settlement/Store queries/UAT2Store remain blocked.

## M01 package ready for human choice, not signature by agent

[TV5-M01-DECISION-v1](../docs/TV5_M01_HUMAN_DECISION_PACKET.md) gives MD01–MD10 choices, numeric examples,
owner/signature form, source and schema/consumer consequences. It preserves TV5-FIN-v0.1 as a candidate.

- Explicit VND/integral positive price/17-digit bounds is candidate A, distinct from NUMERIC19,2 storage max.99.
- Unit/line/Store/fee/net/parent checks before reserve, exact multiplication and aggregate caps are explicit.
- 5%/House0 existing requirements; proposed rate representation/HALF_UP once per SellerOrder still needs full sign-off.
- House source, currency/rate/net/policy/fact snapshots, shipping/tax/discount allocation, COD/refund/reversal,
  business quantity caps, four reporting groups and enable authority each need a named human decision.
- TV2's last-verified snapshot recommendation versus a future cross-source quote/lease is an explicit risk choice,
  not an invented TTL/lock guarantee. Current source enum is not a verification/freshness adapter.

| Contract | Overall status | Missing responsible confirmation / permission after signing |
|---|---|---|
| S01 | PENDING | TV1+TV2 Store/current/House authority, TV3/TV4/TV5 scope; only then trusted Store provider/consumers. |
| C01/E01 | REVISE | TV2+TV3 technical candidate accepted for further sign-off; TV1 pair/privacy/status, full wire/freshness/current sources and TV5 money choice still absent. Only then Catalog/controller/DTO/consumer. |
| M01 | PENDING | Human TV5/business decision maker +TV2/TV3/TV4/TV1 own-scope same-version decisions MD01–MD10. Only then compatible V2+ snapshots/validation/Finance. |
| C06/Inventory | PENDING /C06 REVISE | TV3+TV4 state/event/retry budget/journal, TV1 actor, TV2 mapping/current facts, TV5 COD separation. No live adapter enabled. |
| Security pairs | PENDING (OD-07/09 baseline DECIDED) | TV1 concrete credential/method/path/operation/delegated current actor +each provider/consumer; no broad Inventory token. |
| Finance/COD/settlement | PENDING | Source of delivery/payment, ADMIN/current authority, atomic unique settlement, immutable snapshots and Store queries need TV5/TV4/TV3/TV2/TV1 and business approval. |

Evidence: [TV3 utility/fix gate](https://github.com/duyxl56785/Project_HK5/pull/28#issuecomment-6246631924),
[TV2 freshness](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246597564),
[TV2 current source unavailable](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246611924),
[TV4 state/retry](https://github.com/duyxl56785/Project_HK5/pull/39#issuecomment-6246602587).
No full contract newly qualifies asAPPROVED. Keep Financefalse and current adaptersfail-closed.

## Catalog / Inventory / Fulfillment review dispositions

**Catalog42cc4e7:** verified source unchanged from63668b8. Explicit ABSENT alone can yield notfound;
omitted/incomplete/UNAVAILABLE abort the batch; invalid batch supplies no usable READY snapshot subset.
This is internal source behavior. Adapter must still establish trusted current facts/freshness; Source enum,
11 SQL prototype cases or five negative HTTP403 checks do not prove Product persistence/positive API/auth.
Provider's recipient/current Store/Seller/House API is explicitly NOT_AVAILABLE; suspended/closed Store
policy for existing orders remains human/TV1/TV4/business decision, not a Catalog rule to auto-cancel.

**Inventory7a8d18e:** prior doc findings now represented correctly: exact-tuple retry for claim-conflict,
changed-payload conflict nonretryable, SKU existence before ledger mismatch, current replay balances,
trim+case semantics and init/auth errors. No source/error mapping delta. Retry max3 after initial with
jitter250ms/1s/4s and target<=10s is a TV4 proposal, not approved runtime; TV3+TV1 must also agree per-call
timeout/absolute deadline so sum of delays is not mistaken for whole network budget. UNKNOWN holds, no blind RELEASE.

**Fulfillmentec2ae5a:** static comparison against origin/tv4/week1-analysis WEEK1_TEST_BASELINE.md gives
exact21 legacy IDs+original scenarios/expected meanings preserved. Supplemental namespaced TV4-W1-* has
exact21unique cases =12FUL+4COD+5NOT. All42 business scenarios remainNOT_RUN; no module evidence transferred
to an unrelated case. TV4 now proposes TV3's PLACED→ACCEPTED→PREPARING→READY_FOR_HANDOFF→DISPATCHED→DELIVERED
and CONSUME after committed dispatch/outbox, explicitly not an alias for oldPACKING→SHIPPED. This is progress,
not full C06/event/actor agreement. Currency/adjustments/COD paid source and Store facts remain pending.
TV5's oldCHANGES_REQUESTED is preserved; this task posts COMMENT disposition, no Approve/dismiss/resolve.

## RTM, checklist and publication

[RTM update](../tests/TV5_TRACEABILITY.md) links utilityPASS, FIN-06/FIN-01RED, human decisions and docs-only
case verification to exact evidence. Original earlier RTM rows remain a historical rules snapshot.

- [x] Verify41/42 integration and QA history without reopening conflicts.
- [x] Sync owner utility into TV5 diagnostic branch; official6unitPASS and original1PASS/2FAIL confirmed.
- [x] Prepare versioned M01 human choices/examples/schema/consumer/signature/gating packet.
- [x] Verify revised38/39 docs, all42 NOT_RUN case traceability and remaining lifecycle/auth/freshness gates.
- [x] Preserve original11artifacts/raw failures and publication limits; package new8artifact evidence.
- [ ] Human/owner same-version contract choices and business authority designation.
- [ ] TV3 wires authorized pre-reserve implementation; original2RED becomeGREEN on fix SHA.
- [ ] Production settlement/Store queries/live2Store/COD/UAT acceptance; Issue5/12 DONE.

[New8artifact bundle](evidence/money-decision-20261018/README.md) has original fresh logs/XML/timestamps,
exit and Docker data plus explicitly derived source/static comparisons. Original11 artifacts in fin06-checkout
remain byte-identical and hash-verified. PR40 withheld originals/derived records remain unchanged; no credentials
republished. No missing old3containers/2volumes, no delete/prune/down-v. Code/docs diff-check separate from rawCRLF.

Next work is human decisions or owner source changes; no production settlement/authorization invented while waiting.
