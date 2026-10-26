# Đề xuất chính sách Account để TV1 phê duyệt

Refs #1, #80. **PROPOSED / WAITING_TV1_DECISION**, không phải quyết định đã duyệt.
Đối chiếu source nền `083355be16c4e3ea442ccd861d5f4b40236a0b12`, Issue #1 và
review/checkpoint PR #80 đọc ngày 26/10/2026. Không truy vấn dữ liệu production.

| Nguồn | Trạng thái thực tế và giới hạn |
|---|---|
| `Account.java` | `String status`, bắt buộc nonblank; không có enum/default. Role là enum CUSTOMER/SELLER/ADMIN riêng biệt. |
| `V1__auth_persistence.sql`, V2 | `varchar(32) NOT NULL`, check nonblank; không giới hạn vocabulary/default. V2 chỉ sửa email normalization, không chốt status. Không sửa migration đã áp dụng. |
| `CurrentAccountAuthority` | Chỉ cho phép đúng giá trị active được truyền rõ; status khác, account mất hoặc roles rỗng trả 401. DB/account hoặc lazy roles query lỗi trả 503. Role request phải có ở JWT lẫn DB hiện tại, thiếu trả 403. |
| `AuthBootstrapConfiguration` | Yêu cầu `HK5_AUTH_ACTIVE_STATUS`, không default/coerce; mặc định không đăng ký bean. Cấu hình một string chưa chứng minh string đó được TV1 phê duyệt. |
| Security unit / AuthPersistence tests | ACTIVE là fixture; SUSPENDED là fixture inactive trong unit, không phải trạng thái Account đã chốt. Các chuỗi blank/padded/overlength dùng làm negative input. |
| Năm current-authority PostgreSQL regressions | QA_EXPLICIT_ACTIVE / QA_INACTIVE là fixture riêng, không phải vocabulary triển khai. Giữ nguyên Java/init đã review. |
| Contract v1.0 §§2/4/5/6/7, ADR-0001 | Auth sở hữu Account status; inactive/deleted từ chối cấp/truy cập, kiểm current roles; register CUSTOMER-only. Seller APPROVED/SUSPENDED thuộc TV2, không định nghĩa Account status. |
| Issue #1 và checkpoint PR #80 ngày 26/10 | Canonical active-status vẫn cần TV1 quyết định trước runtime/HTTP; không có phê duyệt ACTIVE/DISABLED trong các nguồn đã đọc. |

## Chính sách tối thiểu đề nghị

- `ACTIVE`: cho phép xác thực sau khi credential hợp lệ; cấp token bằng roles hiện tại từ Auth PostgreSQL. Request vẫn phải qua verified JWT và kiểm role/ownership cần thiết.
- `DISABLED`: từ chối login/refresh/cấp access token và truy cập Auth, kể cả JWT cũ còn hạn. Logout được phép thu hồi refresh family sau Origin/CSRF và durable commit; không tạo phiên mới.
- Mọi giá trị khác, sai hoa/thường, blank/null, account thiếu: fail-closed. Login trả lỗi generic `AUTH_INVALID_CREDENTIALS`; access trả `AUTH_ACCESS_INVALID`; refresh trả `AUTH_REFRESH_INVALID`. DB không đọc được là 503, không giả invalid credential.
- Register tạo Account ở trạng thái ACTIVE **chỉ sau khi TV1 duyệt**; duy nhất CUSTOMER, không nhận role/status/userId từ request; Account + durable profile task commit cùng transaction. SELLER/ADMIN không được tự cấp qua register.
- Không suy ra Account ACTIVE/DISABLED từ Seller APPROVED/SUSPENDED hoặc ngược lại. Đồng bộ role Seller cần contract versioned riêng; không tự mở OD-09.

## Quyết định còn thiếu

TV1 cần phê duyệt vocabulary ACTIVE/DISABLED, trạng thái khởi tạo registration,
cách xử lý dữ liệu/status cũ nếu có và phiên bản áp dụng. TV2 cần review runtime
bootstrap và credential policy/work factor trước wiring. Không biến fixture/test
PASS hoặc tài liệu đề xuất này thành signoff của owner.

Chưa thêm enum/check constraint/migration hoặc default ACTIVE. Bootstrap/runtime
tiếp tục OFF; Gate 3 chỉ hoàn thiện mã cấu hình chưa kích hoạt và negative startup
tests. Gate 4/5 không mở route khi các phụ thuộc bảo mật bắt buộc chưa được duyệt.
