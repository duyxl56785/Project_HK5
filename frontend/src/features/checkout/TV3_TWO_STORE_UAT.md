# TV3 — Kịch bản nghiệm thu Cart/Checkout COD hai cửa hàng

**Trạng thái 23/10/2026:** SCENARIO/PREPARED, **NOT_RUN live**, không chứng minh provider, JWT, COD/Settlement hoặc Inventory stock. Refs #3 #10 #16.

## Dữ liệu fixture độc lập, không phải seed production

| Fixture | Store | Seller | Product | Quantity | Unit-price decimal snapshot | Line amount |
|---|---|---|---|---:|---:|---:|
| A | store-A | seller-A | p1 | 2 | 10000.00 | 20000.00 |
| B | store-B | seller-B | p2 | 1 | 15000.00 | 15000.00 |

Tổng **giá trị mặt hàng** = **35000.00**; currency, shipping, fee, COD settlement, House policy **chưa được quyết định bởi fixture**. Mỗi Seller/Store là nguồn bất biến phải được Catalog server xác minh.

## Tiêu chí cần kiểm chứng sau khi contract được phê duyệt

1. Auth-issued JWT Customer hợp lệ, Cart có revision và đúng hai Product READY có authoritative SKU; không lấy SKU từ Product ID hoặc client.
2. Checkout request mang một Idempotency-Key ổn định cùng `expectedItems=[{p1,2},{p2,1}]` và `expectedRevision` thực tế từ Cart.
3. Server xác nhận atomic **1 Order, 2 SellerOrders** đúng Store, Seller, items, snapshot; không đếm CheckoutAttempt là Order.
4. Inventory RESERVE do provider xác nhận theo Product-owned SKU, operation ID durable và reservation ownership; đo số dư trước/sau qua Inventory provider, không giả lập.
5. Retry **same key + same payload** trở về đúng Order đã xác nhận, không RESERVE thêm; **same key + different payload** phải conflict.
6. Khi Cart revision/price/product/Store/Seller thay đổi, checkout từ chối **toàn bộ** trước khi có side effect bất hợp lệ.
7. Timeout/UNKNOWN giữ key ban đầu và trạng thái chờ reconciliation; **không bấm retry với key mới**; nếu chưa rõ reservation thì không RELEASE.
8. Chỉ sau khi backend xác nhận thành công mới dọn Cart; COD payment status không suy ra từ Inventory/DELIVERED.
9. UI có loading/empty/error/unauthorized, keyboard/mobile khi browser test thật; chụp màn hình và lưu test log tại nơi không chứa secrets.

## Phạm vi kiểm thử và bằng chứng

| Nhóm | Kết quả hiện tại | Bằng chứng cần thu |
|---|---|---|
| Pure cart/checkout model test | **13/13 PASS** on exact Git blob contents (Node 22.16.0); 8 Cart + 5 Checkout | SHA + TAP Node output verified; no real API |
| React SSR component tests | NOT_RUN | Node/Vite console |
| TypeScript build | NOT_RUN | `npm run typecheck`, `npm run build` |
| Browser component tests | NOT_RUN | Chrome/Playwright + ảnh/console |
| Order PostgreSQL module | Đã có các test ở các SHA backend khác; không gộp | Exact SHA + XML |
| Live Cart→Catalog→Inventory→Order E2E | **BLOCKED** | Auth, provider API, SKU+ownership, M01, Inventory stock before/after, DB read |
| COD lifecycle/settlement | **BLOCKED** | TV4/TV5/C06/M01 authorization, payment evidence |

## Bàn giao dependency đúng owner

- **TV1**: shared router/API client, authenticated actor/JWT/pair, endpoint/error DTO; không tự sửa `src/app` hoặc `src/shared`.
- **TV2**: Catalog authoritative current Product→SKU/Store/Seller/price/READY/freshness.
- **TV4**: Inventory RESERVE/RELEASE/CONSUME, ownership and UNKNOWN lookup, C06.
- **TV5 + business owner**: M01 currency/fee, COD paid/settlement boundaries.

Demo fixture/UI **không tương đương** live Checkout. Khi có approval, mở kế hoạch tích hợp nhỏ có test trên service thật; không tự bật flag Finance/Inventory.
