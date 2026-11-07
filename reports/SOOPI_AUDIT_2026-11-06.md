# Soopi TV5 — kiểm tra và sửa lỗi 06/11/2026

Demo đã đọc, giữ nguyên: `D:/CMC Uni/5 Ky Dong/WEB/BCK WEB/Project_HK5_TV5_DEMO`, detached `13ca5d7c0a164b353617c8ebe2f276a402bc5e1a`, untracked evidence được giữ. Source sửa trên feature branch **codex/tv5-qa-dashboard-20261105**, PR #179, không commit detached. Đã đọc AGENTS/CONTRIBUTING/OWNERSHIP, README/RUN_LOCAL, Compose/Vite, PR snapshot mới (8 open, main9ceddba). Không sửa Auth/Cart/Inventory branch owner khác.

Website: **http://localhost:5173**, QA Tester: **http://127.0.0.1:4318**. Stack mới `hk5-soopi-audit-20261106`: 15 container Up, PostgreSQL/Redis/Kafka/Config/Eureka healthy; chỉ UI5173/Gateway18080 bind loopback, DB không publish. Không xóa volume nào. Inventory/Finance OFF. Vite target `http://localhost:18080` đúng; lỗi API trước đó do không có backend listener. Auth/register thực tế hoạt động khi stack chạy. Generic fallback UI do TV2 sở hữu, chưa có bằng chứng bug Auth cần sửa.

## Bug table

| Bug ID | Chức năng | Lỗi cụ thể | Nguyên nhân xác minh | Severity | Owner | Đã sửa? | Test sau sửa | Evidence |
|---|---|---|---|---|---|---|---|---|
| BUG-001 | API Catalog/Store | Gateway không lắng nghe khi demo chỉ chạy frontend | Vite target localhost:18080 đúng; trước startup không có container chạy, sau startup 15 container Up | P1 | Vận hành local/TV5 | VERIFIED | Khởi động project Docker riêng; GET /api/stores và /api/catalog/products HTTP200 | [artifacts](minh-chung-kiem-thu/soopi-audit-20261106/) |
| BUG-002 | Đăng ký | Yêu cầu không thành công theo quan sát ban đầu | Chưa chứng minh lỗi Auth độc lập; không đếm trùng BUG-001 | P1 | Vận hành local | NOT_REPRODUCED_AFTER_ENV_FIX | Đăng ký/login/logout/duplicate/mật khẩu sai đều PASS khi backend chạy | [artifacts](minh-chung-kiem-thu/soopi-audit-20261106/) |
| BUG-003 | Cancel | RUNNING/CANCELLED trong lịch sử | CANCELLED hợp lệ không tự tạo bug; không đếm là lỗi mới | INFO | TV5 | VALID_STATUS | Bản sửa Windows child tree c52f529 đã có; regression thật cha/cháu PASS | [artifacts](minh-chung-kiem-thu/soopi-audit-20261106/) |
| BUG-004 | Runner concurrency | Hai start đồng thời vượt khóa trước await gitSha | server/runner.mjs start: active chỉ được set sau await ở baseline | P1 | TV5 | FIXED_VERIFIED | Giữ khóa trước await; regression chỉ một start được nhận | [artifacts](minh-chung-kiem-thu/soopi-audit-20261106/) |
| BUG-005 | Runner/history | RUNNING mồ côi và reload không tiếp quản kết quả hiện hành | hydrate giữ nguyên status RUNNING mà không có child; UI current khởi tạo null | P1 | TV5 | FIXED_VERIFIED | Checkpoint tiến trình, hydrate INTERRUPTED, dashboard phục hồi run đúng | [artifacts](minh-chung-kiem-thu/soopi-audit-20261106/) |
| BUG-006 | Result status | Toàn bộ skipped được gắn PASS | counts.tests>0 được chuyển PASS kể cả pass=0 | P1 | TV5 | FIXED_VERIFIED | Tách SKIP và hiển thị summary | [artifacts](minh-chung-kiem-thu/soopi-audit-20261106/) |
| BUG-007 | Evidence download | report.html/F15-RULES.log trả404 | server/http.mjs evidence regex trước sửa không nhận dot trong segment đầu | P1 | TV5 | FIXED_VERIFIED | Regex nhận dấu chấm ở filename đầu; HTTP download200 | [artifacts](minh-chung-kiem-thu/soopi-audit-20261106/) |
| BUG-008 | Runner exception | exec throw để report RUNNING và Promise rejection | process().finally không có catch; không finalize report khi exec throw | P1 | TV5 | FIXED_VERIFIED | Bắt exception, persist lỗi đã lọc, giải phóng active | [artifacts](minh-chung-kiem-thu/soopi-audit-20261106/) |
| BUG-009 | ZIP | Ảnh trong ZIP không khớp đường dẫn manifest | archive bỏ run-id lồng trong screenshot path nhưng manifest giữ run-id | P2 | TV5 | FIXED_VERIFIED | Giữ layout tương đối screenshots/run-id/suite; 16 ZIP entries hash khớp | [artifacts](minh-chung-kiem-thu/soopi-audit-20261106/) |
| BUG-010 | Responsive | Dashboard diagnostic/evidence tràn ngang 375px | flex item minimum content width đẩy suite badge ra ngoài viewport | P2 | TV5 | FIXED_VERIFIED | min-width:0, minmax grid, wrap source/path; regression mobile PASS | [artifacts](minh-chung-kiem-thu/soopi-audit-20261106/) |
| BUG-011 | Playwright dashboard | Selector F15-RULES khớp suite và restored result | getByText strict mode trả2 phần tử sau phục hồi current run | P2 | TV5 | FIXED_VERIFIED | Scope role checkbox, kiểm disabled khi run đang chạy | [artifacts](minh-chung-kiem-thu/soopi-audit-20261106/) |

