# PR #168 — TV5 Notification DB/Kafka QA

Nguồn: PR #168 exact HEAD `d29055988cdf55aa17d958886da3028d13e4ee88`, base main `f0190e54a18b263df5b5cc0e9f7987873d30617e`; detached worktree, Windows 03/11/2026, Docker Engine 29.8.1, PostgreSQL và Kafka Testcontainers tạm. Không dùng dữ liệu/volume chung.

Lệnh: `./backend/gradlew.bat -p backend :notification-service:test --tests '*NotificationPersistenceTest' --tests '*OrderEventKafkaIT' --rerun-tasks --no-daemon --console=plain`

Exit 0, `BUILD SUCCESSFUL in 45s`. JUnit XML: NotificationPersistenceTest 4 PASS, OrderEventKafkaIT 3 PASS; **7 PASS / 0 FAIL / 0 ERROR / 0 SKIP**. Có DB/Kafka thật trong test, nhưng không chứng minh toàn hệ thống production hoặc Finance. Test xác nhận lỗi insert chưa có committed eventKey winner được ném ra và valid redelivery có thể lưu.

Raw Gradle log và hai XML giữ nguyên byte nhờ `.gitattributes`; `SHA256.txt` ghi checksum. Không có token/PII production.
