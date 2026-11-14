# Gói review contract Promotion FR-PROMO-01–03

**Dự thảo để TV2 quyết định.** Đọc kèm [RFC](FR_PROMO_01_03_SHARED_PRICE_RFC.md), [ownership](../OWNERSHIP.md) và [M01](../project-report/M01_FINANCE_DECISION.md). Bản SRS Flash/Super/Voucher v2.1 gốc chưa được tìm thấy trong worktree. Đối chiếu SRS của gói này giới hạn ở các mục được RFC và PR #202 dẫn lại, gồm giờ Flash, ngày Super do Admin chọn, quyền quản lý clearance và yêu cầu giá; các lựa chọn bên dưới là đề xuất cần ký.

## Lifecycle, đăng ký và duyệt

| Đối tượng | Chuyển trạng thái đề xuất | Quy tắc |
|---|---|---|
| Campaign Flash/Super | DRAFT → APPROVED ⇄ PAUSED → CANCELLED; có thể hủy từ DRAFT hoặc APPROVED | Admin sở hữu lịch, xuất bản và điều phối. Đã hủy là trạng thái cuối. UPCOMING/LIVE/ENDED suy từ giờ server và khoảng hiệu lực; không có cron ghi giá. |
| Campaign clearance | Cùng lifecycle | Seller tạo draft của Store mình. TV2 chọn Seller tự kích hoạt sau kiểm tra hay Admin duyệt trước. Admin có thể dừng theo chính sách đã ký. |
| Product đăng ký | PENDING → APPROVED, REJECTED hoặc WITHDRAWN | Seller có quyền rút đăng ký đang chờ của mình. Admin duyệt/từ chối Flash/Super và ghi lý do. Chỉ offer đã duyệt thuộc campaign APPROVED được xét. Chốt luồng thu hồi offer đã duyệt và đăng ký lại trước khi viết API. |

Payload chỉ gồm trường được phép. Server đọc Product theo ID rồi lấy Store/Seller từ nguồn có thẩm quyền, kiểm ACTIVE/OPEN/APPROVED, kiểm giá VND nguyên từ 1 đến 1.000.000.000 theo ProductPrice và thấp hơn base hiện tại. Khi duyệt, resume hoặc đổi giá/lịch, kiểm lại các điều kiện. Khi thông tin Store/Seller thiếu hoặc mâu thuẫn, từ chối quyết định giá khuyến mãi. JWT xác thực actor và role, còn sở hữu và trạng thái hiện hành phải kiểm riêng. Các boolean trong ProductFacts chỉ là đầu vào của domain.

## PostgreSQL và xử lý cạnh tranh

