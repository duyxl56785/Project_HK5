# Write freshness fail-closed gate — 29/10/2026
Refs #2 #8 #9. Stacked on #128 / 28b7ec3. TV1/TV2 technical scope only.

`ProductCurrentAuthority` preflight still validates JWT/current Auth and current Seller/Store
facts before any transaction. Its returned policy now rejects both seller management and
moderation writes with `AUTHORIZATION_UNAVAILABLE`, even outside a Spring transaction.
`readStore` remains a five-second snapshot for preflight inspection, not a write permit.
No write HTTP controller, Gateway allowlist, runtime flag, migration or remote DB access added.
Existing persistence services and trusted local policies are unchanged.

Strict freshness remains BLOCKED. Separate Auth, Seller and Product databases cannot make
remote status/role revocation and Product commit atomic through these snapshot RPCs.
Checking again, shortening TTL, version equality or network calls under a local transaction
does not close this race. Unlock requires an explicitly accepted consistency protocol covering
all revocation and write participants: fenced permit acquisition, concurrent revocation ordering,
commit validation, expiry, crashes and recovery. No such protocol is implemented or claimed here.
Seller own-read remains available under its existing opt-in; Seller/Store mutations stay denied.

Regression: deterministic concurrent remote role/status mutation after preflight must never
turn the snapshot into write authority; actual Auth HTTP + Seller HTTP + isolated PostgreSQL
commits each role removal/account disable/Seller suspension/Store close before attempted
Product probe write. Denial rolls back the probe; no extra network in the transaction.
The probe is test-only and does not prove Product CRUD or marketplace E2E completion.

Issue #2 remains OPEN/PARTIAL; P0/write unlock/demo/E2E are not complete.

Verification: Windows / Java 21 / Gradle 8.10 / Docker 29.9.1, actual PostgreSQL16.6-alpine.
Command: backend/gradlew.bat -p backend :product-service:test :product-service:authorityIntegrationTest :seller-service:test :seller-service:roleSyncIntegrationTest --rerun-tasks --no-daemon --console=plain
Exit 0, 1m2s, 20 tasks executed. 630 PASS / 0 FAIL / 0 SKIP:
Product306 unit + Seller297 unit + Product6 actual HTTP/PG + Seller21 actual HTTP/PG.
RED: focused Product unit6/1FAIL, stale preflight wrongly authorizes management.
Two intermediate PG fixture failures (missing suspension reason, then wrong column) corrected;
final actual PG6/0FAIL/0SKIP. Raw logs preserved separately, no edits to hide failures.
Final raw log SHA256: b894bae18bb019099fe3b4eebae0ed22a7455ec0d39bff998bf5baa687a4afaa.
Fresh independent source review: no new findings; independent reviewer runtime NOT_RUN.
Marketplace/Gateway/frontend E2E NOT_RUN in this local slice; CI remains separate evidence.
