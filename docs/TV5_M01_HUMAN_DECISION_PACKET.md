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
| MD03 quantity/line/Store/parent bounds | A: mỗi unit, exact unit×quantity, sum dòng/Store và sum Store/parent không vượt99,999,999,999,999,999 VND. Đồng thời phải fit schema thực, không chỉ từng Store. Cart API/service/DB và Checkout DTO hiện giới hạn quantity1–1000; Java int max2,147,483,647 chỉ là capacity của kiểu, không phải quantity cap được API chấp nhận. Inventory positive long không cho phép consumer tự mở rộng wire. Đề xuất giữ cap hiện có; business cap khác vẫn cần owner chọn. | TV3 orchestration +TV5 policy, TV2 price, TV4 qty consumer | Validate toàn bộ trước reserveRequested/Inventory/write; V2+ constraint/DTO nếu cần. Utility NUMERIC19,2 chỉ kiểm kỹ thuật, chưa áp cap VND hay được wire. |
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
| unit0.01×qty2,147,483,647 | BigDecimal=21474836.47 nhưng **không hợp lệ theo VND nguyên candidate A** | Exact Java math không đồng nghĩa business/storage acceptance; quantity này cũng vượt API cap1000 hiện có |
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

## TV5 follow-up: authority and source correction (18/10/2026)

TV5 Nguyễn Minh Duy xác nhận chỉ tổng hợp/đề xuất, chưa được xác nhận quyền phê duyệt nghiệp vụ cuối cùng. Người phê duyệt: **chờ TV1/chủ dự án xác nhận**. M01 và MD01–MD10 vẫn PENDING; không bật Finance flag.

Đã sửa mô tả MD03 để phản ánh đúng Cart service/schema và Checkout DTO quantity1–1000; không thay business cap, runtime code, V1 hoặc Inventory long contract. Candidate wire/House/money/freshness và owner matrix được đối chiếu tại [đề xuất TV5 v0.2](../reports/tv5-m01-review/M01_PROPOSAL.md), [ma trận MD01–MD10](../reports/tv5-m01-review/MD01_MD10_MATRIX.md). TV2 provider/TV3 consumer C01/E01 design acceptance vẫn scoped, overall REVISE; không yêu cầu lại DTO shape như chưa phản hồi.

## 8. Ledger hiện hành: phần đã xác nhận và phần cần chữ ký

**Checkpoint `M01-OWNER-LEDGER-20261018-r2` — PENDING.** Đây là cập nhật ngay trong packet hiện có, không tạo contract money/API khác.
Contract được tham chiếu: **`TV5-FIN-v0.1 + TV5-M01-DECISION-v1 @ commit chứa phản hồi`**; `catalog.checkout.v1` candidate v0.2 giữ nguyên.
Các lựa chọn bổ sung ở §9 là đề xuất để trả lời, chưa được accepted value hoặc production authorization.
Một người ký phải nêu MD, choice, scope và commit; approval cho giá không tự bao gồm phí/refund/freshness/deployment.

### Evidence đã đọc, không yêu cầu xác nhận lại phần đã có