- `promotion_campaigns` gồm UUID, kind, lifecycle status, starts_at/ends_at TIMESTAMPTZ hữu hạn, super_sale_date DATE khi áp dụng, clearance_store_id khi áp dụng, version và dấu thời gian. CHECK loại/trạng thái, thời lượng dương và trường bắt buộc theo loại. Các FK tới Product nằm trong product-service; Store/Seller là ID từ dịch vụ sở hữu.
- `promotion_campaign_products` có PK (campaign_id, product_id), FK campaign và Product, trạng thái đăng ký, promotional_unit_price BIGINT với CHECK giới hạn VND, version, thời điểm và người duyệt. Kiểm số nguyên ở JSON trước khi bind SQL để tránh ép kiểu làm tròn; giá sale thấp hơn base là kiểm tra liên bảng trong transaction.
- `promotion_campaign_audit` ghi append-only actor đã xác thực, hành động, ID campaign/Product, trạng thái/phiên bản trước và sau, lý do, timestamp và correlation ID cùng transaction. Index danh sách theo campaign/trạng thái và quote theo Product/trạng thái.
- Đề xuất `promotion_super_sale_months` với PK month_start DATE theo tháng Việt Nam, UNIQUE campaign_id và CHECK ngày được chọn thuộc tháng. Đổi ngày qua cùng hàng khóa và expectedVersion, rồi kiểm lại offer liên quan. Đề xuất giữ hàng tháng khi hủy để tránh một ngày thứ hai trong cùng tháng; TV2 chốt có cho đổi ngày sau duyệt hoặc sau khi đã LIVE hay không.
- Đề xuất mặc định từ chối duyệt offer chồng lấn. Khi TV2 cho phép ngoại lệ, lưu winner rõ ràng theo Product và đoạn thời gian, có audit; không chọn theo thứ tự, loại hoặc giá thấp nhất. Phép giao dùng khoảng hiệu lực thực tế, gồm cửa sổ Flash và ngày Super theo giờ Việt Nam, với cận cuối loại trừ.
- Trong transaction READ COMMITTED, khóa hàng lịch tháng khi thao tác có ảnh hưởng lịch, sau đó hàng campaign theo UUID, rồi hàng Product theo UUID. Mọi thao tác cấp hiệu lực, đổi lịch, membership, giá hoặc winner phải đi cùng giao thức khóa. Với batch, kiểm lại tập Product dưới khóa campaign, rồi truy vấn offer và base **sau khi** đã giữ khóa Product. Không lấy thêm khóa campaign khi đang giữ Product. Đổi base chỉ lấy khóa Product rồi đọc trạng thái liên quan. Nếu hai Admin duyệt đồng thời, giao dịch thứ hai đọc trạng thái đã commit của giao dịch trước và trả 409 nếu xung đột; cùng transaction ghi trạng thái và audit. Kiểm độc lập freshness của Seller facts vì dữ liệu nằm ở dịch vụ khác. TV2 chốt mức freshness và hành vi khi winner bị dừng.

## REST DTO và quyền dự thảo

| Method và đường dẫn ứng viên | Actor | Nội dung chính |
|---|---|---|
| GET/POST /api/seller/promotions | SELLER | Danh sách scope Store mình; tạo clearance với name, startsAt, endsAt. |
| POST /api/seller/promotions/{campaignId}/products | SELLER | productId, promotionalUnitPrice dạng chuỗi VND, expectedCampaignVersion; server xác minh ownership. |
| POST /api/seller/promotions/{campaignId}/products/{productId}/withdraw | SELLER | expectedVersion, reason nếu contract yêu cầu. |
| GET/POST /api/admin/promotions | ADMIN | Danh sách xét duyệt; tạo Flash/Super, ngày Super dạng YYYY-MM-DD. |
| POST /api/admin/promotions/{campaignId}/actions | ADMIN | action, expectedVersion, reason; duyệt hoặc đổi lifecycle theo bảng. |
| POST /api/admin/promotions/{campaignId}/products/{productId}/decision | ADMIN | decision, expectedVersion, reason; xét duyệt đăng ký và kiểm xung đột. |

Response quản lý dự kiến trả ID, kind, status, thời gian UTC ISO-8601, phiên bản, các trường đã được cấp quyền đọc và trạng thái hiển thị suy ra. Seller chỉ thấy dữ liệu Store mình, Customer chỉ xem giá công khai qua Catalog, Admin xét duyệt theo scope đã chốt. Đề xuất 401 khi thiếu/sai JWT, 403 khi sai role, 404 cho Seller truy cập ID ngoài Store, 409 khi version cũ, lịch trùng hoặc offer chồng lấn, 400 khi DTO sai. TV2 chốt đường dẫn, quyền tự duyệt, quyền quản lý clearance, trường hiển thị và semantics retry. UI Seller dựa trên trạng thái đăng ký, UI Admin hiển thị lý do/offer xung đột và version để tải lại khi 409.

## Quote và Cart stale

Quote Catalog dự kiến gồm `productId`, `storeId`, `currency`, `baseUnitPrice`, `effectiveUnitPrice`, `campaignId` và `kind` nullable, `pricingRevision`, `evaluatedAt` và `validUntil` nullable. Dùng chuỗi VND nguyên. Một lần resolve lấy một mốc giờ server. Revision opaque và ổn định trong cùng ngữ cảnh, không hash evaluatedAt; phản ánh Product price/version, offer/winner version, eligibility có thẩm quyền và offer có hiệu lực tại mốc đánh giá. Cùng mức tiền nhưng đổi campaign vẫn đổi revision. validUntil là biên lịch gần nhất có thể làm đổi quote, không thay cho kiểm tra trực tiếp khi trạng thái hoặc giá bị sửa.

