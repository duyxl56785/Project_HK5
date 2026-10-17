# Kế hoạch thực thi 21 ngày — Smart E-Commerce Marketplace COD-only MVP

**Trạng thái:** Đề xuất thay thế kế hoạch thực thi cũ, chờ nhóm review/merge tài liệu. **Thời hạn:** D1–D21 là ngày làm việc tương đối tính từ mốc nhóm xác nhận; không tự đặt lại hoặc xóa tiến độ đã thực hiện trước đây. **Nhóm:** 5 người, một owner cho mỗi đầu việc, phối hợp qua GitHub Issues/PR.

**Nguồn đối chiếu:** Báo cáo bàn giao V2, docs/ARCHITECTURE.md, docs/CONTRACTS.md, docs/OWNERSHIP.md, CONTRIBUTING.md và Issue #1–#5. **Checkpoint 17/10/2026:** PR #7 (Category/Seller domain) đã merge vào main tại `98268c2`; PR #20 (Review/Finance rules và QA) cũng đã merge. PR #25 đã merge persistence vào nhánh TV2 tại `5581d59`, còn PR #29 (`f8b0ff4`, base main) đang mở, chưa merge vào main. Các gate D1–D21 bên dưới là kế hoạch, không phải xác nhận mọi module/API đã chạy thật.

