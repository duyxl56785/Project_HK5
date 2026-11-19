# TV5 Final QA Audit — 19/11/2026

**Repository:** `duyxl56785/Project_HK5`
**Vai trò:** TV5 — QA Lead
**Kết luận tổng:** `PARTIAL / WAITING_FOR_TV2_FROZEN_SHA_AND_FINAL_UAT_RECEIPTS`

**Lần làm mới GitHub:** 19/11/2026; commit search mới nhất và compare PR #214 xác nhận `main=4b5bcb4ceb5a6a636e4c943db2d41c8a4c1bb14f` sau merge #212.

> **Delta sau checkpoint cũ:** PR #212 đã được TV2 APPROVE đúng HEAD `3483486` và MERGED vào `main` lúc `2026-11-19T10:05:23Z`; merge SHA `4b5bcb4`. Những dòng phía dưới được cập nhật theo trạng thái mới này. Không dùng các đoạn mô tả #212 còn OPEN trong các tài liệu lịch sử khác làm trạng thái hiện hành.

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
| CI | Retarget run `44496245054`: frontend SUCCESS; E2E C2 OFF 24 passed/0 skipped; E2E C2 ON 25 passed/0 skipped; cả ba jobs SUCCESS trước merge |
| Formal review | TV2 `APPROVED` đúng exact HEAD `3483486` lúc `2026-11-19T10:04:12Z`; review ghi nhận matrix ON/OFF và không có skip |

Sau khi #211 merge, TV3 đổi base #212 sang `main@cee7c87d`; CI `44496245054` hoàn tất SUCCESS: frontend typecheck/unit/build PASS; E2E C2 OFF 24/24 và C2 ON 25/25, đều 0 skip, `FAILS=0`. TV2 sau đó APPROVED đúng HEAD `3483486`; #212 được merge vào main tại `4b5bcb4`. CI `44496245054` là CI retarget trước merge, không phải push CI sau merge hoặc frozen-SHA CI. Connector Actions không trả workflow run gắn merge SHA `4b5bcb4` trong lượt kiểm tra; TV2 cần cung cấp/xác nhận post-merge push run. TV5 không sửa source của TV3.

### PR #214 — tài liệu TV5

| Hạng mục | Trạng thái đã kiểm tra |
|---|---|
| PR | OPEN, GitHub `mergeable=false` tại lần kiểm tra |
| Remote HEAD | `c46138d830f4c65759bff1b9daab591d2db3d52f` |
| Base / compare | Base `main`; main `4b5bcb4`; compare `diverged`, PR branch ahead 22 / behind 4; merge-base `cee7c87d` |
| Review/comments | Chưa có formal review, inline thread hoặc comment; TV2 `duyxl56785` vẫn là requested reviewer |
| Changed files | 7 file TV5 docs/notes/matrix/audit; không có source business logic |

TV5 đồng bộ `main@4b5bcb4` vào clone riêng bằng merge không conflict, chưa push thay đổi đồng bộ này tại checkpoint audit. Cần cập nhật checklist thực thi/evidence intake thuộc ownership TV5, kiểm tra diff rồi mới push branch PR #214. Không merge PR #214.

### Main / freeze

