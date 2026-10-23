# TV5 current-account authority PostgreSQL diagnostic

## Authorized P2 supplement — GREEN on TV5 branch, waiting for TV1 integration

On23/10/2026 the user explicitly authorized **only P2 Auth** on PR82. Fix commit `ad25942998224451fb8c54677e028712e6921ae3` catches JPA `PersistenceException` as well as translated Spring `DataAccessException`, mapping a failed lazy-role read to the existing internal503 decision. It does not mask401/403, add runtime wiring or change schema, status vocabulary, API, Gateway or flags.

The original five-case Java and Gradle init script are byte-identical to RED (hashes in receipts). At the exact committed fix SHA:
- **8 JWT/current-role unit PASS** and **28 Auth persistence PostgreSQL PASS**, separate baseline classes.
- **5 current-authority PostgreSQL regression PASS /0FAIL /0skip**, unchanged assertions and positive controls; command exit0.
- Run 2026-10-23T02:13:33.613682+00:00 to 2026-10-23T02:14:10.566693+00:00; [derived GREEN receipt](../../reports/evidence/tv5-auth-authority-20261023/green.json) identifies actual source/test hashes, command, fresh XML and original private raw hashes. Precommit verification was a separate run, not relabelled as committed-source evidence.

PR80 remains at f88da6d and still reproduces the original4PASS/1FAIL. This supplemental GREEN does **not** resolve its parent finding. TV1 must review/accept PR82, then the parent head must be verified before closing that thread. TV2 security review also remains required. Keep Draft/WAITING_REVIEW_INTEGRATION; no agent Approve/Merge/Close.

Original RED and baseline receipts below are preserved byte-for-byte. Raw log/XML remain private, with generated fixture credentials excluded from public evidence. No existing Docker volume/data was deleted. M01 PENDING BUSINESS APPROVAL; Finance production BLOCKED; Finance/pair flags OFF. These tests are not live HTTP/Gateway or Finance E2E.

## Original diagnostic checkpoint — historical, before the authorized fix

Refs #5/#12/#80. **DIAGNOSTIC RED — not a production fix or security approval.**
Auth source tested: `f88da6d2f87acfa136c955e8d099b9465c72f807`. This QA branch is based on that exact PR80 head and targets the original Auth feature branch, not main.
Only `tests/security/` and derived evidence change; no Auth Java/schema/config/runtime bean or flags change.

## Why this task exists

PR80's eight new unit tests mock `AccountRepository`. They do not execute the LAZY `Account.roles` query under a real Spring transaction proxy.
The existing 28 `AuthPersistenceTest` invocations verify persistence/migration but do not call the new authority as a transactional bean.
This separate task uses real PostgreSQL16/Flyway V1+V2, the actual repository/entity/authority and test-only Spring bean wiring.
It never exposes an HTTP route or enables the production authority.

The fixture uses its own new Testcontainers database `auth_db`, migration actor `auth_migrator`, restricted application actor `auth_app`, generated ephemeral credentials and explicit **QA_EXPLICIT_ACTIVE** test status.
No active-status business decision is made. Only this disposable DB is modified. Normal teardown closes only resources created by the test; no Docker prune or existing volume/data deletion.

## Actual result — 23/10/2026

- PR80 baseline: **8 JWT/current-authority unit +28 Auth persistence PG PASS**, exit0, source f88da6d. Separate invocation, not included in diagnostic counts.
- Explicit diagnostic: **4 PASS /1 FAIL,0skip,exit1**, 00:51:50–00:52:13UTC (07:51:50–07:52:13Asia/Bangkok).
- Passing controls prove real proxy/Lazy-role loading, committed ADMIN grant removal overriding a signed token, added DB privilege not elevating an old token, inactive/missing401, and failed Account query503.
- Failing `unavailableLazyRoleQueryIsAlso503RatherThanLeakingPersistenceFailure`: after revoking SELECT on `account_role_grants` in the fixture only, `requireCurrentForIssue` throws `org.hibernate.exception.SQLGrammarException`, not expected `CurrentAccountAuthority.DecisionFailure(503, AUTHORIZATION_DEPENDENCY_UNAVAILABLE)`.

Root cause at PR80 `CurrentAccountAuthority.java:65–68`: `account.getRoles()` initializes a Hibernate LAZY collection outside the repository method's exception translation. The catch only handles Spring `DataAccessException`; the native Hibernate persistence-read exception escapes.
This remains fail-closed and is **not a demonstrated authorization bypass**. No live HTTP status was tested; the service contract fails to deliver its promised internal503 result to a future adapter.
TV1 should load account+roles within a translated repository boundary or handle the actual persistence-read failure appropriately, preserving401/403 and never allowing on dependency failure. Do not change the assertion, grant production broad rights, disable the case or claim4PASS/1FAIL is GREEN.

## Reproduce from the tested source checkout

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC -Dapi.version=1.44'
$qaSources = (Resolve-Path tests/security/auth-current-authority).Path
./backend/gradlew.bat -p backend --init-script tests/security/auth-current-authority.init.gradle "-Ptv5AuthAuthoritySources=$qaSources" :auth-service:tv5CurrentAuthorityRegression --rerun-tasks --no-daemon --console=plain
```

The task is explicit; normal CI does not run it automatically. It extends only test classpaths in a separate source set and does not alter the standard test task or dependency manifest.
Run unchanged five-case acceptance on the owner's fixed Auth SHA, record that SHA and compare the same Java/init hashes; only then report GREEN.
Pending gates remain runtime bean/transaction integration, configured active vocabulary, authenticated API/Gateway/refresh/logout/current Store authority, operator/security signoff and M01. PG/mock/diagnostic results are not live E2E or Finance enablement.

## Evidence publication

[Baseline receipt](../../reports/evidence/tv5-auth-authority-20261023/baseline.json) and [original diagnostic receipt](../../reports/evidence/tv5-auth-authority-20261023/red.json) are **derived summaries** with testcase names, commands, timestamps, executor/environment, exit/result and raw original byte sizes/SHA256.
Raw logs/XML remain private; they can contain generated DB credentials in diagnostics and must not be copied into public PRs. Their bytes are not sanitized or overwritten; only the derived summary is published.
QA Java/init content hashes identify the exact unmodified acceptance source used for RED. No mock or baseline total is substituted for the failing explicit task.
