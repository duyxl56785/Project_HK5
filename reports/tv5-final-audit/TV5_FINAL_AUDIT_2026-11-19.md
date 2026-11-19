# TV5 Final QA Audit — 19/11/2026

**Repository:** `duyxl56785/Project_HK5`
**Vai trò:** TV5 — QA Lead
**Kết luận tổng:** `PARTIAL / WAITING_FOR_TV2_INTEGRATION_AND_FROZEN_SHA`

## A. Trạng thái GitHub

### PR #211 — Voucher C2

| Hạng mục | Trạng thái đã kiểm tra |
|---|---|
| HEAD | `4f3152bc2188511a26e2b41f64fc94c6f31968ba` |
| Base | `main`, base SHA `879143a1f081da4644ebc29c0950069429731f0f` |
| Main hiện tại | `4471256cacb8ea66693970701250d92f0a9082c4` |
| PR | OPEN, chưa merge |
| GitHub mergeable | `true`; không được coi riêng giá trị này là bằng chứng tích hợp an toàn |
| TV2 | APPROVED tại exact HEAD |
| TV4 | APPROVED tại exact HEAD |
| TV5 | APPROVED tại exact HEAD; MD21 thread đã resolve |
| Review threads | Thread MD21 của TV5 đã resolved; không còn finding review TV5 mở tại HEAD kiểm tra |

**MD21:** finding cũ đã được khắc phục. Migration `V10__seller_order_voucher_snapshot_immutable.sql` tạo PostgreSQL trigger từ chối thay đổi `voucher_snapshot` sau INSERT; PostgreSQL regression kiểm native SQL sửa/xóa/gắn snapshot, no-op cùng giá trị và fulfillment update. Không sửa migration V9.

**Chênh lệch với main:** PR branch diverged so với `main@4471256` (PR có 13 commit riêng; main có 17 commit kể từ common ancestor). `git merge-tree --write-tree` và thử merge trong worktree disposable đều không conflict. Điều này chứng minh ghép cây tự động được, không chứng minh hosted CI trên merge commit hoặc Final UAT.

### PR #212

| Hạng mục | Trạng thái đã kiểm tra |
|---|---|
| HEAD | `3483486acd375e2c45616974ab333d2b850b9771` |
| Base | `codex/tv3-voucher-c2` tại `c5faad9e1cd460f15743d06e2b6d67688b448555` |
| PR | OPEN, chưa merge, chưa đổi base sang main |
| CI | Run `44456964277`: E2E C2 ON 25 passed/0 skipped; E2E C2 OFF 24 passed/0 skipped; frontend typecheck/test/build thành công |
| Formal review | Review TV2 hiện có tại HEAD cũ `a43abf5`, không phải exact HEAD `3483486`; cần owner review lại HEAD mới |

Không đổi base hoặc merge #212 trước khi TV2 merge #211. Sau đó TV3 đổi base, kiểm tra diff và chạy lại CI; owner liên quan review lại.

### Main / freeze

- Main hiện tại: `4471256cacb8ea66693970701250d92f0a9082c4`.
- Chưa có xác nhận của TV2 rằng SHA này là frozen SHA.
- Lịch TV2 mới nhất trên PR #211: freeze trước 18:00 ngày 20/11/2026; Final UAT ngày 21/11/2026.
- M01 hiện còn ghi lịch freeze 19/11 và chữ ký TV5/TV4 `CHỜ`. M01 thuộc owner TV2; TV2 cần đồng bộ tài liệu theo formal reviews và lịch mới.
- Không có receipt clean-clone hoặc Final UAT trên frozen SHA tại thời điểm audit.

## B. Trạng thái QA và kiểm thử

### CI trên PR HEAD

