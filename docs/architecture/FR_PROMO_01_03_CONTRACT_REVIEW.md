# Contract triển khai FR-PROMO-01–03 — đã đối chiếu quyết định TV2

**Nguồn thẩm quyền:** TV2 Nguyễn Văn Duy đã quyết định P01–P05 tại [PR #202, comment 14/11](https://github.com/duyxl56785/Project_HK5/pull/202#issuecomment-6875893285) và thay đổi **hạn/reviewer** tại [comment sửa đổi 14/11](https://github.com/duyxl56785/Project_HK5/pull/202#issuecomment-6876058407). Đây là tài liệu tổng hợp để triển khai; khi khác với lời TV2, **comment mới nhất của TV2 là nguồn ưu tiên**. SRS v2.1 gốc chưa nằm trong worktree; không giả định đã đối chiếu trực tiếp bản SRS ở đây.

**Owner/triển khai:** TV3 làm toàn bộ FR-PROMO-01 Flash Sale, FR-PROMO-02 Super Sale và FR-PROMO-03 Seller xả kho ở product-service, Gateway, Seller/Admin UI và E2E. **TV2 duy nhất review, quyết định và merge, kể cả MD12**. TV3 không cần quyết định thêm từ TV5/TV4 theo ủy quyền 14/11. Không tự merge.

**Hạn:** hoàn thiện, CI xanh và merge đủ chức năng trước khi **đóng băng main hết 19/11/2026**. Không merge một phần; phần nào chưa merge sau hạn là hướng phát triển. Ngày 20/11 chạy E2E trên SHA đóng băng, 21/11 nộp.

## P01a — Campaign và quyền

- Loại campaign: FLASH_SALE, SUPER_SALE, SELLER_CLEARANCE.
- DB lifecycle: DRAFT → APPROVED ⇄ PAUSED. CANCELLED cuối cùng, chỉ hủy **trước startsAt**. Sau LIVE, Admin dừng bằng PAUSED; sau ENDED không thao tác. UPCOMING/LIVE/ENDED suy từ thời gian server, không cron thay giá.
- Flash/Super: **Admin tạo, duyệt và dừng**. Clearance: **Seller tạo DRAFT** cho Store do mình sở hữu, **Admin duyệt sau submit**, không tự publish.
- Product registration: PENDING → APPROVED | REJECTED | WITHDRAWN. Reject yêu cầu reason 1–500 ký tự. Seller có thể **withdraw cả PENDING lẫn APPROVED**, có hiệu lực ngay; Seller SUSPENDED vẫn được withdraw nếu xác minh ownership Store từ principal/JWT và dữ liệu Store, không cần role SELLER. Phải chặn mọi hành vi trái phép khác.
- Không sửa giá sau approve. Muốn đổi giá thì withdraw rồi đăng ký lại; hàng PK (campaign_id, product_id) quay về PENDING và **tăng version**, giữ audit.
- Đăng ký và duyệt chấm dứt tại startsAt; LIVE chỉ còn withdraw và pause. Admin đồng thời là chủ Store Product không được tự duyệt (403).
- REST contract được TV2 duyệt: \`/api/seller/promotions/**\`, \`/api/admin/promotions/**\` (chi tiết endpoint/method từ gói review trước, trừ phần trái các quyết định mới). Gateway route tới product-service và \`GatewayRoutingTest\`.
- Mã lỗi: 401 thiếu/sai JWT, 403 sai role, 404 Seller truy cập ID ngoài Store, 409 version cũ/lịch hoặc offer overlap, 400 payload sai. JSON chặt: loại bỏ trường lạ, null không hợp lệ, giá thực/phân số, storeId client-supplied. DTO tiền là chuỗi VND nguyên canonical, parse tại boundary.

## P01b — Giao dịch, overlap và cạnh tranh

- **Từ chối mọi overlap thực sự của hai offer APPROVED thuộc campaign APPROVED**, bất kể loại; 409 PROMOTION_OVERLAP tại approve và resume. **Không lưu winner, không chọn giá thấp nhất/ưu tiên cố định, không stacking.** Chọn offer bằng chính approval, phải withdraw/reject offer khác trước.
- Kiểm giao nhau bằng cửa sổ **hiệu lực thực** theo giờ server, khoảng [start,end), cận cuối loại trừ: Flash chỉ thứ Bảy 22:00–Chủ nhật 00:00 theo ngày đã chọn; Super là trọn ngày Admin chọn; clearance là lịch duyệt.
- Khi pause/cancel, không tự chuyển sang offer thứ hai; resume phải tái kiểm mọi xung đột và điều kiện Product/Store/Seller.
- **Thứ tự khóa DB:** campaign \`FOR UPDATE\` → Product theo UUID tăng dần. Không thêm khóa hàng theo tháng. Đổi Product base price chỉ khóa Product. Với hai Admin duyệt cạnh tranh, transaction thứ hai thấy trạng thái đã commit và trả 409; audit và mutation cùng transaction.
- Giá sale nguyên VND 1..1.000.000.000 theo ProductPrice hiện có, luôn nhỏ hơn giá gốc khi đăng ký/duyệt/resume; Store, Seller và Product phải là trạng thái hợp lệ. Nguồn facts không tin client.

## P02 — Lịch Super, Flash, Clearance

- SUPER_SALE: đúng **một campaign chưa CANCELLED mỗi tháng theo ngày Việt Nam**, ngày Admin chọn. Partial unique expression index theo tháng: \`date_trunc('month', super_sale_date)\` khi \`kind='SUPER_SALE' AND status<>'CANCELLED'\`; **bắt buộc chạy thử PostgreSQL 16/kiểm IMMUTABLE index expression trên DATE** trước khi chốt cú pháp migration, nhưng phải giữ nguyên ràng buộc nghiệp vụ TV2. Không thêm \`promotion_super_sale_months\`.
- Super Sale chạy \`[00:00, 24:00)\` giờ Việt Nam. Chỉ đổi ngày ở DRAFT; sau APPROVED không đổi. Muốn đổi phải cancel trước startsAt rồi tạo mới.
- FLASH_SALE: campaign gắn đúng một ngày thứ Bảy \`flash_date\` (DB CHECK thứ Bảy); chạy \`[thứ Bảy 22:00, Chủ nhật 00:00)\` giờ Việt Nam; partial unique một campaign chưa CANCELLED mỗi thứ Bảy.
- Mọi Product đăng ký trước startsAt; ACTIVE Product + OPEN Store + APPROVED Seller; giá thấp hơn base tại registration, approval và resume.
- SELLER_CLEARANCE: startsAt ≥ thời điểm submit, duration từ **1 giờ đến 30 ngày**.

## P03 — Catalog, giá thực tế và Cart stale

- **Không sửa DTO nội bộ** \`/internal/catalog/products/{id}\` cho Cart hoặc batch validator của Checkout. Trường \`unitPrice\` của cả hai trả **giá hiện hành theo giờ server** (sale nếu offer APPROVED đang LIVE và hợp lệ; ngược lại base).
- Catalog public list/detail: \`unitPrice\` là giá hiện hành; thêm \`baseUnitPrice\` và nullable \`promotion {campaignId, kind, endsAt}\` để UI hiển thị gạch giá gốc. Endpoint quản lý Seller/Admin Product vẫn trả **giá gốc**. Sort/filter giá vẫn theo base trong bản nộp (phải ghi hạn chế).
- **Không dùng pricingRevision, không mở rộng Cart/Order/Finance DTO.** Existing Cart \`unitPriceSnapshot\` và Checkout \`expectedUnitPrice\`/preflight chịu trách nhiệm stale: chênh lệch giá hiện hành trả PRICE_CHANGED, buộc khách xác nhận lại. Cùng giá nhưng đổi campaign được chấp nhận.
- Product đổi base price khi còn offer APPROVED chưa ENDED: nếu base mới ≤ giá sale, 409 PROMOTION_PRICE_CONFLICT; Seller phải withdraw offer trước. Không sửa \`Product.unitPrice\` cho sale.
- Eligibility Store/Seller lấy từ \`CurrentStoreFactsSource\` (MD07); nếu nguồn unavailable, **không áp sale và dùng base** (fail closed theo TV2).
- UI Cart phải xác minh đã hiển thị PRICE_CHANGED theo lời TV2; nếu chưa, chỉ sửa cần thiết trong cùng PR, không đổi Cart/Order backend.
- Giữ Cart claim → Catalog preflight → durable draft → Inventory RESERVE → Order/outbox → Cart finalize, replay giữ snapshot đã ghi. Race preflight/commit theo MD07 được chấp nhận.

## P04 — M01, MD12

TV2 chốt MD12 và TV3 thêm nguyên nội dung tại \`docs/project-report/M01_FINANCE_DECISION.md\`, phần bổ sung 14/11. Seller **chịu toàn bộ phần giảm**, sàn không bù. Đơn giá trong Order là giá hiện hành lúc Checkout preflight; gross/COD/fee 5% HALF_UP/net tính từ giá này. Snapshot MD08 không thay đổi cấu trúc; campaign provenance chỉ ghi ở product-service audit; Return dùng giá snapshot. Không stacking Voucher (C2 chưa có). MD05 chỉ nói không giảm cấp đơn. **Không sửa cart-service, order-service, Inventory, Finance**.

## PostgreSQL migration và kiểm thử bắt buộc

- **product-service \`V10__promotions.sql\`**; thêm 3 bảng mới \`promotion_campaigns\`, \`promotion_campaign_products\`, \`promotion_campaign_audit\`, không sửa bảng cũ. \`version\` optimistic lock và \`expectedVersion\` trên DTO. DB FK Campaign/Product, CHECK thời gian/giá/status, partial unique index lịch, index quote/ownership, append-only audit.
- DB nâng từ V9 có dữ liệu và DB trống lên V10 (PostgreSQL 16, Flyway validate), không viết lại migration đã áp dụng.
- PostgreSQL IT **hai connection có barrier timeout**: approve đua hai offer overlap trên cùng Product → đúng một SUCCESS; approve đua base-price edit; Super trong cùng tháng; Flash 21:59:59/22:00/00:00; cận cuối hai khoảng giáp nhau không overlap.
- HTTP/JWT IT: 401/403/404, không được self approve, Seller SUSPENDED vẫn withdraw, Store CLOSED, input lạ/null/tiền sai.
- Catalog Internal, public: kiểm biên giờ/Store facts, sale/base đúng; kiểm Cart stale PRICE_CHANGED.
- Seller/Admin DOM + live E2E: Seller đăng ký, Admin approve, Customer mua trong LIVE, Order snapshot sale và fee 5% trên tiền sau giảm.
- **CI exact HEAD xanh**; PR bỏ Draft mới request TV2 review. Không merge một phần trước hạn.

## Chú ý tài liệu cũ

[Shared Price RFC](FR_PROMO_01_03_SHARED_PRICE_RFC.md) chứa các **đề xuất lịch sử** như pricingRevision, winner và review TV5. Các đề xuất đó đã **bị quyết định TV2 mới nhất thay thế**. Khi triển khai chỉ dùng comment TV2 và contract này; không tái sử dụng thiết kế cũ trái P01–P05.
