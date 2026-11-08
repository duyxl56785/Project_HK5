# TV3 — Đối chiếu bản Checkout Codex 351 dòng với báo cáo PR #188

**Trạng thái:** `STRUCTURAL_COMPARISON_DONE` / `EXACT_LINE_DIFF_NOT_RUN` / `ORIGINAL_FILE_INACCESSIBLE`.

**Kiểm tra lại bằng Codex Terminal ngày 08/11/2026:** original path A cũng không tồn tại trong môi trường terminal hiện tại (không chỉ là giới hạn của ChatGPT). Không còn worktree cũ trong `git worktree list`; không tìm thấy bản lưu của A trong các đường dẫn hiện có đã kiểm. Không xóa/reset hay dựng lại A. SHA256 từng được ghi trong phiên trước là `0fd4d7cb4f4de62fee60ebb1ee20df417fcc19748ba7cdfbdf0d3e6c5e34b087`; đây là **historical reference**, không phải checksum vừa đo lại từ A.

Đã fetch PR #188 lúc đầu tại `2eaa7dec356d53369cae7ced5aa635406faf34ed`, sau đó phát hiện owner cập nhật và chuyển sang baseline **`b2187441f4ae669f041ae3274cd53f4b7b7e6bf6`** trước khi tích hợp tài liệu. Snapshot B mới có 166 logical lines, 19583 byte UTF-8, SHA256 `4c200060ca78b5cc7caf44f1ca747fec3e1a8c9a510948fea3c898a24a73b337`. Giữ riêng snapshot ban đầu trong metadata; không ghi đè ba commit caption của owner. Đã lưu [manifest input](evidence/20261108/comparison-inputs.json), [bảng giữ/sửa](evidence/20261108/KEEP_CHANGE.md) và [unified diff giữa hai nguồn thực sự đọc được](evidence/20261108/issue160-draft-to-pr188-baseline.diff): **Issue draft 72 dòng → PR baseline mới**, không phải A 351 dòng → B. Unified diff A→B vẫn **NOT_RUN**, không xuất một diff giả từ file rỗng.

## 1. Nhận dạng nguồn, không được đánh đồng

| Nguồn | Vị trí / định danh | Tình trạng |
| --- | --- | --- |
| A — bản Codex gốc | `/workspace/tv3-checkout-project-report-20261107/docs/project-report/tv3/CHECKOUT.md` | Codex báo **351 dòng**, `git status: ??`, chưa commit/push; phiên ChatGPT hiện tại không truy cập được workspace đó |
| B — bản GitHub có thể review | [PR #188 `docs/project-report/tv3/CHECKOUT.md`](https://github.com/duyxl56785/Project_HK5/pull/188) | Tái dựng từ Issue #160, **không phải bản sao** của A; chỉ nhánh tài liệu, vẫn Draft |
| C — tóm tắt của Codex do TV3 cung cấp | Nội dung nhắn trong Project ngày 07/11/2026 | Có mô tả các mục và tự kiểm 41 internal links, 8 Github HTTP200, 234 SHA256; **không có toàn văn hoặc raw evidence** trong phiên này |
| D — bản báo cáo Word nhóm | `Bao_cao_Smart_ECommerce.docx` đã được chia sẻ trong Project | Bản 12 trang, nội dung mốc 25/10; trang 10 mục 6 Hình 6.4 vẫn là ô `[CHÈN ẢNH DEMO THỰC TẾ]` |

**Không được ghi "đối chiếu 351/351 dòng PASS"** chỉ từ số dòng hoặc tóm tắt A. Cần chạy diff thực tế khi có quyền đọc A.

## 1.1. Đối chiếu trực tiếp nguồn CÔNG KHAI có đủ văn bản (Issue #160 vs PR #188)

Đã đọc full [bản nháp trong comment #160](https://github.com/duyxl56785/Project_HK5/issues/160#issuecomment-6702448960) và [CHECKOUT.md PR #188](https://github.com/duyxl56785/Project_HK5/pull/188) trong cùng phiên:

| Chỉ số nội dung | Comment nháp Issue #160 | CHECKOUT.md PR #188 sau cập nhật |
| --- | ---: | ---: |
| Kích thước văn bản | 9.367 ký tự | 16.993 ký tự |
| Số dòng | 72 | 166 |
| Số mục đánh số Markdown | 6 | 9 |
| Số sơ đồ Mermaid | 0 | 2 |
| Số ảnh Checkout thật nhúng | 0 | 0 |

**Kết luận giới hạn:** Đây là **so sánh thực tế giữa hai nguồn có quyền đọc**, không phải so file 351 dòng Codex A. Không dùng số dòng hoặc số ký tự để suy chất lượng hay tương đương nội dung A. Ảnh giỏ trống đã bị gỡ sau kiểm tra pixel.

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
- Báo cáo Word nhóm trang 10 và [`CAPTURE_REAL_CHECKOUT.md`](CAPTURE_REAL_CHECKOUT.md) chỉ rõ các điểm chụp thật cần thu (Cart 2 Store, COD, Order detail, Order History riêng và receipt PostgreSQL) trong stack được phép.

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