- Main hiện tại tại lần kiểm tra: `4b5bcb4ceb5a6a636e4c943db2d41c8a4c1bb14f` (merge #212; commit search mới nhất và compare PR #214 cùng xác nhận).
- Chưa có xác nhận của TV2 rằng SHA này là frozen SHA; không tự gán frozen SHA từ main hiện tại.
- Lịch TV2 mới nhất trên PR #211: freeze trước 18:00 ngày 20/11/2026; Final UAT ngày 21/11/2026.
- M01 hiện còn ghi lịch freeze 19/11 và chữ ký TV5/TV4 `CHỜ`. M01 thuộc owner TV2; TV2 cần đồng bộ tài liệu theo formal reviews và lịch mới.
- Không có receipt clean-clone hoặc Final UAT trên frozen SHA tại thời điểm audit.

## B. Trạng thái QA và kiểm thử

### CI trên PR HEAD

| PR / Run | Phạm vi | Kết quả | Giới hạn |
|---|---|---|---|
| #211 Backend readiness `44454864588` | PostgreSQL Order integration | 156/156 PASS, 0 skipped | CI trên PR HEAD `4f3152b`, không phải merge commit/frozen SHA |
| #211 Frontend/E2E `44454864498` | Frontend + browser | 24 passed, 1 skipped | C2 OFF; `voucher-c2.spec.ts` không chạy |
| #212 Frontend/E2E `44456964277` | C2 ON browser | 25 passed, 0 skipped | PR HEAD `3483486`; CI trước khi retarget base sang main |
| #212 Frontend/E2E `44456964277` | C2 OFF browser | 24 passed, 0 skipped | PR HEAD `3483486`; CI trước khi retarget base sang main |
| #212 Frontend/E2E `44496245054` | Frontend + E2E C2 ON/OFF | All 3 jobs SUCCESS; C2 OFF 24/24, C2 ON 25/25, 0 skipped | Retargeted run on `main@cee7c87d`, exact HEAD `3483486` |

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

**NOT APPROVED**

- C2: OFF; #212 đã MERGED và TV2 APPROVED đúng HEAD; CI retarget C2 ON 25/25, 0 skip. Vẫn thiếu frozen-SHA CI, P0 triage, Finance business approval và runtime flag receipt. Chỉ TV2/business quyết định bật cờ sau khi đủ gate.
- Finance: NOT_APPROVED cho demo/production khi chưa có business approval/policy source đầy đủ. Không suy approval từ code review hoặc CI.
- Rate 0.05, scale 0 và house-store policy phải theo policy được phê duyệt; không tự bật `--approved-finance-policy`.
- Nếu gate C2 không đạt trước freeze, báo cáo phải ghi: **“đã code, đã test, chưa bật trong bản nộp”**. Không trình bày C2/Finance là chức năng đã nghiệm thu.

## D. Báo cáo Word và slide

| Tệp | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| `Bao_cao_Smart_ECommerce_ban_nop.docx` | MISSING FROM REPOSITORY | Đây là file Word chính thức TV2 chốt ngày 16/11; TV2 ghép sau E2E/freeze. TV5 không thay bằng draft của mình. |
| `Bao_cao_Soopi_TV5_DRAFT_2026-11-14.docx` | DRAFT, giữ nguyên | SHA256 `81abe78af6ae6d1bbe80f0b92465750beb97afda69d2149738e8c857c2202c12`; ZIP PASS. Không phải bản nộp chính thức. |
| `Soopi_TV5_notes_DRAFT_2026-11-14.pptx` | DRAFT, speaker notes cập nhật | SHA256 mới `70c2e0fd02a1bcdcffb4c84f8871944b6e58094f2df3d7049086d41dbcc9dac0`; ZIP PASS; 23 slides/23 notes; lượt cập nhật chỉ đổi notes slide 16 và 23, không thay visible slides/media. |
| `TV5_REPORT_CONTENT.md` | Updated 19/11 | Bổ sung PR #211/#212, CI, merge candidate, frozen SHA và gate C2/Finance. |
| `TV5_DECK_SPEAKER_NOTES.md` | Updated 19/11 | Đồng bộ lời trình bày slide 8, 16, 23 với trạng thái hiện tại. |

Hình 6.1–6.8 phải được TV2 chụp trên frozen SHA theo mapping: 6.1 Homepage; 6.2 mobile 390px; 6.3 Seller product management; 6.4 Admin seller approval; 6.5 two-store cart; 6.6 Parent Order + two SellerOrders; 6.7 Seller isolation; 6.8 Finance/COD reconciliation. Không sử dụng rehearsal thay ảnh final.

## E. Issue và blocker tracker

| Hạng mục | Trạng thái | Owner | Evidence cần có | Điều kiện đóng |
|---|---|---|---|---|
| #211 merge integration | MERGED; readiness SUCCESS; frontend SUCCESS; E2E SUCCESS | TV2 | Merge SHA `cee7c87d`; runs `44493061534` / `44493061575` | CI hậu merge bắt buộc đã xanh; vẫn không thay Final UAT |
| #212 integration | MERGED | TV2 + TV3 | TV2 APPROVED exact HEAD `3483486`; merge SHA `4b5bcb4`; CI retarget `44496245054` (frontend SUCCESS, E2E OFF 24/24, ON 25/25, 0 skip) | TV2 cung cấp post-merge push CI run và frozen-SHA CI |
| #214 TV5 docs | OPEN / WAITING_REVIEW; remote branch behind main 4 commits | TV5 + TV2 | Remote HEAD `c46138d`; main `4b5bcb4`; 7 docs-only files; no review/thread/comment; local clone merge from main is clean | TV5 hoàn thiện checklist/intake, push updated branch; TV2 review exact new HEAD |
| Frozen SHA | BLOCKED | TV2 | Full SHA, freeze time, CI URLs, PR list | TV2 xác nhận bằng văn bản |
| Final UAT #18 | BLOCKED / NOT_RUN | TV2/TV3/TV4/TV5 | Clean stack, exact SHA, HTTP/DB/Inventory/Finance receipts, screenshots | Bắt buộc cases đạt; P0 được xử lý/waiver rõ ràng |
| #157 Return/Finance | OPEN | TV5 + TV3/TV4 | RET-05 stock-once, Return/settlement UAT và các checkbox còn lại | Mọi tiêu chí issue có evidence, TV5 cập nhật trong phạm vi |
| #162 report/deck | OPEN | TV2 + TV5 | Official DOCX, final screenshots, frozen SHA, CI/UAT receipts | Đối chiếu bản ghép và đủ checklist trước nộp |
| Voucher C2 | BLOCKED / OFF | TV2 + TV3 | #211/#212 merged; PR-head/retarget C2 ON 25/25, 0 skip; frozen-SHA C2 E2E chưa có | TV2/business approval, frozen-SHA CI, P0 triage, runtime flags được xác nhận |
| Finance policy | WAITING_FOR_APPROVAL | TV2/business | Policy source, signatures, rate/scale/house policy | Phê duyệt rõ ràng đúng phạm vi demo/production |
| Frontend dependency advisory | OPEN / P2 / FIX NOT VERIFIED | TV2 (đã nhận ownership TV1) | Main `frontend/package-lock.json` blob `28447aea3df83da544b5ff6bc77f5fc78e3e9612` vẫn khóa `vite@6.4.4`; truy vấn GHSA không tìm thấy issue/commit khớp, chưa đủ căn cứ xác nhận đã có PR fix | TV2 chọn bản vá tương thích và rerun frontend CI; TV5 không tự nâng dependency shared |

Trạng thái issue được fetch trực tiếp trong lượt làm mới 19/11: #18 OPEN (updated `2026-11-15T17:03:19Z`), #157 OPEN (updated `2026-11-10T03:49:14Z`), #162 OPEN (updated `2026-11-19T04:29:53Z`). #18/#162 có owner handoff và rehearsal/preflight history, nhưng chưa có Final UAT receipts trên frozen SHA; không tick hoặc đóng issue thay owner.

## F. Bàn giao theo owner

- **TV2:** xác nhận post-merge push CI #212; review PR #214; xác nhận P0 scope, công bố frozen SHA; điều phối clean stack/Final UAT; cung cấp official DOCX và ảnh 6.1–6.8.
- **TV3:** PR #212 đã merge sau review exact HEAD; cung cấp Checkout/Cart/Order/Voucher receipts trên frozen SHA, xử lý blocker/feedback nếu phát sinh.
- **TV4:** xác nhận fulfillment/COD/Inventory/Return receipts trên frozen SHA; xử lý blocker restock nếu muốn đưa vào scope.
- **TV5:** đối chiếu actual receipts, điền ma trận chỉ theo kết quả thật, kiểm ảnh/tiền/report, hoàn tất QA sign-off sau khi gate đạt.

## G. Thay đổi TV5 trong phiên này

- Làm mới trạng thái PR #212/#214 trực tiếp từ GitHub; xác minh review TV2 exact HEAD `3483486`, merge commit #212 `4b5bcb4`, compare/base và changed files PR #214. Không chạy Final UAT hoặc stack runtime.
- Tạo `TV5_FINAL_UAT_EXECUTION_CHECKLIST_20261121.md` với mục tiêu, prerequisites, steps, expected result, evidence, evidence owner và status cho đủ 37 case; tạo `TV5_FINAL_UAT_EVIDENCE_INTAKE_20261121.md` cho cấu trúc folder/manifest, owner mapping, raw/redacted artifacts và receipt acceptance.
- Đồng bộ gate G-05 #212 MERGED; giữ G-06 frozen-SHA CI NOT RUN và G-08 Finance/C2 BLOCKED.
- Đồng bộ ma trận 37 case, acceptance, report content, Markdown speaker notes, audit và submission README với CI mới sau retarget #212.
- Cập nhật speaker notes trong PPTX draft ở slide 16 và 23; ZIP integrity PASS, 23 slide XML/23 notes XML. Chỉ hai notes XML thay đổi; toàn bộ slide XML và media giữ nguyên. SHA256 mới: `70c2e0fd02a1bcdcffb4c84f8871944b6e58094f2df3d7049086d41dbcc9dac0`.
- Các commit đồng bộ văn bản trên branch gồm `51218bf`, `a079680`, `29de073`, `ece68b3`, `13c9e63`, `c809fba`; commit PPTX/README `62019fc`; commit phân biệt retarget CI với frozen-SHA gate `c685d5536fa5cdc34e9045b913548af2a0c20e2d`. PR #214 vẫn OPEN, TV2 đang được request review; chưa có review/comment.
- Clone riêng đã fetch GitHub `main@4b5bcb4` và merge vào branch tài liệu không conflict; merge commit local `f485738cc6eb7ef69761c03e13d1d5cee1780857`. Checklist/evidence guide đang được chuẩn bị trên clone, chưa push tại thời điểm audit này.
- Workspace gốc vẫn ở `codex/tv5-w1-acceptance-evidence` và các mục untracked `.vs/`, `frontend/package-lock.json`, `reports/tv5-m01-local/` được giữ nguyên.
- Không merge PR #214, không bật C2/Finance, không tick/đóng Issue.

**Disposition:** PR #211/#212 đã merge; TV2 APPROVED #212 đúng HEAD `3483486`; retarget CI `44496245054` xanh (frontend, OFF 24/24, ON 25/25, 0 skip). Chưa xác minh được post-merge push CI #212 trên merge SHA `4b5bcb4`. PR #214 OPEN, chờ TV2 review; branch remote sau main 4 commits và đã được đồng bộ clean trong clone riêng. Main `4b5bcb4` chưa được TV2 xác nhận frozen; Final UAT `NOT RUN`; C2 OFF, Finance NOT_APPROVED, runtime flags NOT_VERIFIED. Release vẫn `PARTIAL / BLOCKED` cho tới khi có frozen SHA, post-merge/frozen-SHA CI, clean-stack/clone receipts, evidence 37 case áp dụng, xử lý P0/UNKNOWN/RET-05 và hình 6.1–6.8.
