# TV3 W1 — Cart / Auth v1.0 review fix (PR #28)

Status: PARTIAL / NEEDS VERIFICATION. Owner: TV3. Refs #3 and PR #24.

## Changes
- Cart CRUD / groups by Store / claim-release-finalize / Flyway V1 unchanged.
- Strict TV1 HS256 JWT current/previous kid keyring. No JWKS, no RS256, no default secret.
- Issuer, audience, sub, iat, nbf, exp, jti and roles are validated; max TTL 900s, skew 60s.
- Runtime env: JWT_ISSUER_URI, JWT_AUDIENCE, JWT_HS256_CURRENT_KID, JWT_HS256_CURRENT_KEY_B64, optional JWT_HS256_PREVIOUS_KID, JWT_HS256_PREVIOUS_KEY_B64.
- Invalid or missing verifier configuration prevents application becoming ready. Do not commit keys.
- /internal/cart/** must not be published via Gateway; requires service-pair credential and verified delegated customer JWT.
- TV2 Catalog adapter is still fail closed; no real Product integration.

## Test commands (Docker required for integration)
cd backend
./gradlew :cart-service:test --rerun-tasks --console=plain
./gradlew :cart-service:postgresIntegrationTest --rerun-tasks --console=plain
git rev-parse HEAD

Security and PostgreSQL integration tests are authored but NOT_RUN in this execution environment. PostgreSQL16 tests exercise Flyway/Hibernate schema, cart two Store persistence, uniqueness and cascade. Do not report PASS until process logs and exact SHA are collected.
Baseline Cart/Order Gradle build PASS on the author's earlier Windows log at c3150c6; not a PASS claim for new code.