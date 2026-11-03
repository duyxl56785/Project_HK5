# Minh chứng TV5: preflight Review/Finance trên main, 03/11/2026

## Phạm vi và nguồn

- Source: `main@f0190e54a18b263df5b5cc0e9f7987873d30617e` (full SHA, sau merge PR #164).
- Môi trường: Windows, Java/Gradle theo cấu hình repository; Docker Engine **không kết nối được** (`dockerDesktopLinuxEngine` pipe không tồn tại). Không dùng database hoặc tài khoản thật.
- GitHub tại thời điểm kiểm tra: **0 hosted check runs** gắn trực tiếp với merge SHA trên. Không suy ra CI PASS từ PR hoặc SHA khác.
- Lệnh đã chạy tại đúng worktree của SHA trên:

  `backend\gradlew.bat -p backend :product-service:test --tests '*Review*' :order-service:test --tests '*Finance*' --rerun-tasks --no-daemon --console=plain`

- Exit code `0`, `BUILD SUCCESSFUL`; JUnit XML: product-service 34 test, order-service 51 test, tổng **85 PASS / 0 FAIL / 0 ERROR / 0 SKIP**. Log gốc: `review-finance-unit.log`; 9 XML trong `junit/`.
- Đây là **selected unit regression**, không phải PostgreSQL integration, HTTP/E2E hay UAT hai Store. Không cộng với số test TV2 chạy ở SHA khác.

## Gate UAT còn mở

| Hạng mục | Trạng thái tại SHA này | Lý do |
| --- | --- | --- |
| UAT React→Gateway→services→PostgreSQL hai Store do TV5 chạy | **NOT_RUN / BLOCKED** | Docker Engine không hoạt động; #69 vẫn readiness FAIL tại HEAD cũ; main merge SHA chưa có hosted checks; các fixture UNKNOWN chưa được chạy thật. |
| Hai fixture UNKNOWN claim-conflict và accepted-response-lost | **NOT_RUN / BLOCKED** | TV3 mới chấp nhận giao thức có điều kiện; chờ TV4 cơ chế/receipt thật, TV2 xác nhận exact stack và TV5 review evidence. Xem PR #152/#164 và Issue #18. |
| Finance/COD/Settlement production | **BLOCKED** | M01/MD09 chỉ chốt cho demo local; không có production approval. Return/Refund #155/#156 còn phụ thuộc quy ước TV3. |
| R06 comment limit | **FAIL so với oracle QA hiện hành / PENDING quyết định** | `tests/TV5_UAT.md` và báo cáo V2 A.7 nói 1000, nhưng `ModerationController.ReviewRequest` và DB V4 cho 2000; TV2 ghi request 1001 được nhận 201. TV2/product-business owner cần chốt giới hạn; TV5 chưa sửa code/oracle. Phần Admin revoke thuộc TV1 #153 vẫn NOT_RUN. |

## Kiểm tra độc lập gói UAT TV2

Gói TV2 ở commit `3a5e4381843f2f10e9e81a49d384c6d1d6212c7e`, chạy tại `main@052388c84f378ee90732de5b37f7c7bef6fcc64f` trong stack demo riêng, **không phải lượt UAT TV5 trên SHA hiện tại**. Kiểm 60 mục `SHA256SUMS` với byte Git blob: 59 khớp; `tooling/gaps.mjs` không khớp: manifest `bf8c71fda05cc26d718c9ad71f65deb93d774f15cb0386bc51cb8f2cc39a9985` khớp worktree Windows CRLF, còn Git blob LF là `97d148252fce41a1a0b50b7fd3697b231dbddcea51396a429af14a372c729bbd`. Đây là lỗi quy ước byte của receipt, chưa phải bằng chứng nội dung test giả. TV2 cần chuẩn hóa receipt/attributes mà không sửa log gốc để che sai lệch.

## Bảo toàn minh chứng

`SHA256SUMS` bên cạnh ghi hash của **byte file local được đưa vào commit**. `.gitattributes` giữ log/XML ở dạng byte gốc để Git không đổi CRLF khi checkout. Không chỉnh sửa raw log hoặc JUnit XML. Không có ảnh chụp UAT vì UAT chưa chạy; ảnh Testcontainers FAIL của lượt trước ở thư mục `pr145-testcontainers-init-fail/` trên main.

`git diff --cached --check` mặc định báo trailing whitespace trên XML/log sinh ra với CRLF; đó là byte gốc được giữ để SHA256 khớp. Kiểm riêng README, manifest và `.gitattributes` bằng `git diff --cached --check -- <ba file>` sạch; kiểm toàn bộ với `core.whitespace=-blank-at-eol` cũng sạch. Không sửa raw evidence chỉ để xóa cảnh báo định dạng.
