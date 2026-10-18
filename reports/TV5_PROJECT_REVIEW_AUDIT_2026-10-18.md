# TV5 — kiểm toán toàn bộ GitHub reviews

**Checkpoint REVIEW-AUDIT-20261018-r4. Refs #5/#12/#19/#28/#37/#39/#41/#42/#46/#47/#48.**
Đây là audit review/QA, không phải approval, merge hoặc chữ ký nghiệp vụ. Account đã verify: **nguyenminhduy090-alt (TV5)**.

## A. Phạm vi và toàn bộ PR OPEN

Đã khám phá **33PR:4OPEN,29MERGED**, gồmPR49xuấthiệntronglúcaudit. REST phân trang toàn bộ reviews, inline/conversation comments,
commits/files/timeline của33PR; GraphQL đọc reviewDecision, latestOpinionatedReviews và toàn bộ16threads (pageInfo kiểm đầy đủ).
Đọc16Issue và comments, gồm #1–#5/#12; đọc ruleset đang áp dụng. **0unresolved threads**, không có thao tác resolve/dismiss.
Đọc source liên quan finding đang mở; không nhận đã re-review line-by-line mọi file lịch sử của33PR. CácPRmở đượcrefreshsauheadupdates.

**119review events lịch sử:**83COMMENTED,22APPROVED,14CHANGES_REQUESTED,0PENDING được API trả về.
TV5:28review events (22COMMENTED,3APPROVED,3CHANGES_REQUESTED), tổng61review/inline/conversation activities trên PR.
Draft review chưa publish của người khác không hiện qua account này: không tuyên bố không có draft riêng tư.
[Full review event ledger](evidence/project-review-20261018/review-events.csv) lưu từng reviewer/state/timestamp/SHA/body/URL và head-match;
[TV5 activities](evidence/project-review-20261018/tv5-activity.csv), [derived API snapshot](evidence/project-review-20261018/github-review-snapshot.json).
Các ledger là snapshot **trước** những COMMENT review của TV5 trong lượt bàn giao này.

