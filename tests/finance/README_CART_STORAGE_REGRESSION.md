# TV5 Cart NUMERIC(19,2) PostgreSQL regression

Refs #3, #5, #12, #67. Test-only supplement based on PR #67 head `add83ec`.
No production Java, migration, normal Gradle task, shared dependency, policy or flag changes.
The supplemental PR targets the **PR #67 branch**, not main, so it does not bypass review of the Cart fix.

## What the ten invocations prove

- One SQL characterization: PostgreSQL NUMERIC(19,2) silently stores `1.005` as `1.01`.
- Three unsafe prices (`1.001`, `1.005`, one step above the column maximum) cannot create a Cart.
- The same three prices cannot change the original item quantity/value/count or Cart revision.
- Three exact values (current-schema zero, `1.2300`, column maximum) round-trip through CartService/Flyway/JPA/PostgreSQL unchanged in value.

There are four test methods and ten JUnit invocations, not ten new methods.
`@DataJpaTest` rolls back each fixture transaction. Catalog is mocked and all customers are disposable fixtures.
This does not prove authenticated HTTP, current Store authorization, live Catalog, committed replay/concurrency,
VND-only, business minimum/cap, Finance/COD/settlement or live two-Store E2E.
Zero is allowed by the existing column and is **not** an M01 approval. Flags remain OFF.

## Explicit task (not normal CI)

Run in a checkout containing the target Cart implementation with Docker available. Use the **same QA source** for both targets:

```powershell
$qaRoot = (Resolve-Path .).Path
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC -Dapi.version=1.44'
.\backend\gradlew.bat -p backend --init-script "$qaRoot/tests/finance/cart-storage-regression.init.gradle" "-Ptv5CartRegressionSources=$qaRoot/tests/finance/cart-storage-regression" :cart-service:tv5CartStorageRegression --rerun-tasks --no-daemon --console=plain
```

To reproduce RED, run the target base checkout's wrapper/build with the QA paths pointing to this checkout.
The normal `:cart-service:test` and official readiness CI **do not automatically execute this task**.
No credentials or database settings are needed beyond the disposable Testcontainers instance.

## Recorded executions — 22/10/2026

| Target | Exact SHA | UTC start/end | Result |
|---|---|---|---|
| Original main/base before67 | c2cf55a5b866937acd99ec554655aab249283d23 |05:30:06–05:30:35|**4 PASS / 6 FAIL**, exit1; six real guard assertions fail, not Docker initialization|
| PR67 fix | add83ec2c7f43e5f942c42682d6a4574c152318d |05:30:08–05:30:36|**10 PASS / 0 FAIL**, exit0|

Same test/init hashes in both runs; no assertion was weakened. Java21.0.7/Gradle8.10/Docker29.8.1, UTC/API1.44.
[Original RED derived summary](../../reports/evidence/tv5-cart-storage-20261022/red.json),
[GREEN derived summary](../../reports/evidence/tv5-cart-storage-20261022/green.json).
Raw byte-exact logs/XML remain private under `.verification/tv5-audit-20261022/runs/cart-regression-{red,green}/`.
Published JSON contains labelled derived case/command/time/hash references, **not** public raw or CI artifacts.
Do not add these invocations to Cart unit/PG/CI counts or another SHA's release total.

- [x] Reproduce before fix and verify the same acceptance on the PR67 source.
- [x] Keep positive controls and private original RED/GREEN byte hashes.
- [ ] TV3/TV2 review and accept the supplemental tests; normal CI alone does not replace this explicit task.
- [ ] Production Catalog/Auth/Store/M01/COD acceptance remains independently BLOCKED/NOT_RUN.
