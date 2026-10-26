# TV1 Auth HTTP / JWT / Gateway — candidate delivery and release gates

Refs #1, #80, #96, #101, #102. **DRAFT / NOT_READY / NO_MERGE / NO_DEPLOY.** This document describes a candidate code path on a nested feature branch; no production Auth/Gateway API has been enabled. TV2 security reviews of #101 and the open #80 password-NUL finding are still separate mandatory gates.

## P1 Gateway forwarding — 26/10/2026, FIXED_CANDIDATE / WAITING_REVIEW

Refs #102. This receipt supersedes older forwarding NOT_RUN/BLOCKED entries below only for the checks listed here. Final FIXED_VERIFIED belongs to TV2's independent exact-head re-review; PR remains Draft, runtime flags remain OFF.

- Root cause reproduced on parent `62d7a9a7f743e4c6818d3adeb3674941a742f53c`: Gateway 4.1.6 `ForwardedHeadersFilter` calls `HttpHeaders.headerSet()`, absent in Boot 3.3.5's Framework 6.1.14. Reactor treats the resulting `NoSuchMethodError` as fatal before outbound connect, leaving WebTestClient to time out. Direct HTTP upstream control passed. This is a production dependency linkage defect, not an upstream body-drain deadlock.
- Fix: override `spring-framework.version=6.1.15` **only in api-gateway**. Keep the existing outbound sanitizer, route/security/CORS filters and timeouts. No global framework upgrade, API/schema/event change, Auth runtime activation, Inventory/Finance edit or deployment. Compatibility requirement: [Spring Cloud release note](https://spring.io/blog/2024/11/27/spring-cloud-2023-0-4/).
- RED: original embedded suite **2 PASS / 5 FAIL / 0 SKIP**, exit 1. With only the dependency fix and the original unmodified test: **7 PASS / 0 FAIL / 0 SKIP**, exit 0. Expanded final suite below retains all original cases.
- [x] Four public POST routes and signed GET `/api/auth/me` forward through real embedded Netty Gateway + WebTestClient + separate loopback Netty HTTP upstream, preserving method/path/body, Authorization, Origin and X-CSRF-Protection. POST cookie/Content-Type also preserved.
- [x] All nine spoofed identity/internal headers absent upstream, including repeated names with different casing. Removed failure diagnostics that printed full upstream headers/Bearer.
- [x] Missing/invalid Bearer, forged identity without JWT, unknown/internal/business paths, wrong methods, disallowed Origin and forbidden preflight headers cannot reach upstream. Trusted preflight succeeds without an upstream request.

Windows, JDK 21.0.11, Gradle 8.10, UTC; Docker 29.7.2; `postgres:16` disposable fixtures only. Readiness uses a separate ephemeral loopback PostgreSQL with independent random app/migrator credentials. `TV1_DATABASE_TESTS=true` and `AUTH_EMAIL_INVENTORY_JDBC_URL` target that fixture; the inventory check does not attest to production data.

| Final local task | PASS | FAIL/error | SKIP |
|---|---:|---:|---:|
| `:api-gateway:test` (18 embedded + 4 focused cases) | 22 | 0 | 0 |
| `:auth-service:test` (includes 28 persistence PostgreSQL cases) | 112 | 0 | 0 |
| `:auth-service:tv5CurrentAuthorityRegression` (real PostgreSQL) | 5 | 0 | 0 |

Final combined invocation, from `backend`, exit 0 (1m16s); paths resolved against this worktree:

```powershell
$env:JAVA_TOOL_OPTIONS='-Duser.timezone=UTC -Dapi.version=1.44'
$qaInit=(Resolve-Path ../tests/security/auth-current-authority.init.gradle).Path
$qaSources=(Resolve-Path ../tests/security/auth-current-authority).Path
./gradlew.bat --init-script $qaInit "-Ptv5AuthAuthoritySources=$qaSources" :api-gateway:test :auth-service:test :auth-service:tv5CurrentAuthorityRegression --rerun-tasks --console=plain --no-daemon
```

`:api-gateway:bootJar :auth-service:dependencyInsight --dependency spring-web --configuration runtimeClasspath --console=plain --no-daemon` also exits 0. Gateway's packaged `BOOT-INF/lib` contains Gateway 4.1.6 and all eight Spring Framework jars at 6.1.15; Auth runtime remains on 6.1.14.

Raw logs/XML remain byte-preserved locally in ignored `backend/api-gateway/build/verification/p1-forwarding/`; they are not published because integration output can contain fixture credentials. SHA256:

| Artifact | SHA256 |
|---|---|
| `red/TEST-com.projecthk5.gateway.GatewayEmbeddedNettyForwardingTest.xml` | `39173503e1308fef99d760c17b87ee746bb62cc4e5042f68bb24ed7db0044912` |
| `auth-gateway-final.log` | `7e73058b18bad8854a4dd267e9e9999e014bea1795a04fc628efa1adf38fd566` |
| `gateway/TEST-com.projecthk5.gateway.GatewayEmbeddedNettyForwardingTest.xml` | `03e97a841456829966f33bdf45adf786925a26b55596815c17616aac3535f3f5` |
| `authority-postgres/TEST-com.projecthk5.tv5authqa.CurrentAccountAuthorityPostgresRegressionTest.xml` | `fa7a9f0a4b0eed5002925138d1687414c936ac6adc8128b808ef498b28625b4c` |

Exact commit/remote SHA and post-push CI are recorded on PR #102. The HTTP upstream is a real transport fixture, not a full live Auth/DB/browser stack; release gates below remain in force. No independent TV2 approval is claimed.

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
