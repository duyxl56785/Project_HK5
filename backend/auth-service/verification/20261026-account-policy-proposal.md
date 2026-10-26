# TV1 — Account status policy v1.1: OWNER DECIDED (26/10/2026)

Refs #1, #80, #96, #101; kế thừa khảo sát trong bản proposal ngày 26/10/2026. **Quyết định nghiệp vụ của TV1 cho Auth account status**; không phải phê duyệt bootstrap, HTTP endpoint, migration hay deployment. Đây là cập nhật chính sách trong Draft PR #101; chưa merge vào main.

## 1. Đối chiếu baseline

- `Account.java`: `String status`, nonblank; role là enum `CUSTOMER/SELLER/ADMIN` riêng.
- Migration V1: `accounts.status varchar(32) NOT NULL`, không enum, default hay canonical vocabulary; V2 chỉ chuẩn hóa email. Không sửa migration đã áp dụng.
- `CurrentAccountAuthority`: so sánh status đúng giá trị active cấu hình; current account + current roles kiểm phía Auth DB, fail-closed; DB unavailable -> 503.
- `AuthBootstrapConfiguration`: mặc định OFF; `HK5_AUTH_ACTIVE_STATUS` bắt buộc nếu bật, chưa có default; env là input cấu hình, không tự làm policy.
- Test hiện dùng `ACTIVE`, `SUSPENDED`, `QA_EXPLICIT_ACTIVE`, `QA_INACTIVE` như fixture; không coi fixture là quyết định chính sách triển khai. Seller `APPROVED/SUSPENDED` thuộc miền TV2, không phải Account status.

## 2. Quyết định chính thức — canonical v1.1

| Trạng thái Auth Account | Chính sách |
|---|---|
| `ACTIVE` | **Giá trị active DUY NHẤT**, so sánh case-sensitive và exact; vẫn phải kiểm password, verified JWT, current Auth roles, ownership và policy miền tương ứng |
| `DISABLED` | Không cấp token ở login/refresh, không cho truy cập protected API kể cả JWT cũ; logout vẫn được phép thu hồi refresh family theo Origin/CSRF và transaction |
| Thiếu account, status null/blank/khác, sai hoa/thường, `SUSPENDED` / `APPROVED` / `PENDING` / `QA_EXPLICIT_ACTIVE` | **DENY / fail-closed**; không tự đổi thành ACTIVE hoặc map Seller status |

- Public register tạo account mới với `status=ACTIVE` và duy nhất `CUSTOMER` **sau khi Account + durable profile-provisioning task được commit atomically**. Không cho client truyền role, status hoặc account ID; register **không tự login**.
- Login sai password, email không tồn tại hoặc status khác `ACTIVE` dùng cùng 401 `AUTH_INVALID_CREDENTIALS` với chống enumeration/timing theo contract; không trả status nội bộ ra response.
- Access JWT hợp lệ nhưng Account hiện không còn active hoặc đã mất account trả 401 `AUTH_ACCESS_INVALID`. Principal active nhưng mất role/scope trả 403 `AUTH_FORBIDDEN`; DB/current-role read không kiểm chứng được trả 503 `AUTHORIZATION_DEPENDENCY_UNAVAILABLE`. Refresh invalid/inactive trả 401 `AUTH_REFRESH_INVALID`, outage trả 503.
- `HK5_AUTH_ACTIVE_STATUS=ACTIVE` là giá trị vận hành duy nhất được phê duyệt khi/ nếu bootstrap được bật. Không hardcode secret, không thay bằng fixture `QA_EXPLICIT_ACTIVE` ở production. Bootstrap **vẫn OFF** cho đến khi có review, security filter chain và release gate.
- Account `DISABLED` chỉ được tái kích hoạt qua workflow quản trị có xác thực, ủy quyền, audit và kiểm thử được phê duyệt riêng; không mở endpoint chuyển trạng thái trong PR này. Không biến Seller approval/revocation thành Account state transition; seller role-sync cần hợp đồng idempotent/versioned riêng.

## 3. Dữ liệu trạng thái cũ — migration gate bắt buộc

**Chưa có inventory dữ liệu production** nên KHÔNG khẳng định database chỉ chứa `ACTIVE/DISABLED`, và không có quyền tự chuyển trạng thái. Trước rollout ở từng môi trường có dữ liệu, operator được ủy quyền phải thực hiện kiểm kê read-only (phân loại `status` + số lượng bản ghi, không xuất PII), lưu biên bản và phương án cho từng giá trị khác canonical. Unknown/legacy status **vẫn deny theo mặc định**, kể cả khi làm giảm số tài khoản đăng nhập; không âm thầm `UPDATE status='ACTIVE'`, không tự bulk migration hay gộp account. Nếu cần mapping/chuyển trạng thái, cần một change/migration riêng có phê duyệt TV1, kiểm thử, kiểm soát quyền và rollback. Nếu inventory không hoàn thành hoặc có giá trị chưa được xử lý: **NO-GO deployment**.

Password hash lịch sử tạo từ đầu vào chứa NUL (nếu có) là vấn đề reauth/reset riêng; không thể xác định chỉ từ digest và không tự đổi hash.

## 4. Implementation gate

- Phê duyệt ở đây **chỉ** chốt Account status policy và public register initial status; không phê duyệt credential strength, rate limiter, Origin/CSRF, keyring or route behavior nếu chưa được review.
- Cần TV2 security review PR #101 (`AuthBootstrapConfiguration`, startup/rotation/config tests, compatibility), và TV2 giải quyết finding NUL trên #80 sau khi kiểm tra 58-case exact-parent receipt.
- Sau review: lập PR/commits Auth API gồm transactional register; login + refresh-family/cookie contract; refresh/logout; verified `/api/auth/me`, servlet security filter, MockMvc + PostgreSQL negative/security tests. Gateway reactive JWT + allowlist, header stripping, CORS và downstream verification thuộc gate kế tiếp.
- Không public route khi chưa có full security/refresh/CSRF/rate-limit checks; không tự mở OD-09 internal pair, service khác, Finance/Inventory flags; không merge vào main, không deploy/migrate production.

**Status:** ACCOUNT_STATUS_POLICY = DECIDED BY TV1. IMPLEMENTATION / RUNTIME / DEPLOY = BLOCKED UNTIL SECURITY GATES.
