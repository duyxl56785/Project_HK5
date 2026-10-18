# M01 — gói lựa chọn để con người/owners xác nhận

**TV5-M01-DECISION-v1, 18/10/2026 — PENDING, chưa có business approval.**
Refs #5/#12/#28/#37/#38/#39/#43. Đây là bảng quyết định đồng hành với
[TV5-FIN-v0.1](TV5_FINANCE_CONTRACT_SIGNOFF.md), không tự thay thế hay ký duyệt contract đó.
Mọi lựa chọn bên dưới là candidate; ô chưa có xác nhận giữ trống. Không bật Finance flag từ tài liệu này.

## 1. Evidence đã đọc trước khi chuẩn bị lựa chọn

- [TV2 candidate v0.2 + Product-side compatibility](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246199255).
- [TV3 technical consumer response](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246255122).
- [TV2 provider/freshness disposition](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246597564), source docs42cc4e7.
- [TV2 current facts/recipient/suspended policy chưa sẵn sàng](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246611924).
- [TV3 unwired storage utility / FIN-01/FIN-06 gate](https://github.com/duyxl56785/Project_HK5/pull/28#issuecomment-6246631924), sourceee15851.
- [TV4 state/COD/retry proposal](https://github.com/duyxl56785/Project_HK5/pull/39#issuecomment-6246602587), docsec2ae5a.

TV2 chấp nhận tương thích kỹ thuật từ phía Product; TV3 chấp nhận candidate để tiếp tục sign-off.
Hai phản hồi này không ký thay TV5 human/business owner, TV1 hoặc các consumer khác. Không có
quyết định M01 đầy đủ được APPROVED. Người có thẩm quyền quyết định nghiệp vụ cần được chủ dự án
xác định rõ bằng tài khoản/vai trò, không tự suy rằng người tạo PR có toàn quyền ký policy.

## 2. Các quyết định phải trả lời cùng phiên bản

| ID / PENDING | Lựa chọn cụ thể để xác nhận hoặc REVISE | Người chịu trách nhiệm / cần phản hồi | Ảnh hưởng khi được chốt |
|---|---|---|---|
| MD01 currency | **Candidate A:** VND duy nhất, code VND hiện diện ở provider/Cart/Order/SellerOrder; không default/FX. **B:** nếu cần currency khác hoặc phần lẻ, ghi rõ code + allowed units/scale và sửa cùng phiên bản trước wire/schema. | TV5 human + business decision maker; TV2/TV3/TV4 representation/COD | Product currency source, Cart V2+ currency, Order/SellerOrder/payment/settlement currency; không dùng label fixture làm dữ liệu thật. |
| MD02 unit price/wire | A: giá Product nguyên dương1..99,999,999,999,999,999 VND; UNNECESSARY để reject phần lẻ, không round. Plain decimal JSON string1–17digits, không dấu/exponent/leading zero; BigDecimal parse/compare chính xác. B: chỉ sau sửa MD01 và wire fractional canonical cụ thể. | TV5/business +TV2 provider +TV3 consumer; TV1 validation/privacy | Không JSON number17digits qua JS; review catalog.checkout.v1 candidate v0.2; Account/JWT.sub không bị đổi thành UUID. |
| MD03 quantity/line/Store/parent bounds | A: mỗi unit, exact unit×quantity, sum dòng/Store và sum Store/parent không vượt99,999,999,999,999,999 VND. Đồng thời phải fit schema thực, không chỉ từng Store. Checkout hiện quantity positive int (max2,147,483,647); Inventory positive long không cho phép consumer tự mở rộng wire. Business quantity cap thấp hơn vẫn cần owner chọn. | TV3 orchestration +TV5 policy, TV2 price, TV4 qty consumer | Validate toàn bộ trước reserveRequested/Inventory/write; V2+ constraint/DTO nếu cần. Utility NUMERIC19,2 chỉ kiểm kỹ thuật, chưa áp cap VND hay được wire. |
| MD04 commission/House/rounding | Giữ yêu cầu5% ordinary Store/0% House; candidate rate snapshot0.0500/0.0000 DECIMAL(5,4). House từ trusted persisted classification/privileged assignment, không slug/name/JWT/body. Candidate HALF_UP(scale0) **một lần mỗi SellerOrder merchandise subtotal**, net=subtotal−commission, không pool parent hoặc cộng fee đã round từng dòng. | TV5 human/business, TV2 House source, TV3 snapshot/grouping, TV4 COD | One SellerOrder/authoritative Store; consistent Seller/House facts; rate/House/fee/net snapshots và policyVersion bất biến. |
| MD05 shipping/tax/discount | Candidate initial slice chỉ hỗ trợ đơn không cần adjustment chưa được duyệt; commission base là merchandise subtotal. Nếu muốn hỗ trợ shipping/tax/discount, phải chỉ rõ bên hưởng/chịu từng khoản, allocation từng Store/line, rounding remainder và tác động commission/net/COD trước triển khai. Không mặc định mọi phí bằng0 hoặc trừ discount ở Store khác. | Business owner +TV5 +TV3 totals +TV4 COD/refund; TV2 price semantics | Không thể derive final payable từ gross/fee hiện tại khi adjustments chưa có model. Reject/hold flow cần adjustment chưa thống nhất thay vì settlement sai. |
| MD06 COD/refund/reversal | ADMIN hiện tại xác nhận COD sau DELIVERED một lần, delivery/payment riêng. Candidate Order DB là source delivery/payment, TV4 rules/TV3 local transaction+outbox. Chỉ settleDELIVERED+CODPAID từ approved snapshots. Partial payment/refund/return sau settle cần reversal policy và actor/source riêng, không sửa original fee/net hoặc đoán paid từ Inventory. | TV5/business +TV4 +TV3; TV1 ADMIN/current auth; TV2 Store scope | Define actionId/payload binding, paid_by/paid_at/currency/amount, unique SellerOrder settlement+atomic eligibility/insert; không làm production trước ký. |
| MD07 freshness/race | **A (TV2 khuyến nghị cho MVP, chưa duyệt):** last-verified snapshot, con người chấp nhận race từ verified reads tới Order commit; mandatory current authorization vẫn giữ. Revalidation PhaseB chỉ sau durable journal và safe confirmed-reservation recovery; UNKNOWN hold. **B:** nếu cần commit-time guarantee, phải thiết kế quote/lease cross-source riêng; hiện provider không có. | Business owner +TV5 +TV1; TV2 source +TV3/TV4 recovery | Hai lần đọc/timestamp/hash không phải lock hoặc lease. Không tự đặt TTL/token, không hứa frozen Seller/Store/House/Category bằng Product @Version. |
| MD08 snapshot/version identity | Candidate immutable unitPrice/qty/lineTotal, productId/inventorySku, Store/Seller, currency, subtotal, rate, commission, seller_net, House classification, policyVersion và evidence/reference tới provider facts; wire metadata/version phải được owners định nghĩa, không thêm field ngầm. | TV5 +TV3 persistence, TV2 facts, TV4 lifecycle/COD, TV1 exposure | Reviewed V2+ schema/DTO; no cross-DB FK; no recompute successful replay/settlement bằng Catalog/Store policy mới. |
| MD09 Finance enable authority | Chỉ người vận hành/config được TV1 chỉ định áp flag sau record signatures đúng phiên bản, compatible sources/snapshots/schema/security và evidence. Người ký business và người deploy là vai trò phải ghi rõ. | TV1 + business owner +TV5; TV2/TV3/TV4 xác nhận dependency của mình | HK5_FINANCE_POLICY_APPROVED=false tiếp tục giữ; một env variable/test fixture không thay authorization. |
| MD10 reporting groups/time cutoff | Bốn nhóm doanh thu chưa có đủ tên/công thức/source/cutoff/timezone và chính sách refund. Yêu cầu owner điền cụ thể, không suy từ DELIVERED/PAID guard để dựng dashboard. | TV5/business +TV3/TV4 facts | Reporting SQL/aggregate chỉ sau definition, Store scope và finance schema; hiện BLOCKED. |

## 3. Tách storage fact khỏi business candidate

| Stage | Schema/helper thực tế | Candidate A nếu được ký | Bắt buộc trước RESERVE |
|---|---|---|---|
| unitPrice | Cart/Order NUMERIC(19,2), Catalog prototype có thể giữ precision cao | VND nguyên dương<=99,999,999,999,999,999, lưu exact.00 | Trusted currency/price, no fractional rounding, storage fit |
| lineTotal | BigDecimal multiplication không có precision ceiling | unitPrice×positive quantity, không round, cùng cap VND | Check phép nhân và bound, không chỉ đầu vào |
| Store subtotal | Sum exact lines, gross NUMERIC(19,2) | Sum mỗi Store, cùng cap; consistent Seller/House | No mixed Store facts/currency, cap tổng |
| parent total | Sum SellerOrders, orders.total NUMERIC(19,2) | Sum Store, cùng cap; adjustments chỉ khi approved | Cap parent, không coi từng Store hợp lệ là đủ |
| commission/net | Fee HALF_UP theo scale fixture; DB chỉ giữ gross/fee, net chưa snapshot | 5%/House0, HALF_UP0 mỗi Store, net=subtotal−fee | Range/scale/policyVersion+snapshot completeness; fee/net>=0 |

NUMERIC(19,2) cho phép tối đa99,999,999,999,999,999.99 về mặt lưu trữ. Candidate A nguyên VND
chọn max99,999,999,999,999,999 (khác .99). `OrderAmountStorageBounds` chỉ reject mất precision,
âm và vượt storage max; không chọn currency/scale0, không tự cung cấp source authority và chưa được gọi
từ Checkout. Không sửa V1; bất cứ schema/DTO/currency check mới nào đi qua V2+ và owner review.

## 4. Ví dụ số cho người quyết định

Các ví dụ **candidate A**, không phải production acceptance hoặc test E2E đã đạt:

| Tình huống | Candidate result | Quyết định cần hiểu |
|---|---|---|
| Ordinary Store subtotal9 /10 /11 | commission0 /1 /1; net9 /9 /10 | HALF_UP0 tại half-minor-unit, không round subtotal |
| Hai dòng10 trong **một** Store | subtotal20, fee1, net19 | Không cộng hai line fees1+1 |
| Hai ordinary Store mỗi Store10 | parent20, fees1+1=2, nets9+9=18 | Không pool parent để chỉ tính fee1 |
| Ordinary100000 +House200000 | parent300000, fees5000+0, nets95000+200000 | House fact phải authoritative và snapshot lúc placement |
| Hai Store mỗi Store60000000000000000 | Từng Store fit, parent120000000000000000 phải reject trước reserve | FIN-06 hiện còn RED; DB rollback sau reserve chưa đáp ứng |
| unit0.01×qty2,147,483,647 | BigDecimal=21474836.47 nhưng **không hợp lệ theo VND nguyên candidate A** | Exact Java math không đồng nghĩa business/storage acceptance |
| unit1.001 +2.001, helper fixture scale3 | Initial3.002→DB/replay3.00 hiện tại | FIN-01 RED; không hợp thức hóa mất precision bằng changing assertion |
| Shipping15000/tax5000/discount10000 trên merchandise100000 | **Chưa tính final fee/net/payable** nếu allocation chưa được chọn | Không tự chọn ai chịu discount/thu shipping/tax; MD05 bắt buộc ký |

## 5. Immutable snapshots và consumer/schema impacts

| Snapshot / evidence | Hiện trạng PR28 | Owner action sau chữ ký |
|---|---|---|
| product/unit/qty/lineTotal | OrderItem có productId/qty/unitPrice/lineTotal, scale2; thiếu currency/SKU source | TV2 authoritative Product→SKU/currency, TV3 DTO+V2+ journal/snapshot; giữ exact |
| Store/Seller/House | SellerOrder Store/Seller refs; House chưa được snapshot/provider hiện chưa có | TV2 S01/current facts+privileged House; TV3 persist immutable classification/version theo agreed contract |
| subtotal/commission rate/amount/net | gross/fee có DB; rate/net/policyVersion thiếu | TV3 shared schema phối hợp TV5; rate và result lúc placement, net không recompute từ policy hiện tại |
| COD/delivery/payment audit | Chưa có runtime COD PAID/DELIVERED trong scaffold này | TV4 rules+TV3 authoritative transaction/event; TV1 current ADMIN verification |
| settlement identity/replay | Chưa có atomic unique settlement/query production | TV5 thiết kế sau approval; unique sellerOrderId +atomic eligibility/create, stable replay result, Store-scoped queries |
| successful Checkout replay | Order read từ committed DB, không repricing; nhưng currentscale loss vẫn có | TV3 giữ immutable outcome, fix exact storage trước reserve; không chạy lại Catalog/House/rate mới |

Nguồn sự thật của House/Store/current status không có thì phải fail-closed. `FOUND/ABSENT/UNAVAILABLE`
là kiểu trả về của internal source, không tự verify current identity/freshness hoặc tạo persistence.
11 Product SQL prototype cases và các HTTP403 denial không chứng minh Store/Product production source.

## 6. Người ký điền lựa chọn, không để agent ký hộ

| Vai trò / account | Phần phải xác nhận | Version +choices +APPROVED/REVISE/PENDING +link |
|---|---|---|
| Human TV5 Nguyễn Minh Duy +business decision maker được chỉ định | MD01–MD10 trong phạm vi tài chính/rủi ro; ai có quyền ký business | PENDING — chưa có chữ ký |
| TV2 duyxl56785 | Price/currency/wire/bounds, S01/Store/House/SKU/freshness source | Technical compatibility đã ghi nhận, final/version choices PENDING |
| TV3 tranvanchinhbg2006-alt | Cart/Order DTO V2+, unit/line/Store/parent checks, snapshots/replay/journal/recovery | Conditional technical proposal, final PENDING |
| TV4 phamthecuong2022006-alt | Qty/SKU/lifecycle, delivery/COD/payment/refund/recovery cutoff | Proposal, final PENDING |
| TV1 lexuanbao258-collab | Store boundary/current/delegated auth, risk/privacy/pair/enable authority | OD-07/09 baseline DECIDED; concrete pair/decision PENDING |

Reply mẫu: `TV5-M01-DECISION-v1 + TV5-FIN-v0.1; MD01=A, MD02=A, ...;
scope=...; APPROVED|REVISE|PENDING; evidence=...; remaining=...`.
Một signer chỉ ký phần trách nhiệm của mình; whole M01 chỉ APPROVED khi cùng revision/choices và đủ
mọi bên bắt buộc. Bất đồng MD05/MD07/MD10 không được che bằng defaults hoặc một ô Approve PR.

## 7. Từ diagnostic RED sang GREEN

- [ ] Đủ applicable approvals và TV3 cho phép/triển khai preflight đúng ownership.
- [ ] Wire storage +approved business checks trước reserveRequested; no Inventory/write/event cho input biết sai.
- [ ] Preserve original PR43 assertions; không biến UNKNOWN thành blind RELEASE. Known-safe Cart cleanup theo contract.
- [ ] Chạy nguyên diagnostic trên đúng fix SHA:2 failing cases phải GREEN và positive control vẫnPASS.
- [ ] Rerun affected replay/scanner/tagged PG/security regression; live E2E chỉ khi provider/journal/auth thật sẵn sàng.
- [ ] Thống nhất việc đưa diagnostic vào gate chuẩn; normal CI hiện không chạy task này, không tự đóng finding từ CIgreen.

**Hiện tại tất cả ô trên chưa hoàn thành.** Không sửa Checkout/Inventory/Auth, V1, policy flag hoặc
production settlement để tự vượt sign-off. Issue5/12 giữ OPEN/PARTIAL.