| PR / author | Head / base / draft | Reviews hiệu lực tại scan | Unresolved | CI / mergeability | Blocker và next action |
|---|---|---|---:|---|---|
| [28](https://github.com/duyxl56785/Project_HK5/pull/28) TV3 tranvanchinhbg2006-alt | **c1816752ae4436453c8fc557f2394a67f445b8f2**, main, Ready | **CHANGES_REQUESTED** TV1@8cc5ce4; TV2 APPROVED@bd5ff09. COMMENT sau đó không xóa quyết định này |0| readiness success; mergeable=true/**blocked**, không còn conflict | FIN01/06 verifiedGREEN tạibe1 vàrerunc181; **humanTV1 cập nhật quyết định** sau reviewfix. Integration/business gates còn độc lập |
| [39](https://github.com/duyxl56785/Project_HK5/pull/39) TV4 phamthecuong2022006-alt | **ec2ae5af5162aa93482b9439d1b39dbb7abb3bec**, main, Ready | **CHANGES_REQUESTED** TV5@bf13732; TV1 APPROVED@ec2ae5a chỉ draftdoc. TV2/TV5 COMMENT không đổi blockingdecision |0|0checks, mergeable=true/**blocked**| Sốcase/IDfinding đã verifiedfixed; **humanTV5 quyết định lại scope docs**, không ký C06/M01/runtime |
| [48](https://github.com/duyxl56785/Project_HK5/pull/48) TV1 lexuanbao258-collab | **c0b485aa62ee3275fe56053bdc40497aedbd3a73**, main, Ready | **REVIEW_REQUIRED**; TV2/TV3 COMMENT tạiHEAD; pending requests TV5+TV4 |0|0checks, mergeable=true/**blocked**| TV5 QA COMMENT trong đợt này; TV4 cần phản hồi consumer/operation scope; GitHubapproval do con người quyết định |
| [49](https://github.com/duyxl56785/Project_HK5/pull/49) TV4 phamthecuong2022006-alt | **cf6028168563578e9ab38556d24a9e536b818a49**, main, Ready theoAPI | **REVIEW_REQUIRED**,0reviews tạirefresh trướcTV5handoff; TV1/TV3requested |0|readiness đang chạy tạirefresh; mergeable=true/**blocked**| ScopedInventorypairproposal; testwrappercast tại5cf đãownerfixcf60281,5targetedunitPASS; HTTP/runtime/currentauthority gates vẫnchờ |

**Ruleset protect-main thực tế:** cần1approval và resolvedthreads; dismiss_stale_reviews_on_push=false,
require_last_push_approval=false, require_code_owner_review=false; không có required-status-check rule trong response.
Classic branch protection endpoint trả404; **không suy main không được bảo vệ** vì ruleset active đã đọc được.
Vì stale dismissal=false, không tự gọi approval cũ là đã bị GitHub hủy; nó vẫn là review lịch sử/có thể được server tính.
Nhưng approval ởhead cũ **không chứng minh code mới đã được kiểm**, và CHANGES_REQUESTED khác vẫn chặn, theo reviewDecision server.
Chính sách ownership trong CONTRIBUTING vẫn áp dụng dù server không bật code-owner rule. Không bỏ qua protection.

### PR đóng/merge liên quan và trạng thái đã sửa

- **19@804d6f1:** đã MERGED. **TV5 đã APPROVED đúngHEAD** tại [review5561505938](https://github.com/duyxl56785/Project_HK5/pull/19#pullrequestreview-5561505938), TV1 cũngAPPROVED.
  Không còn TV5CHANGES_REQUESTED hiệu lực ởPR19; đừng yêu cầu approve/merge lại PRđóng. Sourceplan HS256/keyring, #7baseline/F15/F16 đã đối chiếu.
- **37@ef62086 và38@7a8d18e:** mới mergedmain; design/source/W1 acceptance không tự ký Catalog/M01/Inventorylive.
- **41@aaf9d31,42@35297ac,43@89cd293,44@1311f8a,45@9eb4ba0,46@e597daf,47@a879b08:** đã integrated vào28; ancestry được kiểm riêng.
  PR42/46/47 không có review mới ởscan, không còn chờ tiếp nhận; khôngpush/request branchđóng.
- Phần còn lại cũng được scan: **6,7,20,21,22,23,24,25,26,27,29,30,31,32,33,34,35,36,40** đềuMERGED.
  Review lịch sử vẫn ởCSV; không biến oldCHANGES_REQUESTED củaPRđóng thành activeblocker. Lượt này không chạy lại mọi suite củaPRđóng.

## B. Finding/review cần xử lý theo ưu tiên

| PR / reviewer / evidence | Finding | Disposition mới | Owner / next action |
|---|---|---|---|
|28 TV1 [CR5563289579](https://github.com/duyxl56785/Project_HK5/pull/28#pullrequestreview-5563289579)|FIN01 precision/replay, FIN06 parentoverflow trướcRESERVE|**FIXED_VERIFIED** tạibe1d5b6, sourcefix5f2ada8 +originaldiagnostic3PASS; GitHubdecision **NEEDS_APPROVAL**| HumanTV1 reviewactualfix/evidence, quyết định lại; TV3 không cần sửa lại hai finding đãGREEN |
|39 TV5 [CR5561477514](https://github.com/duyxl56785/Project_HK5/pull/39#pullrequestreview-5561477514)|19vs21, IDđổi nghĩa, thiếu SKU/currentfacts/stateproposal|**FIXED_VERIFIED (staticdocs)** tại ec2ae5a:21legacyexact +21supplemental,42NOT_RUN; **NEEDS_APPROVAL**| HumanTV5 cập nhật quyết định trong phạm vi proposal/QA nếu đồng ý; không agentAPPROVE |
|28 TV5 [JWTthread](https://github.com/duyxl56785/Project_HK5/pull/28#discussion_r4291061468)|YAML/envHS256 chưa bind|**FIXED_VERIFIED**, resolved vẫn giữ; actualJWTenv/decoder tests tạibe1 nằm trong88unitPASS; noJWKSfallback|Không mở lại nếu không regression; localdecoder không là positiveAuth/Gateway |
|28 TV2 [replaythread](https://github.com/duyxl56785/Project_HK5/pull/28#discussion_r4293244875), [scannerthread](https://github.com/duyxl56785/Project_HK5/pull/28#discussion_r4293244889)|Lazy replay/scannerlostupdate|**FIXED_VERIFIED** tạibe1, actualPGReplay4+Scanner4PASS, threadsresolved|Khôngresolve lại/đòi sửa lại; testmockremoteports không liveInventory |
|19 TV5/TV1 [finalTV5review](https://github.com/duyxl56785/Project_HK5/pull/19#pullrequestreview-5561505938)|JWKS/HS256 và#7checkpoint|**FIXED_VERIFIED docs**, oldgate **OUTDATED** vì đãAPPROVED+MERGED|Không cần humanTV5 approve lần nữa |
|48 TV1, [TV2review](https://github.com/duyxl56785/Project_HK5/pull/48#pullrequestreview-5563376836), [TV3review](https://github.com/duyxl56785/Project_HK5/pull/48#pullrequestreview-5563408894)|S01/servicepair cụ thể|**NEEDS_OWNER / BLOCKED runtime**, có designconfirmationTV1+TV2+TV3|TV4 currentauthority/operationconsumption; owners implement/testpair; không báo TV1chưa trảlời |
|37/46 M01|Productprice MD01/02/unitcapMD03 humanapproval|**NEEDS_OWNER / PENDING BUSINESS APPROVAL**, technicalcompatibilityTV2 retained|Chủdựán chỉđịnhbusinesssigner, humanTV5+requiredowners kýslice+schema/S01/security; MD04–10 không tự chặnprice slice |
|39 C06 / TV4 [conditionalInventoryresponse](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6250742358)|State/event/CONSUME/retry/unknown recovery|**BLOCKED / REVISE sharedcontract**; TV4ownscope APPROVEDWITHCONDITIONS không làfullapproval|TV3/TV4 chốt state/event vàdeadline includingnetworktime. Nêu rõ atomic **localintent/status/outbox**, remoteInventory saucommit, không cross-service atomicity |
|39 currentboundary paragraph|Câu "currentbaseline Order scaffold/noendpoints/migrations" thiếuSHA, lỗi thời so với28đã cóOrderAPI/DB|**NEEDS_OWNER**, doccleanupnonblocking, không phải hồi quy nghiệp vụ|TV4 ghi rõ baseline/đổi thành fulfillment/COD chưaimplemented. TV5 không sửa fileowner |
|49 TV4pairgate, testline25 tại5cf4d75|ReplayableBodyRequest bịcastMockHttpServletRequest, ClassCastException làmpositive-pathFAIL|**FIXED_VERIFIED** sauownerfixcf60281;4P1F→5P0F, giữpath/bodyassertions. Khônglàproductionauthbypass|TV1security+TV3callerreview; noprovision/enabletoken, nocompleteSPacceptance. TV5đãdừngcommentcũkhipháthiệnheadđổi |
|ClosedPR23/26/27/31 threads|Các thread đãresolved lịch sử|**FIXED_UNVERIFIED cho current runtime** nếu không có test mới tronglượt này; không tự tuyên bố regression|Đã đọc nội dung/resolution/history; không phát hiện regression cụ thể từ audit, không suy allruntimePASS |

Mỗi review event ởCSV giữ nguyên body/state/time/SHA và latest-opinionated flag; event COMMENT có thể chứa technicalacceptance nhưng
không thayGitHubAPPROVED/CHANGES_REQUESTED. Không xóa oldreviews để làm đẹp. Không có regression mới được tái hiện tại OrderHEAD này.

## C. Contract sign-off và quyền triển khai thực tế

| Contract / scope | Owner / sign-off mới | Evidence | Remaining gate |
|---|---|---|---|
|S01 architecture/schema boundary|**TV1CONFIRMED**, **TV2CONFIRMED ownscope**, **TV3CONFIRMED consumerdesign**|[PR48 c0b485a](https://github.com/duyxl56785/Project_HK5/blob/c0b485aa62ee3275fe56053bdc40497aedbd3a73/docs/TV1_S01_OD09_INTEGRATION_HANDOFF_2026-10-18.md), TV2/TV3reviewlinks ởB|TV4consumer/currentauthority vàruntimeverification. TV1 choTV2 bắt đầu reviewedStoreV2+/JPA/currentread/negative tests; **không phảipricepolicy/providerlive** |
|OD09 exactpairs design|TV1CONFIRMED intent; TV3Ordercaller accepts constraints, TV2Catalogproviderdesign; TV4PR49isolatedfilterproposal|SamePR48; [Issue12handoff](https://github.com/duyxl56785/Project_HK5/issues/12#issuecomment-6250970439)|PR49targetedfilterunitsverifiedcf60281, chưaHTTP/deployment/provision/rotation/delegation/currentactor;Auth→User/Seller→Auth endpointTBD; no broadInventorytoken |
|C01/E01 catalog.checkout.v1 v0.2|TV2provider/TV3consumer shape acceptedownscope; **overallREVISE**|[TV3specificreply](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6248646211)|Exactboundary/privacy/authority/wireimplementation/freshness; sourceenum/SQLcandidate khôngprovider |
|M01 partialProductprice|MD01+MD02+**unitcap**MD03, **PENDING BUSINESS APPROVAL**|e597dafpacket +humanTV5 instruction trongphiên; [TV2compatibility](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246199255)|Namedbusinesssigner+humanTV5/requiredparties, approvedschema/S01/security. VNDpositive17digits/exactstrings/noSilentRound làcompatiblecandidate, qty1–1000 làfact |
|MD04–MD10Finance|**PENDING**, theo dõi độc lập; không tự buộcPricecolumnchờreportingMD10|[Packet](../docs/TV5_M01_HUMAN_DECISION_PACKET.md), [TV3MD05/06/08engineering](https://github.com/duyxl56785/Project_HK5/pull/28#issuecomment-6249713586)|Businessfee/House/stage/adjustments/refunds/race/snapshot/enable/reporting decisions, noflag |
|Inventory/C06|TV4ownscope APPROVEDWITHCONDITIONS targetSKU/operationreplay, **sharedC06REVISE/InventoryPENDING**|TV4response ởB +PR39proposal|TV3/TV4 durableT1/T2/systemprovenance/dispatch/state/retry; ProductSKUenforcement/source; no liveE2E |
|Financeproduction/COD|**BLOCKED**, OD07principlesremainDECIDED|ExistingFinanceRules +packet+TV1handoff|Authoritativepayment/delivery/currentADMIN, immutablecurrency/rate/House/net/policy, atomicuniqueconcurrentsettlement/currentStorequeries. Pureguards khôngDBsettlement |

Không có MD businessAPPROVED mới. **Businessapprover/operator: chờ chủdựán/TV1 xác nhận account, vai trò, scope**.
S01/security design có tiến triển thực sự và mở một số ownerimplementation **tách biệt**, không vì thế mở Productmoney hoặcFinance/liveCheckout.

## D. Kiểm thử và việc thực hiện

### Actual TV5 execution: be1d5b6c1a6eafa385f2bd6bd62f8b5efbeb5757

Java21.0.7/Gradle8.10/DockerLinux/PG16Testcontainers; UTC18/10/2026 **14:42:24–14:43:24**,60s, exit0.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:JAVA_TOOL_OPTIONS='-Duser.timezone=UTC -Dapi.version=1.44'
$initTV5=(Resolve-Path 'tests/finance/fin06-regression.init.gradle').Path
backend/gradlew.bat -p backend --init-script $initTV5 :order-service:test :order-service:postgresIntegrationTest :order-service:fin06Regression --rerun-tasks --no-daemon --continue --console=plain
```

| Task / evidence | PASS | FAIL / ERROR / SKIP | Scope |
|---|---:|---|---|
|[Orderunit](evidence/project-review-20261018/test/TEST-com.projecthk5.orderservice.service.CheckoutServiceTest.xml)|**88**|0/0/0|12suites, Money/JWT/claim tests; mocks khôngactualpair |
|[PostgreSQL](evidence/project-review-20261018/postgresIntegrationTest/TEST-com.projecthk5.orderservice.finance.FinanceSnapshotPostgresIT.xml)|**16**|0/0/0|Finance6,Replay4,Scanner4,Order2; actualDBtransactions |
|[OriginalFINdiagnostic](evidence/project-review-20261018/fin06Regression/TEST-com.projecthk5.orderservice.finance.CheckoutMoneyPreflightRegressionTest.xml)|**3**|0/0/0|FIN01+FIN06+positivecontrol; remoteports/syntheticpolicy mocked |
|Catalog/Inventory/Cart suites tronglượtTV5này|NOT_RUN|—|Không delta runtime cần chạy lại trongscope; không lấy ownerrun thayTV5run |
|Pair/currentStore/COD/settlement/liveE2E|NOT_RUN/BLOCKED|—|Xem [securitymatrix](../tests/TV5_SERVICE_PAIR_QA_MATRIX.md), khôngfixturethayprovider |

[Rawlog](evidence/project-review-20261018/runtime.log), [command/source/time/exit](evidence/project-review-20261018/runtime-meta.json),
[22rawartifact hashes](evidence/project-review-20261018/manifest.json). Source diagnostic blob **bbbaf2861fbe35d56b32a565ea5a3bb0200a4df4**
identical8cc5ce4↔be1d5b6. Lượt8cc1P2F/log/XML giữnguyên; **nay3PASS** sauownerfix, không đổi assertion/disabletest.
NoInventory/writer cho knowninvalid; Cartrelease+FAILED, Order/outboxzero; positivecontrol300.00/fee5.00/oneorder+event/reserve/write/replay giữPASS.
Storageexactness fix độc lập với chưa kýVND/caps/fees; không gọi fullFinanceDONE.

**Tách evidence owner:** [TV2 be1run](https://github.com/duyxl56785/Project_HK5/pull/28#pullrequestreview-5563412980) báo117PASS gồmCart9unit/1PG+Order88unit/16PG/3diagnostic.
TV5 không cộng117 với các sốởtrên, khôngclaim đãverifyrawprivateTV2. DockerblockedTV2run tại5f2ada8 làhistoricalNOT_RUN choPGtestbodies,
không6businessregressions; TV5Docker chạyđược ởlượt mới, không sửaDockerconfig/xóa volumes.
InitialDockerVolumes0missing; originalbytes giữ `-text`, publicationmarkerscannohits (khôngexhaustive).
Staticverification:82relative links hợp lệ,21legacyrows exact+21supplementalNOT_RUN,0nonblankmainQAlinesmissing,
PR41/42/46/47ancestorchecksPASS; backenddelta none. Code/docs diffcheckexit0; generatedraw diffcheckexit2 dooriginalCRLF/trailingwhitespace,
khôngsửarawlog/XML. [Derivedstaticchecks](evidence/project-review-20261018/static-checks.json) khônglàruntime/sign-off.

### Delta runs sau khi owner cập nhật trong lúc audit — không cộng snapshots

| Exactsource / command / time | Actual result | Ý nghĩa |
|---|---|---|
|**c1816752ae4436453c8fc557f2394a67f445b8f2**; Ordertest filtered `--tests '*CheckoutServiceTest'` +sameexplicitfin06Regression init/task; UTC14:55:03–14:55:43|**16targetedunitPASS +3originalPGdiagnosticPASS**,0fail/error/skip,exit0/40s|TV3 pre-serializeoutboxpayload trướcRESERVE, testedserializationfailureknownsafe. **Khôngrerun fullOrderunit/16taggedPGởc181**, chúngthuộclượtbe1. [metadata](evidence/project-review-20261018/c181675/runtime-meta.json)|
|**5cf4d753b3723f46b76786061c62de7a8806cf79**; Inventorytest filtered `*InventoryOrderPairGateTest` +`*InventoryInternalTokenFilterTest`; UTC14:55:15–14:55:31|**4PASS/1FAIL**,0error/skip,exit1/15s|ActualClassCastExceptionwrappertest, khôngbáoauthbypass; [originalREDXML](evidence/project-review-20261018/pr49-red/test/TEST-com.projecthk5.inventoryservice.security.InventoryOrderPairGateTest.xml)|
|**cf6028168563578e9ab38556d24a9e536b818a49**; same2Inventoryclasses, originalpath/bodyassertions preserved|**5PASS/0FAIL/error/skip**,exit0/13s|OwnerchangedcasttoHttpServletRequest, not weakerassertion; [GREENXML](evidence/project-review-20261018/pr49-green/test/TEST-com.projecthk5.inventoryservice.security.InventoryOrderPairGateTest.xml), [metadata](evidence/project-review-20261018/pr49-green/runtime-meta.json)|

PR49POST5cfheadcheck chặn mộtcommentfindingcũ trước khi gửi; sauđóđãđọcownerfix/rerun, khôngđăngbugđãsửa nhưOPEN.
35rawassets gồm22be1+4c181+4InventoryRED+4InventoryGREEN+finalDockerinventory; manifest phânlượt/SHA, khôngrelease-totalcộngdồn.
PR49InventoryPG/positiveHTTP/liveE2E **NOT_RUN**, noDBdelta cầnchạylạitrongtargetedfilterreview.

### Các phần đã hoàn thành

- [x] Scanall33PR/119reviewevents/16threads/16Issues; refresh4openPRs, phân biệt effectivegate vàhistoricalevents, verifiedaccounts/rules.
- [x] ActualOrderunit/PG/originaldiagnostic trênownerfixSHA; giữRED/GREENprovenance, resolvedJWT/replay/scanner không bị mở lại.
- [x] RecheckPR19alreadyapproved/merged;PR37/38/41/42/46/47integrated; khôngpush/requestPRđóng.
- [x] UpdatecanonicalcurrentM01/QA/RTM +scopedpairsecuritytestmatrix, giữpartialpricegate/businessauthority.
- [ ] HumanTV1 decisionPR28; humanTV5 decisionPR39; TV4consumerreviewPR48; ownercontract/runtime/businessgates.

GitHub COMMENTreviews/evidencehand off/checklistpublication dùng marker này saucommit/push; actualURLs được ghi trong Issue/PR để tránh self-referencecommit.
Branch **codex/tv5-project-review-audit**, base=actualPR28branchbe1; không sửaproduction/ownerfiles/config/V1/tests/assertions.
Ownerc181 đượcnormalmerge đểbranchbàn giao giữnewsource; testc181làtargetedrunriêng, khônggán88unit/16PGbe1choheadmới.

## E. Việc tiếp theo và reviewer actions

| Người / priority | PR / link | Việc cần làm | Điều kiện đóng blocker |
|---|---|---|---|
|HumanTV1 P1|28 CR5563289579 +evidenceD|Reviewownerfix+originalGREEN, cập nhật CHANGES_REQUESTED khi đủscope; giữintegrationgates riêng|Officialreviewdecision đúnghead; noagentApprove/dismiss |
|HumanTV5 P1|39 CR5561477514|Quyết định lại findingdocs đã sửa, approval chỉproposal/QAplan nếuđồngý|Reviewdecision thayCR;42scenario vẫnNOT_RUN |
|TV5 QA P1|48/Issue12handoff|Bàn giaosecuritymatrix/commentownscope, chuẩn bịcurrentStore/CODtests khiprovider/code sẵn|Đã bàn giao QA; runtimepair/currentFinance vẫnBLOCKED |
|TV2 P1|48 TV2review|Triển khai reviewedStoreV2+/JPA/currentfacts đượcTV1cho phép; giữprice/House/Cataloggates|OwnerPR+constraints/DB/authproof; khôngyêucầulặppricecompatibility |
|TV3 P1|28 +48 TV3review|Giữmoneyfix; implementnegativecaller/journal theoallowedownscope, chốtC06vớiTV4|Paircurrentauthority/T1T2exactpayload/UNKNOWNrecovery/stateeventapproved+evidence |
|TV4 P1|48 +39|Phản hồi currentStore/Inventorypairdesign, C06dispatch/retry/systemprovenance; clarifylocaloutbox vsremoteoperation|Versionedsignature đúngscope+runtimeownerimplementation/tests; no broadtoken |
|TV4 P2|39currentboundary|GhiSHA/boundarycho câuOrder-scaffold lỗi thời|Docsourcefacts đúngscope; không đòi triển khaiCODđểsửacâu |
|TV1/TV3 P1|49pairgatecf60281|Reviewisolatedfilter/runtimewiring/currentauthority/provision/rotation; TV4giữfixedtest|ScopedHTTP+deployment+systemprovenanceevidence trướcpositiveintegration;5unitPASS khônglivepairapproval |
|Chủdựán/business P1|M01e597dafpartialprice|Chỉđịnhnamedbusinessapprover/operator; chọnMD01/02/unitcapMD03+applicablegates; cácMDkhác độc lập|Writtenversion/choice/scope/account/URL; noagent/humanTV5 giảđịnhauthority |

Reviewerrequests được kiểm với author/currentreview/removalhistory. PR28 defaultTV2/TV1/TV5/TV4 đều có removalevents: **không tự thêm lại**;
evidence mới được bàn giao bằngCOMMENT, quyền quyết định/re-requestphùhợp đểowner/conngười thực hiện. PR39TV1 đãAPPROVED,TV2đãCOMMENTđúnghead;
không defaultrequest lại chỉ vìlisttrống. TV3 chưareview/request/removalở39: có thể request mộtlần choC06consumerreview.
PR48TV2/TV3đãreply,TV5+TV4requested; khôngrequesttrùng. PRđóng khôngrequest.
PR49TV1+TV3requested, defaultTV2chưađượcrequest/review/removalởsnapshot: requestmộtlầnphùhợp; TV4author khôngselfrequest.

**WAITING_REVIEW / WAITING_OWNER_CONFIRMATION.** Issue5/12OPEN/PARTIAL; noApprove/Merge/Close, noFinanceflag, no productionmoney/COD/settlementunlock từsuiteGREEN.
