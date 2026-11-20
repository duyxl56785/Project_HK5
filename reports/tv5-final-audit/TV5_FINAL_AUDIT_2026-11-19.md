# TV5 Final QA Audit — 19/11/2026

**Repository:** `duyxl56785/Project_HK5`
**Vai trò:** TV5 — QA Lead
**Kết luận tổng:** `PARTIAL / WAITING_FOR_TV2_FROZEN_SHA_AND_FINAL_UAT_RECEIPTS`

**Lần làm mới GitHub:** 20/11/2026; commit search xác nhận `main=1b652751c76e5558d76d0f1b9037c275cef3ba34` sau merge #215.

> **Delta sau checkpoint cũ:** PR #212 đã được TV2 APPROVE đúng HEAD `3483486` và MERGED vào `main` lúc `2026-11-19T10:05:23Z`; merge SHA `4b5bcb4`. PR #215 được TV5 APPROVED đúng HEAD `be8aa539` và squash-merged ngày 20/11 tại `1b652751`; decision records M01 được cập nhật. TV5 đồng bộ tài liệu QA/UAT hậu merge #215 và main SHA mới. Không dùng các đoạn mô tả trạng thái PR cũ trong tài liệu lịch sử làm trạng thái hiện hành.

## A. Trạng thái GitHub

### PR #211 — Voucher C2

| Hạng mục | Trạng thái đã kiểm tra |
|---|---|
| HEAD | `4f3152bc2188511a26e2b41f64fc94c6f31968ba` |
| Base | `main`, base SHA `879143a1f081da4644ebc29c0950069429731f0f` |
| Merge | MERGED `2026-11-19T09:04:26Z`; merge commit `cee7c87d1c7437fc5ed0009405fd2d0fc394bfb2` |
| Main sau merge | `cee7c87d1c7437fc5ed0009405fd2d0fc394bfb2` (first parent `4471256cacb8ea66693970701250d92f0a9082c4`) |
| TV2 | APPROVED tại exact HEAD |
| TV4 | APPROVED tại exact HEAD |
| TV5 | APPROVED tại exact HEAD; MD21 thread đã resolve |
| Review threads | Thread MD21 của TV5 đã resolved; không còn finding review TV5 mở tại HEAD kiểm tra |

**MD21:** finding cũ đã được khắc phục. Migration `V10__seller_order_voucher_snapshot_immutable.sql` tạo PostgreSQL trigger từ chối thay đổi `voucher_snapshot` sau INSERT; PostgreSQL regression kiểm native SQL sửa/xóa/gắn snapshot, no-op cùng giá trị và fulfillment update. Không sửa migration V9.

**Post-merge CI trên exact main merge SHA:** Backend readiness run `44493061575` `SUCCESS`. Frontend and E2E run `44493061534`: cả job `frontend` và `e2e` đều `SUCCESS`; step `Service logs` là `skipped` (không phải test skip). Hai run gắn exact SHA `cee7c87d1c7437fc5ed0009405fd2d0fc394bfb2`. CI hậu merge #211 đã hoàn tất xanh ở các job bắt buộc.

### PR #212

| Hạng mục | Trạng thái đã kiểm tra |
|---|---|
| HEAD | `3483486acd375e2c45616974ab333d2b850b9771` |
| Base | `main@cee7c87d` tại thời điểm retarget; merge target cuối là `main` |
| PR | MERGED `2026-11-19T10:05:23Z`; merge commit `4b5bcb4ceb5a6a636e4c943db2d41c8a4c1bb14f` |
| CI retarget | Run `44496245054` SUCCESS 3 jobs, nhưng checkout log dùng synthetic merge ref `90a09ea` trên nền PR #211 `4f3152b`, không phải `main@cee7c87d`; không coi là CI main sau tích hợp |
| Post-merge CI | Run `44502375038`, event push, exact SHA `4b5bcb4`; attempt 2 SUCCESS: frontend SUCCESS, C2 OFF 24/24, C2 ON 25/25, 0 skip, `voucher-c2.spec.ts` PASS; attempt 1 C2 ON dừng ở `up.mjs` do Gradle Plugin Portal dependency resolution và đã được rerun |
| Formal review | TV2 `APPROVED` đúng exact HEAD `3483486` lúc `2026-11-19T10:04:12Z`; review ghi nhận matrix ON/OFF và không có skip |

