# Issue #5 — concurrent settlement PostgreSQL regression

Nguồn code: `main@f0190e54a18b263df5b5cc0e9f7987873d30617e` cộng một test mới trên branch TV5. Ngày 04/11/2026; Windows, Docker Engine 29.8.1, PostgreSQL 16 Testcontainers tạm. Không dùng volume hoặc dữ liệu ứng dụng.

Lệnh: `./backend/gradlew.bat -p backend :order-service:postgresIntegrationTest --tests '*FulfillmentPostgres16IT' --rerun-tasks --no-daemon --console=plain`.

Kết quả: exit code **0**, JUnit XML **10 PASS / 0 FAIL / 0 ERROR / 0 SKIP**; Gradle `ORDER_PG16_TEST_SUMMARY tests=10 passed=10 failed=0 skipped=0`, `BUILD SUCCESSFUL in 42s`. Test mới gửi hai HTTP settlement đồng thời cho cùng SellerOrder sau khi DELIVERED và COD COLLECTED: đúng một phản hồi 200, một 409; đúng một sự kiện SETTLED trong outbox; `settled_at` được lưu và Finance Store ghi `settledNet=190000`.

Giới hạn: MockMvc với JWT test principal và StoreDirectory giả lập, PostgreSQL/Flyway thật. Test không chứng minh source xác nhận COD ngoài Order, chuyển tiền thật, hai Store UAT, Finance policy approval hoặc production end-to-end. Không tick F16 tổng hợp của Issue #5 vì các điều kiện đó còn thiếu.

`gradle.log` và JUnit XML là raw generated output, giữ nguyên byte bằng `.gitattributes`; `SHA256.txt` ghi digest tương ứng. Không có credential production.
