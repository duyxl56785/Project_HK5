# Explicit Checkout money preflight diagnostics

Refs #5/#12/#28/#37/#42. **Historical RED; existing-storage diagnostics verified GREEN at be1d5b6. Business sign-off remains pending.**

From repository root, Java21 + Gradle8 + Docker/PostgreSQL16 Testcontainers available:

```powershell
$env:JAVA_TOOL_OPTIONS='-Duser.timezone=UTC -Dapi.version=1.44'
$initTV5=(Resolve-Path 'tests/finance/fin06-regression.init.gradle').Path
backend/gradlew.bat -p backend --init-script $initTV5 :order-service:fin06Regression --rerun-tasks --no-daemon --console=plain
```

The init script adds a separate diagnostic source set/task for this invocation only. It does not edit
the shared build, production source/config, normal test task or any existing assertion. Tests are
executable and not disabled. Default build does not run this explicit acceptance diagnostic, so a green
default build must never be represented as FIN-06 fixed. Keep diagnostic FAIL visible in PR/evidence.

`CheckoutMoneyPreflightRegressionTest` uses real Checkout/IdempotencyCoordinator/transactional writer,
actual Flyway/JPA/PostgreSQL, mocked Cart/Catalog/Inventory ports and a synthetic policy supplied only to
the locally constructed caller. The production-injected MoneyRules remains fail-closed. No live remote
reservation, Auth/service-pair, currency-policy approval, settlement or E2E is proved.

- Positive control: compatible amounts commit once, replay retains money and no second reserve/write/event.
- FIN-06: two Store subtotals each60000000000000000.00 fit current NUMERIC(19,2), parent does not.
  Acceptance expects rejection before Inventory/write and a known failure; current source calls both,
  DB rolls back and attempt enters NEEDS_RECONCILIATION. Safe Cart release is expected only for a
  proven preflight failure; **never change current UNKNOWN handling to blindly compensate**.
- FIN-01: helper currently accepts scale3 while DB stores scale2. Acceptance expects rejection before
  side effects rather than inconsistent monetary snapshot/replay. Current first response3.002 becomes
  replay3.00. This is a provisional technical exact-storage diagnostic, not selection of a business scale.

At parent28dd0cf the actual result is **1 PASS /2 FAIL /0 errors /0 skips**, Gradle exit1.
Do not change assertions just to make this green. TV3 owns the pre-reserve validator after applicable
contract agreement/authorization; TV5 owns this QA handoff. Use current DB schema facts for capacity,
and do not silently select VND/scale0, business caps, HTTP errors or rate approval from these fixtures.

Re-executed at exact `8cc5ce4af1beacfe0d8467ec037d55e59a065691`: **1 PASS /2 FAIL /0 errors /0 skips**,
positive control PASS; not fixed by the new malformed-request guard. [Current raw evidence and commands](../../reports/TV5_M01_REAUDIT_2026-10-18.md).
The older parent28dd0cf result above remains historical. Normal tests do not run this explicit task.

**Current verified owner fix:** TV3 `5f2ada8` wires storage exactness before RESERVE; latest execution at
`be1d5b6c1a6eafa385f2bd6bd62f8b5efbeb5757` gives **3 PASS /0 FAIL /0 errors /0 skips**, positive control retained.
Original diagnostic source blob is identical to the RED source8cc5ce4; historical logs/XML stay unchanged.
[GREEN source/commands/raw evidence](../../reports/TV5_PROJECT_REVIEW_AUDIT_2026-10-18.md). This fixes existing-storage preflight,
not pending business M01 or live service integration. The expected-RED task description is historical context, not current outcome.

## Current execution — 20/10/2026

At main **03fb1b2e8a1c1261568446c1f9ed68d264784e4e**, the unchanged explicit diagnostic was executed again:
**3PASS/0FAIL/error/skip**, positivecontrol retained, alongside25taggedOrderPGPASS; DBinvocationexit0.
[Current command/time/cases/raw hashes](../../reports/evidence/tv5-unblock-20261020/main-db.json).
An earlier combined invocation failed at PG initialization because DockerEngine was offline; this explicit diagnostic
was **NOT_RUN** in that invocation and staleXML was excluded. Its raw initializationFAIL logs/XML are preserved separately.
The initial RED descriptions above and comments/task description in original diagnostic source remain historical context,
not the current code outcome. FIN01/06 technical existing-storage findings are FIXED_VERIFIED; do not ask TV3 to redo them.
No assertion/source/productionpolicy changed, no signedM01/price/COD/settlement or liveE2E approval from this execution.
