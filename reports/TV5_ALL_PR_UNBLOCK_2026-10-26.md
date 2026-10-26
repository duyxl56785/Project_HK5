# TV5 — all PR/reviewer unblock, 26/10/2026

Refs #5/#12. Deep scan hoàn tất 21:34; CLI snapshot lúc 21:41 UTC+7; formal approvals được xác minh 21:16–21:19. Main `ed85f6290de7f611c82e5c0ad973f98180f04df4`. All86 PR metadata paginated: **21 OPEN (4 Draft),63 MERGED,2 CLOSED-unmerged**. Every OPEN has files/commits/comments/reviews/threads/CI/rules read; closed/merged metadata/dependency only. [Derived roster/history/head checks](evidence/tv5-backlog-20261026/audit.json). This is not a claim of a new full security/runtime execution for every PR.

**TV5 active requests=0, effective CHANGES_REQUESTED=0, own unresolved threads=0.** Four formal APPROVED #89/#90/#91/#94 already verified at their exact HEADs; do not submit duplicates. [TV2 handoff](https://github.com/duyxl56785/Project_HK5/pull/89#issuecomment-6418300107). #89/#90/#91 still OPEN, none integrated to main.94separate. Postmerge tests **NOT_RUN/BLOCKED_BY_OWNER_MERGE**, not supplied by historical combined640. Source changed after an approval needs delta review and fresh exact-head tests before continuing.

## A. Every OPEN PR and reviewer

TV1=lexuanbao258-collab;TV2=duyxl56785;TV3=tranvanchinhbg2006-alt;TV4=phamthecuong2022006-alt;TV5=nguyenminhduy090-alt. Requested and effective reviews are distinct; COMMENT may consume a request without approving. Old effective approvals are labelled their recordedSHA, not claimed as fresh whole-delta review.

