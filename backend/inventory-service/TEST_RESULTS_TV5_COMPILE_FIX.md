# TV5 — PR #31 test compilation fix

Refs #4/#5/#31. Separate fix branch based on TV4 head `1abfd4fc6a71fb6ba9d0daec902f8c8a53cf7e30`; this PR targets the TV4 branch, not main. The parent remains Draft until its owner decides readiness.

## Reproduction and change

`InventoryRequestValidationTest.invalidArgumentAndOverflowUseBadRequestErrorCodes` invokes `Class.getMethod` twice without handling its checked `NoSuchMethodException`. The exact parent source fails `:inventory-service:compileTestJava` at lines 75 and 78, before any tests run.

Declare `throws NoSuchMethodException` on the existing JUnit method. This preserves every assertion and makes a missing handler method fail the test normally. No production code, contract, auth mechanism, migration, or business rule changes.

## Actual execution

Java 21.0.7 / Gradle 8.14.3 on Windows, process-only `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC -Dapi.version=1.44`. Command in both runs: `:inventory-service:test --rerun-tasks --no-daemon --console=plain` against separate snapshots.

| Source | Result | Exit | Evidence |
|---|---|---:|---|
| Exact parent `1abfd4f` | FAIL: compileTestJava, 2 unreported checked-exception errors; no tests executed | 1 | red-build.log; red-exit.txt |
| Parent plus the one-line declaration | PASS: 15 tests, 0 failures/errors/skips, BUILD SUCCESSFUL in 12s | 0 | green-build.log; green-exit.txt; 3 JUnit XML files |

Raw [evidence](verification/tv5-compile-fix/) includes provenance and SHA256 manifest. Logs/XML are original bytes; do not normalize their line endings or remove the RED run. Counts are from this execution, not TV4's earlier NOT_RUN statements or other services' totals.

PostgreSQL migration/concurrency/transaction rollback, HTTP/security integration, and live checkout E2E remain **NOT_RUN** in this task. Existing unit tests use mocked repositories; their PASS does not prove DB idempotency or production auth. Shared SKU/product identity, operation IDs, Store scope and reserve/consume/release timing still require TV1/TV3/TV4 agreement. The fix branch is **WAITING_REVIEW/INTEGRATION**; the parent is not fixed until the author accepts it. Issue #5 remains OPEN/PARTIAL.
