# Auth Role Sync transactional core — 29/10/2026

Branch `codex/auth-role-sync-core`, base #120 at `120d05e07d79ae2a4564bc7ef235b6b1e5f51488`.
Runtime/test commit `247f951dc46ed582f03716d2bfdfc8f2266d4e81`.

V3 is the next Auth migration (the inspected baseline has V1/V2, not V3).
It adds immutable Seller/account mapping, per-version status history, event receipts
and one durable serialization row. Runtime grants can update only latest_version,
not the identity binding/history/receipts. Existing migrations remain unchanged.
REQUIRED orchestration writes receipts/version and only SELLER role changes atomically.
ADMIN/CUSTOMER are preserved; unknown accounts are never provisioned. Account reference
remains opaque/exact in the command; Auth-owned lookup rejects unresolvable references
without trimming, case folding or UUID shorthand normalization.

This is an unregistered application core, not a live receiver: no component/controller,
pair credential source, servlet route, sender or flag is activated. Wire codec/ACK is
the user's proposal, not an owner signature. Future host must authenticate the scoped
Seller→Auth pair first and invoke a Spring transaction proxy; ACK only after proxy commit.
DB errors propagate for future sanitized503 mapping, not a false successful response.

The fixed lock serializes Role Sync transactions globally: deliberate low-throughput
MVP tradeoff, not a scalable dispatcher. Lock order is global row then Account row;
outer REQUIRED callers must not pre-lock Accounts in the reverse order. No advisory
hash lock, distributed transaction or network I/O is introduced.

Actual final command (Java21/Gradle8.10/Docker/PostgreSQL16; no API environment override):
`backend/gradlew.bat -p backend --init-script <absolute tests/security/auth-current-authority.init.gradle> -Ptv5AuthAuthoritySources=<absolute tests/security/auth-current-authority> :auth-service:build :auth-service:tv5CurrentAuthorityRegression --rerun-tasks --no-daemon --console=plain`

Result: **144PASS/0FAIL/2optionalSKIP**, exit0/1m8. Includes17 new real PostgreSQL
cases and12 JSON tests, original28AuthPersistencePG and5current-authorityPG.
Optional external database readiness/email inventory remain SKIPPED, not PASS.
Separate earlier invocation: Gateway32PASS (10real Gateway→Auth→PG E2E), core35PASS;
its overall build failed only legacy Auth migration-inventory assertions, subsequently
expanded for V3 while preserving SQLState/data/checksum checks. Do not aggregate runs.
Meaningful mutation control removed only role writes temporarily: the original PG
positive assertion FAILED; exact source bytes restored and final Auth suite GREEN.
This was mutation proof, not claimed preimplementation TDD. Raw failures are retained.

CI/PR final URL and exact publication HEAD are recorded in the PR conversation and
private `auth-role-sync-core-20261029/CHECKPOINT.md` after publication.
NEXT: independent TV1/TV5 review and exact RoleSync/StoreFacts/current-account wire
signoff; then scoped transport/error mapping and real HTTP security tests. No merge,
runtime enablement, receiver acceptance, P0/E2E completion or deployment is implied.