|PR|Author|HEAD|CI|Requested reviewers|Effective opinions|Next/blocker|
|---|---|---|---|---|---|---|
|[69](https://github.com/duyxl56785/Project_HK5/pull/69)|TV4|`b7e4b42`|SUCCESS|—|TV1:APPROVED@4eeeb38|TV4/TV3 chốt C06/runtime; provider candidate giữ tách V3|
|[77](https://github.com/duyxl56785/Project_HK5/pull/77)|TV3|`cbbdb86`|SUCCESS|TV4|TV2:APPROVED@cbbdb86|TV4 review compatibility Inventory|
|[78](https://github.com/duyxl56785/Project_HK5/pull/78)|TV3|`ac5da33`|SUCCESS|—|TV2:APPROVED@ac5da33|TV3 owner integration; không suy live pair|
|[80](https://github.com/duyxl56785/Project_HK5/pull/80)|TV1|`083355b`|SUCCESS|—|TV2:CHANGES_REQUESTED@dfdc814|TV2 cập nhật CR cũ dù NUL thread đã resolved; TV1 giữ Draft/runtime gates|
|[83](https://github.com/duyxl56785/Project_HK5/pull/83)|TV2|`c831edb`|SUCCESS|—|TV1:APPROVED@c831edb|TV2 tích hợp test-only theo quy trình|
|[85](https://github.com/duyxl56785/Project_HK5/pull/85)|TV2|`fc39c71`|SUCCESS|—|COMMENT/no opinionated review|TV1/TV5 human formal review khi owner Ready; trusted runtime còn chờ|
|[86](https://github.com/duyxl56785/Project_HK5/pull/86)|TV3|`28a8abc`|NO_CHECKS|TV2|TV2:CHANGES_REQUESTED@3c23319|TV2 re-review/resolve P1 đã có TV5 RED/GREEN; không thêm lời nhắc trùng|
|[87](https://github.com/duyxl56785/Project_HK5/pull/87)|TV2|`1a67b01`|SUCCESS|—|COMMENT/no opinionated review|Human independent formal review; không có finding mới|
|[89](https://github.com/duyxl56785/Project_HK5/pull/89)|TV2|`17f98f4`|SUCCESS|—|TV5:APPROVED@17f98f4, TV1:APPROVED@17f98f4|TV2 bắt đầu A, test actual main sau tích hợp|
|[90](https://github.com/duyxl56785/Project_HK5/pull/90)|TV2|`6a290ff`|SUCCESS|—|TV5:APPROVED@6a290ff, TV1:APPROVED@6a290ff, TV3:APPROVED@6a290ff|Chờ A GREEN, sync/review delta/test rồi B|
|[91](https://github.com/duyxl56785/Project_HK5/pull/91)|TV2|`4915c37`|SUCCESS|—|TV5:APPROVED@4915c37, TV1:APPROVED@4915c37|Chờ B GREEN, giữ cả Repository batch+search, test rồi C|
|[92](https://github.com/duyxl56785/Project_HK5/pull/92)|TV5|`67a802b`|NO_CHECKS|—|TV2:APPROVED@67a802b|TV1 tiếp nhận docs vào Auth branch; không main/runtime|
|[93](https://github.com/duyxl56785/Project_HK5/pull/93)|TV5|`dd69f3d`|NO_CHECKS|TV2|TV2:APPROVED@bffe12d|TV2 review delta QA/evidence sau bffe; request hiện có giữ nguyên|
|[94](https://github.com/duyxl56785/Project_HK5/pull/94)|TV2|`96d59ca`|SUCCESS|—|TV5:APPROVED@96d59ca, TV1:APPROVED@96d59ca|TV2 tích hợp riêng; chưa có remote House/current Auth adapter|
|[95](https://github.com/duyxl56785/Project_HK5/pull/95)|TV2|`53c2a1b`|NO_CHECKS|—|TV1:APPROVED@53c2a1b|TV2 integration riêng; Auth/shared host/HTTP chưa nghiệm thu|
|[97](https://github.com/duyxl56785/Project_HK5/pull/97)|TV2|`aa790f4`|SUCCESS|—|COMMENT/no opinionated review|Human whole-PR decision; sau89 retarget/test, không chèn vào A/B/C|
|[98](https://github.com/duyxl56785/Project_HK5/pull/98)|TV4|`d53e2a9`|SUCCESS|—|COMMENT/no opinionated review|TV4 Draft,3 unit mới TV5 PASS; helper chưa endpoint/runtime|
|[99](https://github.com/duyxl56785/Project_HK5/pull/99)|TV5|`446ae44`|SUCCESS|TV3,TV2,TV4|COMMENT/no opinionated review|TV2/TV3/TV4 review; source unchanged, không finding để sửa|
|[100](https://github.com/duyxl56785/Project_HK5/pull/100)|TV5|`8bf9f12`|NO_CHECKS|TV2|COMMENT/no opinionated review|TV2 review; TV1 COMMENT không finding; CI_NOT_CONFIGURED|
|[101](https://github.com/duyxl56785/Project_HK5/pull/101)|TV1|`e45cede`|SUCCESS|—|COMMENT/no opinionated review|TV1/TV2 nhận95 PASS/2 SKIP; ACTIVE P1 fixed, chỉ bootstrap source|
|[102](https://github.com/duyxl56785/Project_HK5/pull/102)|TV1|`a4d6360`|SUCCESS|TV2|TV2:CHANGES_REQUESTED@62d7a9a|TV2 re-review P1 Gateway trêna4;22 GatewayPASS, không tự resolve thread|

Main rules require1approval+resolvedthreads, forbid deletion/non-fast-forward; no named required reviewer/statuscheck or code-owner rule in the current ruleset. CONTRIBUTING still requires affected-owner review. API `mergeableState=clean` is server metadata, not runtime readiness/owner authorization. Legacy protection endpoint404 does not mean rulesets absent. No rules bypass or reviewer removal restoration performed.

## B. Actual new test execution — no totals across snapshots

|Exact source|New invocation|PASS|FAIL/error|SKIPPED / limit|
|---|---|---:|---:|---|
|101/e45cede|Auth test +explicit current-authority PG|95|0|2environment-gated tests;62non-PG+28AuthPersistencePG+5explicitPG PASS|
|102/a4d6360|Auth test +explicitPG +Gateway test|137|0|2environment-gated tests;82Authnon-PG+28AuthPersistencePG+5explicitPG+22Gateway PASS|
|98/d53e2a9|Focused Store ownership helper|3|0|0;pure unit, not authenticated Store query|
|100/8bf9f12|Offline CLI unittest suite|27|0|0;fake API fixtures, not runtime application tests|
|100/8bf9f12|Live read-only GitHub collector|21/21complete metadata records|0 read errors|Not21application tests; exit0|

All commands/UTC/time/exit/cases/raw hashes: [101](evidence/tv5-backlog-20261026/auth101.json), [102](evidence/tv5-backlog-20261026/auth-gateway102.json), [98](evidence/tv5-backlog-20261026/store98.json), [offline](evidence/tv5-backlog-20261026/audit-offline.json), [live](evidence/tv5-backlog-20261026/audit-live.json). Java21.0.7/Gradle8.10/Docker29.8.1/PostgreSQL16/Testcontainers; process-only UTC/api.version1.44; disposable fixtures only, no existing volume/data deletion. Raw logs/XML stay private and byte/hash preserved. Derived summaries contain no raw Bearer, password, signing key or credential. Test fixtures, not production account data.

Commands from101/102 exact checkout: `backend/gradlew.bat -p backend --init-script <checkout>/tests/security/auth-current-authority.init.gradle -Ptv5AuthAuthoritySources=<checkout>/tests/security/auth-current-authority :auth-service:test :auth-service:tv5CurrentAuthorityRegression --rerun-tasks --no-daemon --console=plain`;102also`:api-gateway:test`.98uses`:order-service:test --tests *FulfillmentStoreOwnershipTest`.100uses`python -m unittest discover -s scripts/qa/tests -v` and read-onlyCLI. Argument arrays in receipts are authoritative.

Each Auth invocation skipped `DatabaseReadinessTest.migrationProfileMigratesWithSeparateRoleAndRuntimeJpaValidates` (TV1_DATABASE_TESTS unset; requires separately provisioned app/migrator environment), and `EmailInventoryTest.existingDatabaseInventoryIsReadOnly` (AUTH_EMAIL_INVENTORY_JDBC_URL unset; no authorized deployed legacy-data inventory). These are **NOT_RUN**, never counted PASS. AuthPersistence/explicit authority use separate real ephemeral PostgreSQL and did run. Do not infer deployed status/email inventory from Testcontainers.

## C. Finding disposition and concrete owner handoff

|PR/finding|Disposition|Actual evidence / remaining authority|
|---|---|---|
|80 old TV5 lazy-role P2|FIXED_VERIFIED/resolved previously|Do not reopen.96nowMERGED to80@083355b (feature, notmain).|
|80 TV2 NUL P2|FIXED_VERIFIED by TV2 on actual parent083355b; thread resolved|TV2 exact-parent58PASS receipt in original thread; source matches96. Its formalCHANGES_REQUESTED@dfdc814 still effective; only TV2 may update own decision. Not a remaining source defect.|
|86 TV2 stale A→B confirmation P1|FIXED_VERIFIED by earlier TV5 actual28a8abc25GREEN/typecheck/build; original RED22/3 retained|No source change/new rerun. TV2 owns effectiveCR+open thread disposition; existing handoff remains, no duplicate request/comment. No browser/Auth/E2E acceptance.|
|101 ACTIVE-only bootstrap P1|FIXED_VERIFIED technically by new TV5 execution|Owner policy v1.1 DECIDED; exact operational ACTIVE enforced and27startup/negative/keyring cases PASS. TV2 already COMMENT-verified at e45. Do not ask TV1 to re-decide ACTIVE; rollout legacy inventory/secrets/release remain blocked.|
|102 constructor and/me role projection|Previously FIXED_VERIFIED/resolved by TV2; current source retains fixes|Current owner unit/MVC controls PASS; TV2's real-context/PG probes remain other-reviewer evidence, not executed here. No reopening of closed findings.|
|102 Gateway forwarding P1|FIXED_VERIFIED in current bounded Gateway transport test by TV5; WAITING_TV2_DISPOSITION|Currenta4fix Gateway-onlyFramework6.1.15 (Authstill6.1.14), unchanged outbound header policy.22GatewayPASS includes18real embedded Netty/loopback tests:5positive routes,9spoofheader stripping, Bearer/Origin/CSRF/cookie/body preservation, invalid/missingJWT/unknownpath/wrongmethod/CORS negatives. CI exacta4SUCCESS. Original TV2 RED/NoSuchMethodError@62d is historical owner evidence, not a TV5 RED run. Only TV2 may resolve its P1/changeCR.|
|99 Finance durability|No actionable finding or new delta|TV2/TV3/TV4 requested, no response yet. Exact446/109unit+29PG+3originalFINdiagnostics evidence retained/hashverified; currentCI SUCCESS. Runtime here NOT_RUN; no productionOrder edit/test duplication.|
|100 CLI/security/CI|No actionable finding in unchanged source|TV1 COMMENT no defect at8bf;TV2request active.27fresh offlinePASS +21metadata complete. No required toolingCI in rules/workflow; **CI_NOT_CONFIGURED**, notSUCCESS. No needless shared workflow edit. HTTP access/rate-limit failures fail closed with sanitized error/exit2, not empty successful audit.|
|93 QA/RTM/checklist|Historical waiting labels require dated superseding receipt|Append current formal approvals/new101/102/98 QA and owner statuses, keep history/signedpacket/rawlogs. TV2requested for delta; stale bffe approval not current-delta acceptance.|

101/102 COMMENT review evidence is a scoped QA handoff, **not APPROVED**. No agent approval beyond four directly authorized PRs, no resolving other-reviewer threads. Current service-pair/currentRoles baseline exists, but SELLER durable versioned role-sync/current downstream principal, policy binding and frontend/Gateway integration remain separate owner delivery. Gateway→dummy upstream is real transport, **not Gateway→realAuthDB→browser liveE2E**. Single-instance throttle/proxy trust, secrets/status inventory, live securitychain/currentprincipal, User provisioning delivery/reconciliation need TV1/consumers before enablement. Do not ask owner to redo already fixed NUL/ACTIVE or FIN01/06.

## D. Work and remaining owners

|Priority|Task|Owner / blocked consumer|Completion condition|
|---|---|---|---|
|P0 completed|TV5 formal89/90/91/94 decisions|TV5 →TV2|ActualAPPROVED/readback at17f/6a/491/96d; complete, no duplicate.|
|P0 owner action|Actual89→test→90→test→91 integration;94separate|TV2|Normal merges/finalHEADCI/delta review+newmain Product PG/unit aftereach; stop onFAIL. No currentmerge byTV5.|
|P1 owner action|80effective oldNULCR +102GatewayP1 disposition|TV2 +TV1 Auth/Gateway|TV2 review083/a4 evidence, update own formaldecision/thread; retain genuine releasegates.|
|P1 owner action|86P1 decision|TV2 +TV3|Review unchanged28a/TV5publishedRED-GREEN, resolve ownthread/changeCR.|
|P1 contracts/runtime|C01/E01v0.2, concrete authenticated pairs/currentactor/House, MD07freshness; C06durable owner before first send/UNKNOWN recovery|TV1+TV2+TV3+TV4; Finance/business as applicable|Versioned owner/consumer signoffs+implemented providers+real per-pair tests, no blindrelease/keyreplacement.|
|P2 review|99,100,93|99TV2/TV3/TV4;100TV2;93TV2|Existing requests answered; no fakecodechanges to force review, no duplicates.|
|P2 review|85/87/97/98|Affected humans/owners|Bounded technicalQA available, formalapproval/readiness per scope;97retarget/testafter89;98Draft purehelper only.|
|P1 business|Full M01/MD04–10/CODPAID/DELIVERED/refund/settlement|Named approver/TV1projectowner +TV3/TV4/TV5|Written scoped signatures/operator authority, source/version/action/event/audit/replay guarantees. Productprice MD01/02/unitMD03 alreadyapproved, not reblocked.|

|Member|What TV5 delivered now|Still outside TV5 authority|
|---|---|---|
|TV1|New10195 PASS/2 SKIP and102137 PASS/2 SKIP; current Gatewayfix validated, no owner source edit|Runtime/principal/role-sync/servicepair/proxy/distributedthrottle/secrets/legacyinventory/FE CI; owner source fix & release decisions.|
|TV2|Four formal APPROVED currentheads; snapshot/evidence/scopedQA; no stale Productprice gate|Actual sequential integration/tests,80/86/102ownCR disposition,99/100/93pendingreviews, production provider/House boundaries.|
|TV3|99twoStorefixture/4PGexisting; FIN01/06remaintechnicalGREEN, Catalog private source approved|Order/Catalog runtime/currentfacts/MD07; C06T1anddispatch/reconciliation/COD boundary withTV4;99ownerreview.|
|TV4|983unitactualPASS, exactStoreguard limit; Inventorycandidate core unchanged4ee→b7 (only two Inventory docs), oldsourceevidence retained honestly|Provider/authenticated operation tuple/SellerOrder dispatch/cancel/UNKNOWN/CODproducer/outbox, contractsignoff;99review.|

Full M01 **PARTIAL/PENDING**, Finance production **BLOCKED**, flags OFF/unchanged. Productprice #84business+LevelA+TV2mapping+#88LevelB/merge remainvalid. No new business signature or atomic settlement/API/currentStorequery claimed. COD authority cannot be inferred from InventoryCONSUME/notification or testfixture. No new independent TV5 code defect/contract-approved missing implementation identified: new work is actualQA+review/evidence publication; do not invent redundant tests/PRs. Continue on new source/review/approved contract changes, otherwise WAITING_OWNER/REVIEW.

- [x] Paginated86PRmetadata/21 OPENdeep review, all reviewer states and default/request-removal rules checked.
- [x] Four scoped formal APPROVED verified, owner sequential checklist/handoff posted; no duplicate reviews.
- [x] New101/102/98tests and100offline/live executed, logs/XML preserved, source/hash/counts reconciled.
- [x]99unchanged source/CI/raw hashes,100review/security/optionalCI,93docs/RTM/checklist/Finance ledger checked.
- [ ] TV2/TV1/TV3/TV4 remaining reviews and actualpostmerge/runtime/business gates; Issues #5/#12 OPEN/PARTIAL.


## E. Late owner feedback — readback after publication, 26/10/2026

This section supersedes only the initial snapshot's pending-request/open-thread labels. Source heads and TV5 test results above remain unchanged. [Owner receipt/links](evidence/tv5-backlog-20261026/late-owner-receipt.json).

- **#86:** TV2 independently verified current28a (25suitePASS/typecheck/build plus separate8probe with3overlap) and [resolved its original P1](https://github.com/duyxl56785/Project_HK5/pull/86#discussion_r4378583543). No open threads/request now; **older formalCHANGES_REQUESTED remains effective**, not replaced by COMMENT.
- **#102:** TV2 independently verified currenta4 (152PASS/2SKIP in its own invocation incl15privateGatewayprobes), and [resolved its last GatewayP1](https://github.com/duyxl56785/Project_HK5/pull/102#discussion_r4378581969). All3TV2threads resolved. **Older formalCHANGES_REQUESTED@62d remains effective**; only TV2 may change its decision under its review scope. No duplicate fix/retest/thread-resolution request is needed. TV5's later COMMENT receives an appended correction because its first wording used the earlier thread snapshot; original text/history is preserved.
- **#99:** [TV2 COMMENT at current446](https://github.com/duyxl56785/Project_HK5/pull/99#pullrequestreview-5653851066) reports independent141PASS/no new defect. Remaining requested reviewers are **TV3 and TV4**; not an unanswered TV2 request. Formal review still REQUIRED.
- **#100:** TV1 and TV2 now both COMMENT at unchanged8bf; TV2 reports27offlinePASS/no defect. No requested reviewers remain; formal review still REQUIRED. Preserve source reviews, do not re-request just because COMMENT consumed the request. CI_NOT_CONFIGURED remains accurate.
- **#93:** TV2 COMMENT verifieddd69, then TV5 publishedd925 with11newdocs/evidence paths. TV2 re-requested **once for that substantial delta**, no removal event. This later receipt-only correction retains the active request without another notification. Old bffeAPPROVED is not current-delta review.
- **Final metadata readback:**86total/21OPEN/4Draft/63MERGED/2CLOSED-unmerged; no currentCI FAIL, no conflict, no activeTV5request/CR/ownthread. FormalCRPRs80/86/102 remain distinct from resolved findings.89/90/91notmerged;94separate. TV2 owns nextintegration/decision; issues5/12 remainOPEN/PARTIAL and FinanceBLOCKED.

TV2 case counts above are **other-member reported evidence**, not new TV5 executions, not independently raw-hash-verified by TV5 and not summed with TV5/CI or across snapshots. No source code, tests, flags, policy signatures or other-reviewer decisions were edited for this correction.