| PR / Run | Phạm vi | Kết quả | Giới hạn |
|---|---|---|---|
| #211 Backend readiness `44454864588` | PostgreSQL Order integration | 156/156 PASS, 0 skipped | CI trên PR HEAD `4f3152b`, không phải merge commit/frozen SHA |
| #211 Frontend/E2E `44454864498` | Frontend + browser | 24 passed, 1 skipped | C2 OFF; `voucher-c2.spec.ts` không chạy |
| #212 Frontend/E2E `44456964277` | C2 ON browser | 25 passed, 0 skipped | PR HEAD `3483486`, base cũ |
| #212 Frontend/E2E `44456964277` | C2 OFF browser | 24 passed, 0 skipped | PR HEAD `3483486`, base cũ |

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

`FINAL_UAT_NOT_RUN`. Ma trận có 37 test cases (baseline, Voucher C1, Promotion, C2 ON/OFF). SHA và kết quả từng ca vẫn để trống. Không có screenshot hoặc runtime receipt trên frozen SHA. Các case UNKNOWN claim-conflict/lost-response và Return restock RET-05 vẫn BLOCKED/NOT_RUN theo quyết định/contract hiện có.

## C. Release gates: Voucher C2 / Finance

**NOT APPROVED**

- C2: OFF cho đến khi #211 và #212 merge đúng trình tự, frozen-SHA CI có `voucher-c2.spec.ts` PASS không skip, không còn P0 và TV2 xác nhận bật cờ.
- Finance: NOT_APPROVED cho demo/production khi chưa có business approval/policy source đầy đủ. Không suy approval từ code review hoặc CI.
- Rate 0.05, scale 0 và house-store policy phải theo policy được phê duyệt; không tự bật `--approved-finance-policy`.
- Nếu gate C2 không đạt trước freeze, báo cáo phải ghi: **“đã code, đã test, chưa bật trong bản nộp”**. Không trình bày C2/Finance là chức năng đã nghiệm thu.

## D. Báo cáo Word và slide

| Tệp | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| `Bao_cao_Smart_ECommerce_ban_nop.docx` | MISSING FROM REPOSITORY | Đây là file Word chính thức TV2 chốt ngày 16/11; TV2 ghép sau E2E/freeze. TV5 không thay bằng draft của mình. |
| `Bao_cao_Soopi_TV5_DRAFT_2026-11-14.docx` | DRAFT, giữ nguyên | SHA256 `81abe78af6ae6d1bbe80f0b92465750beb97afda69d2149738e8c857c2202c12`; ZIP PASS. Không phải bản nộp chính thức. |
| `Soopi_TV5_notes_DRAFT_2026-11-14.pptx` | DRAFT, speaker notes cập nhật | SHA256 mới `8538a2ce0c976021377ca5c7619e3aa2e4d1a3f0dc4a714d158623004cac0f38`; ZIP PASS; 23 slides/23 notes; chỉ notes slide 8, 16, 23 thay đổi, không thay visible slides/media. |
| `TV5_REPORT_CONTENT.md` | Updated 19/11 | Bổ sung PR #211/#212, CI, merge candidate, frozen SHA và gate C2/Finance. |
| `TV5_DECK_SPEAKER_NOTES.md` | Updated 19/11 | Đồng bộ lời trình bày slide 8, 16, 23 với trạng thái hiện tại. |

Hình 6.1–6.8 phải được TV2 chụp trên frozen SHA theo mapping: 6.1 Homepage; 6.2 mobile 390px; 6.3 Seller product management; 6.4 Admin seller approval; 6.5 two-store cart; 6.6 Parent Order + two SellerOrders; 6.7 Seller isolation; 6.8 Finance/COD reconciliation. Không sử dụng rehearsal thay ảnh final.

## E. Issue và blocker tracker

