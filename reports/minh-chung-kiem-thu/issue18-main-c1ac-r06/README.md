# R06 trên main tích hợp — TV5, 04/11/2026

Nguồn chạy: `main@c1ac4d4e5dd450d5d3fc3d6a8267cb5b45f32d48` trong detached worktree sạch. Main này đã nhận #166, #178 và #174. Windows, Java 21, Docker Engine 29.8.1, PostgreSQL 16.6 Testcontainers tạm; không sử dụng volume hay dữ liệu ứng dụng chung.

Tại thư mục `backend`, đặt `TESTCONTAINERS_RYUK_DISABLED=true` và chạy tuần tự:

1. `.\gradlew.bat :product-service:test --tests '*ReviewCommentLimitTest' --rerun-tasks --no-daemon --console=plain` → exit 0, **2 PASS / 0 FAIL / 0 SKIP**, `BUILD SUCCESSFUL in 18s`.
2. `.\gradlew.bat :product-service:integrationTest --tests '*ReviewCommentMigrationPostgresTest' --rerun-tasks --no-daemon --console=plain` → exit 0, **1 PASS / 0 FAIL / 0 SKIP**, `BUILD SUCCESSFUL in 24s`.

Hai JUnit XML là output gốc, SHA256 ở `SHA256.txt`; `.gitattributes` tắt chuẩn hóa EOL cho XML. Console output của lượt này không được lưu thành raw log; thời gian và exit code ở trên là bản ghi lệnh đã thực thi, không được trình bày như raw log. Test chứng minh giới hạn 1000 UTF-16 ở DTO và migration V9 trên PostgreSQL, gồm dòng legacy dài vẫn được Admin ẩn/hiện. Đây là regression tích hợp hẹp trên main, **không** phải UAT hai Store, Auth revoke thực, Review F15 tổng hợp hoặc production approval.
