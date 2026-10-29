# Internal pair credential isolation

Refs #2 #8; PR #128 security correction. Runtime remains default-OFF.

Receivers now require `HK5_INTERNAL_PAIR_CREDENTIAL_MANIFEST`. Missing, malformed,
incomplete, duplicate fingerprints or a local key that does not match it returns
503 before request parsing, authority lookup or mutation. No plaintext foreign
pair keys are required in a service's Environment.

## Provisioning trust boundary

The deployment/secret-provisioning owner must collect all four current pair keys
and every configured previous key in a single trusted provisioning process:

- `HK5_SELLER_AUTH_ROLE_SYNC_`
- `HK5_SELLER_AUTH_CURRENT_ACCOUNT_`
- `HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_`
- `HK5_PRODUCT_SELLER_STORE_FACTS_`

Use independently generated high-entropy secrets (at least 32 random bytes).
Run `com.projecthk5.security.PairCredentialManifest` from the security-core JAR
with these environment variables; its `main` reads environment only and outputs
the manifest only. Do not put secrets in command arguments or logs. The generator
requires every current key, validates credential encoding/size and rejects all
current/current, current/previous and previous/previous aliases, including expired
previous keys still configured. No fallback defaults exist in production.

Distribute the **same complete manifest** and each process's own pair keys through
trusted configuration management. The manifest is a versioned list of SHA-256
fingerprints, not a bearer credential. Each of eight slots is required; `-` means
no previous key. Receivers validate global uniqueness and bind their exact local
current/previous slots to that manifest. Scope remains server-owned method/path/
operation, not a caller header. Configuration is snapshotted once per verification.

The manifest is not signed and cannot establish trust in compromised deployment
configuration. Do not generate independent partial manifests per service, let a
request supply the manifest, or treat it as verified service identity/mTLS. The
shared provisioning process is the authoritative global isolation boundary; its
integrity and identical distribution must be reviewed before enabling runtime.

## Rotation and failure

Provision current + previous keys and a new complete manifest together. Deploy
the identical manifest to participating processes, then activate their matching
local key configuration; mixed revisions fail closed (503), rather than accepting
an unlisted secret. Existing previous-key deadlines still apply: a listed previous
key stops authenticating at its deadline (401). Remove a previous key and regenerate
the full manifest before reusing that slot; never reuse retired keys in another pair.
This change intentionally requires provisioning before previously configured internal
receivers can accept traffic. Keep endpoints OFF while rolling it out.

## Replay and tests

Cross-pair/process replay is denied by isolated keys + global configuration binding.
Same-pair retries remain supported: Role Sync event receipts/version checks are the
idempotency/replay authority; read RPCs are repeatable and keep their existing nonce
checks. This patch does not add single-use bearer tokens or claim anti-replay against
a stolen credential for its own authorized pair.

Tests include the original separate-environment counterexample (RED before fix),
all current/previous collision directions, malformed/missing manifests, wrong scopes,
rotation, and separate Auth/Seller HTTP environments backed by real PostgreSQL.
The HTTP regression checks repeated foreign credential attempts, zero new grants/
receipts and no Store owner disclosure. Test-only provisioning lives in Gradle test
fixtures and is not packaged in production. No fencing, Gateway route or write API
activation is included.

## Verification — 30/10/2026

Java 21 / Gradle 8.10 / real PostgreSQL Testcontainers, exit 0, 1m47s:

```text
backend/gradlew.bat -p backend :security-core:build :auth-service:build :seller-service:build :product-service:authorityIntegrationTest --rerun-tasks --no-daemon --console=plain
```

JUnit XML: core 47; Auth 158 PASS + 2 optional SKIPPED; Seller unit 297;
Seller PostgreSQL 163; Seller/Auth HTTP/PostgreSQL 22; Product authority HTTP/
PostgreSQL 5. Total 692 PASS / 0 FAIL / 2 SKIPPED, not historical totals.
Full Product regression and production deployment E2E were not run in this scope.
The original separate-environment test failed before the production fix (expected
configuration rejection, none thrown). An intermediate new fixture used a short
previous secret and failed the existing minimum length rule; only fixture data was
corrected, no assertion or credential rule was weakened.
Raw final log SHA256: `7a7fc15e3a834049d84cf7c77ef439bbf499d6b7323ecb92ae2e4546bfbaeca1`.