| Hạng mục | Trạng thái | Owner | Evidence cần có | Điều kiện đóng |
|---|---|---|---|---|
| #211 merge integration | WAITING_FOR_TV2 | TV2 | Merge SHA, hosted CI trên merge/main | PR merge đúng quy trình, CI đạt |
| #212 rebase/review | WAITING_FOR_TV3 | TV3 | Base main, exact diff, CI mới, owner reviews | Chỉ sau #211 merge; CI và review exact HEAD đạt |
| Frozen SHA | BLOCKED | TV2 | Full SHA, freeze time, CI URLs, PR list | TV2 xác nhận bằng văn bản |
| Final UAT #18 | BLOCKED / NOT_RUN | TV2/TV3/TV4/TV5 | Clean stack, exact SHA, HTTP/DB/Inventory/Finance receipts, screenshots | Bắt buộc cases đạt; P0 được xử lý/waiver rõ ràng |
| #157 Return/Finance | OPEN | TV5 + TV3/TV4 | RET-05 stock-once, Return/settlement UAT và các checkbox còn lại | Mọi tiêu chí issue có evidence, TV5 cập nhật trong phạm vi |
| #162 report/deck | OPEN | TV2 + TV5 | Official DOCX, final screenshots, frozen SHA, CI/UAT receipts | Đối chiếu bản ghép và đủ checklist trước nộp |
| Voucher C2 | BLOCKED / OFF | TV2 + TV3 | #211/#212 merge, frozen-SHA C2 E2E no skip, P0 triage | TV2/business approval và tất cả gate đạt |
| Finance policy | WAITING_FOR_APPROVAL | TV2/business | Policy source, signatures, rate/scale/house policy | Phê duyệt rõ ràng đúng phạm vi demo/production |
| Frontend dependency advisory | OPEN / P2 | TV1 | `npm audit` result for `vite@6.4.4`, GHSA-8g5g-mxqh-grgj | TV1 chọn bản vá tương thích và rerun frontend CI; TV5 không tự nâng dependency shared |

Issues #18, #157, #162 vẫn OPEN tại thời điểm kiểm tra. Không tick hoặc đóng issue thay owner.

## F. Bàn giao theo owner

- **TV2:** kiểm tra merge candidate/main divergence; merge #211 nếu đủ điều kiện; cập nhật M01 chữ ký/lịch; xử lý #212 merge order; công bố frozen SHA; điều phối clean stack/Final UAT; cung cấp official DOCX và ảnh 6.1–6.8.
- **TV3:** sau khi #211 merge, đổi base #212 sang main; kiểm tra diff, CI và formal review exact HEAD; phối hợp Cart/Checkout/Parent Order/SellerOrder trong UAT.
- **TV4:** xác nhận fulfillment/COD/Inventory/Return receipts trên frozen SHA; xử lý blocker restock nếu muốn đưa vào scope.
- **TV5:** đối chiếu actual receipts, điền ma trận chỉ theo kết quả thật, kiểm ảnh/tiền/report, hoàn tất QA sign-off sau khi gate đạt.

## G. Thay đổi TV5 trong phiên này

- Cập nhật ma trận 37 case và pointer acceptance trong worktree `docs/tv5-final-uat-c2-matrix-20261119`.
- Cập nhật nội dung báo cáo và speaker notes trong worktree review PR #211.
- Cập nhật speaker notes trong PPTX draft (slide 8, 16, 23); không thay visible slide/media.
- Tạo audit report này.
- Branch `docs/tv5-final-uat-c2-matrix-20261119` đã push; commit nội dung `286597f` và checkpoint PR `a46a76b` (HEAD hiện tại). [PR #214](https://github.com/duyxl56785/Project_HK5/pull/214) mở vào `main`, TV2 `duyxl56785` đã được request review. PR #214 mergeable tại lần kiểm tra cuối; không có hosted workflow run/status check được trả về cho PR docs-only.
- Không merge PR #214, không merge #211/#212, không đổi base #212, không bật flags, không tick/đóng Issue.
- Worktree chính vẫn giữ nguyên các thay đổi chưa commit/untracked của người dùng; mọi thao tác diễn ra trong worktree riêng.

**Disposition:** TV5 phần review #211 và tài liệu QA đã được chuẩn bị; release/final acceptance vẫn `PARTIAL`, chờ TV2 tích hợp và frozen SHA.
