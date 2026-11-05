# Issue #5 — ba finding Inventory DB

Nguồn: `main@f0190e54a18b263df5b5cc0e9f7987873d30617e`; Windows 03/11/2026, Docker Engine 29.8.1, PostgreSQL Testcontainers tạm; không dùng dữ liệu hoặc volume ứng dụng.

Lệnh: `./backend/gradlew.bat -p backend :inventory-service:postgresIntegrationTest --tests '*InventorySchemaPostgresIT' --tests '*InventoryInitializePostgresIT' --tests '*InventoryOperationPostgresIT' --rerun-tasks --no-daemon --console=plain`

Exit 0, Gradle `BUILD SUCCESSFUL in 26s`; JUnit XML schema 2, initialize 4, operation 10 = **16 PostgreSQL integration PASS / 0 FAIL / 0 ERROR / 0 SKIP**. Các lớp có test cho CHAR/VARCHAR hash, khởi tạo SKU lặp không ghi đè số dư, operationId cùng key và concurrency. Đây là QA độc lập trên main, không thay thế TV1/TV3 review ownership hoặc integration production.

Raw log/XML giữ nguyên byte; `.gitattributes` tắt chuẩn hóa dòng, `SHA256.txt` ghi checksum. Trailing whitespace sinh bởi tool trong raw evidence được giữ nguyên. Không có credential production trong evidence.
