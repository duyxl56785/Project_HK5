# Current-account RPC — TV1/TV2 technical handover

Refs #1 #2 #8. The maintainer delegated remaining TV1/TV2 technical implementation
on 29/10/2026. This is the recorded engineering decision, not an invented TV1 signature.
Stacked on #122; #119–#121 source is reused, JWT verification is not reimplemented.

Default hk5.auth.current-account.enabled=false. Private POST endpoints:
- /internal/auth/current-account/seller: seller-service → auth-service CURRENT_ACCOUNT_READ
- /internal/auth/current-account/product: product-service → auth-service CURRENT_ACCOUNT_READ
Never route /internal/** through public Gateway. TLS/private network mandatory except
loopback tests. Single X-HK5-Service-Credential is the scoped pair secret, not identity.
Separate HK5_SELLER_AUTH_CURRENT_ACCOUNT_ and HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_ prefixes:
SECRET, optional PREVIOUS_SECRET + PREVIOUS_UNTIL (ISO Instant). No defaults. Missing/
broken source503, invalid/duplicate secret401, wrong operation403. Reused credentials
across these pairs or RoleSync are rejected503. Operations must provision unique secrets.

Single Authorization: Bearer <original access JWT>. Do not forward identity/role headers.
Auth uses existing Hk5JwtCodec→security-core verifier, canonical UUID Auth-owned subject
lookup, canonical Account ACTIVE and current role grants in a read transaction.
AuthBootstrap remains separately opt-in; no codec/authority produces503, never allowance.
Strict request JSON {requestId:<canonical UUID string>}, <=1024 UTF8 bytes; duplicate,
unknown, malformed, trailing or wrong-type input400; oversized413. Pair before JWT/body.
200 JSON exactly requestId,subject,tokenId,effectiveRoles; no email/status/password/secret.
Effective roles = signed JWT roles ∩ current Auth grants, nonempty. Invalid token/account
401, no effective role403, DB/authority failure503. All results no-store.

CurrentAccountClient locally verifies JWT with existing verifier before RPC. Generates
fresh requestId; validates exact subject/tokenId/requestId, known signed role subset,
strict response fields/UTF8 and8KiB cap. Three-second whole-response deadline + cancellation,
no redirects, no role cache; Auth failures map401/403/503 without leaking response/errors.
Bearer replay is not a mutating one-time operation: every repeated read rechecks grants;
a revoked/disabled account cannot use a previously successful read. Old response replay
fails nonce matching. No revocation lease or atomic cross-service authorization promised.

Seller/Product adapters consume the client-created proof per request BEFORE business DB
transactions; do not cache/register the proof as a singleton. SellerAccessPolicy verifies
exact actor and current ADMIN for moderation. Its read methods match #85's pending
SellerStoreReadAuthority signatures, but that interface/source is absent from this base;
no manual copy or claim that #85 is already wired. ProductAccessPolicy additionally
requires a real Store/Seller policy, exact Store/owner, APPROVED/open for management,
ADMIN/no self-moderation. No House/facts invented. No adapter/filter/controller bean is
registered in Seller/Product; existing business default-deny remains unchanged.

Verification includes actual embeddedTomcat + JavaHttpClient + PostgreSQL16, liveAuth
client→RPC, disabled/revoked/promotion, forged headers, tampered/expired JWT, wrong pair,
rotation/expiry, malformed DTO, unavailable DB, defaultoff/missingbootstrap. Localclient
loopback tests cover malformed/replayed/unsigned-role/oversized/absent Auth responses.
Fresh source reviewer found Java21 request timeout excludes body completion: independent
source finding reproduced RED with stalled body, then fixed with bounded future deadline.
Reviewer did not independently run tests. Raw failed runs retained; no assertions removed.

NEXT independent TV5 review + CI; integrate request middleware into #85 and Product HTTP
on a branch containing their real source. StoreFacts/outbox sender remain separate work.
No endpoints enabled, deployment/merge/Finance/Inventory flags or P0/E2E completion.

Final verification: Java21/Gradle8.10/Docker29.9.1/PostgreSQL16.6. Command:
backend/gradlew.bat -p backend :security-core:build :auth-service:build :seller-service:build :product-service:build :api-gateway:build --rerun-tasks --no-daemon --console=plain
Exit0,3m45s,37tasks;921PASS/0FAIL/2optionalSKIP. Core41,Auth156(+2SKIP),Seller237unit+117PG,Product300unit+38PG,Gateway32(includes10actualAuthGatewayPG E2E). New8RPCPG+6client+2policy tests included, not added again. No separateauthorityinit-task or fullmarketplaceE2E run. LogSHA256459e91ce0dbaafd914e43daf25851ea3484d62dbbcc2e6314b99a74a53575e9d.
