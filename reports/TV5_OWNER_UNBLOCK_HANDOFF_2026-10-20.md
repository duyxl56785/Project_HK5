# TV5 — rà soát việc còn chặn owner và bàn giao, 20/10/2026

Refs #5/#12. **TV5-UNBLOCK-20261020-v1. PARTIAL / WAITING_HUMAN_REVIEW / WAITING_OWNER_CONFIRMATION.**
Nguồn chạy mới: main **03fb1b2e8a1c1261568446c1f9ed68d264784e4e**; PR60 **e0a28de663b3488178a616770eb82de9fd46e580**.
Đây là agent QA/technical handoff, không chữ ký human TV5/business, không quyền bật Finance/pair hoặc triển khai owner khác.

## 1. Phạm vi quét và điều đã hoàn thành từ trước

Đã đọc AGENTS/CONTRIBUTING/OWNERSHIP, README/architecture, packet M01/MD01–MD10, ledger và source liên quan.
[Inventory có 45 PR / 16 Issue](evidence/tv5-unblock-20261020/repository-inventory.json): 3 PR mở39/60/61, 42 merged;
15 Issue mở, Issue13 đóng. Bao gồm pagination reviews, inline comments, conversation, timeline/removal, commits,
Files Changed, threads và current-head CI. Đây là các snapshot trong phiên, không một transaction trạng thái GitHub.
Không có open thread ở3 PR mở tại scan. Không tuyên bố đã runtime-review toàn diff của42 PR lịch sử;
review sâu ưu tiên TV5 và phần thay đổi hiện tại, merged source và các quyết định tác động Finance/COD.

PR20/21/26/27/28/31/33/35/36/37/38/40/41/42/43/44/45/46/47/48/49/50/51/52/53/54/55/56/57/58/59
đã được kiểm tra trạng thái/evidence liên quan; không push hay request lại PR đóng.
Đặc biệt **46/e597daf đã merge**, diagnostic43 và fix41/42 đã tích hợp; FIN01/06 không còn là bug chưa sửa.
PR59/2a10938 đã merge tại03fb1b2; không tiếp tục dùng branch PR59 để bàn giao mới.

Một lần tải API gặp IncompleteRead; giữ snapshot đã lấy, retry các PR còn thiếu và đã có đủ45 file PR/16 Issue.
Legacy branch-protection endpoint trả404; effective `rules/branches/main` đọc được: ít nhất1 approval,
required thread resolution, cấm non-fast-forward/delete. Không suy404 thành không có protection.
Draft review chưa publish của người khác không thấy qua API. Raw API/log/XML giữ private local.

## 2. Danh sách ưu tiên đầy đủ

P0/P1 bên dưới là **ưu tiên công việc**, không tự gán severity bug P0 cho một draft chưa tích hợp.
21 mục: 6 đầu ra kỹ thuật/bàn giao mới; 2 đầu ra cũ được kiểm lại; 1 human review; 12 gate owner/business.
Mục21 chỉ hoàn tất sau khi GitHub xác nhận commit/PR/comment thực tế, không từ việc tạo file local.

