# Phần báo cáo TV5 về Review Finance và kiểm thử Soopi

Nguyễn Minh Duy phụ trách F15 Product Review, F16 Finance, kiểm thử tích hợp và đối chiếu bằng chứng của nhóm. Phần nội dung này dùng cho báo cáo bản nộp và phần thuyết trình TV5. Kiểm chứng hiện có hỗ trợ các chức năng và trường hợp được nêu cụ thể; hệ thống vẫn chưa đạt final UAT vì còn UNKNOWN và tích hợp Seller Return.

## 1 Vai trò và phạm vi đóng góp

TV5 xây dựng quy tắc Review/Finance, đối chiếu API và sự kiện với các miền Order, Product và Inventory; viết regression cho tình huống sai quyền, gọi lặp, sai trạng thái và tranh chấp settlement. Các thay đổi shared được bàn giao owner review. TV5 cũng thực hiện formal review, phân loại lỗi, lưu raw JUnit/log/ảnh và gắn mỗi kết quả với Git SHA. Từ 01/11/2026, TV2 tiếp nhận Auth/Gateway/frontend chung từ TV1; các tên TV1 trong lịch sử được giữ để bảo toàn nguồn.

## 2 F15 Product Review

F15 cho phép người mua đánh giá sản phẩm đủ điều kiện từ đơn đã giao, kiểm tra rating 1–5, ngăn review trùng và hỗ trợ Admin ẩn/hiện. Khi ẩn, review không được tính vào tổng hợp công khai. Kiểm tra ở backend là căn cứ quyền truy cập; UI chỉ hỗ trợ thao tác. R06 bổ sung giới hạn comment theo UTF-16 và bảo toàn khả năng quản trị comment legacy. Input được render như văn bản; kiểm thử XSS phải phân biệt DOM/unit với hành vi trên stack thật.

Bằng chứng hẹp đã công bố: trên main `c1ac4d4e5dd450d5d3fc3d6a8267cb5b45f32d48`, ReviewCommentLimitTest 2 PASS và ReviewCommentMigrationPostgresTest 1 PASS ([XML](../../reports/minh-chung-kiem-thu/issue18-main-c1ac-r06/)). Trên Soopi source `6418d8a8ca9ae7a70c7fbf8a2fbeb4007d463839`, suite F15 có 18 unit PASS, gồm 16 rules và 2 R06. Các lượt này không chứng minh toàn bộ luồng Review hai Store trên main hiện hành; kết luận live cần receipt buyer/order/product, quyền Admin và aggregate trước/sau.

## 3 F16 Finance

F16 ghi nhận gross, commission và seller net từ snapshot của đơn cửa hàng. Phí thông thường là 5%; House 0% chỉ trong phạm vi policy được duyệt. Tiền dùng BigDecimal và quy tắc làm tròn của contract; không lấy giá hay tỷ lệ mới để sửa snapshot đơn cũ. Settlement yêu cầu trạng thái Order/COD hợp lệ, chống thực hiện lặp và kiểm quyền theo Store. Bốn nhóm doanh thu và House operation cần đối chiếu bản policy áp dụng, không suy production approval từ unit arithmetic.

