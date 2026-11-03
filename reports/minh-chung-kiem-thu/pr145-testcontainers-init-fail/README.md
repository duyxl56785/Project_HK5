# PR #145 — PostgreSQL preflight dừng khi khởi tạo Testcontainers

- Ngày kiểm tra: 03/11/2026 (Asia/Saigon).
- Source đúng PR #145: `dc0b54c533659ef89c5dc77946ac874739eb4cb2` (PR đã merge). Worktree tách biệt; không sửa code TV3, không xóa volume.
- Lệnh: `backend\gradlew.bat -p backend :order-service:postgresIntegrationTest --tests '*CheckoutDraftPostgresIT' --tests '*CheckoutInventoryJournalPostgresIT' --rerun-tasks --no-daemon --console=plain`, với `TESTCONTAINERS_RYUK_DISABLED=true`.
- Kết quả thực tế: **FAIL, exit 1**, Gradle 8.10, 2 `initializationError`, 0 thân test được thực hiện. `CheckoutDraftPostgresIT` báo `Could not find a valid Docker environment`; suite còn lại báo Testcontainers không thử lại sau lần khởi tạo đầu. `docker info` cũng không kết nối được `npipe:////./pipe/dockerDesktopLinuxEngine` vì pipe không tồn tại. Đây là lỗi môi trường ở lượt này, chưa chứng minh lỗi logic #145.
- Ảnh `gradle-failure-actual.png` là ảnh chụp **trang báo cáo Gradle thật** ngay sau lượt FAIL. `postgres-testcontainers-fail.log` và hai XML là byte gốc của lượt chạy; không chỉnh sửa để biến FAIL thành PASS.
- Lệnh độc lập không cần Docker: `backend\gradlew.bat -p backend :order-service:test --tests '*InventoryOperationCallerTest' --tests '*CheckoutServiceTest' --tests '*InventoryOperationIntentTest' --rerun-tasks --no-daemon --console=plain` → **PASS, exit 0**, JUnit 12 + 24 + 6 = **42 unit**, 0 fail/error/skip. `unit-regression-pass.log` và ba XML lưu cùng thư mục. Các unit này không thay thế hai fixture live.
- SHA256 từng file gốc/ảnh ở `SHA256SUMS`; ảnh/log/xml không chứa token hoặc dữ liệu khách hàng thật theo kiểm tra mẫu nội dung. Không có ảnh sau sửa vì chưa sửa lỗi môi trường hoặc chạy lại PostgreSQL thành công.
- git diff --cached --check trên README/manifest/attributes PASS. Trên XML/log gốc có cảnh báo CRLF/trailing whitespace do công cụ sinh; các byte gốc được giữ nguyên (-text) để SHA256 của Git blob khớp manifest, không sửa log để che lỗi.
- Giới hạn: không khởi tạo stack UAT mới; không xác nhận Docker Compose/PostgreSQL live cô lập hay provider receipt của hai ca UNKNOWN.
