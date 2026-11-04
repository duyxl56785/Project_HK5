# Soopi TV5 QA Tester

Công cụ QA chạy **chỉ trên `127.0.0.1:4318`**, độc lập với giao diện production. Phụ trách: Nguyễn Minh Duy (TV5). Công cụ tái sử dụng test hiện có của F15 Review, F16 Finance, JWT/RBAC, Catalog, Inventory, Cart và Frontend. Kết quả unit/mock không chứng minh E2E production.

## Cài đặt và mở trên Windows PowerShell

Từ thư mục gốc repository:

```powershell
cd tools/tv5-qa-tester
npm ci
npx playwright install chromium
npm run build
npm run dev
```

Mở `http://127.0.0.1:4318`. Cần Node.js 22+, Java 21 cho backend và Docker local đang chạy cho hai suite PostgreSQL. `npm run dev` phục vụ bản `dist` mới nhất sau `npm run build`.

## Chạy và xem lỗi

Chọn module rồi tick suite. Bấm **Run Selected** hoặc **Run All Safe Tests**. Run All Safe chỉ chạy các unit suite không cần database. Hai PostgreSQL suite cần chọn riêng; công cụ kiểm tra Docker local trước khi chạy và báo `BLOCKED_ENV` nếu không có. `QA-UI` chạy Playwright trên chính dashboard đang mở và lưu ảnh của **mọi** test PASS/FAIL. Các suite LIVE-SECURITY, UNKNOWN và FINANCE-PRODUCTION bị khóa. Không nạp `.env.demo`, không brute force và không gửi request tới môi trường ngoài localhost.

Kết quả phân biệt PASS, FAIL, SKIP, BLOCKED, BLOCKED_ENV, NOT_RUN, TIMEOUT và CANCELLED. Nếu tiến trình kết thúc nhưng không có TAP/JUnit hoặc ảnh Playwright tương ứng, runner không báo PASS. Nút Hủy dừng tiến trình hiện tại, các suite tiếp theo giữ NOT_RUN. Chạy một lần tại một thời điểm để tránh tranh chấp database.

## Bằng chứng và xuất báo cáo

Mỗi lần chạy có timestamp và full Git SHA, thư mục riêng tại `reports/minh-chung-kiem-thu/tv5-qa-tool/<run-id>/`. Ảnh Playwright tại `reports/minh-chung-kiem-thu/tv5-qa-tool/screenshots/<run-id>/`. Có raw log, JUnit XML (khi Gradle tạo thật), `report.html`, `report.json`, `report.csv`, `SHA256SUMS.txt`. Dùng **Xem bằng chứng** để xem ảnh; **Mở thư mục bằng chứng** mở đúng thư mục run local; **Export Evidence** tải ZIP gồm báo cáo, log, XML và ảnh. API chỉ cho truy cập các run/file thuộc evidence root, không nhận path hoặc shell command tùy ý. Các raw artifact có chuỗi trông giống secret bị giữ ngoài thư mục bằng chứng.

Ảnh Playwright là ảnh chụp trình duyệt thật trong test, không phải ảnh tổng hợp. Suite thủ công `QA-UI-DIAGNOSTIC` tạo **một assertion cố ý FAIL** để xác minh screenshot lỗi và báo cáo FAIL; đây là self-test của công cụ, không phải lỗi sản phẩm. Nếu Playwright Chromium chưa cài, suite FAIL/BLOCKED tùy lỗi và không được coi là PASS. Trước khi chia sẻ ZIP, TV5 vẫn phải kiểm tra thủ công nội dung ngoài bộ lọc secret tự động.

## Tự kiểm tra công cụ và demo

```powershell
npm test
npm run build
npm run dev
```

Trong cửa sổ PowerShell thứ hai, chọn `QA-UI` → **Run Selected** để chụp ảnh dashboard thật. Sau đó chọn `F15-RULES` hoặc `F16-RULES`, xem expected/actual, raw log và JUnit. Chọn `FINANCE-PRODUCTION` để trình bày trạng thái BLOCKED. Xuất HTML/JSON/CSV/ZIP và đối chiếu SHA256. Không dùng ảnh hoặc unit PASS để tuyên bố UAT Issue #18 đã hoàn tất.

Không tự bật integration flags, không dùng shared Docker volume, không chạy migration lên dữ liệu thật. Các kịch bản JWT hết hạn, session revoke, XSS HTTP, rate limiting, Store isolation live, Review legacy PostgreSQL và Finance settlement production chỉ được nghiệm thu khi môi trường/contract tương ứng được phê duyệt.