Return MVP được TV2 chốt ở [Issue155](https://github.com/duyxl56785/Project_HK5/issues/155#issuecomment-6650087223): buyer trả nguyên SellerOrder DELIVERED COD chưa settled trong 7 ngày UTC; REQUESTED/APPROVED/RECEIVED chặn settlement, REJECTED bỏ chặn. collectCod vẫn được phép khi tiền thực tế đã thu. Tạo Return và settle khóa cùng SellerOrder trong transaction. Không tự refund/reversal hoặc RELEASE hàng đã CONSUME.

Trên main **618f9ea8f723d607454397012cd5613ca199f660**, FulfillmentPostgres16IT có **20 PASS/0 FAIL/0 SKIP**, gồm concurrent Return–settlement, pending/received block và rejected unblock. [JUnit XML và SHA256](../../reports/minh-chung-kiem-thu/tv5-main-return-settlement-20261107/) là receipt gốc; lần chạy đó không giữ raw console log. Đây là HTTP MockMvc + PostgreSQL Testcontainers với provider/Store mocks, không phải live Gateway, refund hay Finance production. Source không đổi trong phiên tiếp tục nên không chạy lại test chỉ để tạo số liệu mới.

## 4 QA và integration testing

Ma trận QA tách unit, PostgreSQL/Kafka integration, HTTP MockMvc, browser mock và live E2E. Mỗi evidence có command, môi trường, full source SHA, kết quả và manifest. Một suite xanh không tự khép finding ngoài assertion của suite; ví dụ PR182 có 22 PG PASS nhưng V7 vẫn cho receipt rỗng qua SQL. PR185 có full ordered E2E PASS nhưng spec wrong-role chưa có fixture độc lập. Final UAT chỉ chạy khi các dependency và quyền thực thi từng fixture đủ; không cộng kết quả khác SHA thành tổng release.

## 5 Security regression

Các ca cần bảo vệ gồm JWT sai/hết hạn, session revoke, Customer/Seller/Admin RBAC, Store isolation, moderation và input validation. Soopi live smoke trên stack giả đã kiểm invalid JWT401, JWT hết hạn ký đúng401, Customer Admin403, duplicate registration409, XSS query không thực thi và Cart quantity validation400. Đây là scope của source6418d8a, không thay nghiệm thu current authority mọi endpoint hay production rate limiting. PR184 demo register-limit đã có scoped TV5 approval tại HEAD528180e; giữ nguyên nếu source không đổi. Không thực hiện brute force hoặc UNKNOWN ngoài gate.

## 6 Formal reviews và phối hợp

| PR | Kết quả TV5 đã có | Giới hạn và owner tiếp theo |
|---|---|---|
| 179 | Tool TV5, source fixes849e2f4/6418d8a và evidence2a2c4e4 đã push | Chờ duyxl56785 review HEAD mới; approval7cf05ea là cũ |
| 182 | COMMENTED c187e5b; 12 unit +22 PG PASS trong lượt trước | TV4 sửa V7 nonblank, nhận183, giải conflict giữ Admin/Seller routes |
| 183 | APPROVED scoped c7defdc | Không review lặp; chờ TV4 tiếp nhận vào182 |
| 184 | APPROVED scoped528180e | Không review lặp khi HEAD không đổi |
| 185 | COMMENTED e0eb9aa | TV2 sửa fixture độc lập rồi test selected/full suite ở HEAD mới |
| 186 | Main Return PG evidence d83b974 | Chờ duyxl56785; XML/hash20tests đã đối chiếu, không có finding mới |

## 7 Lỗi TV5 đã phát hiện và sửa

Soopi có các lỗi start race, RUNNING mồ côi/reload, skipped-as-PASS, download404, exec exception chưa finalize, ảnh ZIP lệch manifest, dashboard tràn ngang375px và selector trùng sau restore. Regression ban đầu 5 FAIL được giữ; sau sửa bộ runner15 PASS. Mobile có ảnh RED và GREEN. Backend ECONNREFUSED được phân loại lỗi môi trường khi không có listener; khởi động stack riêng khôi phục GET200. Không nhận lỗi môi trường là lỗi Auth business. [Báo cáo và từng bug](https://github.com/duyxl56785/Project_HK5/blob/2a2c4e4/reports/SOOPI_AUDIT_2026-11-06.md) chứa loại, owner và expected/actual.

## 8 Soopi TV5 QA Tester

Công cụ nằm ở `tools/tv5-qa-tester`, bind127.0.0.1:4318, nhận suite ID qua allowlist, không cho HTTP truyền shell command. Dashboard có tiếng Việt, PASS/FAIL/SKIP/BLOCKED/NOT_RUN/CANCELLED/INTERRUPTED, thời gian/full SHA, diagnostics tách suspected và verified cause, screenshot/raw log, exportHTML/JSON/CSV/ZIP. Windows cancel dùng taskkill chỉ cây PID vừa spawn. Evidence có thư mục run riêng và SHA256, không sửa raw log để làm xanh. Smoke cuối source6418: WEBSITE4 +QA-UI3 PASS; Run All Safe riêng316 unit PASS; ZIP16 entry hash khớp. Không gọi316 là số test release.

## 9 Hướng dẫn demo

Website và tester là hai tiến trình riêng. Dựng stack dữ liệu giả theo [RUN_LOCAL](../RUN_LOCAL.md), chọn Compose project riêng, kiểm port/volume trước chạy. Giữ Inventory/Finance OFF khi chưa có approval áp dụng; không sao chép lệnh opt-in thành quyền chạy. Với #179: vào tools/tv5-qa-tester, npm ci, npm run build, npm run dev; mở127.0.0.1:4318. WEBSITE-SMOKE cần website localhost5173 đã chạy. Không dùng down-v, force reset secret hoặc tái sử dụng volume nhóm để chữa lỗi kết nối. Xem [README tool tại PR179](https://github.com/duyxl56785/Project_HK5/blob/2a2c4e4/tools/tv5-qa-tester/README.md).

## 10 Tự đánh giá phần TV5

Đóng góp có thể kiểm chứng của em là nối yêu cầu nghiệp vụ với regression và evidence, phát hiện sai lệch mà CI tổng chưa chứng minh, và bàn giao đúng owner. Điểm cần hoàn thiện là integration Seller Return/restock, hai fixture UNKNOWN, ảnh nghiệm thu cuối và đối chiếu số liệu trên report/deck sau UAT. Em giữ các kết quả PARTIAL/BLOCKED công khai thay vì ghi toàn bộ hệ thống PASS. Chưa có rubric để quy đổi phần đóng góp thành điểm số; phần này không tự chấm điểm hoặc ký thay người duyệt.

## 11 Các trường còn chờ final UAT

| Trường bản nộp | Điều kiện điền |
|---|---|
| Final run SHA và CI URLs | Main cuối đã tích hợp source được owner nhận, hosted checks đúng SHA |
| Ngày/môi trường/actor/seed | Receipt của project cô lập thực chạy, không lấy từ rehearsal |
| Tổng test nghiệm thu | Chỉ một release run với denominator và skip/block giải thích |
| Kết quả UNKNOWN | Actual immutable tuple/waiver, provider COMMIT/stock receipt, runner/stop và quyền chạy từng ca |
| Ảnh B.1–B.5 cuối | Chụp màn hình thật gắn case/run/SHA, che credential/PII |
| Kết luận và chữ ký | TV5/owners xác nhận sau evidence; không suy từ20PG |

## 12 Đối chiếu 23 slide hiện có

Đã đọc XML toàn bộ23 slide của deck Soopi cung cấp ngoài repo, không suy layout từ text. Deck đang mô tả đề tài/kế hoạch/kiến trúc/UI, chưa chứa một phần nghiệm thu TV5 hoàn chỉnh. Đây là nội dung thay/bổ sung cho người tổng hợp, chưa phải PPTX đã chỉnh hoặc render.

| Slide | Nội dung TV5 chuẩn bị để đưa vào lời trình bày hoặc slide khi tổng hợp |
|---|---|
| 8 F15/F16 | Đánh giá đã mua; Admin moderation; Finance snapshot5%; giới hạn production/House và Return guard như mục2–3 |
| 10 Tổ chức nhóm | TV5 Nguyễn Minh Duy: Review/Finance/QA/evidence; cập nhật TV2 nhận Auth/Gateway từ01/11, giữ lịch sửTV1 riêng |
| 11 Quy trình | Evidence phải khớp SHA và scope;20PG không phải finalUAT; không tự merge |
| 15 Tuần2 | Contract integration/Store isolation và bug ownership; tách unit/mock/live |
| 16 Tuần3 | Main618f9ea Return guard20PG PASS; UNKNOWN blocked; Soopi tự động thu diagnostics/ảnh/logs |
| 20–22 UI thật/responsive | Chỉ dùng ảnh thật theo case/SHA; UI demo không thay bảng nghiệm thu; không gắn ảnh smoke cũ cho main mới |
| 23 Demo/hỏi đáp | Mở website và QA tester; trình bày lỗi đã sửa và backlog; chưa công bố finalUAT hoặc Finance production |

Các số thị trường ở slide4–5 là nội dung nguồn lịch sử, phiên này không xác minh web nên không cập nhật/tuyên bố còn đúng. Không sửa nội dung owner khác hoặc tự thêm số liệu.

### Formal review links đã công bố

- [PR182 COMMENTED tại c187e5b](https://github.com/duyxl56785/Project_HK5/pull/182#pullrequestreview-5811031843)
- [PR183 APPROVED tại c7defdc](https://github.com/duyxl56785/Project_HK5/pull/183#pullrequestreview-5811019093)
- [PR185 COMMENTED tại e0eb9aa](https://github.com/duyxl56785/Project_HK5/pull/185#pullrequestreview-5811007513)
