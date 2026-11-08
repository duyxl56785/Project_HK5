# PR #188 — Evidence reconciliation và capture preflight

Ngày 08/11/2026, CODEX TERMINAL Linux. Phạm vi: tài liệu/evidence TV3, không chạy live và không sửa ứng dụng.

## Nguồn và kết quả

Remote thay đổi trong lúc làm: 2eaa7de → b218744 qua ba commit tách Order detail/Order History và caption. Đã giữ nguyên worktree staged ban đầu, tạo worktree mới từ b218744 và apply có chọn lọc patch docs (không conflict, không merge/reset/force push). Giữ toàn bộ cập nhật owner, gồm ảnh Order History riêng `checkout-03b-order-history.png`.

- PR #188 baseline: `b2187441f4ae669f041ae3274cd53f4b7b7e6bf6`, branch `docs/tv3-checkout-report-review-20261108`; GitHub metadata xác nhận state DRAFT. Source main nền: `78de5c08205b41986a5204a8150a9ef810507847`.
- A gốc: `/workspace/tv3-checkout-project-report-20261107/docs/project-report/tv3/CHECKOUT.md` **không tồn tại** trong môi trường hiện tại; không xóa, reset hay ghi lại đường dẫn đó. Con số 351 và hash cũ chỉ là reference từ phiên trước.
- B baseline thực đọc từ Git có 166 logical lines, 19583 byte; SHA256 nằm trong [comparison-inputs.json](comparison-inputs.json).
- Unified diff **A 351 → B: NOT_RUN**, `original351VsPR188.diff=null`. Không có artifact giả mang tên original-vs-PR188.diff.
- Diff [Issue draft → B baseline](issue160-draft-to-pr188-baseline.diff) được tạo bằng `diff --unified=0`, exit **1** vì nội dung khác nhau, không phải lỗi thực thi. Input nháp thật ở [issue160-draft.md](issue160-draft.md), đầu B khôi phục được từ `git show b218744:docs/project-report/tv3/CHECKOUT.md`. Đây **không phải** diff từ A gốc.
- [Bảng giữ/sửa](KEEP_CHANGE.md) ghi rõ các sửa tài liệu dựa trên source hiện hành; không ghi đã đối chiếu nguyên văn A.

## Live / ảnh / SQL

| Hạng mục | Trạng thái thực tế |
|---|---|
| Docker local socket | Có, Docker 28.4.0/VFS |
| Stack được phép có thể sử dụng trong môi trường này | Chưa xác định được; có 0 container và 0 volume |
| Tám biến fixture bắt buộc | Chưa có; chỉ lưu boolean, không lưu giá trị credential |
| TV2/TV5 approval capture đúng source/project/ports/fixtures | Chưa tìm thấy trong các nguồn đã đọc; không suy quyền từ approval demo cũ |
| `two-store.live.spec.ts` live execution | **NOT_RUN, 0 executed / 0 PASS / 0 FAIL** |
| Cart hai Store, COD, Order success/History | **NOT_CAPTURED**, không có PNG mới |
| SQL read-only trước/sau | **NOT_RUN**, chưa có DB/role/fixture cụ thể |
| Hai UNKNOWN fault cases | Không thực thi; vẫn ngoài scope capture và chờ gate riêng |

Nguồn quyền đã đọc: [PR #188](https://github.com/duyxl56785/Project_HK5/pull/188), [checkpoint #160](https://github.com/duyxl56785/Project_HK5/issues/160#issuecomment-6723521309), [TV3→TV5 handoff #18](https://github.com/duyxl56785/Project_HK5/issues/18#issuecomment-6723275770), cùng các hướng dẫn trong PR. Chúng vẫn yêu cầu owner cấp stack/fixture/PII disposition; không phải approval có đủ thông số để chạy mới.

[Environment preflight](environment-preflight.json) chỉ có số đếm và sự hiện diện biến, không token/password/PII. Không khởi động Docker stack, thay daemon/config, bật flags, tạo Order/stock mutation, mở DB hoặc tiếp cận stack Windows. Không dựng ảnh từ HTML mock, sửa DOM, vẽ receipt hay tái sử dụng ảnh cũ như một capture mới.

## Kiểm tra tài liệu

`validation.json` ghi kết quả link/JSON/source-scope/hash checks sau cập nhật. Đây là kiểm tra tài liệu, **không phải application tests PASS**. Không chạy lại Java/PostgreSQL/frontend suites vì không đổi production/test source. Các số hosted #187 trong báo cáo là evidence checkpoint trước, không phải lượt mới của gói này.

`SHA256SUMS` kiểm các input/evidence trong thư mục; `report-sha256.json` ghi hash của ba file Markdown TV3 sau chỉnh. Bản gốc A không có hash thực đo trong lượt này; ảnh chưa chụp không có hash hoặc đường dẫn được giả lập.

## Cần owner cung cấp để tiếp tục

1. Nguyên file A 351 dòng hoặc quyền vào đúng workspace cũ, để hash/diff thực; không chỉ bản tóm tắt. Hash cũ chỉ dùng đối chiếu sau khi đọc lại được bytes.
2. Link TV2/TV5 approval cho exact app SHA, stack/project/loopback ports, fixtures Customer/hai Store/hai Product SKU-stock, tài khoản giả qua kênh secret và quyền DB read-only.
3. Giới hạn PII/media được công bố và người nghiệm thu ảnh/receipt. Sau đó mới tạo private screenshot copy, giữ toàn bộ assertion stock/replay và xem pixel đã mask trước publish.

**Kết luận: PARTIAL — tài liệu/source review đã thực hiện; exact 351 reconciliation BLOCKED, live capture BLOCKED / NOT_CAPTURED.** Không merge PR hoặc tick Issue #160.
