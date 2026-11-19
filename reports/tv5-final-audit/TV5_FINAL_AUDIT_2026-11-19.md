# TV5 Final QA Audit — 19/11/2026

**Repository:** `duyxl56785/Project_HK5`
**Vai trò:** TV5 — QA Lead
**Kết luận tổng:** `PARTIAL / WAITING_FOR_TV3_CI_AND_TV2_FROZEN_SHA`

**Lần làm mới GitHub:** 19/11/2026; `git ls-remote origin` xác nhận `main=cee7c87d1c7437fc5ed0009405fd2d0fc394bfb2`.

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
| Base | `main` tại `cee7c87d1c7437fc5ed0009405fd2d0fc394bfb2` |
| PR | OPEN, chưa merge; TV3 đã đổi base sang main (PR updated `2026-11-19T09:18:43Z`) |
| CI | Run `44456964277`: E2E C2 ON 25 passed/0 skipped; E2E C2 OFF 24 passed/0 skipped; frontend typecheck/test/build thành công; run có trước khi đổi base, chưa có CI mới sau retarget |
| Formal review | Review TV2 hiện có tại HEAD cũ `a43abf5`, không phải exact HEAD `3483486`; cần owner review lại HEAD mới |

Sau khi #211 merge, TV3 đã đổi base #212 sang `main@cee7c87d`. CI mới sau retarget là run `44496245054`: job `frontend` SUCCESS; jobs `E2E C2 OFF` và `E2E C2 ON` vẫn `IN_PROGRESS` tại lần kiểm tra. Run `44456964277` là CI trước retarget, không thay thế run mới. Formal review TV2 hiện có vẫn gắn HEAD cũ `a43abf5`, không phải HEAD `3483486`. TV3 cần theo dõi run mới và xử lý kết quả; TV2 cần review exact HEAD. TV5 không tự thay đổi source/base của TV3.

### PR #214 — tài liệu TV5

| Hạng mục | Trạng thái đã kiểm tra |
|---|---|
| PR | OPEN, mergeable `true`, base `main` |
| HEAD sau lần push vừa xác minh | `bc9e539099fe8bcfadf62fc01fdc9d8f714d3e53` |
| Base | `main@cee7c87d1c7437fc5ed0009405fd2d0fc394bfb2` |
| Review/comments | Chưa có formal review hoặc comment; TV2 `duyxl56785` đang nằm trong requested reviewers |
| Changed files | 7 file TV5 docs/notes/matrix/audit, không có source code trước lần đồng bộ main |

TV5 chỉ cập nhật tài liệu thuộc ownership TV5; không merge PR #214. Sau khi cập nhật/push, xác nhận lại exact head, base và requested reviewer.

### Main / freeze

