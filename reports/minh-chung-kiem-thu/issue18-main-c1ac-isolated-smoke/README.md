# Diễn tập UAT cô lập trên main — 04/11/2026

Refs #18 #5. Source `main@c1ac4d4e5dd450d5d3fc3d6a8267cb5b45f32d48` trong detached worktree sạch. Windows, Docker Engine 29.8.1. Lệnh tại root: `./scripts/qa/uat/Smoke-IsolatedUat.ps1 -ProjectName hk5-uat-tv5-20261104b -GatewayPort 18081 -FrontendPort 5174 -Build`, sau đó `Stop-IsolatedUat.ps1 -ProjectName hk5-uat-tv5-20261104b` trong khối cleanup.

**PASS, chỉ smoke:** Compose project mới `hk5-uat-tv5-20261104b`, môi trường/secrets tạm riêng, 15 container healthy, 8 database có Flyway history, seed tài khoản giả và 3 Store, GET Category/Catalog qua gateway trả 200. Cờ Inventory pair và Finance policy đều OFF. `SMOKE_SUCCESS=True`, `CLEANUP_SUCCESS=True` trong output lệnh. Sau cleanup: thư mục runtime không còn; 0 container/volume mang tên project. Không đụng shared demo/volume khác.

`smoke-and-cleanup.raw.log` là output Docker/seed/cleanup đã lưu nguyên byte, SHA256 ở `SHA256.txt`; kiểm tra các mẫu password/token/key/Authorization trong log không có kết quả. Lưu ý cặp `SMOKE_SUCCESS`/`CLEANUP_SUCCESS` và kiểm tra 0 tài nguyên được in sau pipeline ghi log, nên là **derived execution summary** ở README, không giả là dòng trong raw log. Log giữ nguyên các cảnh báo build.

**NOT_RUN/BLOCKED:** Không chạy Checkout hai Store, Auth revoke, Inventory pair, COD, Finance, Settlement hay hai fixture UNKNOWN. Đây không phải final UAT Issue #18 hoặc production approval. Final UAT cần source/CI, runner và owner sign-off tại SHA tích hợp sẽ chạy.
