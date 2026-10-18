# Nguồn đối chiếu M01 local

> Công bố qua PR44: checkpoint nguồn ban đầu21f6d53/ef62086 được giữ provenance; đã đồng bộ owner5ce0630 và kiểm QA tại1311f8a. Xem [kết quả mới và giới hạn](../TV5_FINANCE_ACCEPTANCE_2026-10-18.md). Mô tả quantity MD03 đã sửa trong packet; business policy vẫn PENDING.


Snapshot metadata kiểm lại lúc10:28:45 UTC ngày18/10/2026; repository duyxl56785/Project_HK5. Các blob link khóa full SHA. Comments là owner position tại thời điểm đọc, không suy từ quyền commit thành business authority.

## Heads và phạm vi

- PR28 OPEN: `21f6d533726533cc49febcc1e977f25bb0270de0`; PR37 OPEN: `ef62086048e2c7a00ecee16db0cc3cde2413deb6`.
- PR43 đã MERGED vào PR28; head patch `89cd293c08d7b9a840c9b022bd57866076d53a3c`, merge `21f6d53`. PR41/42 đã tích hợp trước đó.
- main `ac7ca4fd8a8fe60a8d70fb15f98af9a0bf47bc59`; PR28 có conflict QA mới theo REST/comment S20; không merge trong nhiệm vụ này.
- Tài liệu mới trong reports/tv5-m01-review; không sửa source, old packets, raw logs/XML, sources/, V1 hoặc Finance config.
- REST GET/fetched Git đọc PR source, paginated comments/reviews/files/commits. Không post/comment/push/approve/resolve. Runtime test NOT_RUN; chỉ static/doc/arithmetic checks của phiên này.
- AGENTS.md, CONTRIBUTING.md, docs/OWNERSHIP.md local đã đọc; reviewer/module ownership không thay quyền business approver.
- User xác nhận trong phiên: TV5 chỉ tổng hợp/đề xuất; người ký cuối cùng **chờ TV1/chủ dự án xác nhận**. Không có GitHub approval link cho quyết định chưa được ký.

## S01

