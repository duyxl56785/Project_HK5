# Issue #5 — Seller persistence PostgreSQL

Nguồn: `main@f0190e54a18b263df5b5cc0e9f7987873d30617e`, Windows 03/11/2026, Docker Engine 29.8.1, PostgreSQL Testcontainers tạm; không dùng DB/volume ứng dụng. PR #25 đã merge vào nhánh TV2, mã Seller hiện nằm trên main.

Lệnh: `./backend/gradlew.bat -p backend :seller-service:integrationTest --tests '*SellerPersistenceIntegrationTest' --rerun-tasks --no-daemon --console=plain`

Exit 0; JUnit XML **26 PASS / 0 FAIL / 0 ERROR / 0 SKIP**; Gradle `BUILD SUCCESSFUL in 33s`. Test dùng PostgreSQL thật cho Seller persistence, không phải full backend build, Auth/JWT HTTP, Store/Product integration hoặc UAT.

Raw `gradle.log` và JUnit XML giữ nguyên byte; `.gitattributes` tắt chuẩn hóa dòng, `SHA256.txt` ghi hash hai file. Nếu diff-check báo trailing whitespace ở raw output, không sửa file để giữ checksum. Không có credential production trong evidence.
