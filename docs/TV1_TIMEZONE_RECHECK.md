# TV1 readiness — TV5 environment/timezone fix verification

Refs #1/#5/#27. TV5 first prepared fix7175ea2 from6a7f172, then TV1 independently pushed the runbook correction a810312 before this supplemental PR was opened. The TV5 branch is synchronized with a810312 and preserves the owner's docker/README.md and docs/DEVELOPMENT.md instructions. The remaining diff adds fresh independent diagnostic evidence and this report. Auth/User application code, test assertions, dependencies, credentials model and DB/schema are identical at6a7f172 and a810312; the three runs below test that unchanged Java source. The original readiness report remains historical.

## Cause and selected fix

With Java21/pgJDBC42.7.4, JVM Asia/Saigon is sent during connection startup; the tested PostgreSQL16.15 Debian image does not recognize that name and rejects with SQLState22023. Hibernate's later metadata/dialect error is secondary; changing dialect, credentials or hibernate.jdbc.time_zone does not correct the refused startup parameter.

Use a server-supported JVM timezone. The owner's a810312 runbook saves existing JAVA_TOOL_OPTIONS, temporarily sets -Duser.timezone=UTC for the current shell and child Java processes, then restores the previous value after test/bootRun. A packaged process can select java -Duser.timezone=UTC -jar. Do not change Windows timezone, map aliases silently, rotate passwords or rewrite application/business dates. A different supported IANA zone requires the environment/owner's choice; Asia/Ho_Chi_Minh is also verified below, not mandated as business policy.

## New TV5 execution — 17/10/2026

Java21.0.7/Gradle8.14.3/Docker29.8.1/PostgreSQL16.15 Debian. Dedicated new Compose project, fresh generated secrets only in memory, localhost random host port, original provision/isolation scripts executed twice. Two public schemas remain0table; no migration/history/business data was created. TV1_DATABASE_TESTS=true throughout all three Java runs.

| JVM setting in the real Java test process | PASS | FAIL | ERROR/SKIP | Result |
|---|---:|---:|---:|---|
| Asia/Saigon (negative diagnostic) |1|2|0|exit1; both DB contexts refused before test body, original failure retained |
| UTC (documented configuration) |3|0|0|exit0; two live JDBC/JPA/Flyway readiness tests and1HS256 dependency smoke |
| Asia/Ho_Chi_Minh (supported IANA alternative) |3|0|0|exit0; same source/database/assertions |

Command for each run: `gradle -p backend :auth-service:test :user-service:test --rerun-tasks --no-daemon --console=plain`, with only process JAVA_TOOL_OPTIONS timezone changed. No skipped/removed/relaxed tests, fixed timezone in test source or server-data reset was used. Do not add the runs together or present Asia/Saigon as PASS; the unsupported configuration still fails and must not be deployed.

Raw [logs/XML/suite counts](verification/tv5-timezone/), PostgreSQL version/timezone list, provision/isolation outputs, exit files and resource inventories are retained with manifest.sha256. Generated secrets were compared against the local evidence contents before publication; no values were found. Raw bytes remain unchanged. The isolated service was stopped after tests, its new volume kept, and0pre-existing containers/volumes were lost. No down -v, prune, DROP DATABASE or host timezone change.

The owner's a810312 addresses the missing environment configuration/runbook; this supplement provides independent execution evidence, not a new Auth API implementation or PostgreSQL timezone database patch. bootRun/packaged-JAR HTTP smoke, Auth business migrations/API/security and live integration/E2E are NOT_RUN here. Owner/reviewer acceptance of#27 and this supplemental evidence is still required; no automatic thread resolution/approval/merge is performed. Issue#5 remains OPEN/PARTIAL.
