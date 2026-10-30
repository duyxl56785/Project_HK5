# Durable reservation recovery (stacked on #131)

Refs #2, #8. Component implementation only; business writes and runtime activation remain blocked.

## Safety and ordering

The Product operation and its recovery intent are inserted in one JDBC transaction, using the same connection and row lock, before outbound admission. Failure or process death before that commit leaves neither row; a successful commit leaves both. Recovery first arbitrates abort versus commit under the existing Product operation row lock. A committed write remains COMMITTED; otherwise the operation becomes permanently ABORTED. Recovery then calls Seller seal followed by Auth seal, outside database transactions.

Each participant independently fetches Product terminal closure over its authenticated callback. It validates request ID, intent hash, operation epoch, participant and recorded authority epoch. COMMITTED requires the recorded participant grant; ABORTED permits an unrecorded grant to recover a lost admission ACK. Under the same admission serialization guard it inserts an immutable closure tombstone and releases the matching local reservation, if present. Tombstones reject delayed admission even when the original request had not reached that participant. Neither timeout nor TTL is release evidence.

Worker leases coordinate retries only. Expired claims cannot acknowledge completion; attempts include reclaimed crashed claims. Bounded exponential backoff reaches BLOCKED after the configured limit (maximum 32). A conflict blocks immediately. Repeated callbacks and seals are idempotent. Database lock/statement timeouts bound local recovery contention; HTTP is never called with a local transaction held.

## Revocation priority

Role Sync non-APPROVED events durably enqueue a ROLE_SYNC barrier before the proxied mutation transaction and acknowledge it only after that transaction commits. PENDING/LEASED/BLOCKED barriers deny new admission; existing reservations may finish. Workers claim only their own ticket kind. MANUAL tickets support other status/ownership writers, which must explicitly enqueue and complete the barrier around their mutation. This slice does not wire all legacy mutation paths or business controllers. Durable blocked tickets preserve safety but require operator attention; global priority deliberately trades availability for eventual revocation progress.

## Default-OFF configuration and database roles

- Product scheduler: `hk5.product.reservations.recovery.enabled`; dedicated `HK5_PRODUCT_RECOVERY_DB_URL`, `_USERNAME`, `_PASSWORD`. Poll defaults to 1000 ms, claim lease 30 seconds, maximum attempts 32. Producer grace defaults to 60 seconds and may cancel an unfinished operation; it never releases authority by time alone.
- Auth/Seller seal routes: `hk5.auth.reservations.recovery.enabled` and `hk5.seller.reservations.recovery.enabled`.
- Product closure callbacks: `hk5.product.receipts.recovery.enabled`.
- Auth priority orchestration: `hk5.auth.role-sync.revocation-recovery.enabled`; background retries additionally require `hk5.auth.role-sync.revocation-worker.enabled` and the existing Role Sync gate.
- Existing isolated pair manifests, current/previous credential rotation, operation scopes and private routes remain mandatory. No Gateway exposure is added.
- Apply only the new Auth V5/V6, Seller V6/V7 and Product V7 migrations after their parent versions. No applied migration is edited. Resolve the existing Product V2–V6 target-database ordering gate before activation.
- Provision NOLOGIN groups and separate logins using the SQL grant profiles in this directory. The Product recovery worker can terminalize operations and update its queue, but cannot write business rows. The operator can enqueue/audit retries but cannot update operation state. These scripts are provisioning instructions, not deployment evidence.

## Operator recovery

`ProductReservationRecoveryOperator` is a local administrative entry point, default disabled. Set `HK5_PRODUCT_RECOVERY_OPERATOR_ENABLED=true` with the dedicated operator database identity, then use `status <requestId>`, `retry <requestId> <operationEpoch>`, `cancel <requestId> <operationEpoch>`, or `enlist <strict-intent-file> <operationEpoch>` for a ledger operation left without a queue record. Enlistment verifies the existing operation binding and epoch. Retry/cancel merely queue recovery and append the authenticated database actor to an audit record; they do not claim that a write was aborted. There is no force release, forced commit or unlock command.

Use `orphans [afterEpoch]` with the same operator identity to list up to 100 ledger operations lacking queue records, ordered by epoch/request ID. Pass the last returned operation epoch to continue even when earlier unknown-intent rows cannot be repaired. This is read-only and includes terminal operations. Legacy pre-atomic rows contain only a binding hash, not the full intent: obtain the original trusted intent and use audited `enlist`, then let the worker arbitrate the operation and verify authenticated participant callbacks. An unknown intent remains BLOCKED; never reconstruct it from guessed Store/account data or directly release the reservation. The atomic producer prevents new begin/enlist gaps but does not retroactively reconstruct old rows. No new migration or privilege broadening is required.

