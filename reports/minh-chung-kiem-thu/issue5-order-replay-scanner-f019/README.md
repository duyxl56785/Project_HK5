# Issue #5 — Order replay và scanner PostgreSQL

Nguồn chạy: `main@f0190e54a18b263df5b5cc0e9f7987873d30617e`, Windows 03/11/2026, Docker Engine 29.8.1, PostgreSQL 16 Testcontainers riêng. Không dùng dữ liệu/volume ứng dụng hiện hữu.

Lệnh: `./backend/gradlew.bat -p backend :order-service:postgresIntegrationTest --tests '*CheckoutReplayPostgresIT' --tests '*CheckoutScannerPostgresIT' --rerun-tasks --no-daemon --console=plain`

Exit 0; `CheckoutReplayPostgresIT` 8 PASS/0 FAIL/0 SKIP, `CheckoutScannerPostgresIT` 5 PASS/0 FAIL/0 SKIP. Tổng 13 PostgreSQL integration PASS; log cuối có `ORDER_PG16_TEST_SUMMARY tests=13 passed=13 failed=0 skipped=0` và `BUILD SUCCESSFUL`. Raw log có stack trace connection refusal trong shutdown nhưng JUnit không có failure; không xóa hoặc sửa log.

Các test dùng Order PostgreSQL thật; Cart/Catalog/Inventory là fixture/mock, nên đây là regression của replay và scanner, không phải live E2E hay Finance/COD approval. Order JWT YAML đã được xác minh riêng 2 unit PASS tại `../issue5-order-jwt-yaml-f019/`. PR #28/#33 đã merge; TV1 APPROVED PR #28 đúng head `382f799`.

Các file `gradle.log` và `TEST-*.xml` giữ nguyên byte sau lượt chạy; `.gitattributes` tắt chuẩn hóa dòng cho chúng. `SHA256.txt` ghi SHA256 của ba file evidence này. Raw log/XML có trailing whitespace do Gradle/Testcontainers sinh ra; không chỉnh sửa để làm đẹp `git diff --check`. Kiểm code/tài liệu riêng không có lỗi whitespace. Không có mật khẩu/token production trong evidence.
