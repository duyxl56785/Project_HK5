# Durable reservation recovery (stacked on #131)

Refs #2, #8. Component implementation only; business writes and runtime activation remain blocked.

## Safety and ordering

The Product operation is durably enlisted before outbound admission. Recovery first arbitrates abort versus commit under the existing Product operation row lock. A committed write remains COMMITTED; otherwise the operation becomes permanently ABORTED. Recovery then calls Seller seal followed by Auth seal, outside database transactions.

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

## Verification and remaining acceptance

Java 21 / Gradle 8.10, PostgreSQL 16 Testcontainers, actual embedded HTTP servers and separate database identities. Executed:

```text
backend/gradlew.bat -p backend :security-core:build :auth-service:build :seller-service:build :product-service:build --rerun-tasks --no-daemon --console=plain
```

Results: core 57 PASS; Auth 158 PASS / 2 optional external-environment SKIPPED; Seller unit 297, integration 163, Role Sync integration 22 PASS; Product unit 313, integration 38, authority integration 40 PASS. Total 1088 PASS / 0 FAIL / 2 SKIPPED. The 11 new recovery PostgreSQL cases include lost HTTP ACK, abort/commit races, expired claims, stale epochs, concurrent workers, callback outage, bounded blocking/operator retry, pending revocation, manual-kind isolation and an actual scheduled Spring worker.

Restart is tested by recreating worker instances over persisted state; servers are same-JVM embedded instances. OS process termination, deployment recovery, real Product business-write integration and marketplace E2E are NOT_RUN. Parent security review, real migration upgrade ordering, manifest provisioning, independent TV1/TV5 acceptance and complete mutation-path participation remain gates. F03–F08/P0/API/E2E remain incomplete. No runtime flags are enabled by publication.
