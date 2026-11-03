# Issue #5 — Finance/COD HTTP + PostgreSQL regression

Nguồn: `main@f0190e54a18b263df5b5cc0e9f7987873d30617e`, Windows 03/11/2026, Docker Engine 29.8.1, PostgreSQL 16 Testcontainers tạm. Không dùng dữ liệu/volume ứng dụng.

Lệnh: `./backend/gradlew.bat -p backend :order-service:postgresIntegrationTest --tests '*FulfillmentPostgres16IT' --rerun-tasks --no-daemon --console=plain`

Exit 0; JUnit XML **9 PASS / 0 FAIL / 0 ERROR / 0 SKIP**, Gradle `ORDER_PG16_TEST_SUMMARY tests=9 passed=9 failed=0 skipped=0`, `BUILD SUCCESSFUL in 30s`. Suite kiểm HTTP role, Customer/Store isolation, Seller action, COD flow/Finance, SUSPENDED Seller Finance 403, fail-closed khi Store facts unavailable, hủy/release, concurrent accept và DB chặn COD trước DELIVERED.

Giới hạn: HTTP được gọi bằng MockMvc/JWT test principal, StoreDirectory là mock, Order DB/Flyway là PostgreSQL thật. Test seed trực tiếp snapshots; không có live Seller/Auth/Catalog/Inventory provider, không ký policy hoặc nghiệm thu settlement production/hai Store UAT.

Raw log/XML giữ nguyên byte nhờ `.gitattributes`; `SHA256.txt` ghi hash. Trailing whitespace tool-generated được giữ nguyên. Không có credential production.
