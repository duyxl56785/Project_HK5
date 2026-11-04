# PR #170 — TV5 tái hiện proxy forward lặp, 04/11/2026

Source [PR #170](https://github.com/duyxl56785/Project_HK5/pull/170) exact HEAD `f90754deb6047796f554a2f6122e7d0079f69801`, detached worktree sạch, Node 22.23.3, Windows. Chỉ dùng hai HTTP server giả trên `127.0.0.1` và payload giả `fake-reserve-1`/`fake-sku`; không gọi Inventory, database hoặc hệ thống thật, không bật cờ UNKNOWN.

`verify-replay-tv5.mjs.txt` là probe nguyên byte; copy vào **root** worktree của đúng HEAD và đổi tên thành `verify-replay-tv5.mjs`, rồi chạy `node --test ./verify-replay-tv5.mjs`. Oracle: hai request cùng body/operationId qua cùng handler chỉ được forward **một lần**. Kết quả thực tế: exit **1**, **0 PASS / 1 FAIL**, assertion `actual 2 !== expected 1`; upstream nhận hai request. Đây là **RED tái hiện finding P2**, không phải fix PASS. `verify-replay-tv5-red.raw.log` là TAP output nguyên byte, SHA256 ở `SHA256.txt`. Không thay source/branch TV4.

Owner TV4 cần thêm giới hạn một lần có xử lý concurrent requests, giữ response suppression và không synthesize HTTP outcome; thêm regression thứ hai cho hai request đồng thời. Chỉ chạy lại oracle sau commit mới. TV2 còn nêu finding P3 `onReceipt` throw làm crash runner; probe này **không** kiểm P3. Cả `lost-response` live và `claim-conflict` vẫn NOT_RUN/BLOCKED theo các gate owner hiện hành.
