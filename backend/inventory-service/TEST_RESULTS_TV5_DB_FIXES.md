# TV5 — PR #31 PostgreSQL regression fixes

Refs #4/#5/#31. Separate branch from exact parent `21d8450dae5e4afd7987e1a33fb157fc4b973f9d`, which already contains the compile fix #35. No production source changes after #35 were present at the start. This supplement targets the TV4 branch, not main; no direct author-branch push.

Java 21.0.7 / Gradle 8.14.3 / Docker 29.8.1 on Windows. Process-only `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC -Dapi.version=1.44`. PostgreSQL 16-alpine Testcontainers databases are new and isolated; no existing service DB or volume is used. Original logs/XML/run metadata and resource inventories are in [verification/tv5-db-fixes](verification/tv5-db-fixes/), with SHA256 manifest and raw-byte Git attributes. Runs remain separate from TV2's diagnostic probes and earlier TV5 evidence.

## Group 1 — initial CHAR/VARCHAR reproduction and candidate fix (historical)

- **RED:** exact original production mapping plus new schema regression: 1 PG test FAIL, exit 1/25s. Spring context rejects `request_hash`: found bpchar/CHAR, expecting VARCHAR(64). No disabled schema validation or altered migration is used.
- **Fix:** map the existing String hash with Hibernate `@JdbcTypeCode(SqlTypes.CHAR)`, matching the V1 `CHAR(64)` column. V1/schema/checksums remain unchanged, so no deployed DB rewrite is needed.
- **GREEN:** `:inventory-service:test :inventory-service:postgresIntegrationTest --rerun-tasks --no-daemon --continue --console=plain`: **15 unit + 1 PG PASS**, 0 failure/error/skipped, exit 0/24s. The PG test starts the full application with production Flyway/validate and round-trips the hash through JPA.
- Evidence: `schema-red/` and `schema-green/`; a regression harness compiles separately from unit execution. Unit and PG counts are not combined with another run.

## Concurrent owner update — preserve the accepted source direction

Before the supplemental PR was created, TV4 advanced the parent to `adc705ea69fee23049d0c02528c363aa7085e98e`. It already contains atomic `INSERT ... ON CONFLICT` for initialize/operation claims and a forward V2 migration changing request_hash to VARCHAR(64). TV5 merged that history into this separate branch and removed the superseded candidate CHAR mapping from the final source. No V1 rewrite or direct TV4-branch push. The initial commit/logs remain historical; do not deploy its CHAR annotation together with the owner's V2.

The current schema regression now checks the owner's intentional VARCHAR/V1+V2 target, JPA hash round-trip and an actual V1→V2 upgrade preserving the old hash/ledger, stock/version and V1 checksum. This is an explicit migration target change after the owner update, not a relaxed assertion to hide the startup defect.

Owner follow-up unit tests also introduced mixed Mockito matchers: two verifies combined raw strings with anyString(). TV5 independently reproduced **6 unit tests: 4 PASS/2 FAIL**, exit1/13s (`owner-unit-red/`). The fix uses eq(...) for those exact strings, preserving the expected arguments and every assertion. PostgreSQL cases are tagged into an explicit Docker-required `postgresIntegrationTest` task; unit runs no longer silently include/skip DB tests.

## Group 2 — duplicate initialization

- RED uses the old production source at `9ee299c` (only the proven schema-mapping isolation fix) plus the exact same new initialize regression file. **3 PG invocations: 1 PASS/2 FAIL**, exit1/24s. Fresh duplicate initialization returns201 instead of409 and overwrites a version0 row; the reserved/versioned-row case also fails the required conflict behavior. No assertions are changed between RED and GREEN.
- The owner now uses `InventoryItemRepository.insertIfAbsent` with `ON CONFLICT (sku) DO NOTHING`, returning409 through the existing handler if no row was inserted. TV5 preserves that implementation, with no duplicate rewrite.
- GREEN in the combined execution: all3 initialize PG cases PASS. They verify real HTTP201/409 with the actual token filter, unchanged available/reserved/version/timestamp after duplicate requests, and a competing initialize blocked on a real DB lock before the winner commits. No repository mock, shared existing DB or volume is used.
- Source regression: `InventoryInitializePostgresIT`; raw RED: `initialize-red/`. The combined/final GREEN ledger below records the complete source checkpoint and separate unit/PG totals.

## Remaining work at this checkpoint

- [x] Schema regression RED→GREEN and owner V2 upgrade checks; V1 unchanged, superseded CHAR candidate removed from final source.
- [x] Duplicate initialization owner insert-only fix checked with PostgreSQL/HTTP/concurrent regressions.
- [ ] Atomic operationId arbitration + PostgreSQL race/rollback regressions.
- [ ] Owner review/integration and external auth/identity/lifecycle contracts.

Auth service-pair/role design, Product↔SKU/Store identity, reserve/consume/release timing, live dependencies and E2E remain owner decisions/NOT_RUN. This bugfix does not enable the checkout adapter, settle Finance policy or expand to new week 2–3 features. Issue #5 remains OPEN/PARTIAL.