- **P2:** [TV2 Product compatibility](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246199255),
  [source v0.2 tại ef62086](https://github.com/duyxl56785/Project_HK5/blob/ef62086048e2c7a00ecee16db0cc3cde2413deb6/docs/TV2_CATALOG_CONTRACT_RESPONSE.md#L14):
  explicit VND, positive integral unit price,17-digit cap, exact JSON string/BigDecimal/no silent rounding tương thích kỹ thuật;
  NUMERIC(19,2) có thể giữ exact .00 sau reviewed schema. **Không phải business signature. Không yêu cầu TV2 chứng minh lại compatibility này.**
- **P3:** [TV3 own-scope C01/E01 acceptance](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6248646211),
  [MD02/03/08 technical necessity](https://github.com/duyxl56785/Project_HK5/pull/28#issuecomment-6248595524).
  C01/E01 consumer design/mapping được TV3 ghi **APPROVED own-scope**, overall REVISE; M01/business values PENDING.
  Giữ exact strings/currency và kiểm mọi total trước reserve làm implementation target có điều kiện; không hỏi lại DTO shape như chưa có trả lời.
- **P4:** [TV4 COD/state/retry proposal](https://github.com/duyxl56785/Project_HK5/pull/39#issuecomment-6246602587),
  [current contract ec2ae5a](https://github.com/duyxl56785/Project_HK5/blob/ec2ae5af5162aa93482b9439d1b39dbb7abb3bec/docs/TV4_FULFILLMENT_IMPLEMENTATION_CONTRACT.md):
  ADMIN COD after DELIVERED, delivery/payment riêng, settle DELIVERED+PAID, immutable snapshots/unique atomic/Store scope;
  vẫn proposal, chưa đủ state/event/refund/source/signatures.
- **P1:** [TV1 review và operation gate](https://github.com/duyxl56785/Project_HK5/pull/28#pullrequestreview-5558607917),
  [TV1 coordination](https://github.com/duyxl56785/Project_HK5/pull/28#issuecomment-6236257587), Auth v1.0 đã merge qua PR24.
  **OD-07 (ADMIN COD sau DELIVERED/idempotent/Store isolation) và OD-09 (pair credential/allowlist/delegation) là baseline DECIDED**, giữ nguyên, không xin duyệt lại nguyên tắc.
  JWT fix được ghi nhận; **chưa có quyết định chỉ định business approver/operator hoặc approved concrete M01 service pairs** trong nguồn đã audit.
- **P5:** Người dùng Nguyễn Minh Duy/TV5 xác nhận trong phiên làm việc chỉ tổng hợp/đề xuất, chưa có final business authority;
  [ghi nhận công khai](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6248973390).
  Người phê duyệt nghiệp vụ: **chờ TV1/chủ dự án xác nhận**. Comment của agent ghi nhận instruction này, không phải chữ ký nghiệp vụ của con người.
- Source checkpoint: PR28 **5a3788b34ec4a2a91611d88020442a27b9dc7948**, PR37 **ef62086048e2c7a00ecee16db0cc3cde2413deb6**,
  PR38 **7a8d18eb54c3d134964a282ddd7612283dcea66c**, PR39 **ec2ae5af5162aa93482b9439d1b39dbb7abb3bec**.
  PR43/44/45 đã được owner merge; nhận QA patch không ký M01. Source Finance/Checkout/diagnostic không đổi so với các run đã lưu.

### MD ledger — version chung phía trên áp dụng cho từng dòng

| Decision | Proposed / accepted value | Accountable owner / người xác nhận | Evidence xác nhận thực tế | Status / effective scope | Missing dependencies / module được phép sau khi ký |
|---|---|---|---|---|---|
| **MD01** | Proposed VND duy nhất, explicit code, no default/FX | TV5 đề xuất +business approver; TV2 provider, TV3/TV4 consumer | P2 compatible; P3 explicit-currency design. Chưa business signer | **PENDING**; compatibility được ghi nhận, chưa accepted business currency | Business chọn A/B. Product price/currency slice ở §10; không cần yêu cầu TV2 lặp compatibility |
| **MD02** | Proposed positive integral1..99,999,999,999,999,999; BigDecimal; canonical JSON string; calculation scale0, storage19,2 exact .00 nếu được duyệt | Business approver +TV5 money; TV2 schema/API, TV3 consumer | P2/P3; Product prototype high precision không phải yêu cầu giá phân số | **PENDING** business cap/scale; wire design có own-scope acceptance | Explicit price-slice signature +reviewed V2+/S01 applicable gates; Product validation/persistence rồi re-test production schema |
| **MD03** | Proposed cap17digits cho unit/line/Store/parent/commission/net; exact sums, qty1–1000 hiện có; fee/net0..subtotal | TV3 preflight +TV5/business caps; TV2 price, TV4 qty | P3 đã xác nhận technical necessity; current DTO/service/schema1–1000; utility chưa wire | **PENDING** business caps; technical quantity fact không phải intmax | All storage+business bounds trước RESERVE; Cart/Order V2+/preflight sau applicable approvals. Check fee/net/rate không bỏ qua parent |
| **MD04** | Existing5%/House0/net requirement; proposed HALF_UP0 một lần mỗi SellerOrder merchandise subtotal, rate0.0500/0.0000 snapshot | Business approver +human TV5; TV2 House, TV1 assignment permission, TV3 snapshot, TV4 COD | FinanceRules/CONTRIBUTING giữ rule; P2 trusted House source target; chưa chữ ký rounding/base/House definition | **PENDING** full policy; agent không ký human TV5 | House authoritative persistent classification/audit, policy/config binding; không infer từ slug/JWT/body hoặc unknown→regular |
| **MD05** | Proposed A restricted no-unapproved-adjustment flow; B explicit Store/line allocation theo lựa chọn §9 | Business approver +TV5; TV3 totals, TV4 COD/refund, TV2 semantics | Packet/proposal; P3 giữ decision cho đúng owner, chưa sign-off allocation | **PENDING**, A/B chưa chọn | Chỉ totals/fee/COD cần adjustment mới phụ thuộc lựa chọn này; không tự đặt mọi khoản0 |
| **MD06** | Existing DELIVERED AND COD PAID; proposed full-COD-only/reversal alternatives §9 | TV4 lifecycle +TV3 local transaction/outbox +TV5 Finance; TV1 ADMIN; business approver | P1 OD-07 nguyên tắc DECIDED; P4 proposal phù hợp, chưa ký producer/transaction/refund model | **PENDING** runtime/refund/source; giữ OD-07, helper guard không là atomic settlement | Separate payment facts/idempotency/unique settlement/current Store scope; production implementation chờ chữ ký và phase scope |
| **MD07** | Proposed A conditional last-verified snapshot; B real cross-source quote/lease | Business approver +TV1 risk/security +human TV5; TV2 facts, TV3/TV4 journal | P2/P3 khuyến nghị A có điều kiện; chưa risk acceptance | **PENDING**, không có quote/lease guarantee | PhaseA full current verified batch trước reserve; PhaseB sau durable journal/proven-reserve/recovery; UNKNOWN hold |
| **MD08** | Proposed immutable unit/qty/line/IDs/SKU/currency/Store/Seller/House/subtotal/rate/fee/net/policy +provider fact refs | TV3 shared persistence +TV5 Finance; TV2 facts, TV4 lifecycle, TV1 exposure | P3 đồng ý nhu cầu V2+ snapshots, không ký field/version/source hoàn chỉnh | **PENDING** final snapshot contract | Reviewed V2+, no V1 edits, replay/settlement dùng committed snapshots; không recompute bằng policy/catalog hiện tại |
| **MD09** | Proposed named business signer và TV1-designated config operator; đủ gates mới enable | TV1/chủ dự án authority; human TV5 +business và TV2–TV4 own-scope | P1 gate yêu cầu approval; P5 TV5 chưa có final authority. Không có designation được tìm thấy | **PENDING**, source flag vẫn false | TV1 chỉ định tài khoản/vai trò/phạm vi; operational security/implementation/evidence và signed config; không tự enable |
| **MD10** | Proposed reporting choices §9: basis/timezone/cutoff; bốn nhóm cần owner điền tên/formula/source | Business approver +TV5 reporting; TV3/TV4 facts, TV1 shared time/privacy nếu ảnh hưởng | Packet xác nhận thiếu định nghĩa; không tìm thấy đủ4nhóm để tự gán tên | **PENDING / reporting BLOCKED** | Reporting-specific gate; không tự biến thiếu dashboard definition thành blocker cho Product price slice đã được ký riêng |

Không có MD mới được APPROVED sau audit này. Không có giá trị "cuối cùng" về currency/scale/rounding được agent ký hộ.
Mỗi approver tương lai được ghi thêm đúng account/comment/version/choice/scope ở đây; không thay proposal bằng APPROVED từ testPASS, code merge hoặc agent-authored comment.

## 9. Lựa chọn MD05–MD10 để con người trả lời ngay

### MD05 — shipping, tax, discount và allocation

| Choice / PENDING | Candidate cụ thể | Ưu điểm | Rủi ro / tác động Product–Cart–Order–Finance |
|---|---|---|---|
| **MD05=A — khuyến nghị slice đầu, cần ký** | Chỉ nhận flow có explicit confirmation không cần shipping/tax/discount adjustment chưa được duyệt. Commission base=merchandise subtotal. Flow đòi adjustment phải reject/hold theo contract; không default absent amount thành0. | Hạn chế ambiguity fee/net/COD; Product price slice vẫn độc lập | Không phục vụ flow có phí/khuyến mại thực; business phải chấp nhận giới hạn. Cart/Order validation phải rõ; Finance chưa được derive final payable cho flow ngoài scope |
| **MD05=B — cần allocation đầy đủ** | Business chọn seller-funded hay platform-funded discount; nêu shipping/tax beneficiary/payer, fee base trước/sau discount. Amount đầu vào integralVND theo MD01/02; total adjustment bảo toàn bằng allocation perStore/perLine +deterministic remainder. Chốt tie-break/version trước triển khai | Hỗ trợ adjustments và đối soát perStore | Tăng snapshot/DTO/migration và test; thiếu một lựa chọn không có final payable/commission/net hợp lệ. Không tự chọn prorata/tie-break hoặc chuyển khoản của StoreA sangB |

**Owner reply:** business+TV5 chọn A/B và allowed flow; TV3/TV4 xác nhận totals/COD impact. Ví dụ merchandise100000/shipping15000/tax5000/discount10000 chưa có final fee/net cho tới khi B allocation được ký.

### MD06 — COD, refund, return, reversal và settlement

| Choice / PENDING | Candidate cụ thể | Ưu điểm | Rủi ro / tác động |
|---|---|---|---|
| **MD06=A — khuyến nghị slice đầu, cần ký** | Full COD only; verified current ADMIN xác nhận sauDELIVERED, amount/currency phải khớp approved payable snapshot, stable actionId/payload/audit. Unique atomic settlement theoSellerOrder chỉ khiDELIVERED+PAID. Partial payment/refund/return cần policy chưa ký thì giữ blocked/manual resolution, không tự sửa original amounts | Payment/delivery/stock tách rõ; không tạo double settlement | Business phải chấp nhận giới hạn xử lý returns/disputes; owner phải chọn hold/cutoff và người xử lý, không tự đặt default. TV3 authoritative local transaction/outbox+TV4 rules chưa implemented |
| **MD06=B — hỗ trợ adjustments sau placement/settlement** | Approved reversal/adjustment record riêng: originalSellerOrder/settlement reference, amount/currency, reason, actor/time, stable action ID và ordering. Business chọn full/partial refund eligibility, fee reversal, return window, payer và period treatment | Audit được điều chỉnh, không mutate snapshot gốc | Nhiều state/event/API/schema mới; cần TV3/TV4/TV5 cùng chốt. Goods return sau dispatch phải inspected INCREASE theo lifecycle, không dùng RELEASE hoặc CONSUME làm paid proof |

House không bypass payment/Store authorization. Store closed/Seller suspended của order cũ vẫn cần TV1/TV2/TV4/business quyết định riêng. Product/Catalog không làm payment source.

### MD07 — freshness và race

**A khuyến nghị có điều kiện:** last-verified snapshot choMVP, cùng hướng P2/P3 đã ghi nhận. Full verified batch+money checks trướcRESERVE;
observed price/currency/SKU/Store/Seller/House/status/Category change →không place/reprice/subset. PhaseB chỉ sau durable T1/T2 journal+proven reservation+safe recovery.
UNKNOWN giữ exact IDs/payload và reconcile; SUCCEEDED dùng committed snapshots. **Ưu:** phù hợp kiến trúc hiện tại, ít protocol mới.
**Rủi ro:** facts có thể đổi sau last read trướccommit; các reads không cùng global instant. TV1+humanTV5+named business approver phải chấp nhận bằng văn bản; provider/consumer ký khả năng thực hiện.

**B nếu không chấp nhận race:** hoãn liveCheckout, thiết kế quote/lease đa nguồn có covered facts/issuers, expiry/revocation, fencing/redemption/idempotency/recovery.
**Ưu:** có thể thiết kế guarantee cần thiết khi protocol thật được duyệt. **Rủi ro/tác động:** chưa có provider; phải thêm thiết kế/consumer/schema, không tự thêm TTL/hash/@Version để tuyên bố giải quyết.
Product-only price quote không tự khóa Seller/Store/House/Category/current permission hoặc Inventory. Cả A/B đều PENDING.

### MD08 — immutable snapshots và policy identity

**Candidate A, PENDING:** persist đủ snapshot §5 với explicit currency, exact source values và rate/House/policyVersion/fact refs cùng local placement transaction;
schema V2+ theo owner review. Successful replay/settlement trả/đọc cùng committed outcome; policy đổi sau đó chỉ tác động intent/order mới theo policy đã ký.
**Ưu:** reproducible money/audit; không repricing quá khứ. **Rủi ro:** thêm column/DTO/version compatibility và backfill strategy, privacy của fact refs;
không đoán missing legacy currency/House để backfill. Product cung cấp authoritative refs, Cart giữ expectation, Order lưu snapshots, Finance đọc snapshots.
Current gross/fee hoặc SQLderivednet chưa đạt A. TV3/TV2/TV4/TV1 cần chốt exact fields/version trong own-scope; humanTV5/business chấp thuận Finance semantics.

### MD09 — người ký và người bật chính sách

**Candidate A, khuyến nghị/PENDING:** TV1/chủ dự án chỉ định rõ business approver; TV1 chỉ định config/deployment operator riêng, có scope môi trường/action,
kiểm chữ ký cùngrevision/choices +current sources/schema/security +acceptance evidence; lưu change actor/time/reason/policyVersion và rollback-to-closed procedure.
**Ưu:** tách quyết định thương mại và vận hành, truy vết được. **Rủi ro:** thêm bước bàn giao; cần người thật nhận trách nhiệm.

**Candidate B/PENDING:** một người giữ cả hai vai trò chỉ khi TV1/chủ dự án ủy quyền rõ **cả hai scope** bằng văn bản; không suy từ ADMIN/PR author/ownerFinance.
**Ưu:** ít handoff. **Rủi ro:** tập trung quyền và sai config không có independent check; nhóm phải quyết định chấp nhận hay REVISE.
Chưa có named approver/operator cho A/B. Config rate/scale/exemption phải khớp signed policyVersion; source hiện cho nhiều giá trị hợp lệ kỹ thuật, Boolean flag không chứng minh approval.
Mọi branch/runtime giữ `HK5_FINANCE_POLICY_APPROVED=false` cho đến khi đủ gates. Không viết credentials vào approval record/log.

### MD10 — reporting basis, timezone và cutoff

| Choice / PENDING | Candidate cụ thể | Ưu điểm | Rủi ro / tác động |
|---|---|---|---|
| **MD10=A — khuyến nghị, cần ký** | Báo cáo Finance dựa trên unique committed settlement postings và approved adjustments; lưu instantUTC, proposed reporting timezoneAsia/Ho_Chi_Minh; kỳ ngày local [00:00,next00:00), query đổi chính xác sangUTC; adjustment ghi theo posting-time riêng, không sửa original postings | Dedupe/audit rõ, tách số đã settle khỏi eligibility | Chậm hơn delivered/paid; business phải xác nhận basis/timezone/cutoff/late corrections. Đây là candidate, không suy timezone từ máy người dùng |
| **MD10=B — cần định nghĩa riêng** | Báo cáo operational eligibility khiDELIVERED+PAID theo authoritative event time, separate label với settled postings; quyết định late events/refunds/reclassification và kỳ restatement | Có số liệu pending-settlement sớm hơn | Không gọi eligible amount là tiền đã trả/settled; dễ double count nếu join/event không dedupe. TV3/TV4 cung cấp facts/time, TV5 không tự viết dashboard trước sign-off |

**Bốn nhóm doanh thu:** nguồn được audit chưa đủ tên/công thức/source/cutoff của từng nhóm. Không tự đặt bốn tên rồi nhận đó là yêu cầu V2.
Người quyết định nghiệp vụ phải điền **4 entries** dạng `name | formula | authoritative source | recognition event/time | Store/Admin scope | refund/late-event treatment`.
Nếu chưa điền, reporting vẫnBLOCKED; A/B chỉ là candidate cho cách ghi nhận/kỳ, không hoàn thành định nghĩa bốn nhóm.

## 10. Partial sign-off và module được mở khóa

**Hiện chưa có slice nghiệp vụ mới được mở khóa.** Bảng này mô tả điều kiện tương lai, không cấp quyền agent sửa module khác hoặc mở live API.

| Slice | Chữ ký/điều kiện tối thiểu | Được triển khai sau khi đạt, trong ownership | Chưa được mở bởi slice đó |
|---|---|---|---|
| **Product price/currency** | Named business approver +humanTV5 ký MD01/MD02 và unit-cap củaMD03 cụ thể; ghi nhận compatibility P2/P3 đã có. TV2/TV1 phê duyệt applicable Product/Store schema/S01/auth boundaries | TV2 reviewed price/currency V2+ constraint/JPA/DTO +production PG/validation tests; exact JSON strings theo agreed wire | Không tự mở Catalog HTTP/provider hoặc House assignment/Checkout/Finance. MD05/06/10 reporting/refund không tự là prerequisite cho riêng price column nếu slice approval loại chúng rõ |
| **Cart/Order money preflight** | TV3 own-scope accepted necessity; đủ approved representation/storage/business caps, fee decisions áp dụng, authoritative facts/security và explicit implementation scope | TV3 bounds unit×qty/line/Store/fee/net/parent trướcRESERVE, reviewed Cart/Order V2+; chạy nguyên FIN-01/06 diagnostics | Không tự bật policy/adapter, sửa assertion hoặc hứalive E2E. Utility pass chưa wire không đạt |
| **Snapshots/Checkout live** | MD04/05/07/08 áp dụng +S01/C01/E01/Security +trusted providers; Inventory mapping/journal/lifecycle/recovery ký và implemented | TV2 sources; TV3 consumer/journal/order snapshots; TV4 lifecycle theo scope đã ký | Không có commit-time guarantee nếu chỉ ký last-verified; không settlement permission |
| **COD/settlement** | MD04/05/06/08/09 cùng source/current ADMIN/Store scope, unique atomic eligibility/create +per-SHA tests, được giao production phase | TV4 COD rules, TV3 persistence/outbox, TV5 Finance theo ownership | Không infer PAID/DELIVERED từInventory; không implement khi chỉ price slice APPROVED |
| **Reporting** | MD10 bốn definitions +approved snapshot/settlement/refund facts, Store scope/timezone/cutoff tests | TV5 queries/reporting trong phase được giao | Reporting approval không đổi giá/fee của order quá khứ hoặc bật deployment flag |

## 11. Phản hồi còn thiếu, checkpoint và cách tiếp tục

Mẫu bắt buộc cho **phần còn thiếu**:

```text
TV5-FIN-v0.1 + TV5-M01-DECISION-v1@<commit> | MDxx=<choice/value> | APPROVED / REVISE / PENDING | SCOPE | COMMENT | EVIDENCE URL
```

- **TV1 lexuanbao258-collab / chủ dự án:** chỉ định business approver account/role và operator/scope, MD09 A/B; S01/security/privacy cụ thể.
- **HumanTV5 +named business approver:** chọn MD01–MD10/partial price slice; TV5 hiện chưa có final business authority, agent không ký thay.
- **TV2 duyxl56785:** price/wire compatibility P2 đã ghi nhận, **không yêu cầu trả lời lại**. Còn authoritative House definition/assignment source và applicable schema/current-provider gates; REVISE chỉ nếu giá trị được chọn khác candidate đã xác nhận.
- **TV3 tranvanchinhbg2006-alt:** C01/E01 shape và technical preflight/snapshot need P3 đã ghi nhận. Còn implementation plan/field-version/allocation/payment transaction/recovery phụ thuộc choices được ký; không xin lại approval DTO giống cũ.
- **TV4 phamthecuong2022006-alt:** own-scope lựa chọn MD06, COD producer/audit/dedupe, return/refund/settlement boundary và C06/journal compatibility còn thiếu; không suy proposal state/retry là approved. Không xin lại nguyên tắc ADMIN COD sauDELIVERED đã DECIDED ở OD-07.

FIN-01/06: latest stored diagnostic ởde1f5c6 là **1PASS/2FAIL**, positive controlPASS; source tại5a3788b tương đương phần Order production/diagnostic đã kiểm.
Lượt docs này unit/PG/liveE2E **NOT_RUN**, không gán lại kết quả lịch sử chohead mới. Chuyển GREEN cần TV3 triển khai exact pre-reserve validation,
no Inventory/writer/outbox cho known-invalid, safe Cart cleanup theo outcome, giữ UNKNOWN hold; rerun nguyên diagnostic+affected PG/replay/scanner trênfixSHA.

- [x] Owner replies/MD01–MD10/partial-scope ledger và lựa chọn có tradeoffs đã chuẩn bị.
- [ ] Named business approver/operator; signatures đúng scope/version và evidence URL.
- [ ] Production price/money/Finance dependencies unlocked bằng approval thật; FIN-01/06 fixGREEN.

Tiếp tục sau khi hết quota: kiểm `git status`/`git branch -vv`, fetch latest28/37/38/39 và đọc replies theo marker `M01-OWNER-LEDGER-20261018-r2`;
so sánh delta trước khi sửa; chỉ cập nhật accepted value/signer/link cho scope được ký. Test runtime chỉ rerun khi source/requirements đổi hoặc gate yêu cầu,
không lặp suite/đẻ thêm proposal để tránh chờ. Commit/push feature branch TV5 đúng PR cònOPEN; reviewer removals phải được giữ.
