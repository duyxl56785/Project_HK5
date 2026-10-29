# Role Sync receiver — technical decision and checkpoint

Refs #1 #2 #8. Implemented under the maintainer's explicit 29/10/2026 handover of
remaining TV1 technical decisions to TV2. This records an implementation decision,
not a fabricated TV1 signature or independent security acceptance.

POST /internal/auth/seller-role-sync; never expose /internal/** through Gateway.
Default hk5.auth.role-sync.enabled=false. No live configuration or secret is committed.
X-HK5-Service-Credential is a single exact pair credential, NOT end-user identity.
The receiver's trusted scope is seller-service → auth-service / SELLER_ROLE_SYNC only.
HK5_SELLER_AUTH_ROLE_SYNC_SECRET is required for successful requests; optional
HK5_SELLER_AUTH_ROLE_SYNC_PREVIOUS_SECRET requires an explicit ISO Instant
HK5_SELLER_AUTH_ROLE_SYNC_PREVIOUS_UNTIL. Missing/broken source fails 503;
missing/duplicate/invalid supplied credential fails 401; wrong method/path fails403.
Deploy only on private authenticated TLS transport after independent review.

Strict five-field JSON: eventId/sellerId canonical UUID strings, accountId exact opaque
reference (existing Auth lookup only resolves exact canonical UUID), sellerStatus one
of PENDING_REVIEW/APPROVED/REJECTED/SUSPENDED, sourceVersion canonical nonnegative
Java-long string. No unknown/duplicate fields, malformed UTF8, trailing JSON or body
above 16384 bytes. No normalization or implicit account creation.

200 ACK has eventId,sellerId,sourceVersion,result (APPLIED/DUPLICATE/STALE_IGNORED).
ACK is constructed only after the Spring REQUIRED proxy returns after commit.
400 validation,409 durable identity/payload conflict or account not found,503 DB/commit
failure; sanitized {code,message} envelope, no-store. Client retries must preserve event
identity and payload; reconcile409 rather than invent a new event to bypass conflicts.
No sender/dispatcher or reconciliation job is implemented in this slice.

Existing V3/core unchanged; global serialization and outer transaction lock order remain
limitations. Receiver does not grant ADMIN or equate Account ACTIVE with Seller approval.
No Auth business HTTP, Gateway route, Finance/Inventory flag or deployment activated.

Verification: tests-first missing-class compile failure retained; subsequent Spring
fixture duplicate-bean failures fixed without weakening assertions. Docker was stopped;
started installed Docker Desktop without deleting containers or volumes. Focused run
with real embedded Tomcat + Java HttpClient + PostgreSQL passed; security MockMvc is
separate from that loopback HTTP test. Exact final regression counts/CI in PR.

NEXT: independent review, current-account/effective-role scoped RPC; then Seller outbox
sender and Store facts, protected Seller/Product HTTP, Gateway/frontend integration.
Do not mark P0/F03–F08/API/E2E complete from this receiver slice.

Final local regression: Java21/Gradle8.10/Docker29.9.1, command backend/gradlew.bat -p backend :security-core:build :auth-service:build :api-gateway:build --rerun-tasks --no-daemon --console=plain. Exit0,3m27s; 215PASS/0FAIL/2SKIP = Auth148 + core35 + Gateway32. Includes receiver loopback HTTP/PostgreSQL and original10GatewayAuthPG E2E. No explicit separate current-authority init task in this invocation. Raw log SHA256469d5f50129da2ec424cefe8a594c70ff0177d3ebd651109ae6690d9b770bda2. Final brace/LF formatting only after run; no semantic change. CI pending publication.
