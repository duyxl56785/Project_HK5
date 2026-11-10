# Tiền kiểm Return–Settlement trên main, 10/11/2026

- Source: `main@78de5c08205b41986a5204a8150a9ef810507847`.
- Môi trường: Windows, Java/Gradle của repository, Docker Engine với PostgreSQL Testcontainers; không dùng hoặc xóa volume chung.
- Lệnh: `./backend/gradlew.bat -p backend :order-service:postgresIntegrationTest --tests '*FulfillmentPostgres16IT' --rerun-tasks --no-daemon --console=plain`.
- Exit code: `0`. JUnit XML: **24 PASS, 0 FAIL, 0 ERROR, 0 SKIP**.
- Tệp XML là bản sao nguyên byte từ Gradle; SHA256: `CF264C5E6E10AB68354773EEF5FC96F37A347B555B233E63645C51C96D1377DC`.
- Không lưu raw console log của lượt chạy này. XML chứng minh các assertion trong `FulfillmentPostgres16IT` với MockMvc và PostgreSQL; các provider/Store dependency trong test không phải live production. Không suy ra final UAT hai Store, UNKNOWN, restock hay Finance production PASS.
- Rà soát mẫu Bearer/JWT, credential assignment và email trong XML: không phát hiện. Không chỉnh sửa nội dung XML để làm đẹp kết quả.