| ID | Việc / PR–Issue | Owner | Ai đang bị chặn | Ưu tiên | Trạng thái | Cách giải quyết nhỏ nhất / evidence |
|---|---|---|---|---|---|---|
|U01|Formal review39/docs|Human TV5|TV4 merge39|P0|WAITING_HUMAN_REVIEW|3 findings FIXED_VERIFIED tại ec2ae5a; human Review changes→Approve→Submit review; agent không Approve|
|U02|Technical review60|TV5 QA; TV3 source|TV3 chờ QA|P0|TECHNICAL_REVIEW_COMPLETE|11caller unit +11journal PG PASS đúng e0a28de; COMMENT handoff, human approval riêng|
|U03|Technical review61/docs|TV5 QA; TV2 docs|TV2 chờ Finance/traceability input|P2|TECHNICAL_REVIEW_COMPLETE|Full135added lines/3docs,21relative links/10anchors; không tìm thấy blocker mới thuộc Finance|
|U04|Named business approver|Project authority; TV1 điều phối|TV2/3/4/5|P1|BLOCKED|Chỉ định account/role/delegating authority/version/scope; request cũ còn hiệu lực, không spam|
|U05|MD01/02/unit-capMD03 price slice|Authorized human+TV5, TV2/3|TV2 price persistence|P1|PENDING BUSINESS APPROVAL|Ký/revise M01-PRICE-SCOPE-v1-DRAFT; các technical compatibility đã có, không hỏi lại|
|U06|MD04 House/rate/rounding|Business+humanTV5; TV2 facts/TV3 grouping|Checkout/Finance|P1|PENDING|Trusted House definition+privileged assignment,5%/0%, commission base/stage/version; không áp từ fixture|
|U07|MD05 adjustments|Business+TV5/TV3/TV4|Payable/COD/Finance|P1|PENDING|Chọn explicit no-adjustment scope hoặc allocation/tax/shipping/discount/remainder; không absent→0|
|U08|MD06 COD/refund/settlement|Business+TV4+TV3+TV5; TV1 auth|COD/settlement|P1|PENDING|Ghi nhận TV3 handoff mới; chốt exact source/action/event/transaction và refund/reversal semantics|
|U09|MD07 freshness|Business+TV1/TV2/TV3/TV4/TV5|Catalog→Checkout|P1|PENDING|Chọn last-verified race acceptance hoặc quote/lease mới; không tự đặt TTL/bảo đảm commit-time|
|U10|MD08 immutable snapshots|TV3 shared DB+TV2 facts+TV5; TV4 COD|Finance consumers|P1|PENDING|Exact currency/rate/House/net/policy/fact version fields; reviewed V3+ nếu Order schema cần đổi|
|U11|MD09 enable authority|Project authority+TV1/operator+humanTV5|Finance deployment|P1|PENDING|Business signer và runtime operator là hai quyền; không bật flag từ merge/tests|
|U12|MD10 revenue/cutoff|Business+TV5; TV3/4 sources|Reporting|P1|PENDING|Tên/công thức/source/recognition/cutoff/Store scope/refund cho4nhóm; timezone/basis vẫn candidate|
|U13|FIN01/06 current regression|TV5 QA; TV3 preflight|TV3 cần bằng chứng|P2|COMPLETE_CURRENT_QA|25PG+3original diagnostic PASS tại03fb1b2; no new Checkout fix request|
|U14|F15/F16 current pure rules/security|TV5|TV2/3 cần tương thích|P2|COMPLETE_CURRENT_QA|105Order unit và50Product filtered unit PASS; không production auth/settlement uniqueness|
|U15|Concrete current-role/pair auth|TV1+provider/consumer|TV2/3/4/5|P1|BLOCKED_RUNTIME|Baseline OD07/09/S01 architecture đã bàn giao; cần verifier/current ADMIN/delegated-system provider/routes/evidence|
|U16|Product/SKU/C01/E01 source/wire|TV2+TV3; TV1/TV4/TV5|Checkout/Inventory|P1|BLOCKED_RUNTIME|SKU-only/global immutable target accepted; Product production UNIQUE/mapping/HTTP/currency/House chưa có|
|U17|C06 committed lifecycle/eligible RELEASE|TV3+TV4; TV1/TV2/TV5 boundaries|Fulfillment/Inventory/COD|P1|BLOCKED_RUNTIME|Pure helper/journal đã merge; còn persisted state/event/proven held reservation/remaining qty/authorized system|
|U18|Current QA/RTM and exact owner handoff|TV5|TV1–4 cần input cụ thể|P2|PREPARED_TECHNICAL_DELIVERABLE|Report này, checkpoint ledger/QA/RTM và response Issue12; không hỏi lại decisions đã có|
|U19|Two-Store UAT/refund/security plans|TV5; owners implement prerequisites|Future acceptance|P2|EXISTING_VERIFIED|[UAT](../tests/TV5_UAT.md), [SP01–14](../tests/TV5_SERVICE_PAIR_QA_MATRIX.md); execution NOT_RUN/BLOCKED|
|U20|Previous QA package59 integration|TV5 verify; TV2 review/merge|Không còn chờ59|P2|EXISTING_VERIFIED|Merged main03fb1b2 chứa2a10938; không tạo lại nội dung/test cũ|
|U21|Publish valid checkpoint/reviews/checklists|TV5|Owners cần link truy được|P2|PUBLICATION_GATE|Commit/push branch mới, đúng base main, COMMENT60/61, Issue5/12 checkpoint, verify remote/reviewers|