Sau khi được duyệt tích hợp, Cart so sánh Product/Store/revision và cặp currency–effective price với Catalog hiện hành. Khi khác, hiển thị giá mới và cần khách xác nhận lại. Checkout preflight sau Cart claim phải lấy quote authoritative trước durable draft và Inventory RESERVE; mismatch trả stale, không tự đặt hàng theo giá mới. Retry sau durable draft giữ snapshot và provenance đã chốt; không lấy giá mới cho cùng attempt. TV2/TV3 chốt mốc tạo quote, hành vi legacy Cart thiếu revision, error contract và ngưỡng stale giữa preview với preflight. Checkout/Finance tiếp tục dùng MD05 hiện hành cho tới khi M01 bổ sung được ký.

## Migration và kiểm thử sau khi duyệt

1. Kiểm lại version Flyway trên main và PR đang mở, thêm migration tiến tới trong product-service. Chạy PostgreSQL 16 trên DB mới và DB đã có dữ liệu đến version trước migration. Xác nhận Product giá gốc, version và Catalog cũ không bị đổi, constraint/audit hoạt động và Flyway validate sạch.
2. PostgreSQL IT dùng hai connection và barrier có timeout. Đua hai Admin duyệt offer cùng Product, hai yêu cầu đặt ngày Super cùng tháng, approve với pause/đổi lịch/base-price. Kiểm số bản ghi commit, audit của giao dịch thắng, không có ghi nhận dở dang khi rollback. Kiểm thêm Product khác, cận cuối giáp nhau và trường hợp khoảng thô trùng nhưng lịch không giao.
3. HTTP IT dùng JWT được ký cho actor khác nhau và DB thật. Kiểm Seller đúng Store/chéo Store, Admin, Customer, token sai, Seller SUSPENDED, Store CLOSED, Product inactive, nguồn facts mất kết nối. Kiểm Catalog list/detail, base/effective, cache qua biên giờ, version/revision và retry.
4. Sau khi M01 mới được ký, bổ sung CT-01..04 cho Cart stale, hai Store, snapshot/idempotency và crash/replay theo luồng hiện hữu. UI E2E xác minh đăng ký/duyệt/tạm dừng và giá Catalog. Các ca PostgreSQL, HTTP và E2E này hiện **NOT_RUN**.

## Các quyết định cần phản hồi trong một lượt

| Mục | Ai chốt | Câu hỏi cần trả lời |
|---|---|---|
| P01a | TV2 | Duyệt lifecycle và ma trận REST? Clearance tự kích hoạt hay phải Admin duyệt? Có quy tắc tự duyệt hoặc thu hồi offer đã duyệt? |
| P01b | TV2 | Chấp nhận khóa theo tháng → campaign → Product và mặc định từ chối overlap? Nếu có winner, ai chọn, theo khoảng nào, và pause/cancel có tự chuyển sang offer khác không? |
| P02 | TV2 | Ngày Super được đổi đến mốc nào? Hủy rồi có thể đặt ngày khác cùng tháng không? Điều kiện đăng ký cho từng kỳ Flash? |
| P03 | TV2 + TV3 | Chốt Catalog DTO, revision, freshness và sort/filter; khi giá base giảm tới mức sale thì chặn sửa, tạm dừng hay yêu cầu duyệt lại? |
| P04 | TV2 quyết định nghiệp vụ, TV5 review | M01 bổ sung về bên tài trợ, cơ sở phí MD04/MD05, làm tròn và phân bổ hai Store, số tiền COD, snapshot bất biến theo MD08, Return và settlement. |
| P05 | TV2 + TV3, TV5 khi ảnh hưởng tiền, TV4 nếu đổi Inventory | Chốt chuyển đổi Cart cũ, stale/reconfirm, mốc quote tại Checkout và replay. Giữ thứ tự Cart claim → Catalog preflight → durable draft → Inventory RESERVE → Order/outbox → Cart finalize. |
