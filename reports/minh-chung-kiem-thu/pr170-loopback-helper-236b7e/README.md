# PR #170 — TV5 kiểm tra độc lập cơ chế proxy loopback

Nguồn: PR #170 HEAD `236b7e064251a4770098d17e848d3efc61a1f517`, detached worktree, Windows, Node `v22.23.3`, ngày 03/11/2026. Chỉ dùng fake loopback HTTP, không gọi Inventory, không khởi động Compose, không bật flags hoặc sửa dữ liệu.

Lệnh: `node --test scripts/qa/tv4-unknown/lost-response.test.mjs`.

- Lượt đầu trong môi trường hạn chế: exit 1, **1 PASS / 2 FAIL**, cả hai lỗi `connect EACCES 127.0.0.1` trước khi kiểm chứng proxy. Log `node-test.tap` được giữ nguyên; đây là hạn chế quyền loopback, không đủ căn cứ quy lỗi code PR.
- Chạy lại với quyền truy cập loopback: exit 0, **3 PASS / 0 FAIL**, log `node-test-elevated.tap`. Kiểm cơ chế forward một lần, receipt hash/status của fake upstream, chặn operation/payload khác và target ngoài loopback.

Đây là helper-test, **không phải** hai fixture UNKNOWN thật. `claim-conflict` và `timeout/lost-response` vẫn NOT_RUN/BLOCKED_DESIGN: chờ TV3 xác nhận tuple/lifecycle, TV2 phê duyệt exact stack/source/ports, TV4 xác nhận provider/receipt và TV5 QA execution approval. Không suy từ 3 PASS ra COMMIT, stock hoặc Order reconciliation.

Hai raw TAP logs giữ nguyên byte nhờ `.gitattributes`; `SHA256.txt` ghi checksum. Không có token/PII production.
