> **Current R2 checkpoint 22/10/2026:** [fresh56PR audit, merged63/66/67, open68–72 and exact-head QA](../reports/TV5_PR_REVIEW_FOLLOWUP_2026-10-22_R2.md). Earlier checkpoints below remain historical. M01 PENDING; Finance production BLOCKED.

# Checklist bàn giao độc lập tuần 1 của TV5

> **Latest audit 22/10/2026:** [52 PR / toàn bộ reviewers và current QA](../reports/TV5_ALL_PR_REVIEWERS_AUDIT_2026-10-22.md).
> Các checkpoint20/10 bên dưới giữ lịch sử; #65 nay MERGED, #66/#67/#68 được theo dõi riêng. M01 PENDING, Finance production BLOCKED.


> **Current handoff:** [post-snapshot delta](../reports/TV5_OWNER_UNBLOCK_HANDOFF_2026-10-20.md#12-post-snapshot-status-delta--current-actions)
> (query 2026-10-20T14:55:26.142195+00:00): #39/#61 MERGED; #60/#62 CLOSED WITHOUT MERGE.
> Những next action yêu cầu review các PR đó phía dưới là lịch sử. Không assume #60/#62 đã tích hợp.
> Delta soạn trước công bố; người dùng đã cho phép cập nhật PR #63/Issue #5/#12. Xem receipt tại PR/Issue; M01 vẫn PENDING, Finance production BLOCKED.


Refs #5, PR #20. Checklist này chia nhỏ công việc tuần 1; không thay tiêu chí nghiệm thu F15/F16 hoặc gate owner/consumer. Tick nghĩa là phần việc nêu trong dòng đã có bằng chứng, không có nghĩa cả Issue DONE.

## Phần độc lập có evidence

- [x] Rules Review: buyer/DELIVERED/product, stars1–5, duplicate precheck, Admin policy và hidden/public average — source/test `product-service/.../review`, `tests/TV5_TRACEABILITY.md` F15-01..08.
- [x] Rules Finance:5%/House0, seller_net, precision, eligibility và Store guard — source/test `order-service/.../finance`, RTM F16-01..08.
- [x] Baseline63 unit test invocations, có raw XML/log, testcase CSV và source/evidence hashes — `reports/evidence/w1-followup/`. Đây là local rules, không phải HTTP/Kafka/API/DB/live E2E.
- [x] Đối chiếu báo cáo V2 55 trang và ghi16 mục theo chương/trang; checklist F02, ảnh B.1–B.5, seed/admin-products NOT_RUN và NFR/nguồn tham khảo — `reports/TV5_V2_RECONCILIATION.md`. Chưa xác minh lại URL bên ngoài hoặc source/log V2 gốc.
- [x] Ma trận yêu cầu→test→owner→evidence — `tests/TV5_TRACEABILITY.md`; owner phối hợp và trạng thái theo từng dòng.
- [x] Thiết kế QA/UAT hai Store, negative ownership, replay, cancel/stock — `tests/TV5_UAT.md`; chưa chạy live.
- [x] Bảng12 lỗi/gap/blocker có severity và owner — `reports/TV5_W1_DEFECTS.md`.
- [x] Đề xuất contract/schema và10 câu hỏi cần owner xác nhận — `docs/TV5_CONTRACT_PROPOSAL.md`, `docs/TV5_W1_DECISIONS.md`; mới hoàn thành draft, chưa chốt thay owner.
- [x] Lưu quy trình lâu dài KIỂM TRA → THỰC HIỆN → TEST → TICK → COMMIT → PUSH → REVIEWER → BÁO CÁO trong `AGENTS.md`, gồm reviewer mặc định `duyxl56785`, giới hạn scope và bảo toàn evidence.

## Chờ owner hoặc giai đoạn được giao tiếp theo

- [x] Rules/QA/docs đã được đưa vào main qua PR #20, merge578a8487 (head8d02e74); đã đối chiếu GitHub và nội dung main. Đây là bàn giao source/tài liệu, không xác nhận production F15/F16.
- [ ] WAITING_REVIEW: reviewer xem QA matrix/evidence mới ngày17/10; approval hoặc merge của PR cũ không tự bao gồm bàn giao này.
- [ ] BLOCKED/WAITING_REVIEW: owner xác nhận các quyết định còn mở, có đối chiếu tài liệu Auth/contract mới trên main; không mặc định D01–D10 đã được chốt.
- [ ] BLOCKED: bốn nhóm doanh thu/currency/order key/event/HTTP mapping và các provider-consumer contracts đủ rõ để kiểm thử.
- [ ] NOT_RUN: persistence/unique DB/atomic settlement/concurrency và security adapters production; không triển khai trong lượt thiết lập quy trình W1.
- [ ] NOT_RUN: thực thi live UAT/Gateway/frontend/ảnh B.1–B.5/load test; tuần1 chỉ thiết kế và ghi thiếu sót.
- [ ] Gate tuần1/Issue #5 được nghiệm thu đầy đủ theo tiêu chí và owner/consumer; hiện chỉ PARTIAL.

Các file trên giữ bằng chứng và trạng thái của từng lần bàn giao. Thông tin PR nào đã merge/reviewer nào vừa phản hồi phải đọc GitHub ở mỗi phiên, không dùng checklist này thay trạng thái live.

## Kiểm chứng bổ sung tuần 1 — 17/10/2026

- [x] Đối chiếu PR #19 đã tiếp nhận #21: blob kế hoạch giống hệt5e3d39835fcfbcd5b8a35c267ba34aa2d94dbca2; không cần đưa lại bản sửa hoặc tự Approve.
- [x] Kiểm PR #26 headc4288d5 đã push đủ3 file sửa,21 ID duy nhất/21 NOT_RUN và Wrapper hiện có. Chỉ nghiệm thu kiểm tra tài liệu, chưa tick runtime TV4.
- [x] Rerun rules TV5 trên main98268c2:63 PASS; lưu raw log/XML và manifest.
- [x] Chạy PostgreSQL thật #29:21 PASS, #28:3 PASS trên snapshot đúng head; giữ riêng kết quả lịch sử do TV2 cung cấp.
- [x] Tái hiện timezone #27: Asia/Saigon1 PASS/2 FAIL, cùng source UTC3 PASS; provision/isolation/rerun PASS trên test project riêng; container/volume trước test không bị mất.
- [x] Lập [QA matrix và ledger owner/blocker](../reports/TV5_W1_ACCEPTANCE_2026-10-17.md), có evidence cho từng kết luận.
- [ ] TV1 xác nhận hướng dẫn timezone; TV3 sửa3 finding #28; owner/consumer chốt các quyết định Finance/Review còn mở.
- [ ] Bàn giao QA mới được reviewer xác nhận; Issue #5 đủ gate nghiệm thu. Giữ OPEN/PARTIAL.

## Bản sửa đã được ủy quyền — supplemental PRs

- [x] #19: sửa HS256/profile và toàn bộ chỉ đạo PR #7 lỗi thời, giữ nội dung F15/F16; commit `0c77df6`/PR #32 đã push, static checks PASS; TV1 đã Approve đúng head.
- [x] #28: test tái hiện JWT/replay/scanner và failure-handler race; sửa theo nguyên nhân, giữ V1/enum/money policy. Commits `4e4ab83`/`59dc952`/`11dcf39`, PR #33; final 56 unit + 11 PG PASS, raw FAIL/PASS và 43 artifact hashes khớp Git.
- [x] #27: kiểm lại timezone bằng DB riêng, giữ FAIL Asia/Saigon; UTC và Asia/Ho_Chi_Minh đều 3 PASS. Khi owner push `a810312`, đã giữ bản sửa đó và bàn giao evidence bổ sung qua `369b4f8`/PR #34; 32 hashes khớp Git, dữ liệu Docker có sẵn được giữ.
- [x] Cập nhật QA/TEST_RESULTS, Issue và PR gốc; dùng branch riêng đúngbase, không tự resolve thread/re-add reviewer đã bị gỡ hoặc Approve/Merge/Close trong đợt sửa này.
- [ ] Owner tiếp nhận patch/evidence vào#19/#28/#27, kiểm head mới và ra quyết định review chính thức.
- [ ] D02–D10/provider-consumer Review/Finance và toàn gate W1 được owner xác nhận; Issue#5 vẫn PARTIAL.

## Audit reviewers và head sau tích hợp

- [x] Kiểm toàn bộ Open/Draft, reviews/comments/threads/CI và removal events; [bảng audit](../reports/PR_REVIEWERS_AUDIT_2026-10-17.md).
- [x] Xác minh #32/#33 đã được tác giả nhận vào #19/#28; Git tree giống bản sửa đã kiểm, request lại reviewer phù hợp. Không tự đóng review/thread cũ.
- [x] Sửa lỗi biên dịch test mới của #31 trên branch riêng; RED compile FAIL trước sửa, GREEN 15 unit PASS; commit `afc9461`/`14df61b`, push và mở #35 đúng base, raw log/XML/hash đầy đủ.
- [ ] TV4 tiếp nhận #35; reviewer quyết định lại #19/#27/#28 và nghiệm thu QA/evidence #30/#34. Parent #31 vẫn Draft; các contract/gate owner chưa đủ thì không tick DONE.
- [x] Đọc phản hồi mới trong lúc audit: ghi nhận TV1 APPROVED #34, TV2 xác nhận fixes và tách 3 DB probes FAIL #31 do TV2 cung cấp khỏi unit evidence TV5. Không coi #35 đã sửa DB hoặc review COMMENT là approval.
- [ ] TV4 xử lý ba DB findings #31 và cung cấp regressions; TV1/TV3 review phần schema/auth/contract thuộc ownership. TV5 chưa chạy lại các probes đó.

## Checkpoint 18/10/2026 — sau khi owner tích hợp

Các checklist trước giữ snapshot lịch sử; không tick các mục ghép khi contract/review vẫn thiếu.

- [x] Xác minh #35/#36/#31 đã tích hợp; ba DB defects cũ đã có source fixes/regressions. Không request review trên PR đã đóng.
- [x] Audit đủ5 PR đang mở #19/#28/#37/#38/#39 và kiểm reviewer events/current-head reviews trước khi request.
- [x] Chạy mới exact-head suites: #28 61unit; #37 291unit+49PG; #38 17unit+15PG, tất cả0fail/error/skip; giữ source/prototype/live scope riêng.
- [x] QA/RTM kiểm #39 có19case và ID baseline thay nghĩa; xác nhận nguyên tắc COD/Finance có sẵn, giữ M01/currency/state/auth/schema PENDING.
- [x] Đăng5 review đúng inspected heads và reconcile reviewer: COMMENT #19/#28/#37/#38, CHANGES_REQUESTED #39; request TV1/#39, không request trùng hoặc self-request tác giả. [Links](../reports/PR_REVIEWERS_AUDIT_2026-10-18.md#5-review-đã-đăng-và-reviewer-đã-đối-chiếu).
- [ ] Owner xác nhận các quyết định/tài liệu tương ứng; không tự Approve/Merge/Close/resolve threads hoặc chốt toàn contract.
- [ ] Toàn bộ điều kiện Issue #5 đủ DONE. Giữ OPEN/PARTIAL.

## Current owner handoff — 20/10/2026

Các checkbox trước là snapshot lịch sử, không yêu cầu owner sửa lại bug hoặc review lại PR đã merge.
[Current21task matrix, MD/COD answers and evidence](../reports/TV5_OWNER_UNBLOCK_HANDOFF_2026-10-20.md).

- [x] Verify45PR/16Issue;46/58/59merged;39onlyformalTV5review cònchặn docs; new60/61 read/code-doc cross-check.
- [x] Current main03fb1b2 rules/securityunit andPG/originalFIN01/06 diagnostic thực thi; noassertionchanges, retainDockerinitializationFAIL.
- [x] PR60 e0a28de focused11callerunit+11journalPGPASS;PR61 static21links/10anchors; nofalseHTTP/E2Eclaim.
- [x] Existing1billionProductunit candidate/partialscope andTV3/TV4engineering replies recorded; sharedauthority/MDsignatures stillPENDING.
- [x] Preparepreciseownerhandoff and mapcurrentevidence toRTM/QA; existingUAT/SP01–14plans retained, noformalsignoffbyagent.
- [x] Commit/push and GitHubpublication verified; review60/61COMMENT, Issue5/12checkpoint, reviewereligibility/removalchecks. Commitb923736/PR63, TV1/TV2requested; [receipt](../reports/evidence/tv5-unblock-20261020/publication-receipt.json).
- [x] Late PR62/6a79016 fullcandidate diff/Financeboundary reviewed,10focusedpureunitPASS/diffcheck0; [evidence](../reports/evidence/tv5-unblock-20261020/pr62.json). NoHTTP/PG/M01approval implied.
- [x] FinalPR39headchanged4e4e623: readnewdelta/C06ack, rerun21legacy+21supplementalstatic,all3findingsstillFIXED_VERIFIED; [newheadproof](../reports/evidence/tv5-unblock-20261020/pr39-current-static.json). Humanformalreviewstillpending,newheadmustbereviewed.
- [x] Formal review #39 và kiểm disposition đã hoàn tất; PR39 MERGED04:06:41Z tại4e4e623. Xem current delta; không yêu cầu review PR đã đóng, không phê duyệt Finance/COD.
- [x] Superseding verified39disposition: [scopedAPPROVED5580637495](https://github.com/duyxl56785/Project_HK5/pull/39#pullrequestreview-5580637495) at4e4e623 under explicitpriorTV5authorization; effectiveAPPROVED/clean/0threads atcheck, [TV4notified](https://github.com/duyxl56785/Project_HK5/pull/39#issuecomment-6283126501). Oldpendingrow remains historical; noFinance/runtimeapproval oragentMerge.
- [ ] Công bố checkpoint #39/W1–W2 lên PR #63 và Issue #5/#12: chờ thực thi và xác minh sau ủy quyền mới. Không tick publication từ bản nháp chưa có receipt.
- [ ] Namedbusinessauthority/applicablesignedcontracts/productionReview-Finance-COD-Storequery-E2Eacceptance;Issue5/12remainOPEN/PARTIAL.


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
