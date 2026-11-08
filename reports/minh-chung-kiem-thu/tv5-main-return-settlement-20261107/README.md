# TV5 — Return/Settlement trên main (07/11/2026)

- Source: `main@618f9ea8f723d607454397012cd5613ca199f660`, đã merge PR #180 và #181.
- Môi trường: Windows 11, Java 21.0.7, Docker Engine 29.8.1, PostgreSQL 16 do Testcontainers tạo riêng. Không xóa hoặc dùng lại volume của demo.
- Lệnh: `./backend/gradlew.bat -p backend :order-service:postgresIntegrationTest --tests '*FulfillmentPostgres16IT' --rerun-tasks --no-daemon --console=plain`.
- Kết quả thực thi: exit 0, `ORDER_PG16_TEST_SUMMARY tests=20 passed=20 failed=0 skipped=0`. JUnit XML gốc có `tests=20`, `failures=0`, `errors=0`, `skipped=0`; giữ nguyên byte và SHA256 ở `SHA256SUMS.txt`. XML sinh bởi Gradle có CRLF nên được đánh dấu `-text -diff` để Git không đổi byte hoặc báo whitespace giả khi kiểm diff. Console output không được lưu thành raw log ở lần chạy này; không gọi README là raw log.

Trong 20 test có `pendingReturnBlocksSettlementButCollectCodStillRecordsCash`, `concurrentReturnAndSettlementCannotBothCommit` và `rejectedReturnAllowsNewReturnAndReceivedStillBlocksSettlement`. Đây là regression HTTP MockMvc + PostgreSQL của guard Return trên **main**, không phải E2E qua Gateway hay Finance/Refund production. Seller workflow PR #182/#183, Inventory restock và UAT Issue #18 còn phụ thuộc riêng. Không bật Finance policy hoặc UNKNOWN fixture trong lượt chạy này.

Nguồn duy nhất của số test là XML bên dưới; không cộng số test từ branch PR khác. Quét XML không thấy JWT, Bearer token hoặc giá trị password. Đối chiếu hash trước khi dùng lại bằng chứng.
