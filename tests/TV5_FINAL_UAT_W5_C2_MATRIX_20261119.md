# TV5 — Ma trận Final UAT W5, C2 bật/tắt

**Mục đích:** ma trận thực thi cho Final UAT ngày 21/11/2026, trên đúng full SHA `main` do TV2 đóng băng. Đây là checklist trống để ghi nhận kết quả thật, không phải receipt hay kết quả rehearsal.

**Nguồn quyết định hiện hành:** comment TV2 ngày 18/11/2026 trên [PR #211](https://github.com/duyxl56785/Project_HK5/pull/211#issuecomment-6969662837): đóng băng main trước 18:00 ngày 20/11; TV2 chạy UAT/E2E ngày 21/11. Quyết định này mới hơn lịch freeze 19/11 trong các ghi chú cũ.

**Quyết định C2:** TV2 dự định bật C2 trong demo/UAT cuối, nhưng chỉ khi đủ cả bốn điều kiện: (1) #211 và PR B (hiện là #212) đã merge vào main trước 18:00 20/11; (2) TV5 approve phần tiền #211; (3) CI trên frozen SHA xanh và `voucher-c2.spec.ts` PASS, không skip; (4) không còn P0 mở cho C2. Thiếu bất kỳ điều kiện nào thì UAT chạy C2 OFF; báo cáo ghi **“đã code, đã test, chưa bật trong bản nộp”**. Voucher C1 và Khuyến mãi vẫn kiểm thử độc lập.

**Checkpoint làm mới 19/11/2026:** PR #211 HEAD `4f3152bc2188511a26e2b41f64fc94c6f31968ba` đã MERGED vào `main` tại `cee7c87d1c7437fc5ed0009405fd2d0fc394bfb2`; TV2, TV4 và TV5 đã formal `APPROVED` đúng HEAD, MD21 được sửa bằng V10 PostgreSQL trigger/regression và thread TV5 đã resolve. Main hiện tại được xác nhận qua `git ls-remote origin`: `cee7c87d1c7437fc5ed0009405fd2d0fc394bfb2`. Post-merge Backend readiness run `44493061575` SUCCESS; Frontend and E2E run `44493061534`: cả hai job `frontend` và `e2e` đều SUCCESS (step `Service logs` bị skip, không phải test skip). PR #212 HEAD `3483486acd375e2c45616974ab333d2b850b9771` vẫn OPEN; TV3 đã đổi base sang `main` tại base SHA `cee7c87d1c7437fc5ed0009405fd2d0fc394bfb2`. CI mới sau retarget là run `44496245054`, đã hoàn tất SUCCESS cả ba job: `frontend`, `E2E C2 OFF`, `E2E C2 ON`. Log hai job E2E xác nhận OFF **24 passed/0 skipped**, ON **25 passed/0 skipped**; frontend `typecheck`, `npm test`, `build` đều SUCCESS. Run `44456964277` trước retarget cũng xanh nhưng không thay thế run mới; bằng chứng hiện tại là run `44496245054` trên base `main@cee7c87d`. PR #212 vẫn OPEN và TV2 chưa formal review exact HEAD `3483486`. Review TV2 hiện có ở HEAD cũ `a43abf5`, không phải formal review cho HEAD `3483486`. Main chưa được TV2 xác nhận frozen; **Gate C2 CHƯA ĐẠT** vì #212 chưa merge, CI mới chưa hoàn tất, chưa có CI frozen-SHA, P0 triage và business/Finance approval. Giữ C2 OFF; không bật Finance.

**Tiền kiểm tích hợp do TV5 thực hiện (worktree disposable, không commit/merge):** `git merge --no-commit --no-ff` PR #211 vào `main@4471256` tự động, không conflict. Lệnh `:cart-service:test :inventory-service:test :order-service:test :product-service:test :seller-service:test --rerun-tasks` chạy lại toàn bộ 28 Gradle actions; 72 XML suites có 1.104 backend tests PASS (0 fail/error/skip; không phải PostgreSQL integration). Frontend 185/185 PASS, typecheck PASS, build PASS, `git diff --check` PASS. Đây là local preflight của ứng viên tích hợp, không phải hosted CI, không phải frozen SHA và không phải Final UAT. PR #211 backend CI PostgreSQL 156/156 là evidence riêng trên PR HEAD.

**Vai trò:** TV2 là người vận hành stack, công bố frozen SHA và quyết định cờ; TV3 Checkout/Cart/Order; TV4 Inventory/Fulfillment/Return/COD; TV5 đối chiếu tiền, ghi kết quả, evidence và P0/P1. TV5 không tự chạy stack có `HK5_VOUCHER_C2_ENABLED=true`.

## 1. Quy tắc điền kết quả

- Mỗi ca có bước chạy, kết quả mong đợi và hai cột riêng **C2 bật** / **C2 tắt**.
- Các ô `SHA`, `KQ`, `Evidence` để trống cho người chạy điền sau. `KQ` chỉ nhận `PASS`, `FAIL` hoặc `NOT_RUN`; nếu bị chặn, ghi `NOT_RUN` và lý do `BLOCKED` trong Evidence/ghi chú.
- Với ca chỉ thuộc C2, cột C2 tắt ghi nguyên văn **“không áp dụng, chưa bật”**. Không biến thành PASS.
- Mọi PASS/FAIL phải gắn full 40-character SHA, run ID, môi trường, thời gian Asia/Ho_Chi_Minh, actor giả, command/steps và raw evidence. Ảnh không thay HTTP/DB/Inventory/Finance oracle.
- Không dùng rehearsal SHA `240d028`, `c5faad9`, `ba25d22`, CI PR-head hoặc kết quả unit/integration ở SHA khác làm Final UAT. Không tạo giao dịch, COD receipt, settlement hoặc ảnh giả.
- Raw logs/JUnit/XML/JSON giữ nguyên byte. Chỉ redacted copies mới được chia sẻ; không ghi token, password, JWT, thông tin khách hàng hoặc `.env`.

## 2. Freeze và điều kiện vận hành

| Gate | Điều kiện bắt buộc | Người xác nhận | Receipt / giá trị cần ghi | Trạng thái hiện tại |
|---|---|---|---|---|
| G-01 | TV2 công bố full frozen `main` SHA sau freeze trước 20/11 18:00 | TV2 | SHA 40 ký tự: ____________________ | NOT_RUN |
| G-02 | Hosted readiness, frontend, E2E đều xanh trên đúng frozen SHA | TV2 | URLs + workflow run IDs: ____________________ | NOT_RUN |
| G-03 | Clean clone/clean install receipt cùng frozen SHA | TV1/TV2 | Machine, command, exit code, receipt: ____________________ | NOT_RUN |
| G-04 | PR #211 merged; TV2, TV4, TV5 reviews đủ; không còn thread tiền/fulfillment chưa xử lý | TV2/TV4/TV5 | Merge SHA + review URLs: `cee7c87d1c7437fc5ed0009405fd2d0fc394bfb2`; 3/3 APPROVED tại `4f3152b` | MERGED / 3-of-3 APPROVED; TV2 vẫn cần xác nhận P0 scope |
| G-05 | PR #212 (PR B) merged vào main trước 18:00 20/11 | TV2 | Merge SHA + CI URL: ____________________ | BLOCKED — #212 vẫn OPEN; retarget CI `44496245054` đã SUCCESS cả 3 jobs; chờ TV2 review exact HEAD `3483486` và quyết định merge |
| G-06 | CI frozen SHA có `voucher-c2.spec.ts` PASS, 0 skipped | TV2 | Run URL + exact summary: ____________________ | NOT_RUN — retarget CI `44496245054` đã PASS trên PR HEAD (C2 ON 25/25, 0 skip), nhưng chưa có frozen SHA nên chưa đáp ứng gate này |
| G-07 | Không còn P0 mở liên quan C2; TV2 xác nhận scope | TV2 + TV3/TV4/TV5 | Issue/triage links: ____________________ | NOT_RUN |
| G-08 | Nếu C2 ON: TV2 xác nhận cả `--approved-inventory-pair`, `--approved-finance-policy`, `--approved-voucher-c2`; Finance policy rate `0.05`, scale `0` | TV2 | Cờ đã duyệt + policy source: ____________________ | BLOCKED — #212 chưa merge; chưa có frozen-SHA CI/P0 triage hoặc Finance business approval; runtime env chưa được kiểm tra |
| G-09 | Stack disposable riêng, DB/volume/network/port riêng, dữ liệu giả; không đụng stack dùng chung | TV2/TV1 | Compose project + image digest + DB ID: ____________________ | NOT_RUN |

## 3. Ma trận Final UAT — luồng nền hai Store

Trong các ca nền, chạy cả hai cấu hình nếu TV2 cho phép: C2 ON và C2 OFF. Khác biệt voucher phải được ghi riêng trong phần 4. Không chạy UNKNOWN fault injection nếu chưa có runner/owner approval theo quyết định #18.

| ID | Bước thực hiện | Kết quả mong đợi | C2 bật — SHA / KQ / Evidence | C2 tắt — SHA / KQ / Evidence | Owner |
|---|---|---|---|---|---|
| UAT-01 Auth/RBAC | Gọi API khi chưa đăng nhập; login Customer, Seller A/B, Admin; thử endpoint sai role và token hết hạn/revoke | 401/403 đúng; không có side effect; quyền dựa trên server identity | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV1/TV2, TV5 ghi nhận |
| UAT-02 Catalog | Dùng Product ACTIVE của Store A/B; đối chiếu sellerId, storeId, SKU, giá và trạng thái trước Checkout | Catalog facts nhất quán; product/price/store không xác minh được thì Checkout fail closed | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV2/TV3 |
| UAT-03 Cart hai Store | Một Customer giả thêm ít nhất một sản phẩm từ Store A và một từ Store B; đổi quantity rồi reload Cart | Một Cart có đúng hai nhóm Store; subtotal từng Store và tổng khớp snapshot | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV3 |
| UAT-04 Checkout COD | Từ Cart hai Store, xác nhận một Checkout với COD và lưu Order ID | Một request thành công; không có thanh toán online/chuyển tiền thật; tổng COD khớp Order snapshot | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV3 |
| UAT-05 Parent/Child Orders | Mở Order detail và đọc DB read-only cùng run; đếm root Order và SellerOrders theo Store | Một Parent/Root Order, đúng hai SellerOrders khác ID, mỗi child thuộc đúng Store/Seller; tổng parent = tổng gross child | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV3 |
| UAT-06 Idempotency replay | Gửi lại cùng Idempotency-Key + cùng payload sau khi đã thành công | HTTP 200/replay trả cùng orderId; không tạo thêm Order, SellerOrder hoặc RESERVE | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV3/TV4 |
| UAT-07 Idempotency mismatch | Dùng cùng key nhưng đổi items, revision hoặc shipping | HTTP 409 theo contract; không tạo đơn mới và không phát sinh RESERVE thứ hai | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV3 |
| UAT-08 Shipping mismatch | Gửi shipping khác với quote/attempt đã xác nhận theo cùng key | HTTP 409; Order hiện có không bị thay đổi; không tạo side effect mới | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV3 |
| UAT-09 Inventory RESERVE | Đối chiếu read-only Inventory operation trước/sau Checkout hai Store và sau replay | RESERVE đúng operation/tuple, một lần cho mỗi intent; replay không tăng số lần gửi; tồn kho không âm | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV4/TV3 |
| UAT-10 Inventory lifecycle | Theo đúng luồng accept/prepare/ready/dispatch/deliver; thử cancel/reject chỉ ở trạng thái hợp lệ | CONSUME/RELEASE đúng transition và đúng operationId; không double consume/release; không tự suy từ UI | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV4 |
| UAT-11 Seller isolation | Seller A gọi list/detail/return/finance cho Store A rồi thử ID của Store B; lặp lại với Seller B | Mỗi Seller chỉ thấy dữ liệu Store mình; truy cập chéo bị 403/404 theo contract; không lộ record | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV4/TV2 |
| UAT-12 Return | Chỉ dùng COD order DELIVERED, chưa settled; Buyer tạo return, Seller đúng Store approve/reject/receive; thử duplicate key | Return state và quyền đúng; refundAmount bằng gross đã lưu; không tự hoàn voucher sau giao; không tự restock khi chưa có contract | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV4/TV3/TV5 |
| UAT-13 COD collection | Sau DELIVERED, Admin actor hợp lệ thu COD; thử trước DELIVERED và thử thu lần hai | Chỉ thu sau DELIVERED; một lần; amount khớp gross; có collection receipt/audit actor/thời gian; duplicate bị từ chối | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV4 |
| UAT-14 Finance snapshot | Đối chiếu từng SellerOrder: merchandise, discount, gross, platformFee, sellerNet; đọc report theo Store và platform | Phí 5% trên gross theo policy, HALF_UP/scale 0; sellerNet = gross − fee; không coi settledAt là chuyển tiền thật | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV5/TV4 |
| UAT-15 Admin | Admin xem moderation, Seller approval và COD/Finance read-only đúng quyền | Chỉ Admin được phép; audit/actor đúng; ảnh Hình 6.4 đúng màn Admin Seller approval | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV2/TV4 |
| UAT-16 UNKNOWN claim conflict | Không thực hiện live fault injection trong lượt này nếu chưa có runner, tuple, provider oracle và approval riêng | Giữ NOT_RUN/BLOCKED_DESIGN; không giả lập thành PASS, không tự retry/release | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV2/TV3/TV4/TV5 |
| UAT-17 UNKNOWN lost response | Không thực hiện live fault injection trong lượt này nếu chưa có runner, tuple, provider COMMIT receipt và approval riêng | Giữ NOT_RUN/BLOCKED_OWNER; không giả lập thành PASS hoặc tạo stock/order mới | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV2/TV3/TV4/TV5 |

## 4. Ma trận Voucher C1, Promotion và C2

### 4.1 Voucher C1 — quản lý voucher và hạn mức

Các ca C1 không phụ thuộc cờ C2; thực hiện ở cả hai cấu hình. C1 không áp voucher vào Cart/Checkout, không trừ lượt và không thay đổi tiền SellerOrder.

| ID | Bước thực hiện | Kết quả mong đợi | C2 bật — SHA / KQ / Evidence | C2 tắt — SHA / KQ / Evidence | Owner |
|---|---|---|---|---|---|
| C1-01 Tạo voucher | Seller A tạo voucher của Store A; thử gửi storeId của Store B trong body | Server lấy Store từ JWT; voucher tạo ở DRAFT và thuộc Store A; không thể giả mạo Store | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV3/TV2 |
| C1-02 Validation | Thử code 3/21 ký tự, FIXED không hợp lệ, PERCENT ngoài 1–90/thiếu maxDiscount, FIXED có maxDiscount, minOrderAmount sai, usageLimit ngoài 1–100000, endsAt không hợp lệ hoặc >90 ngày | Input sai bị 400; code chuẩn hóa uppercase; FIXED không có maxDiscount; PERCENT bắt buộc maxDiscount; duration không vượt 90 ngày | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV3 |
| C1-03 Draft/edit/state | Sửa voucher DRAFT; publish; thử sửa khi đã publish; pause/resume/cancel theo trạng thái hợp lệ | Chỉ DRAFT sửa được; transition hợp lệ; state SCHEDULED/ACTIVE/EXPIRED theo server time; history ghi actor và transition | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV3 |
| C1-04 Quota | Tạo/publish tới giới hạn mặc định 20; thử publish voucher thứ 21; xác minh DRAFT/PAUSED không chiếm slot | 20 được publish; voucher vượt hạn mức trả 409 `VOUCHER_QUOTA_EXCEEDED`; không tự hủy voucher đã publish | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV3/TV2 |
| C1-05 Limit request/Admin | Seller xin nâng hạn mức; thử request lặp cùng key/khác payload; Admin approve/reject; thử Seller gọi Admin API và Admin tự duyệt Store mình | PENDING không tăng quota; replay cùng key trả cùng request; khác payload 409; Admin quyết định/audit đúng; self-moderation 403 | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV3/TV2 |

### 4.2 Khuyến mãi — Flash Sale, Super Sale, xả kho

Các ca Promotion phải được kiểm thử độc lập với C2. MD12 quy định Seller chịu phần giảm; giá hiệu lực Catalog được dùng trong Checkout. Khi C2 ON, ca PROMO-04 kiểm thêm việc voucher bị từ chối nếu có Promotion (MD17). Khi C2 OFF, ca PROMO-04 không áp dụng.

| ID | Bước thực hiện | Kết quả mong đợi | C2 bật — SHA / KQ / Evidence | C2 tắt — SHA / KQ / Evidence | Owner |
|---|---|---|---|---|---|
| PROMO-01 Campaign lifecycle | Seller tạo chương trình Promotion, gửi duyệt; Admin xem và approve/reject theo quyền | State/audit đúng; chỉ chương trình được duyệt mới có hiệu lực; không dùng mock UI làm bằng chứng | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV2 |
| PROMO-02 Flash/Super Sale | Tạo sản phẩm thuộc Flash Sale và Super Sale; mở Catalog và Cart bằng Customer giả | Giá hiệu lực/giá gốc hiển thị nhất quán với Catalog; Seller chịu phần giảm; ghi riêng từng loại, không suy một loại từ loại khác | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV2 |
| PROMO-03 Clearance | Seller tạo chương trình xả kho, Admin duyệt chương trình và Product; Customer mở sản phẩm và Checkout COD không dùng voucher | Giá sale đúng snapshot; số lượng/trạng thái theo campaign; Checkout không áp voucher trong ca độc lập này | SHA: ____; KQ: ____; Evidence: ____ | SHA: ____; KQ: ____; Evidence: ____ | TV2/TV3 |
| PROMO-04 Voucher conflict (MD17) | Với sản phẩm đang hưởng Promotion, Customer nhập voucher Store tương ứng rồi yêu cầu quote/Checkout | Khi C2 ON: từ chối rõ `409 VOUCHER_PROMOTION_CONFLICT`, không đặt đơn/không trừ lượt. Khi C2 OFF: không áp dụng, chưa bật | SHA: ____; KQ: ____; Evidence: ____ | không áp dụng, chưa bật | TV2/TV3 |

### 4.3 Voucher C2 — tiền, snapshot và lượt dùng

Các ca C2 chỉ chạy khi toàn bộ gate G-04..G-08 đạt. Nếu C2 OFF, mọi ca C2 dưới đây ghi **“không áp dụng, chưa bật”** ở cột C2 tắt; không được đánh dấu PASS.

| ID | Bước thực hiện | Kết quả mong đợi | C2 bật — SHA / KQ / Evidence | C2 tắt — SHA / KQ / Evidence | Owner |
|---|---|---|---|---|---|
| C2-01 Flag/capability | Đọc voucher capability bằng Customer khi cờ ON; lặp lại khi cờ OFF; gọi quote/checkout voucher khi OFF | ON: `enabled=true`; OFF: `enabled=false` và yêu cầu voucher bị từ chối `VOUCHER_C2_DISABLED`; không ảnh hưởng C1 management | SHA: ____; KQ: ____; Evidence: ____ | không áp dụng, chưa bật | TV2/TV3 |
| C2-02 Per-Store voucher | Áp tối đa một voucher cho mỗi Store; thử hai mã cùng Store và mã thuộc Store không có trong Cart | Hai Store có thể dùng hai mã riêng; trùng Store trả `VOUCHER_DUPLICATE_STORE`; Store không thuộc Cart trả `VOUCHER_STORE_MISMATCH` | SHA: ____; KQ: ____; Evidence: ____ | không áp dụng, chưa bật | TV3 |
| C2-03 MD15 FIXED/minOrder | Tạo FIXED voucher; quote tại đúng minOrderAmount và thấp hơn minOrderAmount | Đúng minOrder được giảm đúng discountValue; thấp hơn bị từ chối `VOUCHER_MIN_ORDER_AMOUNT` | SHA: ____; KQ: ____; Evidence: ____ | không áp dụng, chưa bật | TV3/TV5 |
| C2-04 MD15 PERCENT | Tính 199.999 × 15% = 29.999,85; kiểm HALF_UP về 30.000 rồi cap với maxDiscount; thêm case chạm cap | Làm tròn HALF_UP về đồng một lần, sau đó lấy min với maxDiscount; không làm tròn lần hai | SHA: ____; KQ: ____; Evidence: ____ | không áp dụng, chưa bật | TV3/TV5 |
| C2-05 MD16 two-Store money oracle | Store A: 150.000 − 10.000; Store B: 280.000 − 5.000; xác minh quote, Checkout, Parent Order và hai SellerOrders | A gross 140.000, fee 7.000; B gross 275.000, fee 13.750; tổng khách trả 415.000; phí tính trên gross với rate 5%, scale 0; sellerNet lần lượt 133.000 và 261.250 | SHA: ____; KQ: ____; Evidence: ____ | không áp dụng, chưa bật | TV3/TV5 |
| C2-06 MD19 failed checkout | Dùng voucher hợp lệ, tạo một lỗi Checkout chắc chắn trước khi đặt đơn; đọc voucher redemption ledger | Checkout thất bại không làm mất lượt; release/tombstone idempotent; không có Order/RESERVE ngoài dự kiến | SHA: ____; KQ: ____; Evidence: ____ | không áp dụng, chưa bật | TV3/TV4 |
| C2-07 MD19 cancel/reject | Đặt đơn có voucher; cancel hoặc Seller reject ở trạng thái được phép trước dispatch; đọc redemption ledger và retry/recovery | Lượt voucher được hoàn đúng một lần; retry không release trùng; Return sau DELIVERED không hoàn lượt | SHA: ____; KQ: ____; Evidence: ____ | không áp dụng, chưa bật | TV4/TV3 |
| C2-08 MD20 Return gross | Với SellerOrder đã dùng voucher, DELIVERED COD chưa settled, tạo Return theo flow được phép; đối chiếu refundAmount với snapshot | `refundAmount = SellerOrder.gross` sau giảm; ví dụ gross 140.000 thì amount 140.000, không hoàn theo merchandise trước giảm; không tự chuyển tiền | SHA: ____; KQ: ____; Evidence: ____ | không áp dụng, chưa bật | TV4/TV5 |
| C2-09 MD21 immutable snapshot | Sau khi đặt đơn, đọc `voucher_snapshot`, thử thay đổi bằng đường cập nhật được phép trong test DB/IT | Snapshot không thể thay đổi sau INSERT; DB từ chối UPDATE snapshot; CHECK luôn giữ `gross = merchandiseAmount − discountAmount` | SHA: ____; KQ: ____; Evidence: ____ | không áp dụng, chưa bật | TV3/TV5 |
| C2-10 MD24 Finance report | Đọc report Store A/B và platform sau khi có SellerOrders đủ điều kiện report; so gross, fee, net và voucherDiscount | voucherDiscount hiển thị riêng; không cộng vào platformFee; gross/fee/net theo snapshot sau giảm | SHA: ____; KQ: ____; Evidence: ____ | không áp dụng, chưa bật | TV5 |
| C2-11 MD25 zero gross | Quote FIXED voucher có discount bằng đúng merchandiseAmount; thử giá trị thấp hơn 1 đồng | Bị từ chối `VOUCHER_GROSS_TOO_LOW`; không tự giảm mức discount; không tạo SellerOrder/COD 0 đồng | SHA: ____; KQ: ____; Evidence: ____ | không áp dụng, chưa bật | TV3/TV5 |

## 5. Đối chiếu hình ảnh và bản nộp

Theo quyết định TV2 tại [Issue #162 ngày 16/11](https://github.com/duyxl56785/Project_HK5/issues/162#issuecomment-6907759709), báo cáo nộp chỉ có **một file** `Bao_cao_Smart_ECommerce_ban_nop.docx`, do TV2 ghép từ `Bao_cao_Smart_ECommerce.docx`. Bản Word đó chưa có trong repository tại checkpoint này. Không dùng DOCX nháp TV5 thay thế.

| Hình | Nội dung đúng theo TV2 | Bằng chứng cần đối chiếu | SHA / KQ / Evidence |
|---|---|---|---|
| 6.1 | Trang chủ: banner, danh mục, sản phẩm | Ảnh TV2 chụp lại trên frozen SHA, full SHA + viewport + SHA256 | SHA: ____; KQ: ____; Evidence: ____ |
| 6.2 | Trang chủ trên điện thoại 390 px | Ảnh live viewport 390 px trên frozen SHA | SHA: ____; KQ: ____; Evidence: ____ |
| 6.3 | Người bán quản lý sản phẩm | Seller actor giả, Product/Store response cùng run | SHA: ____; KQ: ____; Evidence: ____ |
| 6.4 | Admin duyệt và quản lý người bán | Admin actor giả, Seller approval screen/audit; không dùng ảnh Checkout | SHA: ____; KQ: ____; Evidence: ____ |
| 6.5 | Giỏ hàng có sản phẩm của hai cửa hàng | Một Cart/session, đúng hai Store, subtotal và total khớp response | SHA: ____; KQ: ____; Evidence: ____ |
| 6.6 | Một Parent Order tách thành hai SellerOrders | Cùng Order ID/run, hai child ID/Store khác nhau, read-only DB receipt | SHA: ____; KQ: ____; Evidence: ____ |
| 6.7 | Người bán chỉ thấy đơn con của cửa hàng mình | Seller A/B request/response redacted và cross-Store negative test | SHA: ____; KQ: ____; Evidence: ____ |
| 6.8 | Tài chính sàn: doanh thu, phí 5%, COD | Finance report snapshot và COD collection evidence; caption dùng “đã ghi nhận đối soát”, không nói đã chuyển tiền thật | SHA: ____; KQ: ____; Evidence: ____ |

Ảnh chỉ được chụp lại trên frozen SHA. Không dùng ảnh rehearsal, ảnh cũ khác theme, ảnh Cart trống, ảnh mock hoặc ảnh từ CI làm hình nộp.

## 6. P0/P1 triage và các giới hạn không được che

| Hạng mục | Kỳ vọng phân loại | Owner | Bằng chứng/điều kiện cần |
|---|---|---|---|
| Auth, Cart hai Store, COD Checkout, Parent/2 SellerOrders, idempotency, shipping, Inventory RESERVE, seller isolation | P0 — phải chạy Final UAT | TV2/TV3/TV4 | UI + HTTP + DB/Inventory read-only receipt cùng frozen SHA |
| UNKNOWN claim-conflict và lost-response | BLOCKED_DESIGN / NOT_RUN cho đến khi có runner/tuple/provider oracle được duyệt | TV2/TV3/TV4/TV5 | Không chạy fault injection tự phát; không dùng fake-loopback làm PASS |
| Inventory lifecycle/Return restock RET-05 | BLOCKED nếu chưa có contract và exactly-once receipt | TV4 | Không tuyên bố restock PASS; không tạo stock effect giả |
| Admin COD collection | P0 nếu demo/báo cáo tuyên bố có | TV4 | Receipt thật, actor, amount, timestamp, idempotency và audit |
| Finance settlement | BLOCKED nếu thiếu business approval/runtime/reconciliation | TV2 + business owner + TV5 | `settledAt` chỉ là ghi nhận trong ứng dụng, không phải chuyển tiền thật |
| C2 | Bật chỉ khi G-04..G-08 đủ; nếu thiếu ghi “đã code, đã test, chưa bật” | TV2 quyết định; TV5 review tiền | Review #211, merge #212, CI frozen SHA 0 skip, không P0 |
| Flash Sale/Super Sale live Checkout | NOT_RUN nếu chỉ có unit/PG/clearance E2E; ghi đúng loại Promotion đã thực chạy | TV2/TV3 | Mỗi campaign cần live evidence riêng |

## 7. Kết quả Final UAT (để trống trước ngày chạy)

| Trường | Giá trị cần điền sau khi TV2 chạy |
|---|---|
| Frozen main SHA | ________________________________ |
| Main freeze timestamp (Asia/Ho_Chi_Minh) | ________________________________ |
| CI readiness/frontend/E2E URLs | ________________________________ |
| Clean clone machine + receipt | ________________________________ |
| C2 decision | `đã bật` / `đã code, đã test, chưa bật` |
| Flags actually used (TV2 records) | ________________________________ |
| Compose project / image digests | ________________________________ |
| Run ID / start-end time | ________________________________ |
| TV2 operator / TV3 / TV4 / TV5 witness | ________________________________ |
| P0 open / P1 backlog | ________________________________ |
| Raw evidence folder + manifest SHA256 | ________________________________ |
| TV5 final disposition | ________________________________ |

**Trạng thái trước Final UAT:** `NOT_RUN`. Không tick Issue #18 hoặc #162 từ việc chuẩn bị ma trận này.
