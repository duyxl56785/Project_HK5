# Seller own-read HTTP integration / demo gate —29/10/2026
Refs #2 #8 #9. User delegated technical TV1/TV2 work; no production activation.
Stacked#127, exact#85 preparation commits cherry-picked(fc39c71,24b072e,dd8c600).
A trial merge#85 imported unrelated main history; aborted ONLY that untouched trial,
then selected Seller commits. README sections and both stronger route assertions retained.
No Order/Inventory/Frontend source changed. Handler unit/PG tests are reused, not replaced.

NEW opt-in hk5.seller.http.read.enabled=false: GET/api/sellers/me (role-neutral ACTIVE
account),GET/api/stores/me (signed/currentSELLER plus localDBowner/APPROVED). ClosedStore
own-read allowed. Original typedDTO/canonicalversion from#85, no Entity/ownerReference/token.
No identity/query/resourceID taken from client; queries rejected, extra headers ignored.
SingleBearer→sharedJwtAccessVerifier→#123currentAuth BEFORE any localtransaction, then
request-scoped existing services wrapped by Spring transactions. Store rechecks current
owner/status under local rowlock. No AuthDBcrossread or fake production authority.
401invalid/disabled,403role/Storedenial,404ownresourceabsent,503dependency/sourceerror;
no-store/sanitizederrors. ALL mutation/unknownroutes denied; no write controller registered.
Requires explicit HK5_AUTH_CURRENT_ACCOUNT_URL=/internal/auth/current-account/seller,
HK5_SELLER_AUTH_CURRENT_ACCOUNT_SECRET,HK5_AUTH_JWT_* existingkeyring vocabulary; no defaultkey.
JWT configuration is local verifier setup only, crypto reused unchanged. Password auth
remains excluded by original#85applicationconfig; no local password principal created.

Security reviews#122/#125: central IsolatedPairCredentials guards all4knownpair prefixes
on BOTH receivers and#123; every configuredcurrent/previous alias to a differentpair
fails503 before codec/service/DB. Conservative: evenexpiredprevious retained inconfig
must not alias; remove retiredkeys rather thanreuse. Constant-time suppliedcredential
comparison remains in sharedverifier. Test current↔current/current↔previous/previous↔previous,
no servicecalls/receipts/grants/disclosure. Existing distinctrotation preserved. Fix lives
in this integrationbranch; originalreviewthreads remainopen until owner integrates/reviews
at matchingHEAD. Do not claim oldPRheads fixed or selfresolve/dismiss reviews.

LocalDocker BLOCKED: DesktopSecretsEngine cannotrename stale AF_UNIX engine.sock
(Windows 'file cannot be accessed by system'); LinuxEnginepipe absent. Safe start/shutdown
attempted, then processrestart/socket-preservingrename also rejected. No socketrename
succeeded, no file/volume/container data deleted, no factoryreset. PostgreSQL attempts
failed initialization; not countedPASS. Stop infrastructure attempts rather thanresetdata.

Localfocused unit/realServlet+loopbackAuthRPC (mockSellerJPA) PASS. New actualAuthHTTP+
SellerHTTP+PG tests compiled for existing roleSyncIntegrationTest task; no localPGPASS
unless Dockerreturns. Productiontest-only moderationfixture gated by explicit testflag
so it cannot contaminate newread-host tests. CI must verify all actualPG/proxy lifecycle.

DEMO NOT READY. ProductPersistence/Image controllers/Gateway allowlist/SharedRouter#105
not wired in this slice: strictwritefreshness#126/127 stillBLOCKED,securityreview pending,
localDocker unavailable. No Gateway/publicAPI/runtime/Finance/Inventory flags enabled.
NEXT securityCI/TV5review, integratefixedparentreviews; restoreDockerwithoutdatareset,
then explicitfencedpermit protocol before writeHTTP, Product/Image and Gateway/frontendE2E.

Actual localcommand(Java21/Gradle8.10):backend/gradlew.bat -p backend :security-core:build :seller-service:assemble :seller-service:test :seller-service:compileRoleSyncIntegrationTestJava :auth-service:assemble :auth-service:test --tests '*SellerRoleSyncHttpTest' --rerun-tasks --no-daemon --console=plain. Exit0/1m4/21tasks;348PASS0FAIL0SKIP=43core297Sellerunit8AuthServlet. These are NOT PostgreSQL counts. PGattempts failedDockerproviderinit; livePG/demo NOT_RUN locally. Existingtests not disabled/skipped; this was explicitfocusedcommand. LogSHA256dd550963ece9d0639b4dc764a2e2f9318ce4689d096934fcdac8eac59ddc4e4e. Fresh source review no actionablefinding,independent runtimeNOT_RUN.
