# TV1 Auth bootstrap — opt-in foundation, NOT live HTTP

Refs: #1, #80, #82. This note supplements, and does not amend,
\`docs/TV1_AUTH_IDENTITY_GATEWAY_CONTRACT.md\`. No signing key or other secret
belongs in Git, application.yml, documentation, screenshots or CI logs.

## What is implemented

When \`hk5.auth.bootstrap.enabled=true\` is **explicitly** set, Auth Spring
context creates two scoped beans: \`Hk5JwtCodec\` (configured local HS256
current/previous keyring and verification) and \`CurrentAccountAuthority\`
(transaction-proxy-compatible Auth DB live status/role source).
Absent the opt-in, neither is registered by this configuration.

Bootstrapping these beans **does not install** a servlet SecurityFilterChain,
Resource Server, controller, Gateway routes or a protected API. No register,
login, refresh, logout or /me HTTP route exists as a result of this change.
Do not enable the bootstrap in a deploy until HTTP routing and current-account
policy are reviewed. This is not permission to deploy or merge PR #80.

## Required runtime-only environment when explicitly enabled

All values are mandatory (unless noted) and are read from **process environment**,
not client requests or JWT fields:

- \`HK5_AUTH_JWT_ISSUER\`: exact trusted issuer; no default.
- \`HK5_AUTH_JWT_AUDIENCE\`: exact trusted audience; no default.
- \`HK5_AUTH_JWT_CURRENT_KID\`: current local key identifier.
- \`HK5_AUTH_JWT_CURRENT_KEY_B64\`: canonical Base64 of CSPRNG HS256 key,
  decoded length >=32 bytes (bounded <=4096); NEVER commit a value.
- \`HK5_AUTH_JWT_PREVIOUS_KID\` and \`HK5_AUTH_JWT_PREVIOUS_KEY_B64\`:
  optional **as a pair**, only during reviewed rotation, distinct kid/key.
- \`HK5_AUTH_ACTIVE_STATUS\`: exact canonical account-active vocabulary
  after explicit owner/schema verification. No guessed default or normalization.

Malformed, absent, padded or weak settings raise a sanitized initialization
failure if opted in. No fallback test key is available at runtime.
Credentials should be supplied using a controlled runtime env/secret manager
and scoped to the trusted HS256 signing domain. HS256 verifiers can also sign:
treat their key exposure as critical.

\`hk5.auth.bootstrap.enabled\` is a Spring property to be set only as part of
a reviewed secured deployment. It is **not a security feature flag**:
setting it alone does not make any HTTP endpoint authenticated.

## Verification and security gates

- Existing baseline: independent TV2 PostgreSQL verification at #80 HEAD
  \`3a92ff16cab121ddf53f5d36d8d70df1af314e68\`:
  9 JWT/current-authority unit + 28 Auth persistence PG + 5 independent
  current-authority PG PASS. These numbers do **not** validate later commits.
- New \`AuthBootstrapConfigurationTest\` covers missing secrets/status,
  canonical Base64, weak key material and invalid/incomplete key rotation.
- Exact new PR HEAD CI and focused test results must be checked separately.
- Before enabling bootstrap: settle canonical Account active/inactive states,
  trusted runtime key injection and rotation process; verify transactional
  proxy and fresh DB reads under production-like secrets and PostgreSQL.
- Before opening HTTP: implement secure password hashing with bcrypt-byte-ceiling
  protection, register/login/me contracts and negative MockMvc/security tests;
  one-use refresh-cookie family rotation + Origin/CSRF; current ADMIN/SELLER
  decisions, Seller role-sync, servlet filters, reactive Gateway and service
  independent JWT verification. Inactive/stale/revoked/broken dependency must
  fail closed with contract-specific 401/403/503, not JWT role alone.

\`INVENTORY_ORDER_PAIR_ENABLED=false\` and
\`HK5_FINANCE_POLICY_APPROVED=false\` remain unchanged. No merge/deploy/E2E
signoff is provided by this configuration.
