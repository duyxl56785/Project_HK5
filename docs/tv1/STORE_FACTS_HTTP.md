# Store Facts batch provider v1

Refs #2 #8. Technical decision under maintainer handover of TV1/TV2,29/10/2026.
Stacked on #123; reuses #120 guard and #94 StoreCurrentSource. No migration rewrite.
Private POST /internal/seller/store-facts/batch, trusted Product→Seller operation
CURRENT_STORE_FACTS_BATCH_READ. Never expose /internal/** through Gateway.
Default hk5.seller.store-facts.enabled=false; independent review before activation.

Single X-HK5-Service-Credential, runtime HK5_PRODUCT_SELLER_STORE_FACTS_SECRET;
optional PREVIOUS_SECRET requires PREVIOUS_UNTIL ISO Instant, same prefix. Provision
a unique secret for this service pair; no JWT/identity/role/caller header substitutes.
Missing/invalid/duplicate credential401; wrong method/path403; broken secret source503.
Use private TLS transport; no production secret/configuration committed.

Request exactly {storeIds:[canonical lowercase UUID strings]},1–100 unique IDs,
<=8192 UTF8 bytes. Reject duplicates/unknown fields/types/trailingJSON/malformedUTF8
with400; oversized413. No account/owner/House/status supplied by caller.
200 response exactly {results:[...]}, one entry per requested ID, in request order:
- ABSENT: storeId,result=ABSENT (only a missing Store row).
- FOUND: storeId,result=FOUND,sellerId,storeStatus,sellerStatus,ownerReference,
  storeVersion,sellerVersion. Versions canonical nonnegative decimal long strings.
Store status OPEN/CLOSED; Seller four existing statuses. Unsellable rows remainFOUND.
Owner reference is exact current persisted mapping, not normalized or parsed as AuthID.
No House, Inventory, current Account state, reviewReason, inferred eligibility or lease.

StoreCurrentSource.findBatch uses one parameterized scalar LEFT JOIN statement under
existing REQUIRED/read-only/READ_COMMITTED policy; versions/status/owner come from the
same statement snapshot, no stale managed-entity reads or N+1. Missing Seller, invalid
mapping/status/version/duplicate projection or DB failure returns503 for WHOLE batch,
no results/usable subset. No pagination, caching or distributed freshness guarantee.
All responses no-store; sanitized {code,message}; unavailable code STORE_FACTS_UNAVAILABLE.

Seller module adds starter-security and exactly reuses #85 default-deny configuration
blob8392c02be3716b029ffd1d1711d2c1a9a70386ec. No #85 business controller/auth adapter
copied or enabled. Existing six nonexposure cases now require403 plus explicit handler
absence and unchanged DB, preserving the original404/no-business-route assertion intent.

Tests-first seven realHTTP/PG cases failed404 before implementation; focusedGREEN,
then rotation/batch100/UTF8/security cases added. Affected regression first exposed six
legacy404 assertions against intentional security403; preserved failures and strengthened
handler nonregistration checks. Test-only orphan fixture bypasses FK triggers on a temporary
Testcontainers connection, not production migration or data. App fixture has SELECT only.
Fresh independent source review reported no finding; reviewer did NOT execute tests.

NEXT TV5 review/CI; then Product consumer adapter using current facts plus #123 current
account. House/freshness/Catalog eligibility and businessHTTP remain separate gates.
No runtime/deploy/merge/Finance/Inventory flags, no fullP0/API/E2E completion claimed.

Final affected regression: Java21/Gradle8.10/Docker29.9.1/PostgreSQL16.6.
backend/gradlew.bat -p backend :seller-service:build --rerun-tasks --no-daemon --console=plain
Exit0,56s,11tasks;364PASS/0FAIL/0SKIP =237unit+127PG (includes10new realembeddedHTTP/securityPG tests). No unrelated Auth/Product/Gateway suites rerun locally. LogSHA2560cca375f1248831247084db5c90bb5a81cd62b935004b3578bd89577b3623800. Fixture qualifier corrected after Actuator mapping ambiguity; failed logs retained. Final brace formatting/doc evidence only after tests, no semantic source change.
