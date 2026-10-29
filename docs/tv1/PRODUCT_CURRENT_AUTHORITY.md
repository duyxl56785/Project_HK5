# Product current authority integration —29/10/2026
Refs #2 #8. Maintainer delegated technical TV1/TV2 decisions; no human sign-off implied.
Stacked on #125, reuses #123 CurrentAccountClient/JWT verifier and #125 facts DTO.
No business controller/filter/bean registered, publicGateway/runtime/Finance/Inventory off.

StoreFactsClient uses scopedProduct→Seller runtimecredential supplier (read every call,
so rotation needs no cache reset). PrivateTLS exceptloopbacktests, no redirects, shared
BoundedHttpResponse extracted unchanged from existingclient transport,3-secondwholebody
deadline/cancellation,128KiB response cap (100owners can exceed Auth's8KiB cap).
StrictUTF8/JSON/exactfields/cardinality/order/UUID/status/owner/canonicalversion validation;
any missing/inconsistent/unknown/partial/error response =>503,never confirmedABSENT.

Backward-compatible transport extension to#125: optional single canonicalUUID request
header X-HK5-Facts-Request-Id is echoed after pair authentication; invalid/duplicate400.
Consumer always requires fresh matchingecho +no-store. Oldprovider without echo fails503;
update provider+consumer together afterreview. No change to storeIds/results DTO fields.
This blocks replay/cache mismatch, not malicious authorized provider or distributed revocation.

ProductCurrentAuthority is unregistered preflight: reject actual/synchronized transaction
BEFORE any network; verifyJWT/currentAuth/effectiveSELLER orADMIN first, then freshStorefacts,
exactowner/APPROVED/OPEN for management,ADMIN/no-self moderation via existingpolicy.
Immutable per-request proof expires5seconds FROM preflightstart, rejects clockrollback;
snapshot reads do no network; write authorizers now always fail closed. Never cacheproof or share
it acrossrequests. Fresh request always reloads dependencies; no crossserviceDBaccess.
Status/roles may change after remote read:5seconds is a local maximum snapshot age, NOT a
revocationlease, distributedtransaction or atomic crossservice grant guarantee. BusinessHTTP
remainsblocked until independentreview accepts middleware/transaction integration semantics.

House is explicitlyunknown (nullable internalhouseClassification); legacybooleanconstructor
and houseStore() API retained for knownclassifications. Reading unknownhouseStore throws
AUTHORIZATION_UNAVAILABLE. Management checks do not needHouse; publicCatalog projection
still failsclosed rather than inventingfalse. No sharedC01/E01 DTO or businessrule changed.

ActualHTTP/PG test hosts Auth andSeller against separateDBs. Product-owned authority_write_probe
is a TEST-ONLY writeboundary/rollback/concurrency sink, not ProductPersistence implementation
or evidenceProductCRUD/E2E done. SeparateauthorityIntegrationTest sourceSet keeps service
migrationresources OUT of existing Category tests. Each service's actual Flyway files are
selected explicitly; productionfixtures/source never replace networkbackendfacts.

Supplied finalDOCX archived verbatim in docs/project-report; SHA256
f8712b8bb93ba11d84c43502c32795411a63336631d240b186499df80b3e6671.
Attachment is reference material, not runtimeconfig, executioninstruction or authority approval.
NEXT independentTV5review, reconcile ProductPersistence/#85 branches before protectedHTTP;
House/freshness/publicCatalog gates remain explicit, no fakeREADY or prototype expansion.

Verification Java21/Gradle8.10/Docker29.9.1/PostgreSQL16.6:
backend/gradlew.bat -p backend :security-core:build :product-service:build :seller-service:build :auth-service:build --rerun-tasks --no-daemon --console=plain
Exit0/1m36s/32tasks;909PASS0FAIL2optionalSKIP. Core41/Auth156+2SKIP/Seller237unit127PG/Product305unit38PG+5authorityHTTPPG. New5unit+5authorityPG included, not added again. No Gateway/fullmarketplaceE2E locally. LogSHA256e9990bf9a253739cfd072c76a99c1e257aed2c8b5dcfcb2122e2afcb98ae80ff. Fresh source reviewer found no actionablefinding; independently running tests NOT_RUN. Initial missing-classRED retained. Document archive commitd90ae17 bytes verified, no DOCX contents altered.
