# TV5: rehearsal UAT cô lập trên main, 03/11/2026

## Nguồn và phạm vi

- Source thực thi: `main@f0190e54a18b263df5b5cc0e9f7987873d30617e` (full SHA). Worktree TV5 riêng; không đổi branch hay WIP của root.
- Docker Desktop 4.93.0 / Engine 29.8.1 được khởi động bình thường từ bản cài của người dùng. Không reset, prune hoặc sửa cấu hình hệ thống.
- Lệnh: `scripts\qa\uat\Smoke-IsolatedUat.ps1 -Build -ProjectName hk5-uat-tv5-20261103a -GatewayPort 18081 -FrontendPort 5174`.
- Kết quả: **PASS, exit 0** cho Compose stack, bootstrap 8 database, Flyway history, seed account/Store giả và HTTP Catalog read. `smoke.log` là output nguyên bản của lệnh. `migration-readonly.txt` ghi truy vấn PostgreSQL chỉ đọc: 8/8 database có migration thành công.
- Compose project và hai volume **mới**, đều mang nhãn `hk5-uat-tv5-20261103a`: `hk5-uat-tv5-20261103a-pgdemo`, `hk5-uat-tv5-20261103a-productimages`. 15 container thuộc project; chỉ Gateway `127.0.0.1:18081` và frontend `127.0.0.1:5174` publish cổng. `isolation-check.txt` ghi nhãn, cổng và hai gate OFF; không chứa giá trị bí mật.
- Sau khi lưu evidence, chạy `Stop-IsolatedUat.ps1 -ProjectName hk5-uat-tv5-20261103a`: **exit 0**, còn 0 container/0 volume của project. Hai volume cũ `tv5-w1-gates-*` vẫn còn (2/2), không bị dọn. `cleanup.log` là output nguyên bản.

## Giới hạn

- Đây là **rehearsal/smoke local**, không phải UAT cuối hoặc production acceptance. `INVENTORY_ORDER_PAIR_ENABLED=false`, `HK5_FINANCE_POLICY_APPROVED=false`; Checkout, Inventory reserve/consume/release, COD, Finance/Settlement và hai fixture UNKNOWN **NOT_RUN** trong lượt này.
- Hosted CI tại đúng main merge SHA chưa có check run; PR #69 vẫn readiness FAIL tại head cũ. R06, retention/PII và Return/Refund contract còn chờ owner. Không tick checklist Issue #18 từ smoke.
- Docker build frontend ghi 1 moderate vulnerability của Vite trong `smoke.log`; không tự nâng dependency hay coi build PASS là security approval.
- `SHA256SUMS` chỉ đối chiếu byte của log và file kiểm tra read-only, không gồm README hoặc `.gitattributes` vì Git archive có thể xuất chúng theo CRLF trên Windows. `.gitattributes` giữ CRLF gốc của log/txt để Git checkout không làm sai receipt. `git diff --check` có thể báo trailing whitespace trên raw log; tài liệu và manifest được kiểm riêng, không chỉnh log để che cảnh báo.
