# TV2: R06 và E2E trên `main@618f9ea` (sau khi merge #176 và #181)

Ngày 07/11/2026. Stack riêng `COMPOSE_PROJECT_NAME=hk5-retrole`, dựng từ worktree `main@618f9ea8f723d607454397012cd5613ca199f660` cộng thêm đúng một spec mới `frontend/e2e/return-access.spec.ts` (không đổi code chạy). Windows 11, Docker 29.9.1, Node v24.21.0, Playwright 1.64.1. Không phải máy khác hay clone sạch (#158 vẫn mở phần đó).

| Bước | Exit | Kết quả | File |
|---|---|---|---|
| `node scripts/demo/up.mjs --approved-inventory-pair --approved-finance-policy` | 0 | Ready | `up.log` |
| R06 thu hồi quyền ([script](../tv2-main-3b4cf7d-clean-clone/r06-revoke.mjs), chỉ đổi đường dẫn `.env.demo` và tên container `hk5-retrole-postgres-1`) | 0 | **PASS**: cùng access token, sau khi khóa Admin (A3–A5) và Seller (S3–S4), cả ghi lẫn đọc trả **401 `AUTH_ACCESS_INVALID`** ngay lần gọi sau; trước đó 200/201 | `r06-revoke.log` |
| `npm run e2e` (lượt 2 trên cùng stack) | 0 | **PASS 19/19**, gồm spec mới | `e2e.log` |

Lượt E2E đầu trên stack này có 18 pass, 1 fail. Lỗi nằm ở spec mới, không phải ở code: spec giả định Seller gọi API trả hàng của khách sẽ nhận 403. Thực tế mọi tài khoản đều có vai trò CUSTOMER (seed: Seller `CUSTOMER,SELLER`, Admin `ADMIN,CUSTOMER`). Vì vậy API trả hàng phía khách được bảo vệ bằng quyền sở hữu đơn: gọi vào đơn của người khác trả **404 `ORDER_NOT_FOUND`** và không để lộ đơn đó có tồn tại. Spec đã sửa theo đúng hành vi này.

Spec `return-access.spec.ts` kiểm:
- Seller tạo hoặc đọc yêu cầu trả hàng trên đơn thật của khách khác nhận 404 `ORDER_NOT_FOUND`.
- Khách và Seller gọi `GET /api/admin/returns` và `/api/admin/returns/{id}` nhận 403.
- Gọi không có token nhận 401.

Chưa kiểm các route trả hàng của Seller (`/api/seller/returns`), vì chúng chưa có trên main (#182, #183).

Đã quét log, không có mật khẩu seed/admin/DB hay khóa JWT. File `*.log` là output gốc, được đánh dấu `-text`.