Không phát hiện lỗi production mới thuộc TV5 cần sửa ngay trong phần đã kiểm. Không viết test mới chỉ để lặp
implementation: existing settlement truth table đủ8 tổ hợp, Review eligibility/duplicate/moderation/hidden-average,
fee/House/precision/conservation và replay/preflight/rollback đã có test. Gate DB uniqueness/Store API không thể
được thỏa bằng thêm một boolean guard test hay tạo production Finance trên policy chưa duyệt.

## 3. PR39, PR60 và PR61

### PR39 — human review vẫn là thao tác còn thiếu

Head **ec2ae5af5162aa93482b9439d1b39dbb7abb3bec**, base API main/edb6886; no conflict, effective CHANGES_REQUESTED.
Count/ID/SKU-authority-state findings FIXED_VERIFIED;21legacy+21supplemental,42NOT_RUN.
[Proof đã publish](https://github.com/duyxl56785/Project_HK5/pull/39#issuecomment-6282027398), không đăng lại cùng finding/lời nhắc.
TV1/TV3 APPROVED docs-only, TV2 COMMENT tại đúnghead; TV5 COMMENT không thay CR cũ.
User đã trả lời trong phiên rằng sẽ cập nhật review GitHub; lời trả lời này **không phải** review đã submit.
Sau khi GitHub nhận review mới, kiểm effective CR, required approval/thread/check/mergeability rồi mới báo TV4 merge-ready.

Review text để human dùng: “Các finding tài liệu/traceability tại ec2ae5a đã FIXED_VERIFIED. APPROVED cho docs/traceability;
42business cases vẫn NOT_RUN; không phê duyệt shared C06/runtime hay Finance/COD/M01 policy.”

### PR60 — test-only journal, không tự hợp thức hóa RELEASE

Head **e0a28de663b3488178a616770eb82de9fd46e580**, base **03fb1b2e8a1c1261568446c1f9ed68d264784e4e**.
Đã đọc toàn bộ3file diff: 2test classes +audit; production/migrations không đổi. Newcases chứng minh3canonicalSKUs,
quantity2147483648L, distinct RESERVE/RELEASE IDs/type fingerprints, durable exact replay/mismatch rejection,
UNKNOWN same-ID retry/no implicit RELEASE và provider SKU-set mismatch. No new actionable finding trong scope.
Author CI27PG là evidence riêng, không cộng vào TV5 focused11PG. Docs nói rõ candidate RELEASE không chứng minh
confirmed held stock/remaining quantity/lifecycle/current actor; không dùng “journal PASS” để bật adapter.
TV2+TV5 đã requested, không duplicate. TV4 không là required reviewer theo author scope; không tự request thêm.

### PR61 — docs-only, source facts và authority labels đúng phạm vi

Head **0af4754b2d2b0925cd3b6c27f698f5d2667f66d1**, cùng base03fb1b2. Đã đối chiếu Seller/Store service methods,
local StoreCurrentSource, closed Product/Catalog route, current Cart/Order ports và M01§14.
135added lines giữ lịch sử, explicit HTTP candidate chưa approved/implemented. Không phát hiện Finance/QA blocker mới.
[Static result](evidence/tv5-unblock-20261020/pr61-docchecks.json):21relative file links,10anchors, diffcheck0.
Runtime **NOT_RUN/docs-only**;529PASS trong PR là **TV2-reported separate execution**, không phải TV5 rerun.
Default reviewer duyxl56785 là tác giả61 nên không self-request. TV1/TV3/TV4/TV5 đã requested.

## 4. M01/MD01–MD10 — phần có evidence và phần không được tự ký

[PR46/e597daf](https://github.com/duyxl56785/Project_HK5/pull/46) là packet gốc đã merge, không phải pending integration.
[PR53/d08f3e5](https://github.com/duyxl56785/Project_HK5/pull/53) bổ sung **M01-PRICE-SCOPE-v1-DRAFT**:
explicitVND, positive integral exact BigDecimal/plain JSON string, no silent rounding, **unit1..1000000000 inclusive**.
17digit là candidate cũ, không current business approval. Quantity1–1000 chỉ current technical constraint.

- [TV5 technical compatibility](https://github.com/duyxl56785/Project_HK5/pull/53#pullrequestreview-5566442640) và
  [TV3 scoped consumer compatibility](https://github.com/duyxl56785/Project_HK5/pull/53#pullrequestreview-5579396238) đã có cho delta1billion; không xin lại.
- [TV1 docs-only approval](https://github.com/duyxl56785/Project_HK5/pull/53#pullrequestreview-5579455852) **không chỉ định** business approver/operator.
- [Existing named-authority request](https://github.com/duyxl56785/Project_HK5/issues/5#issuecomment-6254401376)
  còn hiệu lực; PR61 cũng trỏ đúng request này. Không đăng reminder tương đương chưa có delta cần trả lời.
- Người cần chỉ định: **project authority/chủ dự án; TV1 điều phối xác nhận account/role/scope và delegation evidence**.
  Không suy repo ownerTV2 hay technical lead tự có business mandate. TV5 đã xác nhận chỉ tổng hợp/đề xuất.

| MD | Đã có / proposed choice | Còn thiếu cụ thể; trạng thái business |
|---|---|---|
|01|Explicit VND, reject missing/other/FX; technical compatibility recorded|Named approver +human same-version scoped signature; PENDING|
|02|Exact positive integral price, BigDecimal/plain JSON string, reject fractional/no implicit rounding|Business signature +actual Product constraints/verified DTO; PENDING|
|03|Latest Product unit cap1..1b, compare numeric; unit1b×qty1000=1e12 exact storage fact|Unit-cap signature riêng; line/Store/parent/fee/net bounds riêng chưa chọn; PENDING|
|04|5% ordinary/0%House/net=subtotal−fee existing requirements; proposal HALF_UP once per SellerOrder|Trusted House classification/assignment actor, fee base/stage/policyVersion signatures; PENDING|
|05|TV3 conditional no-adjustment proposal or explicit allocation design đã có|Business chooses scope/payer/beneficiary/remainder/commission impact; PENDING|
|06|CurrentADMIN afterDELIVERED, payment separate, unique atomic settlement requirements; TV3 new technical handoff|Exact delivery/payment source/action/event/current-admin and amount/refund/reversal semantics same-version; PENDING|
|07|Recommend last-verified snapshot with explicit race acceptance; no production quote/lease exists|Business/owners accept read→commit window and safe recovery; no invented TTL; PENDING|
|08|Existing gross/fee/unit/line snapshots+replay PG; immutable extended record proposed|Currency/rate/House/net/policy/source versions and approved schema/consumer signatures; PENDING|
|09|Defaultfalse and separate permission to configure|Named business signer/operator+auditable scoped enable/rollback process; PENDING|
|10|UTC instant/local reporting cutoff candidates and settlement-posting basis|4 revenue groups names/formulas/sources/recognition/Store scope/refund/late-event decisions; PENDING|

**No price implementation newly unlocked.** Sau khi signed riêng MD01/02/unit-capMD03 và applicable schema/S01/security,
TV2 triển khai riêng Product price/currency persistence; MD04–10 **không tự động** trở thành prerequisite của slice đó.
Việc này không mở Checkout live, House assignment, COD/settlement/reporting hoặc quyền sửa owner khác.

## 5. Trả lời mới cho TV3/TV4 về COD và Finance

Tiếp nhận [TV3 COD/AUTH handoff mới](https://github.com/duyxl56785/Project_HK5/issues/12#issuecomment-6282489759),
[TV3→TV1 auth request](https://github.com/duyxl56785/Project_HK5/issues/1#issuecomment-6282503396),
[TV4 key/source response](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6282124816),
[TV3 candidate56 decision](https://github.com/duyxl56785/Project_HK5/pull/39#issuecomment-6281764084) và
[TV4 retry58 compatibility](https://github.com/duyxl56785/Project_HK5/pull/58#issuecomment-6282158271).
Các câu này đã có owner response, không gán họ là chưa phản hồi toàn bộ.

**COD POLICY | PENDING | TV5-FIN-v0.1 + TV5-M01-DECISION-v1 / handoff20261020**.
Agent có thể xác nhận code/evidence và lập expected technical acceptance; không thay human/business ký COD rule.

| Câu hỏi | Existing requirement / candidate để owner chọn | Còn cần ai xác nhận / evidence |
|---|---|---|
|Điều kiện settle|Chỉ committed DELIVERED **và** trusted COD PAID, approved immutable snapshots, chưasettled|TV5/business policy; TV3/TV4 authoritative facts+atomic persistence; pure guard không thay DB|
|Ai ghi COD PAID|Existing OD07 current verified ADMIN, afterDELIVERED; no seller/customer/body/header authority|TV1 exact current-role procedure; TV4 command business guard; human/business applicable scope|
|Nguồn COD|Candidate Order DB payment record +actor/time/actionId/fingerprint, một local transaction+outbox|TV3 implementation owner; TV4 producer/command; TV1/TV5 same-version exact wire/schema chưa ký|
|Nguồn giao hàng|TV4 rules; candidate authoritative Order committed state/version+outbox, không Inventory balance/CONSUME|TV3+TV4 state writer, delivery actor/event fields/version; TV1 current actor; no silent state aliases|
|Giao thất bại|Không tựDELIVERED/PAID/settled; UNKNOWN hold/reconcile; không suy failure=>release/refund|TV4+TV3 failure/cancel/held stock; business+TV5 money/refund outcome|
|Hủy/hoàn reservation|Proven pre-dispatch cancel/reject, confirmed reservation liên kết samechild/lines và remainingheld; distinct stable RELEASE ID|TV3 orchestration+TV4 ledger; systemprovenanceTV1; existing proposed retry semantics không cần hỏi lại|
|Replay/concurrency|Sameaction+samepayload once; changedpayload conflict; payment effect và settlement uniqueness tách biệt|TV3/TV4 payment unique/lock/version; TV5 settlement uniqueSellerOrder+atomic create; actualPG missing|
|Crash/recovery|T1/T2 local durable exacttuple+generation fence; remoteUNKNOWN không proof rollback, no newID/blindRELEASE|TV3+TV4 deadline/manual escalation/status proof; no distributedtransaction/automatic refund|
|Finance event needs|Candidate eventId/schemaVersion/aggregateVersion, parent+child+Store/Seller refs, authorized delivery/payment state/time, action ref, approved currency/amount/snapshotVersion; no unnecessary PII|TV3+TV4 producer/wire/order/retry; TV1 exposure/currentactor; TV5 consumer exactschema; field list vẫn proposal|
|Refund/settlement boundary|Physical return sau dispatch khôngpre-dispatchRELEASE; original monetary snapshots immutable; adjustment/reversal separately idempotent|Business/humanTV5+TV4 selects eligibility/window/partial amounts/fee allocation/reversal/cutoff; chưa tự đặt expected amount|

TV3 source hiện **không có** persisted delivered state/COD ledger/paidBy/paidAt/actionId/settlement table trênSellerOrder.
Không viết production Finance để giả các facts đó. Existing OrderV1 money NUMERIC(19,2), V2journal giữ nguyên;
migration mới phải additive và tránh trùng phiên bản, **không rewrite V1/V2**.

## 6. Kiểm thử thực tế trong phiên — tách từng invocation

Java21.0.7/Gradle8.10, JVM UTC/API1.44; Dockerclient29.8.1. Ban đầu Engine tắt, đã khởi động Docker Desktop,
không prune/delete/reset/teamDB migration. Sau đó disposable PostgreSQL16 Testcontainers hoạt động.

| Nguồn / invocation | Lệnh (từ repo root checkout tương ứng) | Kết quả thực tế |
|---|---|---|
|main03fb1b2,03:19:47–03:20:40UTC|`backend/gradlew.bat -p backend --init-script <absolute tests/finance/fin06-regression.init.gradle> :order-service:test :order-service:postgresIntegrationTest :order-service:fin06Regression :product-service:test --tests '*ReviewRules*' --tests '*ProductServiceSecurityTest' --rerun-tasks --no-daemon --console=plain`|105Orderunit+50filteredProductunit PASS; **5 initializationError FAIL records**, Docker unavailable, PG business scenariosBLOCKED; diagnosticNOT_RUN, staleXML excluded; combinedexit1|
|main03fb1b2,03:22:25–03:24:13UTC|Same init, `:order-service:postgresIntegrationTest :order-service:fin06Regression --rerun-tasks --no-daemon --console=plain`|**25PG PASS+3original diagnostic PASS**,0fail/error/skip,exit0; positivecontrol retained|
|PR60/e0a28de,03:19:47–03:20:27UTC|`:order-service:test --tests '*InventoryOperationCallerTest' :order-service:postgresIntegrationTest --tests '*InventoryOperationJournalPostgres16IT' --rerun-tasks --no-daemon --console=plain`|11callerunit PASS; **1initializationError FAIL record**, Engine unavailable, journal scenariosBLOCKED; combinedexit1|
|PR60/e0a28de,03:22:27–03:23:20UTC|`:order-service:postgresIntegrationTest --tests '*InventoryOperationJournalPostgres16IT' --rerun-tasks --no-daemon --console=plain`|**11journalPG PASS**,0fail/error/skip,exit0|
|PR61/0af4754|Git/blob/Markdown relative-file+anchor checks|21links/10anchors/diffcheck0; runtimeNOT_RUN/docs-only|

50Product unit gồm **30Review rules +20ProductServiceSecurity**, deny-default MockMvc tests, khôngsignedJWT/Review API.
105Order unit gồm **49 Finance package +5 JWT/config**, các tập này **nằm trong105**, không cộng lần nữa.
25PG gồm FinanceSnapshot6 +CheckoutReplay4 +CheckoutScanner4 +InventoryJournal9 +Order2.
PR60 focused11PG là snapshot riêng, không cộng vào main25 hay authorCI27.
Original FIN diagnostics3cases không hạ assertion/disable: FIN06 parentoverflow, FIN01 incompatibleprecision và positivecontrol đềuPASS.
DB NUMERIC(19,2) max99,999,999,999,999,999.99 là storagefact, không signed Product/businesscap.
Knowninvalid dừng trướcInventory.reserve/writer; rollback directwriter vẫn đượccharacterize; successreplay giữsnapshot/no neweffect.
Không yêu cầu TV3 sửa lại FIN01/06 hoặc chạy lại sourcecũ khi đã có source/freshGREEN.

**Live E2E/current ADMIN/COD/unique atomic settlement/Store Finance query/positive pair: NOT_RUN/BLOCKED.**
Mock ports và operationIdPK không chứng minh các gate đó. Existing tests đã phủ phần độc lập; không thêm test hình thức.

Derived case/command/time/hash summaries: [maininitial](evidence/tv5-unblock-20261020/main.json),
[mainDBGREEN](evidence/tv5-unblock-20261020/main-db.json), [60initial](evidence/tv5-unblock-20261020/pr60.json),
[60DBGREEN](evidence/tv5-unblock-20261020/pr60-db.json).
Raw originals private `.verification/tv5-unblock-20261020/runs/{main,main-db,pr60,pr60-db}/`; khôngpublish raw credentials/system-out,
không gọi chúng là publicCIartifacts. Giữ nguyên byte/SHA256 và initialFAIL; summaries là derivative được gắn nhãn.

## 7. Bàn giao để owner không phải tự điều tra lại

| Thành viên | Thực sự chờ TV5 gì | TV5 bàn giao | Evidence / còn blocker | Bước tiếp theo |
|---|---|---|---|---|
|TV1|Finance decision packet/security test expectations; không chờ TV5 sửaAuth|MD01–10+partialscope, SP01–14, COD401/403/503/currentactor table|PR46/53; authorityrequest vẫnopen; OD07/09/S01 design giữ nguyên|Coordinate namedauthority, provide concrete current-role/delegated-system procedure; không mở lại baseline|
|TV2|Price-slice human/business signoff; QA61|Delta1b đãtechnicalcompatible; currentMDtable và61staticcheck|Human/businesssignatures/schema/security gates thiếu; StoreDB/image đãmerge không làm lại|Finalize approved HTTP/currentauthority slice vớiTV1; pricepersistence chỉ sau đủown-scope approvals|
|TV3|QA60 và precise Finance/COD input|Focused11unit/11PG; freshmainFIN01/06GREEN; COD/PENDING source/event questions|No new Finance correctness bug; no proofpositiveHTTP/paymentfacts; currentauthority/MDchoices thiếu|Human review60; state/command/schema/producer proposal đúngversion, không sửa lại oldFINbug|
|TV4|Formal review39 và applicable COD policy|39proof/manualtext đãcó; 58compatibility+56candidate ghi nhận; 10CODquestions/source responsibilities vàUATnegativeplan|TV5human39 vẫnchặn docs; sharedCOD/business/currentactor/heldstock/DBwire thiếu|HumanTV5submit; TV4reviewremaining fields/producer/failure/return semantics theo đúngscope; no blindRELEASE|

Một số việc owner có thể tiếp tục **đã được phép từ trước**: isolated tests/docs, scoped StoreDB/current-facts work,
default-OFF filter và journal/purehelper. Phiên này **không mới mở khóa** Product price/positive Catalog/COD/settlement.
Khi chỉ còn owner/human gates, không tạo scaffold/production API/DB/FE/E2E thay owner để tránh chờ.

## 8. Publication và nghiệm thu

- [x] Quét45PR/16Issue, cả comments mới TV3/TV4/PR61, current sources/rules/removal events.
- [x] Current FIN01/06 và F15/F16 unit/PG evidence, giữ Docker initialization failures và original diagnostics.
- [x] Hoàn thiện matrix21tasks, MD01–10, COD/source/security/owner handoff và acceptance limits.
- [ ] Publication checkpoint: commit/push, verifyPR/reviewers, publish nonduplicate COMMENT60/61 and Issue5/12 links.
- [ ] HumanTV5 formal review39 +post-review mergegate check; không Approve/Merge bởi agent.
- [ ] Namedauthority+same-versionbusiness/owner signatures và approved runtime implementations/evidence.
- [ ] ProductionF15/F16/currentStore/COD/uniqueatomicsettlement/liveE2E và fullIssue5/12DONE.

Nếu không có source/reply mới sau publication, phần độc lập hoàn tất trong scope đãaudit;
**WAITING_HUMAN_REVIEW / WAITING_OWNER_CONFIRMATION**, không tạo việc hoặc commit rỗng để lấp thời gian.
