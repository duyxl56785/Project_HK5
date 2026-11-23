# TV5 — Ma trận Final UAT W5, C2 bật/tắt

**Mục đích:** ma trận thực thi và đối chiếu kết quả Final UAT ngày 21/11/2026 trên Frozen SHA `eaf3bf270255b80db4525f836bfae7e495dc8651`. Kết quả thực tế được lấy từ run-manifest và raw receipts PR #221, không suy từ checklist trống.

**Kết quả receipt 22/11:** 34 PASS, 1 PARTIAL (PROMO-02), 2 BLOCKED (UAT-16/17), 0 FAIL; không có P0/P1. TV5 xác minh 37 case manifests và 98 checksum, không có sai lệch. [Kết quả #158](https://github.com/duyxl56785/Project_HK5/issues/158#issuecomment-7075353935) · [Receipt PR #221](https://github.com/duyxl56785/Project_HK5/pull/221).

**Tài liệu thao tác:** dùng [checklist chi tiết đủ 37 case](TV5_FINAL_UAT_EXECUTION_CHECKLIST_20261121.md) khi thực thi; TV2/TV3/TV4 gửi receipt theo [hướng dẫn evidence intake](TV5_FINAL_UAT_EVIDENCE_INTAKE_20261121.md). Ma trận này vẫn là nguồn mapping ID và kết quả gốc.

**Nguồn quyết định hiện hành:** TV2 xác nhận tại Issue #158: Frozen SHA `eaf3bf270255b80db4525f836bfae7e495dc8651`, freeze lúc 20/11/2026 20:44 +07, thay thế freeze cũ `950279e`. TV2 vận hành UAT/E2E ngày 21/11.

**Quyết định C2:** TV2 cho phép opt-in C2 riêng trong `.env.demo` local cho UAT 21/11 khi đủ bốn điều kiện. Receipt xác nhận C2 đã ON trên stack UAT; triage không phát hiện P0 C2 và capability trả `enabled=true`. C2 OFF được kiểm qua CI trên Frozen SHA. Đây là quyền chạy demo/UAT local, không phải production approval.

**Checkpoint GitHub làm mới 22/11/2026:** lịch sử #211/#212/#215/#218/#220 được giữ bên dưới làm provenance CI/review. Frozen SHA hiện hành là `eaf3bf270255b80db4525f836bfae7e495dc8651`; CI đúng SHA `44746995567` Frontend, C2 OFF và C2 ON SUCCESS; backend readiness `44493061575` còn áp dụng vì backend không đổi. Final UAT đã có receipt: 34 PASS, 1 PARTIAL, 2 BLOCKED, 0 FAIL. #216 đã MERGED vào `main@dafb95f`. Xem bảng receipt ở đầu file và [run-manifest/receipt PR #221](https://github.com/duyxl56785/Project_HK5/pull/221).

**Checkpoint bổ sung 21/11/2026 sau merge PR #220 (đã được cập nhật bằng receipt 22/11):** Frozen SHA `eaf3bf270255b80db4525f836bfae7e495dc8651`; CI đúng SHA `44746995567` và backend readiness `44493061575` được TV2 xác nhận áp dụng. Final UAT receipt hiện có 34 PASS, 1 PARTIAL, 2 BLOCKED, 0 FAIL. PROMO-02 Flash Sale in-window NOT_RUN; UAT-16/17 BLOCKED; RET-05 restock BLOCKED. Snapshot 0 PASS/23 NOT RUN/13 BLOCKED bên dưới là lịch sử trước khi chạy.

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
| G-01 | TV2 công bố full frozen `main` SHA và thời điểm freeze | TV2 | `eaf3bf270255b80db4525f836bfae7e495dc8651`; 20/11/2026 20:44 +07; Issue #158 | PASS — TV2 xác nhận; thay freeze cũ `950279e` |
| G-02 | Hosted readiness, frontend, E2E xanh hoặc có xác nhận applicability trên đúng Frozen SHA | TV2 | Frontend/E2E run `44746995567` trên exact SHA; Backend readiness `44493061575` trên `cee7c87` | PASS — 3 job Frontend/E2E SUCCESS trên Frozen SHA; TV2 xác nhận backend readiness áp dụng vì backend không đổi |
| G-03 | Clean clone/clean install receipt cùng frozen SHA | TV1/TV2 | TV2 run-manifest #221 ghi separate clone tại exact Frozen SHA, git status sạch trước/sau; TV1 PR #226 bổ sung clone độc lập cùng máy và E2E C2 OFF | PASS — clean clone đã được chứng minh; tiêu chí máy thứ hai của Issue #158 vẫn NOT_RUN |
| G-04 | PR #211 merged; TV2, TV4, TV5 reviews đủ; không còn thread tiền/fulfillment chưa xử lý | TV2/TV4/TV5 | Merge SHA + review URLs: `cee7c87d1c7437fc5ed0009405fd2d0fc394bfb2`; 3/3 APPROVED tại `4f3152b` | MERGED / 3-of-3 APPROVED; TV2 vẫn xác nhận P0 scope |
| G-05 | PR #212 (PR B) merged vào main trước 18:00 20/11 | TV2 | Merge SHA + CI URL: `4b5bcb4ceb5a6a636e4c943db2d41c8a4c1bb14f`; TV2 review exact HEAD `3483486` APPROVED | DONE — merged 19/11 17:05:23 +07; post-merge push run `44502375038` attempt 2 SUCCESS đúng merge SHA: frontend PASS, C2 OFF 24/24, C2 ON 25/25, 0 skip; attempt 1 ON gặp lỗi tải Gradle dependency và đã rerun thành công |
| G-06 | CI frozen SHA có `voucher-c2.spec.ts` PASS, 0 skipped | TV2 | [Run 44746995567](https://github.com/duyxl56785/Project_HK5/actions/runs/44746995567), job E2E C2 ON: 25 passed, 0 skipped | PASS — exact Frozen SHA `eaf3bf2`; TV2 xác nhận C2 được opt-in cho UAT nếu gate P0 còn lại đạt |
| G-07 | Không còn P0 mở liên quan C2; TV2 xác nhận scope | TV2 + TV3/TV4/TV5 | [TV2 triage tại Issue #158](https://github.com/duyxl56785/Project_HK5/issues/158#issuecomment-7075353935): không có P0/P1 | PASS — TV2 xác nhận không có P0/P1; không suy từ CI |
| G-08 | Nếu C2 ON: TV2 xác nhận ba opt-in trong `.env.demo` local (`--approved-inventory-pair`, `--approved-finance-policy`, `--approved-voucher-c2`) và policy MD01–MD11 đúng phạm vi demo/local | TV2 | [Run-manifest PR #221](https://github.com/duyxl56785/Project_HK5/pull/221): ba flags observed true trong `.env.demo`, capability `enabled=true`; M01 MD09/MD23 và quyết định tại Issue #158 | PASS — receipt runtime xác nhận C2 ON cho UAT local sau triage không P0. Finance production vẫn NOT_APPROVED. |
| G-09 | Stack disposable riêng, DB/volume/network/port riêng, dữ liệu giả; không đụng stack dùng chung | TV2/TV1 | [Run-manifest PR #221](https://github.com/duyxl56785/Project_HK5/pull/221): project `hk5-promo-test-uat2111`, DB mới, network/volumes/ports và image digests riêng | PASS — stack UAT riêng được ghi nhận; không phải production |

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

Theo quyết định TV2 tại [Issue #162 ngày 16/11](https://github.com/duyxl56785/Project_HK5/issues/162#issuecomment-6907759709), báo cáo nộp chỉ có **một file** `Bao_cao_Smart_ECommerce_ban_nop.docx`, do TV2 ghép từ `Bao_cao_Smart_ECommerce.docx`. TV2 xác nhận tại [kết quả Final UAT #158](https://github.com/duyxl56785/Project_HK5/issues/158#issuecomment-7075353935) đã chụp ảnh 6.1–6.8 trên Frozen SHA và chèn vào DOCX chính thức; DOCX/ảnh hiện được TV2 giữ ngoài repository nên TV5 chưa thể kiểm hash hoặc nội dung file. Không dùng DOCX nháp TV5 thay thế.

| Hình | Nội dung đúng theo TV2 | Bằng chứng cần đối chiếu | SHA / KQ / Evidence |
|---|---|---|---|
| 6.1–6.8 | Bộ ảnh báo cáo chính thức | TV2 xác nhận đã chụp trên Frozen SHA và chèn vào DOCX chính thức tại Issue #158; file gốc giữ ngoài repo | TV2 xác nhận hoàn tất; TV5 chưa kiểm trực tiếp bytes/hash vì file ngoài repo |

Ảnh chỉ được chụp lại trên frozen SHA. Không dùng ảnh rehearsal, ảnh cũ khác theme, ảnh Cart trống, ảnh mock hoặc ảnh từ CI làm hình nộp.

## 6. P0/P1 triage và các giới hạn không được che

| Hạng mục | Kỳ vọng phân loại | Owner | Bằng chứng/điều kiện cần |
|---|---|---|---|
| Auth, Cart hai Store, COD Checkout, Parent/2 SellerOrders, idempotency, shipping, Inventory RESERVE, seller isolation | P0 — phải chạy Final UAT | TV2/TV3/TV4 | UI + HTTP + DB/Inventory read-only receipt cùng frozen SHA |
| UNKNOWN claim-conflict và lost-response | BLOCKED_DESIGN / NOT_RUN cho đến khi có runner/tuple/provider oracle được duyệt | TV2/TV3/TV4/TV5 | Không chạy fault injection tự phát; không dùng fake-loopback làm PASS |
| Inventory lifecycle/Return restock RET-05 | BLOCKED nếu chưa có contract và exactly-once receipt | TV4 | Không tuyên bố restock PASS; không tạo stock effect giả |
| Admin COD collection | P0 nếu demo/báo cáo tuyên bố có | TV4 | Receipt thật, actor, amount, timestamp, idempotency và audit |
| Finance settlement | BLOCKED nếu thiếu business approval/runtime/reconciliation | TV2 + business owner + TV5 | `settledAt` chỉ là ghi nhận trong ứng dụng, không phải chuyển tiền thật |
| C2 | Đã bật cho UAT local sau khi đủ G-04..G-08; mặc định OFF ngoài `.env.demo` local | TV2 quyết định; TV5 review tiền | PR #221 run-manifest xác nhận flags/capability; triage #158 không P0; production vẫn NOT_APPROVED |
| Flash Sale/Super Sale live Checkout | NOT_RUN nếu chỉ có unit/PG/clearance E2E; ghi đúng loại Promotion đã thực chạy | TV2/TV3 | Mỗi campaign cần live evidence riêng |

## 7. Kết quả Final UAT và disposition của TV5

| Trường | Giá trị đã đối chiếu |
|---|---|
| Frozen main SHA | `eaf3bf270255b80db4525f836bfae7e495dc8651` |
| Main freeze timestamp (Asia/Ho_Chi_Minh) | `2026-11-20 20:44 +07` |
| CI readiness/frontend/E2E URLs | Frontend + C2 OFF/ON: [44746995567](https://github.com/duyxl56785/Project_HK5/actions/runs/44746995567); backend applicability: [44493061575](https://github.com/duyxl56785/Project_HK5/actions/runs/44493061575) |
| Clean clone machine + receipt | TV2 separate clone, clean status before/after, run-manifest #221; TV1 independent clone on same Windows machine, [PR #226](https://github.com/duyxl56785/Project_HK5/pull/226). Second physical/virtual machine: NOT_RUN. |
| C2 decision | `ON` for this local UAT only; TV2 triage found no P0/P1 |
| Flags actually used (TV2 records) | Inventory pair, Finance policy and Voucher C2 observed `true` in `.env.demo`; capability `enabled=true`; defaults remain OFF outside local demo |
| Compose project / image digests | `hk5-promo-test-uat2111`; DB/network/volumes/ports and image digests recorded in run-manifest #221 |
| Run ID / start-end time | `tv2-uat-eaf3bf2-20261121`; `2026-11-21T11:54:02+07:00` → `2026-11-22T13:19:38+07:00` |
| TV2 operator / TV3 / TV4 / TV5 witness | Operator: `duyxl56785 (TV2)`; no witness recorded in run-manifest; TV5 performed post-run receipt audit |
| P0 open / P1 backlog | TV2 triage: no P0/P1; known scope blockers UAT-16/17 and RET-05 remain separately BLOCKED; PROMO-02 Flash Sale in-window NOT_RUN |
| Raw evidence folder + manifest SHA256 | [PR #221](https://github.com/duyxl56785/Project_HK5/pull/221), 99 files; 98 checksum entries; all checksum and artifact metadata verified |
| TV5 final disposition | Receipt integrity verified; accept 34 PASS, 1 PARTIAL, 2 BLOCKED, 0 FAIL as TV2's local Final UAT result. Not production acceptance or full closure of Issue #158/#162. |

### 7A. Case-level result from the TV2 run-manifest

| Case set | Result | Disposition |
|---|---:|---|
| UAT-01..15 | 15 PASS | UAT-12 return non-restock PASS; RET-05 restock remains BLOCKED and is not included |
| UAT-16..17 | 2 BLOCKED | UNKNOWN claim-conflict/lost-response not executed |
| C1-01..05 | 5 PASS | All five case manifests and referenced artifacts verified |
| PROMO-01, PROMO-03, PROMO-04 | 3 PASS | PROMO-04 tested with C2 ON under TV2 authorization |
| PROMO-02 | PARTIAL | Super Sale PASS in-window; Flash Sale in-window NOT_RUN because Docker stopped during the 21/11 window |
| C2-01..11 | 11 PASS | C2 ON local UAT; C2 OFF CI is separate evidence |
| **Total** | **34 PASS, 1 PARTIAL, 2 BLOCKED, 0 FAIL** | No P0/P1 per TV2 triage |

**Trạng thái sau Final UAT:** receipt của TV2 xác nhận 34 PASS, 1 PARTIAL, 2 BLOCKED, 0 FAIL. Không tự tick/đóng Issue #18 hoặc #162 chỉ từ kết quả của TV2; các issue còn yêu cầu đối chiếu deliverable chính thức và các blocker được nêu riêng.
