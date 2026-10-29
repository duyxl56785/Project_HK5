# Shared verification foundation — no routes enabled

`JwtAccessVerifier` is the verification-only part extracted from Auth's existing
`Hk5JwtCodec`. Auth delegates verification to this library and retains its issuing,
current-account and public Java APIs. No Auth entity/repository is exported to consumers.
Trusted hosts supply issuer, audience, current/previous keys and Clock explicitly.
No component scan, default secret, filter or security-chain registration is provided.

The returned opaque subject and immutable token roles prove signed claims only.
They do not prove ACTIVE account, current grants, Seller approval, Store ownership,
House classification or public eligibility. Effective roles must still intersect
fresh Auth-owned grants; unavailable authority must fail closed. The current-account
transport/pair/freshness contract is not defined by this library.

Validation retains HS256, local kid allowlist, typ JWT, issuer/audience, exact900s TTL,
iat=nbf, at most60s skew, nonblank opaque subject/jti and unique known roles. It rejects
wrong raw claim types/fractional NumericDates before trusting mapped claims. Existing
Nimbus-issued single-audience strings remain compatible alongside arrays of strings;
no numeric audience coercion. This preserves existing issued-token behavior, not a new
HTTP DTO. Gateway's separate reactive decoder remains unchanged.

`InternalPairVerifier` checks one trusted caller/callee with explicit exact method/path
scopes and server-owned operation names. It never reads identity or operation headers.
Its credential supplier must load runtime secrets, not request data. Missing/invalid
credentials return401 `INTERNAL_AUTH_INVALID`, valid credentials outside scope403
`INTERNAL_FORBIDDEN`, unavailable credential source503 (internal decision only).
Current/previous credentials require a bounded previous-key deadline. Comparison uses
fixed-length hashes and constant-time equality; malformed UTF-16, NUL/CRLF and short
credentials are rejected. Credential/error representations never expose secret values.

No role-sync/current-account/Store-facts HTTP endpoint, sender, role mutation, Gateway
route, runtime flag or downstream policy bean is activated. Exact Role Sync ACK,
Store Facts DTO and current-account service-pair wire still need owner signoff; the user
confirmed no FINAL CO-SIGN exists on29/10/2026. Do not claim live Seller/Product API
acceptance from library or fixture tests. A future servlet host must enforce duplicate
header/raw-path handling and use the approved security/error contract before activation.