- Main hiện tại tại lần kiểm tra: `cee7c87d1c7437fc5ed0009405fd2d0fc394bfb2` (merge #211; `git ls-remote origin refs/heads/main` xác nhận).
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
| #212 Frontend/E2E `44496245054` | Frontend + E2E C2 ON/OFF | Frontend SUCCESS; both E2E jobs IN_PROGRESS | New run after retarget to `main@cee7c87d`; no E2E result yet |

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

- C2: OFF; #211 đã merge nhưng #212 mới retarget sang main, chưa merge/CI mới. Cần frozen-SHA CI có `voucher-c2.spec.ts` PASS không skip, không còn P0 và TV2 xác nhận bật cờ.
- Finance: NOT_APPROVED cho demo/production khi chưa có business approval/policy source đầy đủ. Không suy approval từ code review hoặc CI.
- Rate 0.05, scale 0 và house-store policy phải theo policy được phê duyệt; không tự bật `--approved-finance-policy`.
- Nếu gate C2 không đạt trước freeze, báo cáo phải ghi: **“đã code, đã test, chưa bật trong bản nộp”**. Không trình bày C2/Finance là chức năng đã nghiệm thu.

## D. Báo cáo Word và slide

| Tệp | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| `Bao_cao_Smart_ECommerce_ban_nop.docx` | MISSING FROM REPOSITORY | Đây là file Word chính thức TV2 chốt ngày 16/11; TV2 ghép sau E2E/freeze. TV5 không thay bằng draft của mình. |
| `Bao_cao_Soopi_TV5_DRAFT_2026-11-14.docx` | DRAFT, giữ nguyên | SHA256 `81abe78af6ae6d1bbe80f0b92465750beb97afda69d2149738e8c857c2202c12`; ZIP PASS. Không phải bản nộp chính thức. |
| `Soopi_TV5_notes_DRAFT_2026-11-14.pptx` | DRAFT, speaker notes cập nhật | SHA256 mới `5e4e503dc63e7cee75f9ba7ab1bc0c66b7c6206d1af092bb2cec8e5b4ac2920f`; ZIP PASS; 23 slides/23 notes; lượt cập nhật hiện tại chỉ đổi notes slide 16 và 23, không thay visible slides/media. |
| `TV5_REPORT_CONTENT.md` | Updated 19/11 | Bổ sung PR #211/#212, CI, merge candidate, frozen SHA và gate C2/Finance. |
| `TV5_DECK_SPEAKER_NOTES.md` | Updated 19/11 | Đồng bộ lời trình bày slide 8, 16, 23 với trạng thái hiện tại. |

Hình 6.1–6.8 phải được TV2 chụp trên frozen SHA theo mapping: 6.1 Homepage; 6.2 mobile 390px; 6.3 Seller product management; 6.4 Admin seller approval; 6.5 two-store cart; 6.6 Parent Order + two SellerOrders; 6.7 Seller isolation; 6.8 Finance/COD reconciliation. Không sử dụng rehearsal thay ảnh final.

## E. Issue và blocker tracker

| Hạng mục | Trạng thái | Owner | Evidence cần có | Điều kiện đóng |
|---|---|---|---|---|
| #211 merge integration | MERGED; readiness SUCCESS; frontend SUCCESS; E2E SUCCESS | TV2 | Merge SHA `cee7c87d`; runs `44493061534` / `44493061575` | CI hậu merge bắt buộc đã xanh; vẫn không thay Final UAT |
| #212 retarget/review | BASE UPDATED; CI IN_PROGRESS; WAITING_FOR_TV2_REVIEW | TV3 + TV2 | Base `main@cee7c87d`, run `44496245054`, review exact HEAD | CI và review exact HEAD đạt; sau đó TV2 quyết định merge |
| #214 TV5 docs | OPEN / WAITING_REVIEW | TV2 | Exact head, docs-only diff, `git diff --check` | TV2 review và phản hồi; TV5 xử lý feedback trong ownership |
| Frozen SHA | BLOCKED | TV2 | Full SHA, freeze time, CI URLs, PR list | TV2 xác nhận bằng văn bản |
| Final UAT #18 | BLOCKED / NOT_RUN | TV2/TV3/TV4/TV5 | Clean stack, exact SHA, HTTP/DB/Inventory/Finance receipts, screenshots | Bắt buộc cases đạt; P0 được xử lý/waiver rõ ràng |
| #157 Return/Finance | OPEN | TV5 + TV3/TV4 | RET-05 stock-once, Return/settlement UAT và các checkbox còn lại | Mọi tiêu chí issue có evidence, TV5 cập nhật trong phạm vi |
| #162 report/deck | OPEN | TV2 + TV5 | Official DOCX, final screenshots, frozen SHA, CI/UAT receipts | Đối chiếu bản ghép và đủ checklist trước nộp |
| Voucher C2 | BLOCKED / OFF | TV2 + TV3 | #211/#212 merge, frozen-SHA C2 E2E no skip, P0 triage | TV2/business approval và tất cả gate đạt |
| Finance policy | WAITING_FOR_APPROVAL | TV2/business | Policy source, signatures, rate/scale/house policy | Phê duyệt rõ ràng đúng phạm vi demo/production |
| Frontend dependency advisory | OPEN / P2 / FIX NOT VERIFIED | TV2 (đã nhận ownership TV1) | Main `frontend/package-lock.json` blob `28447aea3df83da544b5ff6bc77f5fc78e3e9612` vẫn khóa `vite@6.4.4`; truy vấn GHSA không tìm thấy issue/commit khớp, chưa đủ căn cứ xác nhận đã có PR fix | TV2 chọn bản vá tương thích và rerun frontend CI; TV5 không tự nâng dependency shared |

Trạng thái issue được fetch trực tiếp trong lượt làm mới 19/11: #18 OPEN (updated `2026-11-15T17:03:19Z`), #157 OPEN (updated `2026-11-10T03:49:14Z`), #162 OPEN (updated `2026-11-19T04:29:53Z`). #18/#162 có owner handoff và rehearsal/preflight history, nhưng chưa có Final UAT receipts trên frozen SHA; không tick hoặc đóng issue thay owner.

## F. Bàn giao theo owner

- **TV2:** post-merge CI #211 đã SUCCESS; review PR #214; kiểm tra CI/review mới của #212; xác nhận P0 scope, công bố frozen SHA; điều phối clean stack/Final UAT; cung cấp official DOCX và ảnh 6.1–6.8.
- **TV3:** đã đổi base #212 sang main; tiếp tục kiểm tra diff/conflict, chạy CI mới trên base main và xin formal review exact HEAD; phối hợp Cart/Checkout/Parent Order/SellerOrder trong UAT.
- **TV4:** xác nhận fulfillment/COD/Inventory/Return receipts trên frozen SHA; xử lý blocker restock nếu muốn đưa vào scope.
- **TV5:** đối chiếu actual receipts, điền ma trận chỉ theo kết quả thật, kiểm ảnh/tiền/report, hoàn tất QA sign-off sau khi gate đạt.

## G. Thay đổi TV5 trong phiên này

- Cập nhật ma trận 37 case và pointer acceptance trong worktree `docs/tv5-final-uat-c2-matrix-20261119`.
- Cập nhật nội dung báo cáo và speaker notes trong worktree review PR #211.
- Cập nhật speaker notes trong PPTX draft (slide 16 và 23; note slide 8 giữ nguyên từ checkpoint trước); không thay visible slide/media.
- Tạo audit report này.
- Branch `docs/tv5-final-uat-c2-matrix-20261119` đã push qua [PR #214](https://github.com/duyxl56785/Project_HK5/pull/214); commits trước đó gồm `4100cfa`, `6e06d84`, `d93a8fc`. Lượt này phát hiện #212 đã retarget sang main và CI hậu merge #211 đã hoàn tất; cập nhật tiếp bốn tài liệu TV5 trên cùng branch. TV2 đang được request review trên #214; chưa có review/comment.
- Không merge PR #214, không merge #212, không đổi base PR của TV3, không bật flags, không tick/đóng Issue.
- Worktree chính vẫn giữ nguyên các thay đổi chưa commit/untracked của người dùng; mọi thao tác diễn ra trong worktree riêng.

**Disposition:** PR #211 đã merge và post-merge readiness/frontend/E2E đều SUCCESS. TV3 đã đổi base #212 sang main; CI mới `44496245054` đang chạy (frontend SUCCESS, E2E ON/OFF IN_PROGRESS), TV2 chưa review exact HEAD. PR #214 chờ TV2 review. Main chưa được xác nhận frozen; Final UAT `NOT_RUN`; C2 OFF, Finance NOT_APPROVED, runtime flags NOT_VERIFIED. Release vẫn `PARTIAL / BLOCKED` cho tới khi CI/review #212 hoàn tất, có frozen SHA, clean-stack/clone receipts, evidence 37 case áp dụng và hình 6.1–6.8.