**Thống kê:** 7 nhóm yêu cầu được đối chiếu (Auth, Catalog, Cart/Checkout, F15, F16, Return/Inventory, Frontend/QA); mức thực thi khác nhau, không phải mọi case live đều hoàn thành. **9 lỗi mới được xác minh**: 1 lỗi môi trường +8 lỗi code/test thuộc TV5 (7 implementation, 1 locator test). 8 lỗi TV5 đã sửa/test; môi trường đã khôi phục. BUG002 không đếm trùng môi trường; BUG003 không phải defect mới. Không có security finding sản phẩm được xác minh hoặc bug code owner khác đã chứng minh trong phạm vi chạy. Các dependency owner khác bên dưới vẫn mở.

## Test và provenance

- Baseline `13ca5d7`: 5 regression mới **0 PASS/5 FAIL**, raw runner-red.tap. Sau sửa runner: 15/15 PASS (gồm Windows kill cha/cháu thật); build/typecheck PASS. Một lượt trung gian 12/13 PASS có race ở assertion cleanup được sửa bằng đợi persist/finally; không giấu log FAIL.
- Source `849e2f43205e398e816e380b27d47be4e19b3aac`: WEBSITE3 PASS, F15 rules18 PASS, F16 rules15 PASS, legacy R06 PostgreSQL1 PASS, Finance/COD PostgreSQL9 PASS; QA-UI **0 PASS/2 FAIL** do selector restore. Đây là MockMvc+PostgreSQL thật với provider/Store mocks trong integration tests, không phải live Finance. Run report/raw XML giữ nguyên.
- Mobile diagnostic display fixture: **2 PASS/1 FAIL** tràn ngang → sửa CSS → **3 PASS/0 FAIL**. Đây là fixture render diagnostics/XSS, không giả response để chứng minh website lỗi. Screenshot RED/GREEN được giữ.
- Final smoke source **6418d8a8ca9ae7a70c7fbf8a2fbeb4007d463839**: WEBSITE **4 PASS**, QA-UI **3 PASS**; 7 screenshot thật và 4 JSON metadata, input/email che, không trace/video/body/header/cookie. Website kiểm Catalog/Store200, register/login/logout/duplicate409, wrong-password error, Customer Admin403, invalidJWT401, JWT hết hạn ký đúng401, hai Store Cart add/change/remove/quantity400, XSS query không execute, viewport375 không tràn.
- Các export thật HTML/JSON/CSV/raw log/ZIP đều HTTP200. ZIP **16 manifest entries khớp SHA256, 0 mismatch**, 7 ảnh/4 metadata; `export-verification.json`. F15 legacy/COD unit và PG không được cộng vào số final UAT.
- Run All Safe tại final source có receipt riêng được bổ sung sau hoàn tất; không gộp các lượt hoặc suy PASS từ tên suite.

## Phạm vi chưa nghiệm thu

Checkout/COD/settlement live **BLOCKED_DEPENDENCY** khi pair/Finance OFF; không tự bật để demo. Review delivered/aggregation/Admin moderation live **BLOCKED_DEPENDENCY** bởi seed Order journey; unit/legacy PG chứng minh riêng, không thay live evidence. Seller Finance isolation với dữ liệu nhiều Store live NOT_RUN. Rate-limit PR #177 chưa tích hợp trong demo, **NOT_INTEGRATED**; không brute force. Return #180 đã merged trên main mới nhưng **NOT_INTEGRATED trong source demo #179**, Admin Return #181/Seller Return #182 còn PR riêng theo snapshot; không coi thiếu Return/restock ở demo là regression. Chờ owner integration/review; UNKNOWN vẫn NOT_RUN/BLOCKED, không đổi policy. Final #18 và Finance production chưa đạt gate.

## Chạy lại

Từ worktree source #179, PowerShell:

```powershell
$env:COMPOSE_PROJECT_NAME='hk5-soopi-audit-20261106'
node scripts/demo/up.mjs
```

Lệnh này giữ gate mặc định OFF, chỉ dùng project riêng đã tạo ở phiên này. Không dùng `down -v` hoặc force regenerate secret. Khi stack đang chạy không cần build lại để xem UI.

```powershell
cd tools/tv5-qa-tester
npm ci
npm run build
npm run dev
```

Chọn WEBSITE-SMOKE/QA-UI để chạy live local. Run All Safe chỉ unit. Raw run reports HTML/JSON/CSV, logs/XML, ảnh và metadata ở `reports/minh-chung-kiem-thu/tv5-qa-tool/<run-id>/` và `screenshots/<run-id>/`. [Bug JSON và RED/GREEN](minh-chung-kiem-thu/soopi-audit-20261106/bugs.json). SHA256 manifest không thay owner sign-off. Báo cáo tự động là triage; verifiedCause chỉ ghi khi có căn cứ, không biến suspicion thành fact.

## Receipt hoàn tất

Run All Safe tại `6418d8a`: **8/8 suite PASS**, F15 18, F16 15, JWT 2, RBAC 5, Catalog 100, Inventory 9, Cart 10, Frontend 157; 316 test trong đúng một lượt unit này, không gọi là release total. Final smoke riêng WEBSITE4 +QA-UI3 PASS. [Run IDs/full SHA và từng kết quả](minh-chung-kiem-thu/soopi-audit-20261106/runs.json). [HTML bug report](minh-chung-kiem-thu/soopi-audit-20261106/audit-report.html).
