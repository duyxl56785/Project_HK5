> **LaterAuthdelta23/10:** [P2/PR80 and diagnostic82:4PASS/1FAIL](../reports/TV5_REVIEW_ACCEPTANCE_2026-10-23.md#8-later-auth-pr80--diagnostic82-delta--p2-remains-open-not-a-green-fix). TV1ownerfix pending; no broadgreen claim.

> **Current23/10checkpoint:** [63PR/allreviewers/currentQA and ownerhuman decisions](../reports/TV5_REVIEW_ACCEPTANCE_2026-10-23.md). Prior22/10snapshots below retain history;69oldCRsuperseded,68/70/73/75MERGED. M01PENDING,FinanceproductionBLOCKED.

> **Current R2 checkpoint 22/10/2026:** [fresh56PR audit, merged63/66/67, open68–72 and exact-head QA](../reports/TV5_PR_REVIEW_FOLLOWUP_2026-10-22_R2.md). Earlier checkpoints below remain historical. M01 PENDING; Finance production BLOCKED.

# Kiểm thử và bằng chứng

> **Latest audit 22/10/2026:** [52 PR / toàn bộ reviewers và current QA](../reports/TV5_ALL_PR_REVIEWERS_AUDIT_2026-10-22.md).
> Các checkpoint20/10 bên dưới giữ lịch sử; #65 nay MERGED, #66/#67/#68 được theo dõi riêng. M01 PENDING, Finance production BLOCKED.


> **Current handoff:** [post-snapshot delta](../reports/TV5_OWNER_UNBLOCK_HANDOFF_2026-10-20.md#12-post-snapshot-status-delta--current-actions)
> (query 2026-10-20T14:55:26.142195+00:00): #39/#61 MERGED; #60/#62 CLOSED WITHOUT MERGE.
> Những next action yêu cầu review các PR đó phía dưới là lịch sử. Không assume #60/#62 đã tích hợp.
> Delta soạn trước công bố; người dùng đã cho phép cập nhật PR #63/Issue #5/#12. Xem receipt tại PR/Issue; M01 vẫn PENDING, Finance production BLOCKED.


Main đã nhận rules/unit tests Review/Finance qua PR #20 và Category/Seller domain qua PR #7. API/persistence/JWT production của F15/F16 chưa được chứng minh hoàn tất. Mỗi thành viên tự chạy test liên quan và báo cáo:

| Hạng mục | Test command | PASS | FAIL | NOT_RUN | Bằng chứng |
|---|---|---|---|---|---|
| Ví dụ Auth | gradle :auth-service:test | - | - | chưa chạy | link PR/log |

Không suy số test của báo cáo V2 thành kết quả của repository mới khi chưa import code và chạy lại.

Một Issue được coi DONE khi: có PR đã merge, kiểm thử có bằng chứng, API/schema/event được kiểm tra và tài liệu được cập nhật.

## Baseline TV5 — 15/10/2026

- [Báo cáo thực thi và review PR](../reports/TV5_W1_AUDIT_2026-10-15.md).
- [Ma trận QA, UAT hai Store và checklist báo cáo](../tests/TV5_UAT.md).
- [Đề xuất contract/schema Review/Finance cần owner review](TV5_CONTRACT_PROPOSAL.md).

Rules Review/Finance có unit tests; chưa có API/persistence/JWT adapter production. Không dùng kết quả unit để tích hoàn thành F15/F16 hoặc E2E. Các con số test lịch sử ở PR #7 phải phân biệt với lượt chạy lại trong báo cáo TV5.

## Follow-up tuần 1

- [Đối chiếu báo cáo V2 theo chương/trang](../reports/TV5_V2_RECONCILIATION.md).
- [Truy vết yêu cầu → test → bằng chứng](../tests/TV5_TRACEABILITY.md).
- [Bảng lỗi và blocker](../reports/TV5_W1_DEFECTS.md).
- [Quyết định cần TV1–TV4 xác nhận](TV5_W1_DECISIONS.md).
- [Kết quả chạy follow-up và phân loại bằng chứng](../reports/TV5_W1_FOLLOWUP.md).

PASS: đã chạy đạt; FAIL: đã chạy lỗi; NOT_RUN: chưa thực thi; BLOCKED: chưa thể chuẩn bị/chốt ca do phụ thuộc được ghi rõ. Không cộng test của các mốc khác nhau, unit với E2E hoặc claim báo cáo V2 vào cùng tổng hiện tại. Việc thiết kế UAT/contract trong W1 không đồng nghĩa đã triển khai/chạy luồng tuần 2–3.

## Kiểm chứng điều kiện tuần 1 — 17/10/2026

- [QA matrix, PR heads, owner/blocker và ledger nguồn kết quả](../reports/TV5_W1_ACCEPTANCE_2026-10-17.md).
- [Raw log/XML, suite counts và SHA256](../reports/evidence/w1-gates-20261017/README.md).
- TV5 chạy mới: Review/Finance63 unit PASS; Seller21 PostgreSQL PASS; Cart/Order3 PostgreSQL PASS; Auth/User timezone Asia/Saigon1 PASS/2 FAIL, rerun UTC3 PASS. Không cộng các lượt hoặc xóa FAIL lịch sử.
- Issue #5 vẫn PARTIAL: test DB Seller/Cart/Order không chứng minh DB Review/settlement, mapping JWT runtime hoặc shared Finance contract đã nghiệm thu.

## Bản sửa bổ sung theo finding — 17/10/2026

- [PR #32](https://github.com/duyxl56785/Project_HK5/pull/32) → branch PR #19: sửa HS256/JWKS và checkpoint PR #7; giữ F15/F16. TV1 đã Approve đúng `0c77df6`; còn chờ tác giả tiếp nhận vào #19.
- [PR #33](https://github.com/duyxl56785/Project_HK5/pull/33) → branch PR #28: JWT YAML mapping, replay/customer scope và scanner/failure-handler CAS; final build Cart/Order **56 unit + 11 PostgreSQL PASS** tại `11dcf39`. RED/GREEN logs, test source và manifest nằm trong `backend/order-service/verification/tv5-fixes` của PR bổ sung. Không cộng với 63 rules/21 Seller/3 PG của lượt bàn giao trước.
- [PR #34](https://github.com/duyxl56785/Project_HK5/pull/34) → branch PR #27: owner đã sửa runbook ở `a810312`; TV5 giữ phần đó và bổ sung evidence cùng source Java: Asia/Saigon 1 PASS/2 FAIL, UTC 3 PASS, Asia/Ho_Chi_Minh 3 PASS. Không che lỗi của alias không được server hỗ trợ.
- Ba PR là patch/evidence bổ sung, không đưa source PR gốc chưa duyệt vào main. [Ledger bàn giao và owner](../reports/TV5_W1_ACCEPTANCE_2026-10-17.md#7-bản-sửa-bổ-sung-cho-các-finding) giữ WAITING_REVIEW/INTEGRATION; contract Review/Finance, live dependencies và E2E vẫn chưa được nghiệm thu.

### Checkpoint sau khi tác giả tiếp nhận patch

[Audit toàn bộ PR/reviewer](../reports/PR_REVIEWERS_AUDIT_2026-10-17.md): #32 đã vào #19 (`804d6f1`), #33 đã vào #28 (`89c20bc`), Git tree giống bản sửa đã kiểm chứng. Chờ reviewer quyết định lại tại parent, không coi review cũ tự được khép. #31 vẫn Draft và FAIL compileTestJava tại `1abfd4f`; bản sửa một dòng với **15 unit PASS** được bàn giao riêng qua #35 (`14df61b`). Đây là kết quả mới của TV5, PG/HTTP/E2E Inventory vẫn NOT_RUN. #27 có 2 CI readiness checks success đúng `a810312`, tách khỏi lượt chạy local TV5.

Phản hồi mới trong lúc audit: TV1 đã APPROVED #34 đúng `369b4f8`. TV2 xác nhận fixes #26/#27/#28, đồng thời báo **3 PostgreSQL probes FAIL ở #31** (schema CHAR/VARCHAR, duplicate initialize, concurrent operationId ledger). Đây là kết quả do TV2 cung cấp; TV5 chưa chạy lại và #35 không xử lý các DB defects đó. Xem audit §6 để biết owner/evidence; giữ #31 Draft và Issue #5 PARTIAL.

## Checkpoint 18/10/2026 — audit PR sau tích hợp

Các đoạn trên là snapshot lịch sử. #31/#36 và các PR QA/infrastructure trước đã merge; không còn báo ba DB findings cũ như source chưa sửa. [Audit hiện tại](../reports/PR_REVIEWERS_AUDIT_2026-10-18.md) kiểm5 PR mở #19/#28/#37/#38/#39: TV5 chạy mới #28 **61unit PASS**, #37 **291unit+49PG PASS** (11PG candidate SQL), #38 **17unit+15PG PASS**. Các lượt/source khác nhau, không cộng vào release total; runtime #19/#39 NOT_RUN/docs-only.

#39 cần sửa số19/21 và ID baseline bị đổi nghĩa; #38 cần rõ retryable errors/current replay balances. M01 money/Cart currency, S01 Store authority, C01/E01 Catalog và fulfillment state/auth/event còn owner-gated. [Raw evidence và publication limits](../reports/evidence/pr-audit-20261018/README.md) tách49 public hashes với3 credential-bearing XML giữ local/derived summary. Issue #5 vẫn PARTIAL; không suy unit/PG thành F15/F16 production hoặc live2Store E2E PASS.

## TV5 — Finance contract / current-head checkpoint 18/10/2026

- [Sáu contract, owner/signature/module matrix và M01 candidate TV5-FIN-v0.1](TV5_FINANCE_CONTRACT_SIGNOFF.md):
  S01/M01/Inventory/Security PENDING, C01/E01 và C06 REVISE; không có cross-owner APPROVED.
- [Lượt chạy mới TV5 và QA evidence](../reports/TV5_FINANCE_QA_2026-10-18.md): parent28 bd5ff09
  61unit+11PG PASS; branch bổ sung44f2838 72unit+13PG PASS,0fail/error/skip. Hai lượt riêng, không cộng.
- FIN-01..05 ghi rõ giới hạn precision/snapshots/settlement/Store authority/rounding. Test cấu hình dùng
  fixture policy không bật cờ deploy. Live UAT2Store/COD/settlement/Store query NOT_RUN/BLOCKED.

## TV5 — hậu tích hợp PR41, Finance persistence/replay regression

[Checkpoint mới](../reports/TV5_FINANCE_FOLLOWUP_2026-10-18.md): PR41 đã được owner merge vào
PR28 tại55bf5ff; chạy mới72unit+13PG PASS. Thêm1unit+4PG đạt73unit+17PG PASS tại sourcecd2c0be,
0fail/error/skip, không cộng hai lượt. FIN-06: parent aggregate vượt NUMERIC(19,2) dù từng Store hợp lệ;
PG rollback thật, nhưng validation trước RESERVE cần TV3/TV5 thống nhất. Replay giữ tiền/phí/event cũ.
S01/M01/Inventory/Security/Finance vẫn PENDING; C01/E01/C06 REVISE. Live settlement/Store API/E2E
NOT_RUN/BLOCKED; giữ policyfalse và các adapter fail-closed, không coi merge41 là contract approval.

## TV5 — FIN-06 Checkout diagnostic RED tại head28dd0cf

[Báo cáo và raw evidence mới](../reports/TV5_FIN06_CHECKOUT_RED_2026-10-18.md): PR42 đã tích hợp;
QA union từ main giữ nguyên byte và15relative linksPASS. Chạy task diagnostic riêng trên Checkout thật/PG:
**1PASS/2FAIL**,0error/skip,exit1. FIN-06 gọi reserve/write trước DB reject; FIN-01 first total3.002→replay3.00.
Không sửa assertion/production hoặc ghi thành E2E. Unit Catalog thay đổi tại63668b8 **50PASS** riêng.
Normal build không chạy diagnostic này; CI xanh không khép finding. Fix chờ TV3+applicable sign-off;
M01 và các contract chưa đủ chữ ký giữ PENDING/REVISE. [Cách chạy RED task](../tests/finance/README.md).

## TV5 — M01 human decision / owner utility checkpoint

[Gói quyết định MD01–MD10](TV5_M01_HUMAN_DECISION_PACKET.md) ghi lựa chọn, ví dụ số, schema/consumer
và signer cho TV5-FIN-v0.1; vẫn PENDING, không tự ký VND/scale0/bounds/freshness. [Lượt mới](../reports/TV5_MONEY_DECISION_CHECKPOINT_2026-10-18.md)
tại sourcec4d517e có6utility unitPASS bằng JUnit thật và diagnostic1PASS/2FAIL nguyênassertions,overallexit1.
Utility owneree15851 chưa được wire vàoCheckout. Docs38/39 đã sửa traceability/error semantics,21baseline+
21supplemental đềuNOT_RUN. RTM phân biệt staticchecks, PG/mock và liveE2E, không cộng kết quả các snapshot.

## TV5 — M01 authority và latest Finance preflight QA,18/10/2026

[PR44 / báo cáo source-specific](../reports/TV5_FINANCE_ACCEPTANCE_2026-10-18.md) công bố proposal/MD01–MD10 và sửa mô tả quantity thực tế1–1000.
TV5 chỉ tổng hợp/đề xuất; business approver **chờ TV1/chủ dự án xác nhận**. C01/E01 own-scope TV2/TV3 được ghi nhận, overall REVISE;
M01/S01/Security/Inventory/Finance vẫn PENDING. Không bật policy hoặc sửa owner runtime.

Source21f6d53: Cart6unit+1PG,Order73unit+16PG PASS; diagnostic1PASS/2FAIL,exit1.
Sau owner c97a656,source de1f5c6: Order75unit+16PG PASS,diagnostic1PASS/2FAIL,exit1; positive control còn PASS.
Sau owner5ce0630+4test TV5,source1311f8a: Cart8unit,Order81unit,Cart1PG PASS,exit0/27s. Không cộng các snapshots.
Order production/PG/diagnostic không đổi de1f5c6→1311f8a nên không chạy lại chúng trong lượt cuối; runtime findings vẫn RED, không nhận là fixPASS.

F16-16..19 bổ sung gate không reserve/write, inconsistent House/Seller trong cùng Store và actual per-Store rounding bằng mocked ports.
QA union mới với main: TV5 xác nhận giữ toàn bộ lịch sử main +hai checkpoint Finance18/10/provenance; chưa merge/resolve thay owner.
Settlement atomic/unique, Store Finance API, COD authority và live E2E tiếp tục NOT_RUN/BLOCKED. Issue5/12 OPEN/PARTIAL.

### Sau khi owner merge PR44 initial

PR44 đã merge vào28 tại ccfb38d (head1311f8a);4Finance tests và proposal không còn chờ tiếp nhận. Hai commit evidence push sau merge được chuyển sang branch codex/tv5-finance-evidence-handoff từ28ca639bb, không tiếp tục push PR đóng. Cart.claim key guard mới của owner được QA riêng; xem báo cáo cùng ngày để tách lượt chạy tiếp theo và phần evidence chờ review.

### M01 owner ledger r2 — docs-only,18/10/2026

PR45 đã được owner merge vào28 tại5a3788b. [Canonical packet §§8–11](TV5_M01_HUMAN_DECISION_PACKET.md) ghi đủ10MD/6tradeoffs/partial price-slice gates,
giữ TV2/TV3 own-scope confirmations và TV1 OD07/09. M01/MD01–MD10 vẫnPENDING; business signer chờTV1/chủdựánxácnhận, chưa mở ProductJPA/Checkout money/Finance runtime.
[Checkpoint/commands/resume](../reports/TV5_FINANCE_ACCEPTANCE_2026-10-18.md) và [derived audit](../reports/evidence/m01-owner-ledger-20261018.json) xác minh61existing raw hashes,
source equivalence và original diagnostic1PASS/2FAIL; **runtime NOT_RUN trong lượt docs này**, không cộng lại các test đã chạy hoặc yêu cầu duyệt lại DTO/price compatibility giống cũ.

### M01 re-audit r3 — actual run tại8cc5ce4,18/10/2026

PR42/46 đã merge vào28; không push/request trên PRđóng. [Re-audit](../reports/TV5_M01_REAUDIT_2026-10-18.md)
và [raw manifest](../reports/evidence/m01-reaudit-20261018/manifest.json): **83Order unitPASS,16Order PGPASS, diagnostic1PASS/2FAIL**,
0error/skip, combined commandexit1/73s tại **8cc5ce4af1beacfe0d8467ec037d55e59a065691**. Đây là lượtTV5 mới, không phải17assertion harness củaTV3 hoặc cộng các snapshots cũ.
New malformed-request guard verified; FIN01precision/replay vàFIN06pre-reserve vẫnFAIL, positivecontrolPASS; assertions/source giữ nguyên.
TV3 MD05/06/08 engineering reply đã ghi nhận trong canonicalpacket; không businesssignature. M01/all10MDPENDING,
S01/Security/Inventory PENDING, C01/E01/C06REVISE; chưa production slice mới. LiveE2E/settlement/Storequeries/currentCOD NOT_RUN/BLOCKED.
Lưu22rawassets exactbytes,61historicalhashes+Gitblobs reverified;0missinginitialDockerVolumes. Giữ toàn bộ QA history, không giải quyết conflict thayowner.

### All-PR review audit r4 — original FIN diagnostic GREEN,18/10/2026

[Current report](../reports/TV5_PROJECT_REVIEW_AUDIT_2026-10-18.md): scan33PR/119reviewevents/16resolvedthreads/16Issues;
4openPR28/39/48/49 tạirefresh, không bỏ sótPRmới. PR19alreadyapproved/merged; PR37/38/41/42/46/47integrated.
OwnerTV3 mergedmain tại4a6c9a2; audit so từngnonblankline mainQA đều giữ trongbe1; khônglàmlạiconflict.
**ActualTV5be1d5b6:88OrderunitPASS,16OrderPGPASS,originalFINdiagnostic3PASS,0fail/error/skip,exit0/60s.**
FIN01/06 đãFIXED_VERIFIED sauownerfix5f2ada8; **8cc1P2F ởcheckpointtrước làhistoricalRED**, assertions/blob khôngđổi.
LocalJWT/replay/scannertests cũngPASS; không mởlạiresolvedthreads. [Rawmanifest](../reports/evidence/project-review-20261018/manifest.json).
TV2be1run117PASS làownerreportedseparate, không cộngvớiTV5. Mainrulesetstaledismissalfalse: oldapprovalkhôngtựbịhủy, nhưngkhôngchứngminhheadmớiđãreview.
PR28TV1CR vàPR39TV5CR vẫnhiệulực chođếnhumanreviewdecisionmới; PR48S01/pairdesignđượcTV1+TV2+TV3confirmedownscope, chưaruntimeapproved.
[PairsecurityQA](../tests/TV5_SERVICE_PAIR_QA_MATRIX.md) táchexistinglocalunitPASS vàSP01–14NOT_RUN/BLOCKED. M01all10MDbusinessPENDING,
partialProductpriceMD01/02/unitcapMD03 vẫnchờbusiness+schema/S01/security; khôngbịMD04–10tựchặn. NoFinanceflag/COD/settlement/liveE2Eunlock; Issue5/12PARTIAL.
Deltaownerhead**c181675**: targetedCheckoutService16unit+3originalPGdiagnosticPASS,exit0/40s; full88unit/16PGvẫnthuộcbe1run.
PR49security5cf**4P1F**wrappercast, ownersửacf60281→**5P0F**same2classes,exit0/13s; partialfilterunitproofkhôngdeployedpair/liveE2E.
35rawassetsperSHAgiữRED/GREEN/commands/hashes; khôngcộngsnapshotshoặcchủsởhữu117PASSvàoTV5total.


### TV5 acceptance checkpoint — 20/10/2026 (PR39/54/56)

[Report/owners/source lines](../reports/TV5_ACCEPTANCE_PR39_54_56_2026-10-20.md) và [derived evidence](../reports/evidence/acceptance-20261020/pr54-summary.json).
Checkpoint này supersede các nhãn hiện trạng cũ về39/54/56; toàn bộ lịch sử/raw evidence phía trên giữ nguyên.
PR54 đã merge tại864c941, TV1 approve dormant journal only; PR56 merge b09236d, TV3 confirm candidate/helper only.
PR39ec2ae5a: ba finding tài liệu **FIXED_VERIFIED / REVIEW_READY**;21legacy+21supplemental đúng,42business casesNOT_RUN.
HumanTV5 vẫn cần submit formal review để thay CHANGES_REQUESTED; agent không Approve/dismiss và chưa báo TV4 merge-ready.

Lượt TV5 mới, source54**5955b1f**:96OrderunitPASS+23OrderPGPASS,exit0,20/10 02:24:06–02:25:34UTC; source56**8c88fff**:7domainPASS,exit0,02:24:05–02:24:34UTC.
Không cộng các snapshot/TV2/CI thành release total. Hai harness invocation ban đầu không khởi chạy Gradle, **NOT_RUN**, không là PRFAIL; XML cũ không được dùng làm kết quả mới.
[PR39 static](../reports/evidence/acceptance-20261020/pr39-static.json), [PR56 cases](../reports/evidence/acceptance-20261020/pr56-summary.json),
[merged source identity](../reports/evidence/acceptance-20261020/integration-source.json). Public files là derived metadata/case outcomes/hashes; raw log/XML private local, không publish credentials.

C06 candidate đã được TV3 confirm theo own-scope; **shared DB/wire/event/actor/cancel/Inventory/COD runtime vẫn PENDING**.
Order journal/scanner đã có source + realPG proof; **live operation provenance/fencing/HTTP/reconciliation chưa được nghiệm thu**.
M01/business signer/operator, approved snapshots/current ADMIN COD, atomic unique settlement/current Store Finance queries/live two-Store E2E còn BLOCKED/NOT_RUN.
Issue5/12OPEN/PARTIAL, Finance/pairflags không được bật từ merge hoặc tests.

### TV5 owner unblock checkpoint — 20/10/2026, TV5-UNBLOCK-20261020-v1

[21-task handoff/current decisions](../reports/TV5_OWNER_UNBLOCK_HANDOFF_2026-10-20.md) supersedes current-status labels above;
history/raw evidence stays unchanged. PR46/53/58/59 merged; PR39 docs findings repaired, human formal review still needed at scan.
OpenPR60/e0a28de and61/0af4754 now have scoped TV5 technical QA; default/current reviewers retained, not business approval.

Fresh main03fb1b2: **105Order unit +50filteredProduct unit PASS** (30Review/20deny-default security included),
then **25OrderPG+3originalFINdiagnostic PASS** in a separate DB invocation after Docker startup. First combined invocation
exit1 with5PG initializationError records because Engine offline; diagnosticNOT_RUN/staleXML excluded. Retain that FAIL raw evidence.
PR60/e0a28de:11callerunitPASS; firstPGinitializationError, then11journalPGPASS afterDocker startup. No source/assertion changes.
[Per-invocation counts/commands/private raw hashes](../reports/evidence/tv5-unblock-20261020/main-db.json).
PR61 static21filelinks/10anchors/diffcheck0; runtimeNOT_RUN,529reportedbyTV2 is not TV5 execution.

FIN01/06 existing-storage preflight remains FIXED_VERIFIED; no new Checkout fix requested. Shared C06/current-actor/Store/SKU/
CODpayment/uniqueatomicsettlement/refund/liveE2E and M01business decisions stay PENDING/BLOCKED. Existing source confirmation
for SKU-only design, candidatehelper and fencedjournal is recorded; do not ask owners to repeat those decisions.

Publication confirmed b923736/[PR63](https://github.com/duyxl56785/Project_HK5/pull/63), reviewerTV1/TV2;
COMMENT60/61 and Issue5/12checkpoint URLs in [receipt](../reports/evidence/tv5-unblock-20261020/publication-receipt.json).
LatePR62 **6a79016** adds pureunwiredCatalogpreflight: TV5 [10targetedunitPASS](../reports/evidence/tv5-unblock-20261020/pr62.json),
diffcheck0. No fullOrder/PG rerun attributed to62, no provider verification/M01policy/wireapproval from its candidate tests.

### Lượt cục bộ trước 14:54 UTC — historical, not published

[Báo cáo cục bộ](../reports/TV5_PR63_W1_W2_CHECKPOINT_2026-10-20.md) ghi nhận scoped review #39
[5580637495](https://github.com/duyxl56785/Project_HK5/pull/39#pullrequestreview-5580637495) tại `4e4e623`
và [thông báo TV4](https://github.com/duyxl56785/Project_HK5/pull/39#issuecomment-6283126501), đã kiểm trên GitHub
trong phần việc trước theo ủy quyền cụ thể. Các nhãn “human review #39 còn chờ” phía trên là lịch sử;
không yêu cầu thực hiện lại review này. Scoped docs approval không phê duyệt Finance/COD/M01.
Không thực hiện Approve/Merge mới trong lượt cục bộ.

- #64 unit và #65 PG: **NOT_RUN / BLOCKED_TOOL_APPROVAL**. Bộ duyệt quyền báo hết hạn mức trước thực thi;
  không có runtime log/XML mới, không quy thành lỗi PR hoặc thay bằng kết quả CI/owner.
- Theo chỉ dẫn mới nhất, giữ thay đổi cục bộ **chưa commit/push**, không cập nhật GitHub.
  Head công bố gần nhất vẫn `a1bce60`; snapshot PR #63 lúc 11:04:48 Asia/Bangkok chưa được đọc lại.
- Issue #5/#12 vẫn thiếu gate production/business để DONE. Chi tiết owner, SHA và lệnh chờ chạy ở báo cáo.


### PR63 review follow-up — checkpoint trước công bố

[Current finding/test checkpoint](../reports/TV5_PR63_W1_W2_CHECKPOINT_2026-10-20.md):
TV1 P2 stale handoff được sửa cục bộ; remote a1bce60 vẫn CHANGES_REQUESTED cho đến khi công bố và reviewer kiểm lại.
Các dòng NOT_RUN/BLOCKED_TOOL_APPROVAL phía trên giữ lịch sử lượt trước, được thay thế bởi lượt mới đã qua phê duyệt:
#64 exact06ebfc4 **27 unit PASS**; #65 exact5bfbc15 initial10initializationFAIL vì Engine tắt,
sau Docker startup retry **10 PG PASS**. Raw FAIL/GREEN giữ riêng, không cộng counts/CI/owner thành release total.
M01 PENDING, flagsOFF. Phần trên ghi trước công bố; chỉ tick publication trên GitHub sau khi push và kiểm receipt.


### TV5 audit — 22/10/2026

- [x] Quét đủ toàn bộ PR/reviewers/Issues tại snapshot; [full report/index](../reports/TV5_ALL_PR_REVIEWERS_AUDIT_2026-10-22.md), PR63 P2 FIXED_VERIFIED/TV1 decision pending.
- [x] Exact66 e20263e:236unit+117integration PASS; exact67 add83ec:11Cartunit+1PG PASS. Fixture/prototype vs production/live scopes ghi riêng.
- [x] Main c2cf55a:27Inventoryunit/19PG/3originalFINdiagnostic PASS; invocation riêng30Reviewunit/49Financeunit PASS. Không cộng thành release total.
- [x] Thêm CartPG test-only supplement68: base RED4PASS/6FAIL →fix GREEN10PASS, sameQAhashes/assertions; e24731f pushed, TV3/TV2requested.
- [ ] Formal reviewer decisions, approved M01/COD/shared facts/runtime/API/Storequeries/atomic settlement/liveE2E; Issue5/12PARTIAL.

## Fresh R2 audit — 22/10/2026, after PR63/66/67 merge

- [x] Refresh all56PR/16Issue/reviews/threads/request-removal/CI/rules;39/63 now MERGED, no stale human approval action on closed PRs.
- [x] Verify69twoTV1findings on4eeeb384; actual27unit+34PG PASS, including1defaultV2/14candidate/19legacy PG invocations.
- [x] Focused70/84b3257 90PASS;71/5958990 53PASS;72/c0329a6 67PASS; exact-source derived evidence and private raw hashes.
- [x] Retarget68to main safely after normal merge;10PG on tested69297ba, finalae8ceaa is docs-only; reviewer requests checked, no production delta.
- [x] [Finance/reservation QA handoff](../docs/TV5_RESERVATION_FINANCE_QA.md), owner decision matrix and fresh RTM; no partial-stock→partial-refund assumption.
- [ ] Human review latest openheads; TV1 updates69oldCR; other requested reviewers respond within own scope.
- [ ] Named business authority/same-version M01/C06/currentAuth/source/COD approvals; production settlement/Store queries/liveUAT/Issue5/12DONE remain blocked.

Evidence: [R2 report and test commands](../reports/TV5_PR_REVIEW_FOLLOWUP_2026-10-22_R2.md). Current invocations are not summed with older runs or owner results.

### Subsequent PR74 UI verification delta

- [x] New74/165f5d9 source/DTO review,37tree/SSR/HappyDOM PASS and typecheck/app+feature builds exit0; no tracked source/dependency changes.
- [ ] Real browser/mobile/screen-reader/native keyboard/router/Gateway/API/liveE2E remain NOT_RUN; TV1 runtime integration pending.

[R2 sections7–8](../reports/TV5_PR_REVIEW_FOLLOWUP_2026-10-22_R2.md#7-new-pr74-discovered-during-final-scan--separate-verification-delta), [derived74evidence](../reports/evidence/tv5-audit-20261022-r2/pr74.json). Snapshot56PR and later58PR kept separate;37 is not added to earlier runs or owner counts.

## Independent verification — 23/10/2026

- [x]63PR/allreviews/16Issue audit;69TV1APPROVED exact4ee supersedesoldCR; no openTV5CHANGES_REQUESTED.71needs humanformaldecision, no newcode/repeatedtechnicalreview required.
- [x]77/cbbdb86 16unitPASS;78/ac5da33 108unit+29PGPASS (12caller/13journal subsets);79/f8a242d 19focusedPASS; source-specific evidence, no release total.
- [x]Main/e55f6b8 30Reviewunit+49Financeunit+6Intentunit and3FINPGdiagnostics PASS; not productionreviewDB/settlementproof or approvedM01.
- [x]74/2215d61 37logic/SSR/DOM+12realChromePASS;76/93f6a08 32model/SSR+11ChromePASS in separateinvocations; eachtypecheck/app+standalonefeaturebuild exit0.
- [x]375/1280screenshots visually inspected;768viewportasserted; private rawlog/XML/AX/screenshots hashes and derivedsummaries retained. Preview fixtures are not realaccount/API/UAT.
- [ ]Human71finalreview, TV4review78/79, authorDraftdispositions and normalmergegates; no agentApprove/Merge or duplicate owner requests.
- [ ]M01/designatedauthority/C06/COD/refund/currentAuth/currentStore/ProductSKU/atomicsettlement/liveE2E; Issue5/12/18remainPARTIAL.

[Report/commands/source/ownerledger](../reports/TV5_REVIEW_ACCEPTANCE_2026-10-23.md), [metadata/evidence](../reports/evidence/tv5-audit-20261023/repository-reviewers.json).

### QA23-AUTH01 — later current-authority real PostgreSQL diagnostic

- [x]80/f88baseline8newunit+28persistencePGPASS; actualSpringproxy/lazycurrentrole/faulttests added in own[DraftQA82](https://github.com/duyxl56785/Project_HK5/pull/82) from correctAuthhead.
- [x]Reproduced4PASS/1FAIL/exit1 with unchanged5caseassertions; failedLAZYrolequery leaksSQLGrammarException instead of promised503, [inlineP2](https://github.com/duyxl56785/Project_HK5/pull/80#pullrequestreview-5622704537), [deriveddelta](../reports/evidence/tv5-audit-20261023/auth-foundation-delta.json).
- [x]TV4responses recorded:79scopedAPPROVED,78COMMENT nofinding; do not repeat pendingreview claims from earlier datedsnapshot.
- [ ]TV1rootfix+sameQA GREEN atnewSHA, then reviewerverifiedclosure; no sourceAuth edit byTV5, no Finance/M01/DONE flag.

### QA23-AUTH02 — authorized P2 supplement GREEN, parent still waiting

- [x] Scoped P2 Auth explicitly authorized, minimal JPA read-failure→503 fix committed as ad25942998224451fb8c54677e028712e6921ae3 on TV5 PR82; no owner-branch push.
- [x] At exact fix commit:8unit+28AuthPersistencePG PASS, separate unchanged5current-authorityPG PASS/0FAIL/0skip, exit0. QA Java/init and original RED remain unchanged. Evidence3338cfd pushed; TV1/TV2 requests retained, no duplicates.
- [x]66PR/211review refresh and current owner replies reconciled; TV4 already COMMENT78/APPROVED79. Earlier checkpoints remain historical, not current pending claims.
- [ ] Parent80 stillf88/P2OPEN; TV1 review/integration and rerun on actual parent SHA before thread closure. Supplement GREEN does not tick parent fix or production acceptance.
- [ ] Human71formaldecision; designated M01business signer, C06/COD/currentAuth/Store/SKU/atomicsettlement/liveUAT. Issue5/12/18 remainOPEN/PARTIAL; Finance/pairOFF.

[Current handoff section9](../reports/TV5_REVIEW_ACCEPTANCE_2026-10-23.md#9-r2--bản-sửa-p2-được-ủy-quyền-và-bàn-giao-hiện-tại-23102026), [derivedsource/run/ownerledger](../reports/evidence/tv5-audit-20261023/auth-p2-green-refresh.json).

### QA79-REVIEW-20261023 — current S01 Checkout guard acceptance

- [x] Re-audit79 exactf8a242d/2commits/2files; TV2+TV4APPROVED, TV5COMMENT,0activeCR/0threads/0pendingrequests. No new actionable finding; no duplicate reviewer request.
- [x] Independent exact-head Orderbuild exit0;108unitPASS (19Checkout subset),25OrderPGPASS and separate3originalFINdiagnosticsPASS/0FAIL/0skip. Real PG is Order-local with mocked remote ports, not live Catalog/Inventory/COD/E2E.
- [x] S01 both Cart orders reject before reserve/write and release only confirmed Cart claim; sameStore2Products→1SellerOrder/35000 remains valid. Source/tests unchanged, original historical evidence retained.
- [ ] Human formal decision if needed and TV3Draft disposition; M01/business/currentAuth/Store/SKU/COD/atomicsettlement/liveUAT gates remain separate and pending. No Finance/pair enablement or IssueDONE.

[Focused report, findings, reviewers and member handoff](../reports/TV5_PR79_REVIEW_2026-10-23.md), [derived test/source/CI receipt](../reports/evidence/tv5-audit-20261023/pr79-comprehensive.json). Initial TV5/owner/CI test counts are separate historical invocations, not a combined release total.

### QA25 review and unblock delta — 25 October 2026

- [x] All75initialPR/230submittedreview/16Issue metadata audited;61merged/2closed-unmerged/12open. NewpublicationPRs are later events, not retroactive totals.
- [x] Actual80/c8parent14unit+28AuthPG+5unchangedauthorityPGPASS; ownP2resolved,92runbookcorrection pushed.82actuallyMERGEDintoAuthfeaturec8, notmain; oldclosed-unmergedownercheckpoint obsolete.
- [x] Independent83(33candidatePG),85(16unit/14PG),87(121focused),88(437unit/61PG),89(3unit/59PG),90(10unit/46PG),91(11unit/35PG)PASS,0fail/skip perrun; no cross-head release total.
- [x] Productprice84business+TV5LevelA+TV2mappingACK confirmed/MERGED; do not keep that slice pending. BroaderM01/Finance stillnotapproved.
- [x] Synthetic83after88compileFAIL retained separately: newcandidate testmissingexplicitcurrency. Baseline83/88remainPASS; TV2mustadapt atintegration, no defaultVND or assertionweakening.
- [ ] HumanLevelB88/formalreview andauthorDraftdisposition;86TV2SSRfindingOPEN/needsTV3fix; no agentapproval or other-reviewerresolve.
- [ ] RuntimecurrentAuth/Store/House/SKUprovider/C06/COD/refund/atomicsettlement/liveUAT. Issue5/12/18OPEN/PARTIAL,Finance/Inventoryflagsunchanged.

[Current all-PR handoff](../reports/TV5_PR_REVIEW_UNBLOCK_2026-10-25.md), [API/source/compatibility metadata](../reports/evidence/tv5-audit-20261025/repository-reviewers.json). Previous checkpoints remain dated history.

#### Later publication — explicitly authorized Level B88

- [x] Human explicitly authorized onlyLevelB88; [TV5APPROVED](https://github.com/duyxl56785/Project_HK5/pull/88#pullrequestreview-5639341936) read back at199211b. EarlierwaitingLevelBcheckboxes are historical; authorDraft/merge andallruntime/Financegatesremain separate.
- [x] COMMENTQA83/85/87/89/90/91published,83inlineintegrationcompilefinding handedtoTV2; no duplicateapproval/request or86threadtakeover.

[Current report section7](../reports/TV5_PR_REVIEW_UNBLOCK_2026-10-25.md#7-later-authorized-level-b-decision-and-published-review-handoff), [reviewreceipt](../reports/evidence/tv5-audit-20261025/review-publication.json).


### TV5 current Product/Seller review — 25/10/2026, later integration checkpoint

- [x]88 MERGED recognized;89/17f98f4 440unit+98PG,94/96d59ca 246unit+138PG,90/6a290ff 447unit+107PG,91/4915c37 448unit+96PG and83/c831edb 437unit+70PG PASS, each separate full build/exit0. No release-total sum.
- [x]Actual combined89→90→91 tree29d2904:461unit+179PG=640PASS in one fresh TV5 invocation. Not an individual PR head, CI or live E2E.
- [x]Current main ed85f62:109Orderunit+25PG+3original FIN01/06/control PASS. Existing technical bugs fixed, not policy/atomicsettlement acceptance.
- [x]All89/94/90/91 changed paths reviewed; no actionable introduced blocker found; READY_FOR_HUMAN_APPROVAL for their entire internal PR scopes.83own finding FIXED_VERIFIED; thread/publication receipt kept separately.
- [x]Current price-only business/LevelA/TV2ACK/LevelB88 receipt appended to Finance ledger, original signed packet/history retained.
- [ ]Human TV5 actual formal reviews; owner readiness/integration and runtime gates.95 source-only TV1approval is not HTTP/E2E; TV5 frontend runtime NOT_RUN in this checkpoint.
- [ ]FullM01/COD/refund/currentAuth/House/C01E01/MD07/reservation/atomicsettlement/Storequeries/live2StoreE2E; Issue5/12 OPEN/PARTIAL, flags unchanged.

[Current review, commands and member handoff](../reports/TV5_PRODUCT_INTEGRATION_REVIEW_2026-10-25.md); [source/CI/reviews/compatibility receipt](../reports/evidence/tv5-product-integration-20261025/review-context.json). Original raw logs/XML private and byte-preserved; publication derived, never mock-as-E2E.


#### Publication receipt — Product/Seller integration review

- [x] COMMENT89/94/90/91/83 published at exact tested heads; own83thread RESOLVED_FIXED_VERIFIED after507PASS. [Reply](https://github.com/duyxl56785/Project_HK5/pull/83#discussion_r4371081982).
- [x] Commit76e9957 pushed on existing TV5 PR93; own Issue5/12 evidence/checklists updated and historical content preserved, issues OPEN/PARTIAL. TV1/default TV2 re-requested once for substantive delta.
- [ ] Human formal review and external runtime/business gates remain pending. No agent APPROVE/Merge/Ready; no test rerun claimed for this receipt-only update.

[Published receipts](../reports/evidence/tv5-product-integration-20261025/publication.json), [current review report](../reports/TV5_PRODUCT_INTEGRATION_REVIEW_2026-10-25.md).
