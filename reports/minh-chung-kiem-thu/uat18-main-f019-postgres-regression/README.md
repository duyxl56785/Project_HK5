# TV5 PostgreSQL regression trên source main, 03/11/2026

- Source code: `main@f0190e54a18b263df5b5cc0e9f7987873d30617e`. Nhánh PR #165 sau đó chỉ thêm evidence; diff `backend/` so với source SHA là rỗng.
- Môi trường: Windows, Docker Engine 29.8.1, Testcontainers/PostgreSQL trong container tạm. Không dùng database hoặc volume chung.
- Lệnh thực sự chạy với `TESTCONTAINERS_RYUK_DISABLED=true`:

  `backend\gradlew.bat -p backend :product-service:integrationTest --tests '*CatalogSchemaProposalPostgresTest' :order-service:postgresIntegrationTest --tests '*Finance*' --rerun-tasks --no-daemon --continue --console=plain`

- **PASS**, exit `0`, `BUILD SUCCESSFUL in 1m 33s`; 4 JUnit XML: Product schema/Flyway 33, Order Finance 11, Order Fulfillment 2 = **46 PASS / 0 FAIL / 0 ERROR / 0 SKIP**. Product 33 là kiểm schema/migration, không chứng minh Review HTTP/eligibility; hai ca Fulfillment không được tính là Finance.
- Log gốc `postgres-regression.log`, SHA256 `74dd63ac46aef99b8557d987c4e2b0e9ce27f84123b5c9fc5e48295d4b1c9a84`; `SHA256SUMS` chỉ chứa raw log và XML. `.gitattributes` giữ byte gốc để Git checkout/archive không đổi receipt.
- Log có lỗi kết nối của scanner nền sau khi PostgreSQL Testcontainers đã dừng. JUnit vẫn 46/46 PASS và Gradle exit 0. Cảnh báo này được giữ nguyên trong log; chưa được phân loại là lỗi assertion hoặc Production scanner PASS.
- Lệnh thử đầu dùng sai tên task `:product-service:postgresIntegrationTest` (task không tồn tại), exit 1 trước khi chạy test; không cộng vào tổng PASS/FAIL của test. Lệnh đúng ở trên dùng `:product-service:integrationTest` theo `build.gradle`.
- Đây là selected PostgreSQL regression, **không phải UAT hai Store**, không chứng minh live Review HTTP, Finance/COD/Settlement production hay hai fixture UNKNOWN. Issue #18 vẫn NOT_RUN/BLOCKED cho nghiệm thu cuối.
