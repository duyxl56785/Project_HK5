# PR #69 — Inventory candidate PostgreSQL tại đúng HEAD

Nguồn: `f2d56d763fa64670f95c77bb6d54d01b1cfa3b67` trên PR #69, base main `f0190e54a18b263df5b5cc0e9f7987873d30617e`; chạy trong detached worktree riêng ngày 03/11/2026. Windows, Docker Engine 29.8.1, PostgreSQL Testcontainers tạm; không tác động DB/volume hiện hữu.

Lệnh: `./backend/gradlew.bat -p backend :inventory-service:postgresIntegrationTest --tests '*InventoryReservationLedgerCandidatePostgresIT' --rerun-tasks --no-daemon --console=plain`

Exit 0, Gradle `BUILD SUCCESSFUL in 24s`; JUnit XML **18 PASS / 0 FAIL / 0 ERROR / 0 SKIP**. Bao gồm ca mới `lookupCannotTreatAnUncommittedReserveAsACompletedOutcome` ở cả commit và rollback. Hosted checks tại HEAD này khi đọc: readiness/frontend/e2e SUCCESS. Đây chỉ là candidate V3 ở `db/candidate-migration`, không bật migration/flag hoặc xác nhận production Inventory/UNKNOWN. PR #69 vẫn cần reviewer độc lập tại HEAD hiện tại.

Raw log/XML giữ nguyên byte nhờ `.gitattributes`, checksum trong `SHA256.txt`. Không có credential production trong evidence.
