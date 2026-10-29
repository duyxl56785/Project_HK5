# Seller role delivery —29/10/2026
Refs #2 #8. Exact producer#118 cherry-picked with -x(7d5213f→926baad),stacked on#126.
No V1/V2/V3 rewrite; newSellerV4 adds delivery state/constraints/index and immutable
snapshotUPDATE trigger. V3 producer remains same transaction as Seller status, never
calls Auth. Dispatcher only sees committed rows; refuses ambientTX/synchronization.

DeliveryStore uses short local JDBC transactions to discover100missing delivery rows
(current-profile snapshots first),claimSKIPLOCKED,incrementattempt and lease30seconds
with randomclaimUUID. HTTP runs AFTER claimCOMMIT/outsideDBlocks. Completion is a single
conditionalUPDATE fenced by event/token/state/unexpiredlease; late workers cannot overwrite.
Current snapshot must match persisted Seller id/owner/status/version; gaps are counted
and blocked,not synthesized. Latest-first sorting alone was insufficient: historical
versions are barred until current snapshot's delivery isDELIVERED. This includes newest
backoff/leased/blocked/exhausted/undiscovered states. New source commit after claim can
still create propagationlag; currentSeller facts remain mandatory for business rights.

Scoped Seller→Auth POST/internal/auth/seller-role-sync; #120pair guard/#122receiver reused.
Exact originaleventID/payload/version, no normalize/implicitAccount/ADMINgrant. Runtime
secret supplier supports rotation against receiver's previouscredentialdeadline.
RoleSyncClient privateTLS(loopbacktests),no redirect,shared3secondwholebodydeadline/8KiBcap,
strictUTF8/JSON4-fieldACK matchingevent/seller/canonicalversion/result; malformed/mismatched
ACK neverDELIVERED.409/permanentwire4xx→BLOCKED,no autoretry;401/403/transient/unknownACK
retry up to8attempts with1/2/4/8/16/32/64sec backoff. Exhausted→EXHAUSTED. No rawremoteerror,
credential/token/payload logged. ACK lost/crash recovered by sameevent replay/DUPLICATE;
Authversionhistory prevents stale/out-of-order grant after a newer decision.

reconcile() discovers missing durable states,expires exhaustedleases and reports
PENDING/LEASED/DELIVERED/BLOCKED/EXHAUSTED/current-snapshot-gaps. Polling claim also performs
discovery/expired-lease recovery. BLOCKED/EXHAUSTED require auditedmanual investigation;
no automatic reset,source/AuthDBediting,syntheticnewversion or blindrepair of Authgrantdrift.
Retention/operatorUI/DLQ replaytool not implemented. runOnce=true means attempted work,
NOT successful delivery; inspect durable state. Low-volume MVP tick1event/sec/instance.

RoleSyncDispatchConfiguration is opt-in hk5.seller.role-sync.dispatch.enabled=false;
requires explicit HK5_AUTH_ROLE_SYNC_URL +HK5_SELLER_AUTH_ROLE_SYNC_SECRET, no defaults.
No businessHTTP/Gateway/runtime/Finance/Inventory flags enabled. Scheduler logs static
sanitized outage only; no sourceentity/JWT authority registration introduced.

Phase6 review: #126preflight correctly rejects networkinsideTX and Houseunknown fails
closed. Five-secondproof DOES NOT detect remote revoke/statuschange between read and
write. Strict authority-valid-through-write semantics/race proof remainBLOCKED; no
claim of zero-stale write or acceptance of boundedstaleness. Need an explicit permit/
fence protocol or acceptedconsistency contract before businessmiddleware activation.
This is not waitingTV1 technicalconfirmation; user owns technical decisions. Keep APIoff.

Tests use actual SellerJpa producer +AuthembeddedHTTP +separatePGDBs. Moderation access
policy is testfixture only,not JWT/AdminHTTP acceptance. RealAuthPGlock simulates timeout
after remotecommit; processCrashError before localACK tests lease recovery. BadACK HTTP
fixture is a fault injection,not authoritativeAuth/E2E success. InitialREDmissingclasses,
latestpriority/partiallease negatives and reviewerbackoff/backlogP1RED retained, thenGREEN.
Reviewer rechecked fixedbarrier/no newfinding,source only,testsNOT_RUN independently.

Actual verification Java21/Gradle8.10/Docker29.9.1/PostgreSQL16.6:
backend/gradlew.bat -p backend :seller-service:build :auth-service:build :product-service:build --rerun-tasks --no-daemon --console=plain
Exit0/1m51s/31tasks;894PASS0FAIL2optionalSKIP:Seller237unit136PG+17roleSyncHTTPPG/Auth156+2SKIP/Product305unit38PG+5authorityHTTPPG. No localGateway/security-core suite invocation, no cross-run aggregation. LogSHA256f876f25152dc42c9ed52d3e203d533de54c9f6ad765a5b9c38d9c7b93e666f69. NewV4inventory assertions retain V1–V3/data/checksum/SQLState checks. MigrationV4 changed only while unpublishedcandidate, V1–V3bytes unchanged. Phase6strictfreshness remainsBLOCKED,not markedPASS.