**Auth checkpoint:** Contract v1.0 đã được TV1 chốt qua [PR #24](https://github.com/duyxl56785/Project_HK5/pull/24), commit `1a9d579`: HS256 với current+previous `kid` keyring và trusted claims; không dùng JWKS/RS256 discovery trong MVP. Đây là quyết định contract, chưa xác nhận Auth issuer/Gateway hoặc consumer runtime đã hoàn tất.

## 1. Quyết định phạm vi sản phẩm

**P0 — BẮT BUỘC để công nhận MVP hoàn chỉnh:**
- 3 vai trò Customer, Seller, Admin; xác thực, JWT, server-side authorization/ownership.
- Seller đăng ký, Admin duyệt/từ chối/đình chỉ, seller được duyệt mới quản lý một Store.
- Store, Category (cây cha/con; slug/status cần chốt), Product, SKU không trùng, ảnh tối thiểu và kiểm duyệt; chỉ sản phẩm hợp lệ được công khai; tìm kiếm/lọc/phân trang cơ bản.
- Cart của một Customer chứa sản phẩm từ >=2 Store; checkout **COD duy nhất**, tạo **1 MarketplaceOrder + n SellerOrder**; tiền, phí, đơn vị tính được chụp tại lúc đặt và kiểm chứng server-side.
- Inventory đủ hàng, chống bán âm; reserve/consume/release không lặp; order có idempotency để không tạo đơn/trừ kho hai lần.
- Seller chỉ thấy/xử lý SellerOrder thuộc cửa hàng mình; trạng thái tối thiểu CONFIRMED → PACKING → SHIPPED → DELIVERED; hủy/từ chối và hoàn kho theo quy tắc chốt ở D2.
- Khách xem chi tiết/lịch sử/trạng thái đơn; COD được ghi nhận thủ công sau giao thành công bởi vai trò được cho phép, không tích hợp cổng thanh toán.
- Giao diện React hoạt động thật (không chỉ mock): đăng nhập, catalog/detail, cart/checkout COD, lịch sử đơn, seller store/product/orders, admin duyệt seller/product và ghi nhận COD.
- PostgreSQL/Flyway, cấu hình môi trường, hướng dẫn chạy một lần cho cả FE/BE, kiểm thử bảo mật cơ bản và E2E luồng 2 Store.

**P1 — Làm nếu P0 chạy ổn trước D14:** Review 1–5 sao do chính người mua sản phẩm của đơn DELIVERED thực hiện; mỗi cặp (order, product) chỉ một review kể cả khi có nhiều line/SKU cùng product. Admin được ẩn/hiện; review ẩn không tính vào tổng điểm hoặc số review công khai. Thông báo trong ứng dụng dạng polling hoặc cơ chế sẵn có; báo cáo doanh thu/hoa hồng COD cơ bản với phí sàn 5%, house-store 0%, `seller_net = subtotal - commission`. Chụp subtotal/rate/commission/net/currency khi tạo SellerOrder; không tính lại phí từ trạng thái Store hiện tại khi quyết toán. Chỉ quyết toán DELIVERED + COD PAID, chống quyết toán trùng bằng unique SellerOrder và transaction; Seller chỉ truy cập Finance thuộc Store của mình. TV3/TV4/TV5 chốt order key, currency/scale, quy tắc làm tròn và bốn nhóm doanh thu trước API/schema. Không được trì hoãn P0 để đạt P1; nhóm/chủ dự án cần xác nhận ảnh hưởng của việc hoãn F15/F16 đến nghiệm thu báo cáo V2, không tự coi các mục đó đã hoàn thành.

**Ngoài phạm vi:** VNPay/MoMo/Stripe, trả tiền online, kết nối hãng vận chuyển, hoàn tiền tự động, vận chuyển định vị, chatbot/AI/recommender, voucher/phức tạp, mobile app, production scale/HA. Bảo mật tối thiểu và tính nhất quán tiền/tồn kho **không** bị bỏ qua.

**Nguyên tắc thay đổi kiến trúc:** Giữ Java 21, Spring Boot 3.3.5, React/TypeScript, PostgreSQL, microservices/service ownership hiện có. Redis/Kafka/Eureka/Config Server dùng khi cần cho luồng thực sự; không buộc phải xây đủ hạ tầng chưa dùng trước khi P0 chạy. Nếu khác báo cáo V2/contract hiện hành, TV1 cùng owner consumer phê duyệt và ghi quyết định, không âm thầm thay thế Kafka/event/tiền/role đã được chốt.

## 2. Ownership không đổi

| Thành viên | GitHub | Chịu trách nhiệm và vùng code | Consumer/bên review |
|---|---|---|---|
| TV1 — Lê Xuân Bảo | @lexuanbao258-collab | Auth/User/Gateway/Docker/CI/Gradle Wrapper, FE shared/router/API client, điều phối hợp đồng liên service | TV2–TV5 |
| TV2 — Nguyễn Văn Duy | @duyxl56785 | seller-service; product-service: Category/Store/Product/catalog/moderation/images; FE features seller/catalog/product | TV1, TV3, TV4, TV5 |
| TV3 — Trần Văn Chính | @tranvanchinhbg2006-alt | Cart, Checkout, Order creation/idempotency; FE cart/checkout/order history | TV2, TV4 |
| TV4 — Phạm Thế Cường | @phamthecuong2022006-alt | Inventory, seller-order fulfillment, COD, Notification tối thiểu; FE fulfillment/inventory | TV3, TV5 |
| TV5 — Nguyễn Minh Duy | @nguyenminhduy090-alt | Review, Finance báo cáo cơ bản, QA/E2E/test evidence, FE review/finance nếu P1 được làm | TV2, TV3, TV4 |

Các phần dùng chung (đặc biệt order-service và product-service) tuân thủ docs/OWNERSHIP.md; không sửa chồng nhau nếu chưa có owner/PR review.

## 3. Contract bắt buộc chốt ở D1–D2 (không đợi hoàn thiện module)

| Contract/đầu ra | Owner chốt | Consumer | Deadline | Điều kiện bàn giao |
|---|---|---|---|---|
| JWT verified identity theo Auth v1.0: HS256 current+previous kid keyring, issuer/audience/sub/roles/claims, 401/403 | TV1 | TV2–TV5 | D2 | Contract đã chốt qua #24; consumer đối chiếu profile, ví dụ token test + test quyền; không tin headers giả, không để secret lên Git |
| API path Gateway + CORS, response error code, frontend API client | TV1 | tất cả | D2 | docs/CONTRACTS.md có endpoint/DTO/HTTP status, phân biệt public/internal |
| Seller/Store identity, APPROVED/SUSPENDED, unique owner + slug, profile minimum | TV2 + TV1 | TV3/TV4 | D2 | DTO/schema đề xuất/transition và ownership được chốt |
| Category slug/status; Product SKU, categoryId, storeId, price, visibility | TV2 + TV5 | TV3/TV4 | D2 | Catalog read/validate-product response có trạng thái/giá/seller |
| Cart / Checkout COD, Idempotency-Key, expectedItems, order totals/fee snapshots | TV3 + TV2 | TV4 | D2 | HTTP payload, error mã hết hàng/giá khác, quy tắc replay |
| Inventory reserve/consume/release theo operation/orderId, không âm | TV4 + TV3 | TV3 | D2 | DTO, dedup/retry timeout/compensation semantics |
| SellerOrder lifecycle, cancel/refuse/restore, DELIVERED + COD paid | TV3 + TV4 + TV5 | frontend/finance | D2 | Ranh giới ownership, người được xác nhận COD, event/read model |
| Review/Finance query contract (P1) | TV5 + TV3/TV4 | UI/admin | D5 | Không chặn P0 |

Đưa quyết định vào docs/CONTRACTS.md qua PR được review; **được code song song bằng stub/mock đúng contract**; trước nghiệm thu phải thay bằng API thật. Không tạo FK giữa database thuộc hai service.

## 4. Tuần 1 — D1–D7: xây nền và bàn giao API thật

**D1–D2:** TV1 làm chủ trì freeze contract v1 (Auth, identity, Store/Product, Checkout/Inventory, SellerOrder/COD); xác nhận Docker/DB schema ownership, seed 2 Seller/2 Store/1 Customer/1 Admin. Issue #1–#5 được **cập nhật, không xóa lịch sử/checklist/bằng chứng cũ**.

**D3–D5:** code độc lập, commit nhỏ, build/test; mọi service cung cấp endpoint tối thiểu hoặc adapter stub có giao ước. Không để 1 người phải chờ toàn bộ service của người khác.

**D6–D7:** chạy smoke end-to-end ở mức từng API, xác nhận handshake thật Auth→Seller→Product→Cart→Inventory/Order; PR nhỏ có reviewer; báo cáo Blocked/PASS/FAIL/NOT_RUN.

| TV | Bàn giao tuần 1 tối thiểu | Tiêu chí đạt W1 |
|---|---|---|
| TV1 | Gradle Wrapper, env sample không secret, Postgres + Gateway khởi động, Auth/User/JWT/role tối thiểu; FE shell/router/API client chuẩn, CI build/test cơ bản | Một thành viên khác clone và chạy; token Customer/Admin có thể kiểm chứng và 401/403 được test |
| TV2 | Giữ Category đã test trong PR #7; chốt Category slug/status/transaction + PR review; hoàn thiện Seller persistence/validation/role adapter theo contract; Store/Product model+contract và catalog read tối thiểu nếu kịp | Có API/schema dữ liệu seller-store-product (hoặc stub dựa hợp đồng) cho TV3/TV4; ghi rõ phần chưa có, không coi Seller domain mock là REST API thật |
| TV3 | Cart API/DB tối thiểu; schema MarketplaceOrder/SellerOrder; Checkout COD DTO, idempotency contract; adapter tới catalog/inventory | Có thể xây/test giỏ hàng 2 Store bằng dữ liệu stub và các contract chốt |
| TV4 | Inventory DB/schema và API check/reserve/consume/release với unique operation; SellerOrder/COD transition và contract với TV3 | Test chống tồn âm/replay; bàn giao response tiêu chuẩn cho checkout |
| TV5 | Review/Finance rule/schema nháp, ma trận test/QA; kịch bản UAT 2 Store, contract COD→Finance, checklist kiểm thử bảo mật | Test case được gắn owner/expected; phát hiện contract mismatch sớm, không ghi đã kiểm thử chức năng chưa tồn tại |

**Gate cuối W1:** có tài liệu contract thống nhất, build đơn lẻ từng module, FE shell gọi ít nhất một API thật qua Gateway, dùng stub đúng contract cho phụ thuộc còn thiếu. Nếu chưa đạt, đánh dấu BLOCKED, không tích DONE.

## 5. Tuần 2 — D8–D14: hoàn thành P0 end-to-end

| TV | Hạng mục thực thi | Tiêu chí chấp nhận |
|---|---|---|
| TV1 | Auth/role bảo vệ mọi endpoint, Gateway thực, frontend shared/session/CORS, deploy local bằng script; tích hợp UI các feature owner | FE→Gateway→service thật; khách/seller/admin được phân quyền; sai quyền nhận 401/403, endpoint mới fail-closed |
| TV2 | Seller approval + Store CRUD/ownership, Category slug/status/migration, Product CRUD/SKU/ảnh/moderation, public catalog filter, FE seller/catalog/product/admin screens liên quan | Seller chưa duyệt không bán; A không sửa B; SKU duy nhất; public chỉ ACTIVE + store/seller hợp lệ; TV3 lấy được 2 store/products thật |
| TV3 | Cart đa store, Checkout COD, Order + SellerOrder tạo atomically theo ranh giới DB; snapshots, Idempotency-Key, expectedItems, order history + FE | Giỏ 2 Store → 1 order + 2 seller_orders; replay không tăng đơn; lỗi giá/kho có mã rõ, không thanh toán online |
| TV4 | Inventory chống oversell + idempotent reserve/consume/release, fulfillment SellerOrder, cancel/refuse/hoàn kho, COD paid manual sau DELIVERED, FE thao tác Seller/Admin liên quan | Checkout không bán âm; retry không trừ/hoàn hai lần; seller không xem đơn store khác; COD paid đúng điều kiện |
| TV5 | Contract tests checkout→COD, smoke test phối hợp, tối thiểu bộ automated happy-path + negative QA; P1 review/finance khi P0 đã ổn | Mỗi lỗi có severity/owner; test luồng 2 Store; nếu đủ thời gian review sau delivered/finance báo cáo đơn giản |

**D10 gate:** demo nội bộ giỏ 2 Store tạo COD Order + SellerOrders qua backend thật (UI có thể chưa đủ đẹp). **D14 freeze tính năng P0**: các chức năng P0 đã có trên branch/PR và được review theo phạm vi; chỉ sửa blocker/tích hợp trong tuần 3. Nếu chậm, cắt P1 trước.

## 6. Tuần 3 — D15–D21: tích hợp, hồi quy và nghiệm thu

| TV | Nhiệm vụ tuần 3 | Bằng chứng |
|---|---|---|
| TV1 | Merge có review, chạy FE/BE/DB theo README; fix Auth/Gateway/CI/route, kiểm tra quyền mặc định và startup từ clone sạch | Docker/API health, build, UI screenshots và hướng dẫn setup sạch |
| TV2 | Fix luồng Seller→Store→Product→Category, kiểm thử seller A/B, moderation, slug/SKU, Product-Category restrictive FK, hỗ trợ seed cho checkout | API tests, data seed 2 Store, ảnh UI/admin/seller |
| TV3 | Fix Cart→Checkout→Order (2 Store), retry/idempotency/giá thay đổi, order history; check DB snapshots | Test cùng key/different payload, 1 order+2 seller_orders và totals |
| TV4 | Fix concurrency/oversell, release/consume, seller order state, COD paid once, notification P1 nếu đủ | Test 2 người đặt hàng đồng thời và retry; COD không ghi lần hai |
| TV5 | Điều phối UAT/E2E thật, test đối nghịch quyền/cancel/replay, thống kê P0/P1, bug triage, checklist release và tài liệu | Ma trận requirement→test→evidence, PASS/FAIL/NOT_RUN minh bạch, demo script |

**D17:** E2E đầu tiên toàn bộ qua UI. **D19:** không còn blocker P0, nghiệm thu hồi quy. **D20–D21:** chạy từ clone sạch, seed, ghi video/ảnh demo, tag release sau review. Không tự tuyên bố production-ready.

## 7. Kịch bản nghiệm thu bắt buộc (UAT COD-only)

Thiết lập: Admin A, Seller S1/S2 đã APPROVED với Store1/Store2, Customer C; sản phẩm P1/P2 ACTIVE, có stock thực.
1. C đăng nhập, xem public catalog, tìm kiếm/lọc, mở Product; không nhìn thấy DRAFT/REJECTED.
2. C thêm P1 (Store1), P2 (Store2) vào Cart; thấy giá/tổng COD rõ ràng.
3. C checkout bằng Idempotency-Key và expectedItems → HTTP success, đúng **1 MarketplaceOrder + 2 SellerOrder**; snapshot từng line và tổng, tồn kho trừ/giữ đúng **một lần**.
4. Gọi lại cùng key/cùng body trả kết quả cũ; cùng key/khác body bị từ chối; stock không đổi lần hai.
5. S1 chỉ thấy SellerOrder của Store1, S2 chỉ thấy của Store2; Customer khác không xem đơn C.
6. Seller cập nhật trạng thái theo phép, C xem lịch sử; sau DELIVERED, actor có quyền xác nhận COD paid một lần (chỉ ghi nhận thủ công, không nhận tiền online).
7. Hủy/từ chối theo policy đã chốt → hoàn kho chính xác một lần; gọi lặp không hoàn hai lần.
8. Admin có thể duyệt/từ chối Seller/Product; người không có quyền bị chặn ở backend; upload không hợp lệ bị chặn.
9. Không restart làm mất dữ liệu Postgres, khởi động lại theo README thực tế; toàn bộ UAT sử dụng Gateway+service+DB thật.

**P1 riêng**: thông báo đơn; review sau DELIVERED không trùng; dashboard thu COD/phí sàn đơn giản. P1 không được tính PASS của P0 khi chưa được kiểm chứng.

## 8. Quality gates và quy tắc bàn giao

- **Per task:** code trong module owner, test + kết quả thực thi, OpenAPI/contract/DTO/migration cập nhật, commit nhỏ → push feature branch → PR → reviewer; chỉ tích [x] nếu tiêu chí có bằng chứng.
- **Security:** JWT verified; ownership backend, không tin storeId/customerId/role từ client; mặc định từ chối endpoint chưa được bảo vệ; không log secret/PII.
- **Database:** migration Flyway chỉ tiến lên, DB per service, FK nội bộ DB; tiền BigDecimal/numeric, không double; stock không âm; unique SKU/idempotency, unique review theo (order, product) và settlement theo SellerOrder. Owner chốt order identity trước migration; precheck trong bộ nhớ không thay thế unique DB/transaction chống race.
- **Transaction/timeout:** Category REQUIRES_NEW cần review khi phối hợp Product; Checkout/Inventory có bù trừ và retry, không transaction phân tán tùy tiện; test cạnh tranh và replay.
- **Release blocker (P0):** luồng COD 2 Store hỏng, đặt trùng, bán âm, xem/sửa dữ liệu người khác, unverified JWT vượt quyền, leak secret, không chạy được FE→Gateway→DB, lỗi mất dữ liệu.
- **P1 không đạt:** ghi PARTIAL và để backlog; không coi toàn bộ sản phẩm thất bại nếu UAT P0 đạt.
- **Test trạng thái:** PASS / FAIL / NOT_RUN (+ reason). Unit mock không được gọi là integration thật; báo cáo CI chỉ khi workflow chạy.
- **Definition of Done:** PR có review + merge main, acceptance test có log/ảnh, không còn P0 blockers, documented startup từ clone sạch; Issue tuần chỉ đóng khi toàn bộ checklist bắt buộc của tuần đạt.
- **Hiện trạng baseline (17/10/2026):** #7 đã merge Category/Seller domain vào main; #25 đã merge vào nhánh TV2; #29 là PR persistence vào main. Review/Finance rules và QA từ #20 cũng đã vào main. Seller HTTP/JWT/Gateway và các tích hợp còn thiếu phải được ghi theo từng PR/evidence, không suy từ domain hoặc DB tests thành nghiệm thu API.

## 9. Kiểm tra tính khả thi của kế hoạch (pre-flight review)

| Rủi ro | Biện pháp thiết kế | Quyết định |
|---|---|---|
| W1 thiên về test trong khi code chưa tồn tại | Thêm deliverable code/API/contract theo owner vào Issue W1, giữ lịch sử cũ | Đã xử lý trong kế hoạch |
| TV3/4 chờ TV2 và TV1 quá lâu | Freeze DTO D2, stub adapter, seed; gate demo thật D10 | Giảm blocker, cần cả nhóm thực thi |
| 5 người/21 ngày quá tải | P0 tối thiểu + P1 cắt nếu chậm, ưu tiên E2E từ D10 | Khả thi có điều kiện, không đảm bảo đúng hạn |
| Service phát triển riêng không tích hợp | Contract owner/consumer, CI, 2 Store UAT, daily PR | Kiểm soát được bằng gate |
| Auth/ACL/stock/COD sai | Security deny-default, replay/concurrency/negative tests | Không được cắt |
| Code TV2 đã merge qua #7 và persistence đang bàn giao qua #29 | Bảo toàn Issue #2 checkbox + commit/PR/evidence, review phần bổ sung đúng scope | Không làm lại |
| Phạm vi khác báo cáo V2 | COD-only & bỏ tính năng nâng cao do chủ dự án yêu cầu; owner ký chốt thay đổi contract | Cần nhóm xác nhận |
| Bảo vệ main và CI chưa rõ | Kiểm tra ruleset enforcement, bổ sung CI, review PR | Không giả định đã bảo vệ/CI PASS |

**Đánh giá:** Nếu toàn bộ **P0** và UAT đạt với API/UI/DB thật, đủ tạo ra marketplace COD cơ bản **dùng thử được**, không chỉ demo tĩnh. Không cam kết tương đương sàn sản xuất quy mô lớn. Nếu thiếu P0 hoặc không có tích hợp E2E, không được gọi là hoàn chỉnh.

## 10. Cách quản lý GitHub từ nay

- Giữ 5 Issue W1 #1–#5, nguyên các mục đã làm, bằng chứng lịch sử; bổ sung checklist Implementation/Contract/Verification ở đầu.
- Tạo 5 Issue W2, 5 Issue W3, đúng owner và Acceptance Criteria, liên kết các Issue tương ứng.
- Các PR tính năng liên kết đúng Issue bằng "Refs #N"; chỉ "Closes #N" khi đáp ứng toàn bộ tiêu chí.
- Không push/merge trực tiếp main; workflow commit nhỏ, push nhánh cá nhân, Draft PR khi chưa sẵn sàng, review ≥1 người khác.
- PR #7 đã merge; giữ lịch sử review/commit và theo dõi bàn giao persistence vào main qua #29. PR tài liệu kế hoạch không thay thế PR tính năng hoặc tự hoàn thành Issue #2.
- Mỗi ngày cập nhật blocker, commit/PR link, PASS/FAIL/NOT_RUN. Ngày D7/D14/D21 đánh giá gate chung.