Sau khi #211 merge, **TV2** đổi base #212 sang `main` và mở lại PR để chạy CI. Run `44496245054` SUCCESS nhưng checkout log dùng synthetic merge ref `90a09ea` với nền PR #211 `4f3152b`, không phải `main@cee7c87d`; không dùng run này để chứng minh CI trên main. TV2 APPROVED đúng HEAD `3483486`; #212 được merge tại `4b5bcb4`. Post-merge push run `44502375038` gắn đúng SHA `4b5bcb4`, attempt 2 SUCCESS: frontend typecheck/unit/build PASS, E2E OFF 24/24, ON 25/25 (có `voucher-c2.spec.ts`), 0 skip, smoke `FAILS=0`. Attempt 1 ON gặp lỗi resolve dependency Gradle Plugin Portal trước Playwright; TV2 rerun job và attempt 2 thành công. Backend readiness không trigger do #212 không thay backend; run gần nhất `44493061575` gắn `cee7c87` sau #211. Đây vẫn là CI, không phải Final UAT.

### PR #215 — M01 decision records

PR #215 được TV5 APPROVED đúng HEAD `be8aa5392d7b0ee547885e2ff91c3ae0bee206db` (review ID `5988604288`) và squash-merged ngày 20/11/2026 tại `1b652751c76e5558d76d0f1b9037c275cef3ba34`. Diff chỉ cập nhật `M01_FINANCE_DECISION.md` và `M01_VOUCHER_C2_DECISION.md`: ghi mốc lịch sử 16/11, cập nhật chữ ký TV5/TV4 và lịch freeze/UAT mới. Không thay source/runtime, không sửa MD13–MD25. PR docs-only không có workflow run; ruleset `protect-main` yêu cầu một approval và không có CI check bắt buộc; approval TV5 exact HEAD thỏa số lượng review.

### PR #214 — tài liệu TV5

TV5 đã sửa 7 nhóm theo review TV2 ngày 19/11: (1) phân biệt retarget synthetic merge ref với main; (2) ghi post-merge CI run `44502375038` attempt 1/2; (3) Finance MD01–MD11 approved cho demo local/bản nộp, production chưa được phê duyệt và C2 không có gate Finance approval riêng; (4) bỏ thông tin tự tham chiếu PR #214 khỏi tài liệu; (5) ghi đúng TV2 là người đổi base #212; (6) cập nhật speaker notes Markdown/PPTX; (7) sửa đường dẫn M01 Voucher C2 thành `docs/project-report/M01_VOUCHER_C2_DECISION.md`. Decision file M01 do TV2 sở hữu; các cập nhật chữ ký/lịch/scope được ghi nhận tại PR #215.

### Main / freeze

