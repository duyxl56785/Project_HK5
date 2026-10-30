# Authority reservation RPC v1

Refs #2 #8 #9. Technical scope: authenticated transport around #130 components.
All new flags default OFF; no business write controller, Gateway route or deployment.
Ledger, epoch allocation, barriers and applied migrations are unchanged.

| Receiver | Exact POST route | Allowed pair / operation |
|---|---|---|
| Auth | `/internal/auth/write-reservations/product/admit` | Product–Auth / AUTHORITY_RESERVE; original verified JWT required |
| Auth | `/internal/auth/write-reservations/product/lookup`<br>`/internal/auth/write-reservations/product/release` | Product–Auth / AUTHORITY_LOOKUP, AUTHORITY_RELEASE |
| Auth | `/internal/auth/write-reservations/seller/lookup` | Seller–Auth / AUTHORITY_LOOKUP only |
| Seller | `/internal/seller/write-reservations/product/admit`<br>`/internal/seller/write-reservations/product/lookup`<br>`/internal/seller/write-reservations/product/release` | Product–Seller / corresponding operation |
| Product | `/internal/product/write-receipts/auth`<br>`/internal/product/write-receipts/seller` | corresponding Auth/Seller pair / AUTHORITY_TERMINAL_RECEIPT_READ |

These are the existing isolated **shared pair keys**, also used for reciprocal
callbacks with a different server-owned method/path/operation scope. They do not
constitute mTLS or cryptographic proof of a process beyond possession of its pair
credential. Global manifest provisioning and rotation from #128 remain required.
A stolen *different* pair credential cannot grant access; compromise of a trusted
pair or service still requires credential rotation/incident response. Never claim
this protocol is immune to a fully compromised participant or its database role.

## Wire and proof

Requests: `{nonce,intent}` for admit/lookup; `{nonce,intent,epoch}` for release/receipt.
Intent fields are exactly `requestId,accountId,storeId,tokenId,mode,sellerVersion,
storeVersion,productVersion,commandHash`. UUIDs canonical; versions nonnegative
decimal **strings**, epochs positive decimal strings; commandHash lowercase SHA256.
Strict UTF-8/JSON, duplicate/unknown/trailing fields rejected, body ≤16KiB.
Every response echoes a fresh client nonce and is bound to the exact request ID,
fingerprint, expected participant and epoch. Same-operation replay is idempotent,
not a second grant/write; this is not a one-time bearer credential scheme.

Auth derives identity from the original verified JWT, checks ACTIVE and signed
required role ∩ current grants. Seller first resolves the matching ACTIVE Auth
reservation through its own scoped RPC, **before** its local admission transaction.
Caller identity/role headers are ignored. Lookup/release permit authenticated
cleanup without an expired user JWT; they do not admit a new write.

Release accepts intent/epoch only. It reloads its **own** persisted grant, then calls
the configured Product receipt provider before starting a local release transaction.
Product reads its own committed operation/grant tables using a single SELECT
snapshot; it accepts only COMMITTED/ABORTED with the exact binding/participant epoch.
It needs no UPDATE privilege. Untrusted Grant/Receipt/state fields are never accepted.
Wrong nonce, stale epoch, nonterminal/missing receipt, timeout, DB failure or malformed
response cannot release a reservation. A delayed valid terminal callback may finish
cleanup; a caller timeout alone is not proof of either success or abort. No TTL release.

## Configuration and least privilege

- Flags: `hk5.auth.reservations.enabled`, `hk5.seller.reservations.enabled`,
  `hk5.product.receipts.enabled`, `hk5.product.reservations.client.enabled`.
- Required receiver URLs: `HK5_AUTH_PRODUCT_TERMINAL_URL`,
  `HK5_SELLER_PRODUCT_TERMINAL_URL`, `HK5_SELLER_AUTH_RESERVATION_URL`.
- Product factory URLs: `HK5_PRODUCT_AUTH_RESERVATION_URL`,
  `HK5_PRODUCT_SELLER_RESERVATION_URL`. Intent/JWT are captured per operation,
  never in a singleton authority cache. Endpoints are pinned, no redirects;
  HTTPS required except explicit loopback tests; whole-response deadline 3 seconds.
- Existing pair prefixes and JWT settings are reused; no default secret or URL.
- Provision NOLOGIN groups named in `sql/reservation-*-grants.sql`, then attach
  separate runtime logins. Execute grants as DB owner after migrations. Revoke
  PUBLIC CONNECT/CREATE and grant only own-database CONNECT and schema USAGE.
  Scripts do not create passwords, alter existing roles or run automatically.
- Auth RPC role reads only account ID/status and role grants (not password hashes)
  and owns reservation DML only;
  Seller RPC role cannot change profiles/stores; Product callback role SELECTs
  only operation/grant tables. Product writer is a different role. Runtime migration
  is OFF; no cross-database reads or runtime DDL.

Statuses: invalid pair/JWT401, authority/scope403, invalid JSON400, oversized413,
unsupported media415, request-binding/epoch conflict409, dependency/proof/DB failure503.
Seller stale facts are an authority denial403, not a Product business-version response.
Responses are sanitized/no-store. Only explicit absent lookup returns ABSENT.

## Remaining acceptance gates

Independent review, global credential provisioning and recovery/operator fairness
remain gates. Seller-exclusive mutation orchestration and real Product lifecycle
version/write callbacks are not part of this RPC slice. Business writes stay denied.
The base still has Product migrations V1/V6; a test proves lower versions cannot
be silently introduced after V6 with outOfOrder=false. This is **not** a verified
main/pending-image V2–V6 upgrade. Reconcile that sequence before any migration rollout.
No production activation or full marketplace E2E acceptance is claimed.

## Verification — 30/10/2026

```text
backend/gradlew.bat -p backend :security-core:build :auth-service:build :seller-service:build :product-service:build --rerun-tasks --no-daemon --console=plain
```

Java21/Gradle8.10, actual PostgreSQL16.6 Testcontainers; exit0, 2m14s, 36 executed
tasks. **1069 PASS / 0 FAIL / 2 optional Auth SKIPPED**: core54; Auth158+2skip;
Seller297unit+163PG+22HTTPPG; Product308unit+38PG+29authority integration.
The 11 new RPC integration cases boot independent embedded Auth/Seller/Product
application instances plus a separate Product worker, with separate Environments,
pair keys and least-privilege DB logins. They share a test JVM; this is not a live
Docker Compose deployment or mTLS/process-attestation acceptance. Fault proxies
relay actual service responses; they do not fabricate authoritative grants/receipts.
The business write is a test-only SQL sink. Existing #130 cases retain their own scope.

New coverage: genuine JWT/current grants, current/previous credential rotation and
expiration, wrong/leaked foreign pair, forged headers, malformed DTO/epoch/binding,
replay, live revocation-vs-admission, callback timeout/stale nonce, lost ACK/restart,
terminal-proof release and flags OFF. SQL42501 assertions prove password hashes and
moderation reasons inaccessible to RPC roles, callback cannot UPDATE ledger, and
cross-database CONNECT/DDL are denied. Column-level grants are exercised from files.

Review found missing receipt Content-Type enforcement: regression RED (expected415,
actual503/DB access), then GREEN after checking media type after pair auth and before
body/DAO. Raw earlier failures retained, including a test import error and Docker
not running; Docker29.9.1 was safely started, without reset/data/volume deletion.
Fresh source reviews found no additional P1/P2; reviewer runtime NOT_RUN.
Final raw-log SHA256: `1e82c788419fffabd27d58aa374bef0950298de27fd5391420595347c66221b1`.
