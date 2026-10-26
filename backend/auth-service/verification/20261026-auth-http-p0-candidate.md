# TV1 Auth HTTP / JWT / Gateway — candidate delivery and release gates

Refs #1, #80, #96, #101, #102. **DRAFT / NOT_READY / NO_MERGE / NO_DEPLOY.** This document describes a candidate code path on a nested feature branch; no production Auth/Gateway API has been enabled. TV2 security reviews of #101 and the open #80 password-NUL finding are still separate mandatory gates.

## Current implementation (candidate; opt-in only)

- **Account policy (TV1 decided in #101)**: `ACTIVE` is the only active Auth Account state, `DISABLED` or unknown state fails closed. Register creates `ACTIVE + CUSTOMER-only`; account + provisioning task use the same transaction via existing `AccountPersistence`. Existing status values must be inventoried read-only before any rollout; no silent mapping, DDL or migration.
- **AuthSessionService**: verifies PBKDF2 credentials and current DB state, creates a single refresh family (absolute <= 7d) and SHA-256 token hash, issues HS256 access JWT via verified `Hk5JwtCodec`. Refresh acquires family lock, consumes root/child once and creates child atomically; used-token replay revokes the family **by returning a negative result so revocation commits**, not by throwing and rolling back. Logout is idempotent for absent/unknown credentials and persists matching-family revocation. `me` verifies JWT and intersects with current Auth roles; signed ADMIN without current grant cannot elevate.
- **Opt-in Auth HTTP adapter**: strict register/login JSON field list, refresh/logout body prohibition, login-only secure HttpOnly SameSite=Lax refresh cookie, max-age bounded by family expiry, no-store responses, protected `GET /api/auth/me`, generic credential failure and 401/403/503 mapping. `AuthHttpGate` enforces exact Origin, custom CSRF header and *single-node test-only* IP rate limit. `AuthServletSecurityConfiguration` denies unknown routes, and keeps the whole controller OFF unless `hk5.auth.http.enabled=true`.
- **Opt-in Gateway**: HS256 cryptographic decoder rejects unknown kid, invalid issuer/audience/claim and wrong algorithm; narrow Auth method/path routing; public POST login/register/refresh/logout are not blocked by stale Bearer; protected `/me` verifies JWT; strips client-supplied identity/internal headers, restricted CORS origin. `hk5.gateway.auth.enabled` defaults OFF.

## Verification scope / NOT-RUN

- Existing #101 source receipts: 58 reported PASS at standalone `083355be`; 87 locally reported PASS at `f70dbdb`, recorded at #101. These are historical parental baseline receipts, **not** new runtime HTTP verification.
- PR #102 first inert service-layer revision had GitHub backend readiness CI SUCCESS. Additional HTTP, Gateway and security changes must pass a **new final-head** CI; do not substitute older SUCCESS.
- New `AuthSessionServiceTest`, `AuthHttpControllerTest`, `AuthHttpGateTest`, `GatewayJwtDecoderTest` provide focused unit, standalone MVC adapter and crypto tests. They do **not** constitute a full Spring Security MockMvc chain, Gateway WebTestClient or PostgreSQL concurrency E2E.
- **NOT_RUN/NOT_READY**: full exact-head combined 25/28/5 or updated 54/28/5 on the last #102 SHA; multi-process rate limiting; trusted proxy & Origin preflight; full servlet `SecurityContext` principal integration; browser FE router; public Gateway/Auth traffic; verified independent service-pair OD-09; Seller-to-Auth versioned role synchronization; two-Store E2E; production secrets; status inventory and migration review.

## Release blockers (must remain closed until approved)

1. TV2 disposition of the #80 NUL review thread and independent #101 bootstrap/keyring security review.
2. Full exact-head CI SUCCESS + independent security review of #102, adversarial request/body/header/cookie tests, token rotation race/replay with disposable PostgreSQL and transactional rollback on error.
3. Replace/test **single-instance** in-memory throttling with reviewed distributed/ingress rate limiting and a trusted proxy/IP model. Current rate limiter is for isolated test only.
4. Review end-to-end servlet SecurityFilterChain principal context for protected routes, negative 401/403/503, and downstream Auth current-state checks. The controller's own cryptographic guard is not a substitute for service-wide authorization.
5. Gateway WebTestClient verifies exact allowlist, CORS preflight, untrusted headers, token key rotation and no `/internal` or business wildcard exposure; every downstream protected service must verify JWT/role/ownership separately.
6. Owner-approved operational secret provisioning, origin allowlist, Auth upstream URI, keyring rotation and deployed environment consistency; explicit `HK5_AUTH_ACTIVE_STATUS=ACTIVE`; read-only inventory of legacy Account status and approved remediation.
7. Auth register/login/refresh/logout/me live-stack tests, transactional User provisioning replay/reconciliation and FE integration; no silent migration, no default passwords.
8. Existing Finance/Inventory feature flags remain OFF. No merge into #101/#80/main, production deploy, migration, public port or profile enablement from this PR alone.

**State**: CODE CANDIDATE ONLY. This PR intentionally provides a reviewable implementation without authorizing runtime enablement.