**Packet câu hỏi MD01–MD10** — [docs/TV5_M01_HUMAN_DECISION_PACKET.md:26](https://github.com/duyxl56785/Project_HK5/blob/21f6d533726533cc49febcc1e977f25bb0270de0/docs/TV5_M01_HUMAN_DECISION_PACKET.md#L26).

MD01–MD10 ở các dòng26–35, lần lượt theo ID. Đây là TV5-M01-DECISION-v1 đề xuất, không phải chữ ký business.

## S02

**FinanceRules: phí và eligibility** — [backend/order-service/src/main/java/com/projecthk5/orderservice/finance/FinanceRules.java:9](https://github.com/duyxl56785/Project_HK5/blob/21f6d533726533cc49febcc1e977f25bb0270de0/backend/order-service/src/main/java/com/projecthk5/orderservice/finance/FinanceRules.java#L9).

PLATFORM_RATE0.05 L9; Amounts/snapshot L13–25, House0 L23, HALF_UP L24; settleable L28; Store-owner guard L38. Helper không chứng minh DB atomic settlement hoặc Store-scoped query.

## S03

**MoneyRules: cấu hình Checkout** — [backend/order-service/src/main/java/com/projecthk5/orderservice/domain/MoneyRules.java:19](https://github.com/duyxl56785/Project_HK5/blob/21f6d533726533cc49febcc1e977f25bb0270de0/backend/order-service/src/main/java/com/projecthk5/orderservice/domain/MoneyRules.java#L19).

Constructor/config L19–34; rate0..1 và scale0..4; fee L45–50, lineTotal L53–56. Có thể lệch signed rate/House/scale nếu enable chỉ dựa Boolean.

## S04

**CheckoutService: replay, reserve và grouping** — [backend/order-service/src/main/java/com/projecthk5/orderservice/service/CheckoutService.java:48](https://github.com/duyxl56785/Project_HK5/blob/21f6d533726533cc49febcc1e977f25bb0270de0/backend/order-service/src/main/java/com/projecthk5/orderservice/service/CheckoutService.java#L48).

SUCCEEDED replay L48; reserveRequested L76 rồi reserve L77 và writer L79; prepareOrder L116; nhóm Store L118; gross/fee L138–139. Không gọi OrderAmountStorageBounds trong source này.

## S05

**Order migration hiện tại** — [backend/order-service/src/main/resources/db/migration/V1__checkout_baseline.sql:1](https://github.com/duyxl56785/Project_HK5/blob/21f6d533726533cc49febcc1e977f25bb0270de0/backend/order-service/src/main/resources/db/migration/V1__checkout_baseline.sql#L1).

orders L1, seller_orders L9, order_items L18: tiền NUMERIC(19,2). Đây là schema fact, không phải lựa chọn VND scale0.

## S06

**SellerOrder entity** — [backend/order-service/src/main/java/com/projecthk5/orderservice/entity/SellerOrder.java:9](https://github.com/duyxl56785/Project_HK5/blob/21f6d533726533cc49febcc1e977f25bb0270de0/backend/order-service/src/main/java/com/projecthk5/orderservice/entity/SellerOrder.java#L9).

gross L14, platform_fee L15; không có currency/commission_rate/House/policyVersion/seller_net snapshot trong entity này.

## S07

**YAML Finance gate** — [backend/order-service/src/main/resources/application.yml:17](https://github.com/duyxl56785/Project_HK5/blob/21f6d533726533cc49febcc1e977f25bb0270de0/backend/order-service/src/main/resources/application.yml#L17).

Finance L17–22; policy-approved mặc định false L19, rate/scale/house-exempt lấy config. Không xác minh deployment environment trong phiên này.

## S08

**Utility storage bound chưa wire** — [backend/order-service/src/main/java/com/projecthk5/orderservice/domain/OrderAmountStorageBounds.java:15](https://github.com/duyxl56785/Project_HK5/blob/21f6d533726533cc49febcc1e977f25bb0270de0/backend/order-service/src/main/java/com/projecthk5/orderservice/domain/OrderAmountStorageBounds.java#L15).

MAX_NONNEGATIVE NUMERIC19,2 L15; requireExactNonnegative L23. Utility không tự chọn business cap/currency hoặc kiểm orchestrator trước reserve.

## S09

**StoreProfile** — [backend/seller-service/src/main/java/com/projecthk5/sellerservice/store/StoreProfile.java:6](https://github.com/duyxl56785/Project_HK5/blob/ef62086048e2c7a00ecee16db0cc3cde2413deb6/backend/seller-service/src/main/java/com/projecthk5/sellerservice/store/StoreProfile.java#L6).

Record Store hiện có không có House field/persisted privileged assignment.

## S10

**ProductAccessPolicy / StoreAccess** — [backend/product-service/src/main/java/com/projecthk5/productservice/product/ProductAccessPolicy.java:5](https://github.com/duyxl56785/Project_HK5/blob/ef62086048e2c7a00ecee16db0cc3cde2413deb6/backend/product-service/src/main/java/com/projecthk5/productservice/product/ProductAccessPolicy.java#L5).

Trusted adapter boundary L5; StoreAccess L12 có houseStore boolean. Type không tự cấp identity/current-fact authority.

## S11

**TV2 Catalog contract response v0.2** — [docs/TV2_CATALOG_CONTRACT_RESPONSE.md:18](https://github.com/duyxl56785/Project_HK5/blob/ef62086048e2c7a00ecee16db0cc3cde2413deb6/docs/TV2_CATALOG_CONTRACT_RESPONSE.md#L18).

S01/Security L11–16; wire L18–45; E01 L47–63; House L67; last-verified/PhaseA-B/risk L75–81; VND compatibility L83. Technical proposal/overall REVISE-PENDING, chưa API production.

## S12

**CatalogSnapshotSource** — [backend/product-service/src/main/java/com/projecthk5/productservice/catalog/CatalogSnapshotSource.java:12](https://github.com/duyxl56785/Project_HK5/blob/ef62086048e2c7a00ecee16db0cc3cde2413deb6/backend/product-service/src/main/java/com/projecthk5/productservice/catalog/CatalogSnapshotSource.java#L12).

LookupResult/FOUND-ABSENT-UNAVAILABLE L12–14. Explicit absence khác omitted/unavailable, enum không verify freshness/source.

## S13

**CatalogBatchValidator** — [backend/product-service/src/main/java/com/projecthk5/productservice/catalog/CatalogBatchValidator.java:35](https://github.com/duyxl56785/Project_HK5/blob/ef62086048e2c7a00ecee16db0cc3cde2413deb6/backend/product-service/src/main/java/com/projecthk5/productservice/catalog/CatalogBatchValidator.java#L35).

Result withholding L35–39; explicit ABSENT L55/97, source load L81, full key coverage L108. Không usable READY subset khi batch reject; internal source behavior khác HTTP adapter.

## S14

**CatalogPort hiện tại phía Checkout** — [backend/order-service/src/main/java/com/projecthk5/orderservice/ports/CatalogPort.java:5](https://github.com/duyxl56785/Project_HK5/blob/21f6d533726533cc49febcc1e977f25bb0270de0/backend/order-service/src/main/java/com/projecthk5/orderservice/ports/CatalogPort.java#L5).

ProductSnapshot L7 chưa có currency/inventorySku; hiện là verify theo product, chưa batch v0.2 wire.

## S15

**Cart schema: quantity và price** — [backend/cart-service/src/main/resources/db/migration/V1__cart_baseline.sql:14](https://github.com/duyxl56785/Project_HK5/blob/21f6d533726533cc49febcc1e977f25bb0270de0/backend/cart-service/src/main/resources/db/migration/V1__cart_baseline.sql#L14).

Quantity CHECK1..1000 L14; unit_price_snapshot NUMERIC19,2 L15. Không có currency trong Cart schema này.

## S16

**Checkout DTO quantity** — [backend/order-service/src/main/java/com/projecthk5/orderservice/api/CheckoutDtos.java:11](https://github.com/duyxl56785/Project_HK5/blob/21f6d533726533cc49febcc1e977f25bb0270de0/backend/order-service/src/main/java/com/projecthk5/orderservice/api/CheckoutDtos.java#L11).

ExpectedItem @Min(1) @Max(1000) L11; request items @NotEmpty L12, chưa max100 ở DTO này. Candidate batch count1..100 khác quantity1..1000.

## S17

**Cart service quantity** — [backend/cart-service/src/main/java/com/projecthk5/cartservice/service/CartService.java:28](https://github.com/duyxl56785/Project_HK5/blob/21f6d533726533cc49febcc1e977f25bb0270de0/backend/cart-service/src/main/java/com/projecthk5/cartservice/service/CartService.java#L28).

Validate add L28, combined qty cap1000 L41, update L48. Business cap không phải Integer.MAX_VALUE.

## S18

**ProductListing price** — [backend/product-service/src/main/java/com/projecthk5/productservice/product/ProductListing.java:7](https://github.com/duyxl56785/Project_HK5/blob/ef62086048e2c7a00ecee16db0cc3cde2413deb6/backend/product-service/src/main/java/com/projecthk5/productservice/product/ProductListing.java#L7).

Record/constructor L7 trở đi: yêu cầu price dương, chưa có currency/cap VND17digits ở model này.

## S19

**TV3 final consumer-scope sign-off** — [comment owner](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6248646211).

TV3 APPROVED riêng consumer design C01/mapper E01; overall REVISE. MD07/M01/S01/Security PENDING; conditional A, no quote/lease guarantee. Không yêu cầu lại DTO shape như chưa phản hồi.

## S20

**TV3 sau PR43 merge và MD packet** — [comment owner](https://github.com/duyxl56785/Project_HK5/pull/28#issuecomment-6248595524).

PR43 tích hợp tại21f6d53; technical MD02/03/08 acknowledged, không business sign-off. TV1 cần chỉ định người ký; overlap docs/QA mới với mainac7ca4f, không làm lại conflict union cũ.

## S21

**TV2 candidate v0.2 / M01 compatibility** — [comment owner](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246199255).

Provider technical position, không business signature hoặc implementation approval; xem thêm source S11 và phản hồi mới S19.

## S22

**TV2 provider/freshness disposition** — [comment owner](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246597564).

Khuyến nghị last-verified có human/TV1/TV5 risk acceptance; không có quote/lease. Source42cc4e7.

## S23

**TV2 current facts/recipient/existing orders** — [comment owner](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246611924).

Authoritative Product/SKU/Store/Seller/recipient provider NOT_AVAILABLE/PENDING. Existing-order policy khi Seller suspended/Store closed cần TV1/TV4/business, không tự cancel/release.

## S24

**QA checkpoint lịch sử** — [reports/TV5_MONEY_DECISION_CHECKPOINT_2026-10-18.md:1](https://github.com/duyxl56785/Project_HK5/blob/89cd293c08d7b9a840c9b022bd57866076d53a3c/reports/TV5_MONEY_DECISION_CHECKPOINT_2026-10-18.md#L1).

Nguồn report chỉ ra utility6PASS, diagnostic1PASS/2FAIL tại c4d517e và Catalog50PASS tại63668b8. Đây là lượt chạy trước, không chạy lại runtime trong phiên local; không cộng các snapshot thành release total.

## S25

**TV5-FIN-v0.1 và sáu-contract checkpoint** — [docs/TV5_FINANCE_CONTRACT_SIGNOFF.md:1](https://github.com/duyxl56785/Project_HK5/blob/21f6d533726533cc49febcc1e977f25bb0270de0/docs/TV5_FINANCE_CONTRACT_SIGNOFF.md#L1).

Proposed decision package, own-scope owners/signatures/permission, current requirements5%/House0/net, COD/currentADMIN, atomic settlement và Store scope. Các đoạn evidence cũ giữ SHA riêng; không đọc đoạn đầu như latest source state.

## S26

**Quy chuẩn repository** — [CONTRIBUTING.md:1](https://github.com/duyxl56785/Project_HK5/blob/21f6d533726533cc49febcc1e977f25bb0270de0/CONTRIBUTING.md#L1).

Ownership/shared changes; BigDecimal/numeric, HALF_UP, phí5%/House0 lúc tạo đơn con; migrations thêm version, no cross-DB FK; QA/E2E evidence và DONE gates. Cùng docs/OWNERSHIP.md và AGENTS.md local đã đọc trước thay đổi.

## S27

**TV4 state/COD/retry proposal** — [comment owner](https://github.com/duyxl56785/Project_HK5/pull/39#issuecomment-6246602587).

Owner proposal cho delivery/COD và retry, không phải đủ same-version group signatures. Inventory outcome không chứng minh COD PAID/delivery.

## Phân biệt fact, proposal và evidence

1. Source code/schema fact chỉ chứng minh nội dung tại SHA, không khẳng định runtime deployment hoặc production readiness.
2. Owner technical acceptance chỉ áp own-scope đã nêu. M01 và MD01–10 vẫn PENDING, C01/E01 overall REVISE.
3. Định nghĩa House, VND/caps, HALF_UP0 theo Store, authority/process, snapshot fields và freshness recommendation trong tài liệu local là đề xuất cần ký.
4. S24 và report được S25 dẫn là lịch sử test theo exact SHA; phiên này không chạy unit/PG/live E2E hoặc kiểm lại raw test XML. Không sửa/publish lại raw secrets.
5. VALIDATION.json chỉ là derived record của static source/Markdown/arithmetic checks local, không phải runtime acceptance hay business signature.

## S28

**PR37 latest independent database preparation** — [TV2 progress tại ef62086](https://github.com/duyxl56785/Project_HK5/blob/ef62086048e2c7a00ecee16db0cc3cde2413deb6/docs/TV2_WEEK1_PROGRESS.md#independent-database-verification--2026-10-18), [handoff](https://github.com/duyxl56785/Project_HK5/blob/ef62086048e2c7a00ecee16db0cc3cde2413deb6/docs/TV2_CATALOG_HANDOFF.md#independent-schema-preparation-and-pending-api-acceptance--2026-10-18).

- Đã đọc toàn bộ delta42cc4e7→ef62086 (6files): hai test Java, hai SQL candidate resources, hai Markdown. Không đổi src/main hoặc docs/TV2_CATALOG_CONTRACT_RESPONSE.md. Các source links S09–13/S18 khóa latest ef62086; blobs/line nội dung không đổi so với42cc4e7 đã được đối chiếu.
- [Store test](https://github.com/duyxl56785/Project_HK5/blob/ef62086048e2c7a00ecee16db0cc3cde2413deb6/backend/seller-service/src/test/java/com/projecthk5/sellerservice/store/StoreSchemaProposalPostgresTest.java#L1), [Store SQL](https://github.com/duyxl56785/Project_HK5/blob/ef62086048e2c7a00ecee16db0cc3cde2413deb6/backend/seller-service/src/test/resources/proposals/store-candidate.sql#L1), [Product test](https://github.com/duyxl56785/Project_HK5/blob/ef62086048e2c7a00ecee16db0cc3cde2413deb6/backend/product-service/src/test/java/com/projecthk5/productservice/catalog/CatalogSchemaProposalPostgresTest.java#L1), [Product SQL](https://github.com/duyxl56785/Project_HK5/blob/ef62086048e2c7a00ecee16db0cc3cde2413deb6/backend/product-service/src/test/resources/proposals/catalog-product-candidate.sql#L1).
- TV2 report: Store26 focused, Product24 focused; full406PASS tại3ecf9c3,88PG gồm38 Category/Seller production và50 candidate. Focused/historical counts không cộng vào full run. TV5 **chưa chạy lại hoặc kiểm raw logs/XML** của lượt này. Source inspection chỉ xác nhận test/candidate tồn tại, không độc lập xác nhận kết quả execution.
- Latest tests là SQL constraint/concurrency/CAS, không phải runtime Store/Product JPA/House authority, source freshness, price lease hoặc positive Catalog API. Không thay overall PENDING/REVISE.
