# TV5 — PR #31 PostgreSQL regression fixes

Refs #4/#5/#31. Separate branch from exact parent `21d8450dae5e4afd7987e1a33fb157fc4b973f9d`, which already contains the compile fix #35. No production source changes after #35 were present at the start. This supplement targets the TV4 branch, not main; no direct author-branch push.

Java 21.0.7 / Gradle 8.14.3 / Docker 29.8.1 on Windows. Process-only `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC -Dapi.version=1.44`. PostgreSQL 16-alpine Testcontainers databases are new and isolated; no existing service DB or volume is used. Original logs/XML/run metadata and resource inventories are in [verification/tv5-db-fixes](verification/tv5-db-fixes/), with SHA256 manifest and raw-byte Git attributes. Runs remain separate from TV2's diagnostic probes and earlier TV5 evidence.

## Group 1 — CHAR/VARCHAR schema compatibility

- **RED:** exact original production mapping plus new schema regression: 1 PG test FAIL, exit 1/25s. Spring context rejects `request_hash`: found bpchar/CHAR, expecting VARCHAR(64). No disabled schema validation or altered migration is used.
- **Fix:** map the existing String hash with Hibernate `@JdbcTypeCode(SqlTypes.CHAR)`, matching the V1 `CHAR(64)` column. V1/schema/checksums remain unchanged, so no deployed DB rewrite is needed.
- **GREEN:** `:inventory-service:test :inventory-service:postgresIntegrationTest --rerun-tasks --no-daemon --continue --console=plain`: **15 unit + 1 PG PASS**, 0 failure/error/skipped, exit 0/24s. The PG test starts the full application with production Flyway/validate and round-trips the hash through JPA.
- Evidence: `schema-red/` and `schema-green/`; a regression harness compiles separately from unit execution. Unit and PG counts are not combined with another run.

## Remaining work at this checkpoint

- [x] Schema regression RED→GREEN, no V1 migration modification.
- [ ] Duplicate initialization insert-only + PostgreSQL/HTTP/concurrent regressions.
- [ ] Atomic operationId arbitration + PostgreSQL race/rollback regressions.
- [ ] Owner review/integration and external auth/identity/lifecycle contracts.

Auth service-pair/role design, Product↔SKU/Store identity, reserve/consume/release timing, live dependencies and E2E remain owner decisions/NOT_RUN. This bugfix does not enable the checkout adapter, settle Finance policy or expand to new week 2–3 features. Issue #5 remains OPEN/PARTIAL.