- Main hiện tại tại lần kiểm tra: `1b652751c76e5558d76d0f1b9037c275cef3ba34` (squash merge #215 sau #212).
- Chưa có xác nhận của TV2 rằng SHA này là frozen SHA; không tự gán frozen SHA từ main hiện tại. Post-merge CI `44502375038` gắn parent `4b5bcb4`, không phải SHA `1b652751` hiện tại.
- Lịch TV2 mới nhất trên PR #211: freeze trước 18:00 ngày 20/11/2026; Final UAT ngày 21/11/2026.
- M01 Finance MD01–MD11 đã chọn cho bản nộp và demo local: 5%, HALF_UP/scale 0; Finance production chưa được phê duyệt. PR #215 đã merge decision record M01 tại `1b652751`; mục 8 Voucher C2 ghi nhận TV5/TV4 approvals, TV3 implementation và lịch freeze/UAT mới. C2 vẫn OFF cho tới khi đủ bốn gate, runtime flags chưa xác minh.
- Không có receipt clean-clone hoặc Final UAT trên frozen SHA tại thời điểm audit.

## B. Trạng thái QA và kiểm thử

### CI trên PR HEAD

| PR / Run | Phạm vi | Kết quả | Giới hạn |
|---|---|---|---|
| #211 Backend readiness `44454864588` | PostgreSQL Order integration | 156/156 PASS, 0 skipped | CI trên PR HEAD `4f3152b`, không phải merge commit/frozen SHA |
| #211 Frontend/E2E `44454864498` | Frontend + browser | 24 passed, 1 skipped | C2 OFF; `voucher-c2.spec.ts` không chạy |
| #212 Frontend/E2E `44456964277` | C2 ON browser | 25 passed, 0 skipped | PR HEAD `3483486`; CI trước khi retarget base sang main |
| #212 Frontend/E2E `44456964277` | C2 OFF browser | 24 passed, 0 skipped | PR HEAD `3483486`; CI trước khi retarget base sang main |
| #212 retarget `44496245054` | Frontend + E2E C2 ON/OFF | All 3 jobs SUCCESS; C2 OFF 24/24, C2 ON 25/25, 0 skipped | Synthetic merge ref `90a09ea` trên nền PR #211 `4f3152b`, không phải `main@cee7c87d` |
| #212 post-merge `44502375038` | Push CI trên main merge SHA | Attempt 2 SUCCESS; frontend PASS, C2 OFF 24/24, C2 ON 25/25, 0 skip, smoke `FAILS=0` | Exact SHA `4b5bcb4`; attempt 1 ON gặp dependency resolution failure trước Playwright, rerun attempt 2 PASS |

### TV5 local preflight trên ứng viên tích hợp

Worktree disposable được tạo từ `main@4471256`, sau đó merge `origin/codex/tv3-voucher-c2` với `--no-commit --no-ff`. Không sửa branch remote, không commit, không chạy stack có C2 ON.

| Kiểm thử | Kết quả |
|---|---:|
| Gradle `test` tasks: cart, inventory, order, product, seller với `--rerun-tasks` | 28 actions executed; 1.104 tests PASS; 0 failure, 0 error, 0 skipped; 72 XML suites |
| Frontend `npm test` | 185/185 PASS; 0 skipped |
| `npm run typecheck` | PASS |
| `npm run build` | PASS |
| `git diff --check` | PASS |
| `npm audit` | 1 moderate finding: direct dependency `vite@6.4.4`, GHSA-8g5g-mxqh-grgj (`server.fs.deny` bypass via queries); fix available |

1.104 là các test trong Gradle `test` tasks, **không phải** PostgreSQL integration tests. PostgreSQL 156/156 ở bảng trên là CI riêng của PR #211. Không cộng hai nguồn thành một số release.

### Final UAT

`FINAL_UAT_NOT_RUN`. Ma trận có 37 test cases (17 baseline, 5 Voucher C1, 4 Promotion, 11 C2). SHA và kết quả từng ca vẫn để trống; không có screenshot hoặc runtime receipt trên frozen SHA. Rehearsal/preflight owner-provided trước đó không được chuyển thành Final UAT. Các case UNKNOWN claim-conflict/lost-response và Return restock RET-05 vẫn BLOCKED/NOT_RUN theo quyết định/contract hiện có.

## C. Release gates: Voucher C2 / Finance

**C2: BLOCKED / OFF. Finance: APPROVED for local demo/submission only; production NOT APPROVED.**

- C2: #211/#212 đã MERGED; TV5 formal APPROVED phần tiền #211 đúng HEAD `4f3152b`; post-merge CI trên candidate SHA `4b5bcb4` attempt 2 có C2 ON 25/25, 0 skip. Còn thiếu TV2 xác nhận frozen SHA (và rerun CI nếu SHA freeze khác) cùng P0 C2 triage. Bốn điều kiện theo comment TV2 #211 ngày 18/11; không thêm điều kiện Finance business approval riêng ngoài M01 đã duyệt cho demo/local.
- Finance: `M01_FINANCE_DECISION.md` ghi MD01–MD11 ĐÃ CHỌN cho bản nộp và demo local; phí 5%, HALF_UP, scale 0. `HK5_FINANCE_POLICY_APPROVED` mặc định OFF và chỉ opt-in trong `.env.demo` local theo TV2. Finance production NOT_APPROVED; không suy production approval từ M01 demo/local.
- Runtime flags chưa được kiểm tra trong phiên audit; giữ `HK5_VOUCHER_C2_ENABLED=false` và `HK5_FINANCE_POLICY_APPROVED=false` theo default, không bật từ TV5.
- PR #215 đã merge decision records M01 tại `1b652751`; TV5 không sửa MD13–MD25. C2 vẫn BLOCKED/OFF do chưa có frozen-SHA CI và P0 C2 triage receipt.
- Nếu 2 gate C2 còn thiếu trước freeze, UAT chạy C2 OFF và báo cáo ghi **“đã code, đã test, chưa bật trong bản nộp”**. Không trình bày C2 là tính năng đã nghiệm thu.

## D. Báo cáo Word và slide

| Tệp | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| `Bao_cao_Smart_ECommerce_ban_nop.docx` | MISSING FROM REPOSITORY | Đây là file Word chính thức TV2 chốt ngày 16/11; TV2 ghép sau E2E/freeze. TV5 không thay bằng draft của mình. |
| `Bao_cao_Soopi_TV5_DRAFT_2026-11-14.docx` | DRAFT, giữ nguyên | SHA256 `81abe78af6ae6d1bbe80f0b92465750beb97afda69d2149738e8c857c2202c12`; ZIP PASS. Không phải bản nộp chính thức. |
| `Soopi_TV5_notes_DRAFT_2026-11-14.pptx` | DRAFT, speaker notes cập nhật | SHA256 `3b63baa7a0ec4f3e8134342579f13736988f76884d9142037742436523bf1d03`; ZIP/XML PASS; 23 slides/23 notes. So với `f31f3e3`, lượt hậu #215 chỉ đổi `notesSlide16.xml` và `notesSlide23.xml`; tổng so với bản nguồn đã đổi notes 8/16/23, slide XML/media giữ nguyên. |
| `TV5_REPORT_CONTENT.md` | Updated 19/11 | Bổ sung PR #211/#212, CI, merge candidate, frozen SHA và gate C2/Finance. |
| `TV5_DECK_SPEAKER_NOTES.md` | Updated 19/11 | Đồng bộ lời trình bày slide 8, 16, 23 với trạng thái hiện tại. |

Hình 6.1–6.8 phải được TV2 chụp trên frozen SHA theo mapping: 6.1 Homepage; 6.2 mobile 390px; 6.3 Seller product management; 6.4 Admin seller approval; 6.5 two-store cart; 6.6 Parent Order + two SellerOrders; 6.7 Seller isolation; 6.8 Finance/COD reconciliation. Không sử dụng rehearsal thay ảnh final.

## E. Issue và blocker tracker

| Hạng mục | Trạng thái | Owner | Evidence cần có | Điều kiện đóng |
|---|---|---|---|---|
| #211 merge integration | MERGED; readiness SUCCESS; frontend SUCCESS; E2E SUCCESS | TV2 | Merge SHA `cee7c87d`; runs `44493061534` / `44493061575` | CI hậu merge bắt buộc đã xanh; vẫn không thay Final UAT |
| #212 integration | MERGED | TV2 + TV3 | TV2 APPROVED exact HEAD `3483486`; merge SHA `4b5bcb4`; post-merge run `44502375038` attempt 2 SUCCESS (frontend, OFF 24/24, ON 25/25, 0 skip) | TV2 xác nhận frozen SHA; rerun nếu main tiến thêm |
| #215 M01 decision | MERGED tại `1b652751` | TV2 author; TV5 reviewer | TV5 APPROVED exact HEAD `be8aa539`; merge SHA `1b652751` | DONE; không sửa MD13–MD25 |
| Frozen SHA | BLOCKED | TV2 | Full SHA, freeze time, CI URLs, PR list | TV2 xác nhận bằng văn bản |
| Final UAT #18 | BLOCKED / NOT_RUN | TV2/TV3/TV4/TV5 | Clean stack, exact SHA, HTTP/DB/Inventory/Finance receipts, screenshots | Bắt buộc cases đạt; P0 được xử lý/waiver rõ ràng |
| #157 Return/Finance | OPEN | TV5 + TV3/TV4 | RET-05 stock-once, Return/settlement UAT và các checkbox còn lại | Mọi tiêu chí issue có evidence, TV5 cập nhật trong phạm vi |
| #162 report/deck | OPEN | TV2 + TV5 | Official DOCX, final screenshots, frozen SHA, CI/UAT receipts | Đối chiếu bản ghép và đủ checklist trước nộp |
| Voucher C2 | BLOCKED / OFF | TV2 + TV3 | #211/#212 merged; TV5 money review done; post-merge CI candidate `4b5bcb4` C2 ON 25/25, 0 skip; #215 chỉ đổi 2 decision docs, không đổi code/workflow paths | TV2 xác nhận frozen SHA + CI applicability cho code frozen (diff path nếu SHA chỉ đổi docs) + P0 C2 triage; TV2 xác nhận runtime flags trong `.env.demo` |
| Finance policy | APPROVED for local demo/submission; production NOT APPROVED | TV2/business | `M01_FINANCE_DECISION.md` MD01–MD11: 5%, HALF_UP/scale 0, demo local/bản nộp | Production scope cần quyết định riêng; runtime flag mặc định OFF và TV2 mới opt-in local |
| Frontend dependency advisory | OPEN / P2 / FIX NOT VERIFIED | TV2 (đã nhận ownership TV1) | Main `frontend/package-lock.json` blob `28447aea3df83da544b5ff6bc77f5fc78e3e9612` vẫn khóa `vite@6.4.4`; truy vấn GHSA không tìm thấy issue/commit khớp, chưa đủ căn cứ xác nhận đã có PR fix | TV2 chọn bản vá tương thích và rerun frontend CI; TV5 không tự nâng dependency shared |

Trạng thái issue được fetch trực tiếp trong lượt làm mới 20/11: #18, #157 và #162 đều OPEN. TV5 đã thêm comment handoff mới ngày 20/11 trên cả ba issue; comments yêu cầu frozen SHA/receipts TV2, Checkout/Order evidence TV3, Inventory/Return/COD evidence TV4 và ghi rõ #157 vẫn chờ #5/#12 cùng RET-05. Không tick hoặc đóng issue thay owner.

## F. Bàn giao theo owner

- **TV2:** công bố frozen SHA; xác nhận P0 C2 triage và runtime flags; review lại PR #214 exact latest HEAD; điều phối clean stack/Final UAT; cung cấp official DOCX và ảnh 6.1–6.8. M01 decision records đã được đồng bộ qua PR #215.
- **TV3:** PR #212 đã merge sau review exact HEAD; cung cấp Checkout/Cart/Order/Voucher receipts trên frozen SHA, xử lý blocker/feedback nếu phát sinh.
- **TV4:** xác nhận fulfillment/COD/Inventory/Return receipts trên frozen SHA; xử lý blocker restock nếu muốn đưa vào scope.
- **TV5:** đối chiếu actual receipts, điền ma trận chỉ theo kết quả thật, kiểm ảnh/tiền/report, hoàn tất QA sign-off sau khi gate đạt.

## G. Thay đổi TV5 trong phiên này

- Làm mới trạng thái PR #211/#212/#215 và Issues #18/#157/#162 trực tiếp từ GitHub; xác minh merge #215 tại `1b652751` và review TV5 exact HEAD #215 `be8aa539`. Không chạy Final UAT hoặc stack runtime.
- Tạo `TV5_FINAL_UAT_EXECUTION_CHECKLIST_20261121.md` với mục tiêu, prerequisites, steps, expected result, evidence, evidence owner và status cho đủ 37 case; `case-evidence-map.md` hiện có evidence path template riêng theo `<run-id>/<test-id>` cho cả 37 case, owner và status; `TV5_FINAL_UAT_EVIDENCE_INTAKE_20261121.md` quy định manifest, raw/redacted artifacts và receipt acceptance.
- Đồng bộ gate G-05 #212 MERGED và post-merge CI `44502375038` attempt 2 SUCCESS; G-06 vẫn chờ frozen SHA, G-07 chờ P0 triage.
- Sửa 7 nhóm review TV2 trong ma trận 37 case, acceptance, report content, Markdown speaker notes, audit và submission README; tạo thêm evidence kit tại `reports/minh-chung-kiem-thu/tv5-final-uat-20261121/`.
- Cập nhật speaker notes PPTX draft slide 8/16/23; sau cập nhật hậu merge #215, ZIP integrity PASS, 23 slide/23 notes; các nội dung slide hiển thị không đổi. SHA256 mới được ghi trong README sau lần đồng bộ tiếng Việt cuối.
- Không đưa thông tin HEAD/compare/mergeable tự tham chiếu PR #214 vào audit/report/acceptance. PR description đã cập nhật theo commit `a1c0714`; TV5 đã phản hồi 7 nhóm review bằng top-level comment và request TV2 review lại. Không tự approve/merge.
- Không merge PR #214, không bật C2/Finance, không tick/đóng Issue.

**Disposition:** #211/#212/#215 MERGED; #215 TV5 APPROVED exact HEAD. CI `44502375038` attempt 2 SUCCESS trên `4b5bcb4`; main hiện `1b652751` sau #215, và `git diff 4b5bcb4..1b652751` chỉ có hai decision docs M01, không đổi code/workflow paths. TV2 chưa xác nhận Frozen SHA hoặc bằng văn bản xác nhận CI applicability cho code frozen. Final UAT `NOT RUN`. C2 OFF; Finance approved cho local demo/submission nhưng production NOT_APPROVED; runtime flags NOT_VERIFIED. Release vẫn `PARTIAL / BLOCKED` tới khi TV2 xác nhận freeze/CI applicability, có clean-stack/clone receipts, evidence 37 case, xử lý P0/UNKNOWN/RET-05 và ảnh 6.1–6.8.
