# TV2: R06 và E2E trên PR #176 `fix/tv2-read-current-authority@3ae524fb9c885ac6a2f14f7dc7b0d28d0f4c3952`

Cùng clone sạch, project `hk5-clean`, máy và công cụ như [lượt main](../tv2-main-3b4cf7d-clean-clone/README.md); chỉ checkout head PR rồi chạy lại `up.mjs` (build lại jar).

## Lỗi phát hiện ở head trước `2738503`

Mọi request đọc qua service trả **503 `AUTH_AUTHORITY_UNAVAILABLE`**, kể cả khi chưa thu hồi gì, và `up.mjs` hết 4 phút chờ. Nguyên nhân: `AuthSecurityConfig` chưa cho phép `POST /internal/accounts/*/authority-checks`, nên request rơi vào `denyAll`. Unit test gọi controller trực tiếp nên không bắt được; CI e2e của PR tại `2738503` cũng FAIL. Sửa ở `3ae524f` (một matcher, pair token vẫn do controller kiểm).

## Kết quả tại `3ae524f`

| Bước | Exit | Kết quả | File |
|---|---|---|---|
| `up.mjs --approved-inventory-pair --approved-finance-policy` | 0 | Ready | `up.log` |
| `node r06-revoke.mjs` ([script](../tv2-main-3b4cf7d-clean-clone/r06-revoke.mjs)) | 0 | **PASS**: ghi và đọc đều **401 `AUTH_ACCESS_INVALID`** ngay lần gọi sau khi thu hồi, cả Admin (A3–A5) và Seller (S3–S4); trước thu hồi 200/201 | `r06-revoke-pr176.log` |
| `npm run e2e` | 0 | **PASS 17/17** | `e2e.log` |

Kết quả này thuộc branch PR, không phải main. Đã quét log: không có mật khẩu seed/admin/DB hay khóa JWT.

`*.log` là output gốc (CRLF, có khoảng trắng cuối dòng do Docker/script in ra), được đánh dấu `-text` để blob commit giữ nguyên byte; `git diff --check` báo trailing whitespace ở các file này là dự kiến, phần tài liệu đã kiểm riêng.
