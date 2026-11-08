# TV3 — Đối chiếu bản Checkout Codex 351 dòng với báo cáo PR #188

**Trạng thái:** `STRUCTURAL_COMPARISON_DONE` / `EXACT_LINE_DIFF_NOT_RUN` / `ORIGINAL_FILE_INACCESSIBLE`.

## 1. Nhận dạng nguồn, không được đánh đồng

| Nguồn | Vị trí / định danh | Tình trạng |
| --- | --- | --- |
| A — bản Codex gốc | `/workspace/tv3-checkout-project-report-20261107/docs/project-report/tv3/CHECKOUT.md` | Codex báo **351 dòng**, `git status: ??`, chưa commit/push; phiên ChatGPT hiện tại không truy cập được workspace đó |
| B — bản GitHub có thể review | [PR #188 `docs/project-report/tv3/CHECKOUT.md`](https://github.com/duyxl56785/Project_HK5/pull/188) | Tái dựng từ Issue #160, **không phải bản sao** của A; chỉ nhánh tài liệu, vẫn Draft |
| C — tóm tắt của Codex do TV3 cung cấp | Nội dung nhắn trong Project ngày 07/11/2026 | Có mô tả các mục và tự kiểm 41 internal links, 8 Github HTTP200, 234 SHA256; **không có toàn văn hoặc raw evidence** trong phiên này |
| D — bản báo cáo Word nhóm | `Bao_cao_Smart_ECommerce.docx` đã được chia sẻ trong Project | Bản 12 trang, nội dung mốc 25/10; trang 10 mục 6 Hình 6.4 vẫn là ô `[CHÈN ẢNH DEMO THỰC TẾ]` |

**Không được ghi "đối chiếu 351/351 dòng PASS"** chỉ từ số dòng hoặc tóm tắt A. Cần chạy diff thực tế khi có quyền đọc A.

## 2. Bản đối chiếu theo mục/khả năng (không phải diff từng dòng)

| Hạng mục do Codex báo cáo cho A | Kiểm tra bản B | Kết luận hiện tại |
| --- | --- | --- |
| Mục tiêu, kiến trúc Java/React/PG | Có phần 1–2, nguồn GitHub main theo full SHA | PRESENT; chờ so wording với A |
| Hai sơ đồ luồng/transaction | Có hai Mermaid diagrams | PRESENT; chưa biết khác sơ đồ A |
| Cart→Checkout→Inventory→Order | Có phần 3, giải thích 7 bước từ source | PRESENT |
| API, database, tiền/snapshot | Có phần 3–4 và source links | PRESENT; cần đối chiếu các bảng DB chi tiết A |
| Idempotency/rollback/UNKNOWN | Có, giữ `NOT_RUN/BLOCKED` cho fault thật | PRESENT |
| Evidence test theo SHA và Test type | Có phần 5, bổ sung PR #187 hosted branch 140 PG/166 Node/20 Playwright | PRESENT; không gộp branch khác vào final main UAT |
| 5 phút thuyết trình | Có phần 7 | PRESENT; cần so với script A |
| 10 câu hỏi và trả lời | Có phần 8 (10 câu) | PRESENT; cần so với A |
| 41 links, 8 Github URLs, 234 SHA256 verified do Codex báo cáo | B có link subset khác; kiểm tra toàn văn A chưa thực hiện | **NOT_VERIFIED**, không kế thừa kết quả 41/8/234 cho B |
| Ảnh Checkout thành công thật | Không có ở A theo báo cáo Codex; B chưa có | **NOT_CAPTURED** |
| Raw TV2 10-case, hai live UNKNOWN, final TV5 UAT | Codex nói còn thiếu; B giữ thiếu | **NOT_VERIFIED/NOT_RUN/BLOCKED** đúng phạm vi |

## 3. Phát hiện khi kiểm tra ảnh cũ trên GitHub ngày 08/11

- Ảnh [`cart-hai-store-quantity-remove-va-jwt-het-han-passed.png`](https://github.com/duyxl56785/Project_HK5/blob/78de5c08205b41986a5204a8150a9ef810507847/reports/minh-chung-kiem-thu/soopi-audit-20261106/website-extended/cart-hai-store-quantity-remove-va-jwt-het-han-passed.png) hiển thị **giỏ hàng trống**. Filename test không chứng minh hai Store trong **khung hình**. Đã **gỡ embed** khỏi báo cáo B và không dùng làm Hình 6.4.
- `reports/evidence/tv5-ui-20261028/cod-desktop.png` là **TV5 preview / DEMO DATA**, không phải website Checkout thật.
- GitHub Actions runs của [PR #187](https://github.com/duyxl56785/Project_HK5/pull/187) (backend/frontend+e2e) hiện **không có artifact screenshot** để bổ sung vào Hình 6.4.
- Báo cáo Word nhóm trang 10 và [`CAPTURE_REAL_CHECKOUT.md`](CAPTURE_REAL_CHECKOUT.md) chỉ rõ bốn ảnh thật cần thu trong stack được phép.

## 4. Cách chạy so sánh nguyên văn an toàn trong Codex Terminal Linux

**Điều kiện:** Worktree A gốc còn đúng file untracked; đã fetch được branch PR #188. Không commit, reset hoặc sửa file A.

~~~bash
cd /workspace/tv3-checkout-project-report-20261107
git status --short
test -s docs/project-report/tv3/CHECKOUT.md
git fetch origin docs/tv3-checkout-report-review-20261108
mkdir -p /tmp/tv3-checkout-report-compare
git show origin/docs/tv3-checkout-report-review-20261108:docs/project-report/tv3/CHECKOUT.md > /tmp/tv3-checkout-report-compare/pr188.md

wc -l docs/project-report/tv3/CHECKOUT.md /tmp/tv3-checkout-report-compare/pr188.md
sha256sum docs/project-report/tv3/CHECKOUT.md /tmp/tv3-checkout-report-compare/pr188.md
diff -u docs/project-report/tv3/CHECKOUT.md /tmp/tv3-checkout-report-compare/pr188.md > /tmp/tv3-checkout-report-compare/original-vs-pr188.diff
status=$?
test "$status" -eq 0 -o "$status" -eq 1
echo "diff_exit_code=$status"
~~~

`diff` exit 1 có nghĩa hai nội dung **khác nhau**, không phải lỗi thực thi; exit >1 mới là lỗi. Lưu lại `wc`/SHA256/diff, sau đó review từng phần của A giữ hoặc thay ở B **trong nhánh tài liệu của TV3**. Đừng tự xóa A hay copy toàn bộ 351 dòng đè lên B khi chưa biết nội dung đang chồng hoặc khác nguồn.

## 5. Điều kiện để TV3/TV2 chốt báo cáo

- [ ] So sánh raw Markdown A với B: line diff, headings, link targets, các mã test và SHA; giữ bản gốc.
- [ ] TV2 xác nhận vị trí ghép báo cáo Word: **mục 6, Hình 6.4**, và chỗ đặt phụ lục kỹ thuật Checkout.
- [ ] Có ảnh thực sự hiển thị **Cart hai Store, COD confirmation, Order success, SellerOrder/DB** và metadata/PII redaction do TV5 chấp thuận.
- [ ] TV5 xác nhận các kết quả PASS/NOT_RUN/BLOCKED được trích dẫn đúng test SHA và phạm vi.
- [ ] PR #188 được review, merge vào đúng tài liệu nhóm; TV3 **tự** tick Issue #160 sau khi đáp ứng tiêu chí owner.

**Kết luận tại checkpoint:** Nội dung B đã bao phủ các **nhóm chủ đề** Codex tóm tắt, nhưng **chưa thể chứng minh tương đương toàn văn** với A; ảnh demo Checkout đủ điều kiện vẫn chưa tồn tại trong nguồn đã kiểm. Không được đánh dấu "hoàn thành" hai công việc này.