## Verification and remaining acceptance

Java 21 / Gradle 8.10, PostgreSQL 16 Testcontainers, actual embedded HTTP servers and separate database identities. Executed:

```text
backend/gradlew.bat -p backend :security-core:build :auth-service:build :seller-service:build :product-service:build --rerun-tasks --no-daemon --console=plain
```

Results: core 57 PASS; Auth 158 PASS / 2 optional external-environment SKIPPED; Seller unit 297, integration 163, Role Sync integration 22 PASS; Product unit 313, integration 38, authority integration 40 PASS. Total 1088 PASS / 0 FAIL / 2 SKIPPED. The 11 new recovery PostgreSQL cases include lost HTTP ACK, abort/commit races, expired claims, stale epochs, concurrent workers, callback outage, bounded blocking/operator retry, pending revocation, manual-kind isolation and an actual scheduled Spring worker.

The original component run above recreated worker instances. The follow-up process crash suite below adds actual OS process termination; it does not replace the remaining deployment acceptance gates. Real Product business-write integration and marketplace E2E are NOT_RUN. Parent security review, real migration upgrade ordering, manifest provisioning, independent TV1/TV5 acceptance and complete mutation-path participation remain gates. F03–F08/P0/API/E2E remain incomplete. No runtime flags are enabled by publication.

## Reproduce atomicity and process recovery

With Java 21, Docker running and PostgreSQL Testcontainers available, run from the repository root:

```text
backend/gradlew.bat -p backend :product-service:authorityIntegrationTest --tests "*ReservationProcessCrashPostgresTest" --no-daemon --console=plain
backend/gradlew.bat -p backend :product-service:authorityIntegrationTest --tests "*ReservationRecoveryPostgresTest" --no-daemon --console=plain
```

The process test launches a separate Java executable per producer/recovery attempt, sends test-only credentials through stdin, waits for an explicit crash boundary and invokes `Process.destroyForcibly()`, then starts a fresh executable over the same PostgreSQL databases. Boundaries cover before begin, ledger INSERT before queue enlist, both inserts before commit, legacy split begin, after atomic commit, before/during/after business transaction commit, held terminal HTTP callback ACK and before/after recovery completion. JDBC observation proxies pause actual SQL; they do not replace the worker, ledger, RPC or database. Callback/completion cases execute the real `ProductReservationRecoveryWorker` with production Seller-then-Auth ordering. The restart assertions inspect durable rows, terminal state, exactly-once test sink writes, immutable closures and late-admission rejection.

The legacy split-begin case deliberately reproduces an orphan from the old sequence. Unattended recovery leaves it unchanged because no trusted intent exists in that ledger row. The operator discovers it through a read-only query; supplying the original intent permits binding-checked enlistment and subsequent authenticated recovery. The separate atomicity/privilege test denies queue INSERT to the writer and verifies that no ledger row survives, then restores the original grant and verifies idempotent retry. The operator pagination test proves unknown earlier orphan rows do not hide later rows.

Scope: Product producer and recovery worker are killed/restarted OS processes; Auth/Seller and Product receipt servers remain real embedded HTTP servers in the test runner, with distinct PostgreSQL database logins. This is not Docker container/service redeployment, Auth/Seller process-kill proof, real marketplace Product CRUD or production provisioning evidence. No timeout/lease expiration is used to release authority; an expired worker claim merely permits another authenticated terminal reconciliation attempt.

Follow-up verification (30/10/2026, Java 21 / Gradle 8.10 / PostgreSQL 16): full four-module command above with `--rerun-tasks` executed all 36 tasks, BUILD SUCCESSFUL in 4m 1s, exit 0. Results: **1102 PASS / 0 FAIL / 2 optional SKIPPED**. Authority suite now has 54 PASS, including all 12 process crash boundaries and two atomicity/operator-pagination regressions; the other suite counts remain as listed above. No historical runs are added to this total. Raw full-run log SHA256: `f1b3add622db64340408f3690a58d8086a862f3d4a9c8872c2452c6470aee6de`. Product queue/ledger INSERT privileges and recovery/operator restrictions use the unchanged parent grant profiles. No migration changes. Independent static review identified the missing orphan cursor and inaccurate manual-worker crash coverage; both were corrected before this final run, with no remaining actionable finding reported. This does not substitute for independent TV1/TV5 acceptance.
