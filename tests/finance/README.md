# Explicit Checkout money preflight diagnostics

Refs #5/#12/#28/#37/#42. **Known RED / owner implementation and contract sign-off required.**

From repository root, Java21 + Gradle8 + Docker/PostgreSQL16 Testcontainers available:

```powershell
$env:JAVA_TOOL_OPTIONS='-Duser.timezone=UTC -Dapi.version=1.44'
gradle -p backend --init-script ../tests/finance/fin06-regression.init.gradle :order-service:fin06Regression --rerun-tasks --no-daemon --console=plain
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
