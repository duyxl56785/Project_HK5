# TV3 — Pre-final evidence handoff, frozen-SHA UAT và đối chiếu bản nộp (16/11/2026)

**Phạm vi:** Cart, Checkout COD đa cửa hàng, Order/Return, Flash Sale/Super Sale/Seller Clearance và Voucher Store **C1**. **Trạng thái: PRE-FINAL; chưa có nghiệm thu cuối ngày 20/11.** Refs #160, #18, #162, #158. Chỉ là báo cáo đối chiếu/bàn giao, không sửa API/code/DB/CI/cờ.

## 1. Đã xác minh từ nguồn (không suy thành UAT cuối)

| Hạng mục | Provenance | Kết luận giới hạn |
| --- | --- | --- |
| Báo cáo Checkout | [PR #188](https://github.com/duyxl56785/Project_HK5/pull/188) và [#197](https://github.com/duyxl56785/Project_HK5/pull/197): TV2/TV5 reviewed, merged; [CHECKOUT.md](CHECKOUT.md) | **DONE nội dung TV3**, chưa ghép vào DOCX cuối/Hình 6.4 |
| Cart/Checkout/Return | [#196](https://github.com/duyxl56785/Project_HK5/pull/196) Windows/PG regression đã được TV2 kiểm; source đã merge | PASS trong phạm vi suite; UNKNOWN thật và RET-05 chưa nghiệm thu |
| Voucher C1 | [PR #200](https://github.com/duyxl56785/Project_HK5/pull/200) merge `d023e104`, owner APPROVED; [CI backend 43560390842](https://github.com/duyxl56785/Project_HK5/actions/runs/43560390842), [CI E2E 43560390914](https://github.com/duyxl56785/Project_HK5/actions/runs/43560390914), Playwright **23/23 PASS** | **DONE C1**: quản lý voucher, slot, yêu cầu Admin; **chưa có C2 redeem Checkout**; #198/#199 đã closed |
| Flash, Super, xả kho | [PR #202](https://github.com/duyxl56785/Project_HK5/pull/202) merge `552e8eb`; [backend run 43702848981](https://github.com/duyxl56785/Project_HK5/actions/runs/43702848981), [frontend/E2E run 43702848877](https://github.com/duyxl56785/Project_HK5/actions/runs/43702848877), Playwright **24/24 PASS** | CI ở **`main@552e8eb`**, không tự coi là frozen-SHA UAT |
| Source lúc lập | `main@b2bee1d13eef2d404bc79c917b36af9489983351` | Repo có [DRAFT báo cáo TV5](../../../reports/submission/Bao_cao_Soopi_TV5_DRAFT_2026-11-14.docx), **không** được tự gọi là final |
| TV3 Windows smoke 15/11 | Người chạy xác nhận Catalog HTTP 200, `seed.mjs` in `Seeded` | PASS smoke cơ bản; **không** có screenshot/SQL receipt ràng buộc SHA |

## 2. Ma trận ảnh cần thu trước 19/11

| File đề nghị | Actor / route | Điều kiện phải thấy | Trạng thái |
| --- | --- | --- | --- |
| `promo-01-flash-sale.png` | Admin `/admin/promotions` | Campaign Flash thực, đúng loại/giờ | **NOT_CAPTURED** |
| `promo-02-super-sale.png` | Admin `/admin/promotions` | Campaign Super thực | **NOT_CAPTURED** |
| `promo-03-seller-clearance.png` | Seller `/seller/promotions` | Xả kho của đúng Store và trạng thái duyệt | **NOT_CAPTURED** |
| `voucher-01-seller.png` | Seller `/seller/vouchers` | C1, voucher/slot thật; trang trống chỉ ghi UI_ONLY | **NOT_CAPTURED** |
| `voucher-02-admin-limit-request.png` | Admin `/admin/voucher-limit-requests` | Yêu cầu PENDING để duyệt | **NOT_CAPTURED** |
| `checkout-01-cart-two-stores.png` | Customer `/cart` | Hai Store + hai Product cùng một Cart | **NOT_CAPTURED** |
| `checkout-02-cod-confirmation.png` | Customer `/checkout` | COD trước khi gửi đơn, chỉ dùng PII giả và mask | **NOT_CAPTURED** |
| `checkout-03-order-detail.png` | Customer `/orders/:id` | Order đã commit, đúng ID trong receipt | **NOT_CAPTURED** |
| `checkout-03b-order-history.png` | Customer `/orders` | Cùng ID trên danh sách History, phân biệt trang detail | **NOT_CAPTURED** |
| `checkout-04-db-receipt.md` | DB read-only | Order + 2 SellerOrders, fee, stock/journal; redacted | **NOT_CAPTURED** |

Nguồn hướng dẫn chi tiết: [CAPTURE_REAL_CHECKOUT.md](CAPTURE_REAL_CHECKOUT.md). Ảnh `cart-hai-store-quantity-remove-va-jwt-het-han-passed.png` lịch sử là giỏ **trống**, `cod-desktop.png` là mock/preview; không dùng làm ảnh Checkout thật. Ảnh B.1 trong [PR #204](https://github.com/duyxl56785/Project_HK5/pull/204) chỉ là PRE-FINAL; không phải cả bộ B.1–B.5 đã đủ.

**Ràng buộc:** chỉ chụp từ stack local/disposable do TV2/TV5 cho phép, source SHA và Compose project ID rõ; không upload credential/JWT/PII. Screenshot có dữ liệu thật chưa duyệt phải nằm trong thư mục private ngoài repo, TV5 xem pixel, kiểm SHA256 rồi mới chèn báo cáo/commit. Nếu fixture không có thì giữ `NOT_CAPTURED`, không tạo ảnh giả. Không tự bật cờ Checkout, Finance hoặc UNKNOWN chỉ để chụp.

## 3. Ma trận UAT đúng frozen SHA — 20/11

| Case | Expected/Oracle | Owner | Hiện trạng |
| --- | --- | --- | --- |
| UAT-TV3-01 Checkout COD 2 Store | 1 root Order, đúng 2 SellerOrders, SKU/qty/reservation/fee snapshots; browser + SQL read-only | TV3+TV4+TV5 | **NOT_RUN_FROZEN_SHA** |
| UAT-TV3-02 replay/mismatch | Cùng key+payload không nhân đôi; đổi shipping/items trả 409; stock không âm | TV3+TV5 | **NOT_RUN_FROZEN_SHA** |
| UAT-TV3-03 Product/Promotion PRICE_CHANGED | Giá thay trước Checkout bị từ chối, Order không được tạo với giá cũ | TV3+TV2+TV5 | **NOT_RUN_FROZEN_SHA** |
| UAT-TV3-04 sale price/Finance | Order unit_price_snapshot theo giá sale, fee 5% HALF_UP trên gross sau sale | TV3+TV5 | **NOT_RUN_FROZEN_SHA** |
| UAT-TV3-05 cancel/reject/dispatch | RELEASE/CONSUME đúng một lần, không tác động bừa | TV4+TV3+TV5 | **NOT_RUN_FROZEN_SHA** |
| UAT-TV3-06 Voucher C1 | Seller publish 20, request, Admin approve, publish #21; không áp mã Checkout | TV3+TV2+TV5 | **PASS historical CI / FINAL NOT_RUN** |
| UAT-TV3-07 Flash/Super/Clearance | Giờ VN, duyệt, tiền, bảo vệ chồng campaign | TV3+TV2+TV5 | **PASS historical CI / FINAL NOT_RUN** |
| UNKNOWN U11/U12 | Cần chính xác fixture, owner approval, provider oracle | TV4+TV5+TV3 | **BLOCKED_DESIGN / NOT_RUN** |
| RET-05 stock restock | Cần owner/contract riêng, không tự kích hoạt | TV4+TV5 | **BLOCKED_DESIGN** |

**Không chạy trước ngày 20 dưới nhãn final.** TV2 chốt full SHA cuối 19/11; 20/11 dùng stack sạch, dữ liệu giả, Git clean, bằng chứng `git rev-parse HEAD`, `git status --short`, Compose project, version, các exit code/JUnit/Playwright, ảnh redacted, SQL read-only và manifest checksum. Hai cờ `--approved-inventory-pair --approved-finance-policy` chỉ được dùng trong demo local đã phê duyệt, không phải dấu chứng nhận production. Không xóa volume, không bật kịch bản UNKNOWN/RET-05 khi còn blocked.

## 4. Những gì phải đối chiếu trong bản Word và slide trước 21/11

Có bản nháp [DOCX TV5 14/11](../../../reports/submission/Bao_cao_Soopi_TV5_DRAFT_2026-11-14.docx) và [deck TV5 14/11](../../../reports/submission/Soopi_TV5_notes_DRAFT_2026-11-14.pptx), nhưng **chưa kiểm trực tiếp nội dung nhị phân từng trang** trong lượt này. Dùng bảng này làm checklist, không nhận đã sửa/render bản Word/PowerPoint:

- §6, **Hình 6.4:** Cart hai Store + COD + Order detail thật, cùng source/run; không dùng ảnh cart trống, không nhầm Order detail với Order History.
- **MD12:** Flash/Super/Clearance thay **đơn giá Product** trước preflight; 5% tính trên tiền hàng sau khuyến mãi; immutable snapshot, `PRICE_CHANGED` nếu quote thay; không bảo C2 đã hiện diện.
- **Voucher C1:** DRAFT/PAUSED không chiếm slot; 20 slot là số voucher `PUBLISHED` chưa hết hạn, không phải số chương trình tối đa được tạo; chưa trừ lượt/áp mã lúc Checkout. C2 chỉ là hướng phát triển theo PR #206.
- **Finance/COD:** `FulfillmentService.settle` chỉ đánh dấu đối soát & ghi thông báo, chưa tự chuyển khoản ngân hàng; không tự khẳng định đã thanh toán thực.
- **Return:** Chưa tự động hoàn tiền/restock, RET-05 blocked; UNKNOWN không tự giải quyết.
- **QA:** mọi số CI phải ghi đúng SHA/run; `PASS` CI không đồng nghĩa `PASS` final UAT; nếu ca chưa chạy ghi `NOT_RUN/BLOCKED`.
- **B.1–B.5:** ảnh phải runtime thật trên SHA/stack đã cho phép, đã mask PII; ảnh B.1 pre-final của PR #204 không thay toàn bộ ảnh cuối.
- **Lịch:** frozen main hết **19/11**, clean-stack E2E & clean-clone **20/11**, nộp **21/11**.

## 5. Điều kiện chốt Issue #160

**Đã DONE trong phạm vi TV3:** implementation Cart/Checkout/Order/Return, Promotions, Voucher C1; report source đã được owner duyệt. **Chưa DONE:** ảnh Checkout hợp lệ, ghép chính thức DOCX/slide, final UAT TV5 #18/#162 và P0/P1 owner sign-off. Do đó #160 tiếp tục **OPEN**. Chỉ tick các ô còn lại sau khi có receipt exact SHA và TV2/TV5 ký, hoặc waiver nêu rõ phạm vi. Đề nghị TV5 sử dụng crosswalk này để ghép report; TV2 review tài liệu/hình sau khi có ảnh thật.

**Tài liệu không tự nhận đã chạy script hay tạo ra ảnh.** Khuyến nghị owner báo ngắn theo `PASS / FAIL / NOT_RUN / BLOCKED`, gắn nguồn chính xác, chỉ comment khi đổi trạng thái hoặc cần quyết định.