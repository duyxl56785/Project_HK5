# RFC — Giá khuyến mãi dùng chung cho Flash Sale / Super Sale / Seller xả kho

**Trạng thái: DRAFT — CẦN TV2 chốt contract, TV5 review M01 mới trước khi ảnh hưởng số tiền Order.**

Căn cứ: SRS Flash Sale / Super Sale / Voucher v2.1 (13/11/2026), mục 4.1–4.3, 5–9; docs/OWNERSHIP.md; docs/project-report/M01_FINANCE_DECISION.md MD04–MD08. Đây là **đề xuất kỹ thuật**, không phải quyết định nghiệp vụ đã ký. Dựng trên main@d023e10474408b959f8e0ec872cff217f101d151.

## Phạm vi

- **FR-PROMO-01 Flash Sale:** tuần nào cũng có cửa sổ thứ Bảy 22:00 (bao gồm) đến Chủ nhật 00:00 (không bao gồm), múi giờ Asia/Ho_Chi_Minh; Seller đăng ký Product ACTIVE thuộc Store OPEN, Seller APPROVED; Admin duyệt.
- **FR-PROMO-02 Super Sale:** Admin chọn **một** ngày mỗi tháng (không hard-code ngày 15), duyệt Product; dùng chung cách tính giá. Khi trùng Flash Sale, chỉ một offer được công bố là offer thắng theo quyết định của Admin.
- **FR-PROMO-03 Seller xả kho:** Seller tạo lịch giảm giá sản phẩm thuộc Store mình; Admin có quyền xem và dừng vi phạm. Không thay đổi số lượng tồn, không tự động xác định hàng bán chậm.
- Không hứa hạn mức 30/100 suất sale, không thay đổi Product.unitPrice gốc, không stacking cùng Product. Không thêm Voucher C2 trong RFC này.

## Kiến trúc theo giai đoạn

1. **Pure rules (commit đầu tiên):** PromotionPriceRules tại product-service/promotion; đọc các offer **đã duyệt và được lọc từ nguồn tin cậy**. Trả PriceQuote(baseUnitPrice, effectiveUnitPrice, campaignId, kind, evaluatedAt). Chỉ so sánh số nguyên VND bằng BigDecimal, không thay đổi dữ liệu hay expose endpoint. Mốc thời gian lấy một Instant từ server; khoảng hiệu lực [startsAt, endsAt).
2. **Persistence và moderation (chưa thực hiện):** đề xuất promotion_campaigns, promotion_campaign_products, audit & optimistic version; FK tới Product/Store; uniqueness/overlap guard do DB transaction + khóa phù hợp bảo vệ. Admin tạo lịch/duyệt/ưu tiên, Seller đăng ký, backend kiểm ownership/eligibility tại mỗi thao tác. Phải chốt contract/API, migration version với TV2 trước khi tạo bảng.
3. **Catalog quote (chưa thực hiện):** Catalog trả base + effectivePrice + campaignId + kind + **pricingRevision/quote provenance** theo hợp đồng mới. Product vẫn lưu giá gốc. Event/offer bị tạm dừng/Store CLOSED/Product inactive phải bị loại bởi provider tin cậy; pure rules không thay thế kiểm quyền.
4. **Cart/Checkout (BLOCKED M01 mới):** khi giá thay đổi, Cart stale và yêu cầu Customer xác nhận lại. Checkout dùng Catalog quote authoritative đúng mốc thiết kế, ghi bất biến revision/price/source vào draft và Order; idempotency/fingerprint được mở rộng, replay không tính tiền lại. Theo luồng hiện có Cart claim → Catalog preflight → durable draft → Inventory RESERVE → commit Order/outbox. Không tạo endpoint sale bypass Checkout.
5. **Finance (BLOCKED M01 mới + TV5 review):** MD05 hiện không có giảm giá. Phải chốt ai tài trợ, fee 5% tính trên cơ sở nào, snapshot gross/discount/net, tác động refund/settlement, ownership TV5 trước khi bật giá sale ở đường mua hàng thật.

## Quy tắc runtime cho pure rules

- ProductPrice.validate vẫn kiểm tra giá VND nguyên dương của base và promotion; promotion phải **nhỏ hơn** base khi được áp dụng.
- FLASH_SALE cần thỏa cả khoảng chiến dịch do server lưu và giờ hàng tuần Việt Nam; Super/Seller cần thỏa khoảng riêng đã được duyệt.
- Nếu 2 offer cùng hiệu lực trên một Product, **fail closed**, không tự lấy giá thấp nhất, không cộng hai lần. Admin phải có quyết định cụ thể và nguồn Catalog chỉ trả offer thắng cuộc.
- Input chỉ từ verified Catalog/campaign projection, **không tin client-supplied price, StoreId, status**. PriceQuote không phải giấy phép tự đặt giá Checkout.
- Chính sách ưu tiên khi **Super Sale trùng Flash Sale** cần TV2 chốt cách lưu quyết định và xử lý việc tạm dừng; không giả vờ đã giải quyết bằng thuật toán đoán.

## Quyết định còn mở / trách nhiệm

| ID | Câu hỏi cần chốt | Owner/reviewer |
|---|---|---|
| P01 | Schema, lifecycle (DRAFT/APPROVED/PAUSED...), conflict resolution/explicit winner, quyền approve, API/DTO | TV2 (Product/Admin owner) |
| P02 | One Super Sale per month, time zone/UTC storage, priority when dates overlap | TV2 |
| P03 | Price revision và endpoint Catalog trusted quote cho Cart/Checkout; Product base-price edits trong khi sale | TV2 + TV3 |
| P04 | MD05 mới: discount sponsor, fee basis, split Store, snapshot, Return/COD/settlement | TV2 business owner + TV5 Finance reviewer, TV3/TV4 consumers |
| P05 | Checkout price eligibility timestamp, stale handling, replay/crash/UNKNOWN; không thay journal Inventory khi chưa thiết kế | TV3 + TV4 (nếu Inventory contract đổi) |

## Bằng chứng và điều kiện đạt

- Pure rules: unit tests FS-01 (21:59:59 / 22:00 / 00:00 VN), FS-02 (xung đột fail closed), FS-04 (VND nguyên/giá nhỏ hơn base), cửa sổ [start,end), ownership-id boundary, Super Day do Admin chọn.
- **Chưa đạt**: FS-03 quyền Seller/Admin/Store thật; campaign DB/HTTP/UI; CT-01..04 stale/Order snapshot/replay; real two-Store E2E; Finance M01 mới. Không tính pure rules là feature đã hoạt động trên website.
- PR triển khai giai đoạn tiếp theo phải có PostgreSQL IT, contract & source review và exact SHA CI. Không sửa main trực tiếp, không đổi V9/V10 Voucher C1, không đưa sale vào Checkout trước khi chốt M01.
