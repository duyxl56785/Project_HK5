# Đề xuất M01 của TV5 — để con người xem xét

**Phiên bản:** `TV5-M01-PROPOSAL-v0.2` · **Trạng thái: PENDING**.
**Người tổng hợp/đề xuất:** Nguyễn Minh Duy — TV5.
**Người phê duyệt nghiệp vụ cuối cùng:** **chờ TV1/chủ dự án xác nhận**, theo trả lời trực tiếp của TV5 trong phiên này.
TV5 chưa được xác nhận có quyền phê duyệt nghiệp vụ cuối cùng. Không có chữ ký business approval trong tài liệu này.

Bản này công bố lại phân tích local đã kiểm tra theo yêu cầu mới cho phép commit/push. Phân tích ban đầu chỉ làm tài liệu; regression của đợt bàn giao được ghi riêng trong báo cáo kiểm chứng mới TV5_FINANCE_ACCEPTANCE_2026-10-18.md.

Đây là bổ sung đề nghị sửa/hoàn thiện `TV5-FIN-v0.1` và `TV5-M01-DECISION-v1`, không tự thay đổi các phiên bản đã đăng.
Phạm vi: source/tài liệu hiện tại, đề xuất, ví dụ số, owner và bản phản hồi. Không sửa code, V1 hoặc Finance flag. Việc công bố GitHub tuân theo yêu cầu mới của người dùng, không có contract approval.
[Ma trận đầy đủ MD01–MD10](MD01_MD10_MATRIX.md) · [Bản phản hồi sẵn để gửi](REPLY_DRAFT.md) · [Nguồn và file/dòng](SOURCES.md).

## 1. Đã xác minh từ source và phản hồi mới

| Phần | Bằng chứng hiện tại | Kết luận có giới hạn |
|---|---|---|
| PR28 | Head `21f6d533726533cc49febcc1e977f25bb0270de0`, đã nhận PR43; PR41/42 cũng nằm trong lịch sử. [S20](SOURCES.md#s20) | Merge patch QA không phải policy signature hoặc fix acceptance. Không cập nhật PR đóng. |
| PR37 | Head mới `ef62086048e2c7a00ecee16db0cc3cde2413deb6`; production source và candidate `catalog.checkout.v1` v0.2 không đổi từ42cc4e7. [S11](SOURCES.md#s11), [S28](SOURCES.md#s28) | Delta sáu file chỉ test SQL candidate/tài liệu; chưa có provider Product/Store/House production gọi được. [S23](SOURCES.md#s23) |
| C01/E01 | TV3 **đã chấp nhận thiết kế consumer/mapping trong phạm vi TV3**, overall vẫn REVISE. [S19](SOURCES.md#s19) | Không yêu cầu TV3 duyệt lại shape như chưa phản hồi; vẫn cần TV1/provider/M01/freshness và implementation. |
| Rule phí | FinanceRules dùng `0.05`, House →0, net=subtotal−fee; snapshot HALF_UP theo scale do caller cấp. [S02](SOURCES.md#s02) | Chưa thấy nguồn thay thế rule 5%/House0/net trong các tài liệu/phản hồi đã đọc. Không đồng nghĩa full M01 đã ký. |
| Checkout money | MoneyRules nhận rate/scale/house-exempt qua config; nếu được bật chỉ kiểm rate0..1, scale0..4. [S03](SOURCES.md#s03) | Config có thể khác5%/House0; phải đối chiếu và khóa theo signed policyVersion trước enable. Không sửa rule hoặc bật cờ trong phiên này. |
| Gate | YAML mặc định `HK5_FINANCE_POLICY_APPROVED:false`. [S07](SOURCES.md#s07) | Source default fail-closed; không khẳng định đã kiểm runtime environment triển khai. |
| Quantity | Cart API/service/DB và Checkout DTO đều1–1000. [S15–S17](SOURCES.md#s15) | Packet MD03 cũ chỉ nêu Java int; cần bổ sung giới hạn thực tế1000, không dùng Integer.MAX_VALUE làm business cap. |
| House | StoreProfile chưa có House; ProductAccessPolicy.StoreAccess có boolean houseStore. [S09](SOURCES.md#s09), [S10](SOURCES.md#s10) | Chưa có authoritative House persistence/assignment. Một boolean hoặc role/slug không chứng minh Store là House. |
| Monetary schema | Cart/Order tiền NUMERIC(19,2); SellerOrder mới lưu gross/platform_fee, chưa có currency/rate/House/policy/net snapshot. [S05](SOURCES.md#s05), [S06](SOURCES.md#s06) | SQL derived gross−fee không chứng minh immutable seller_net snapshot, settlement hoặc quyền Store. |
| FIN-01/06 | Utility kiểm storage có nhưng Checkout chưa gọi; reserve trước writer. [S04](SOURCES.md#s04), [S08](SOURCES.md#s08) | Không coi utility PASS là sửa luồng trước reserve. Evidence RED ởc4d517e là lịch sử, không phải test mới của head21f6d53. |

Main đã tiến tới `ac7ca4f`; snapshot GitHub của PR28 báo dirty và TV3 mô tả overlap mới ở docs/QA.md. Đây là conflict QA mới,
khác bản union đã giải quyết trước; không tự merge/resolve trong nhiệm vụ local này. [S20](SOURCES.md#s20)

**Checkpoint PR37 mới trong lúc đối chiếu:** TV2 bổ sung Store26 SQL candidate cases và tăng Product SQL candidate từ11 lên24,
gồm optimistic SQL CAS/timestamps. TV2 báo full run406PASS tại3ecf9c3 (trong đó88PG:38 production Category/Seller +50 SQL candidate),
docs head mới ef62086. Đây là **kết quả do TV2 cung cấp**, TV5 chỉ đọc delta/source, chưa chạy lại hoặc kiểm raw XML của lượt đó.
Không cộng focused26/24 hay historical367/50 vào406; SQL CAS/version không phải lease/JPA runtime/House persistence. [S28](SOURCES.md#s28)

## 2. Đề xuất tiền tệ, kiểu dữ liệu và bounds — cần người có thẩm quyền duyệt

### 2.1 Phương án khuyến nghị để ký M01

**Khuyến nghị VND nguyên cho MVP**, tương thích technical candidate của TV2/TV3; chưa APPROVED:

- Currency `VND` bắt buộc, lưu/cấp rõ từ nguồn có thẩm quyền; không default, FX hoặc đoán từ giao diện/fixture.
- Java tính bằng BigDecimal, không float/double. Giá trị dùng đối chiếu số học chính xác, không round để làm hai giá khớp.
- Giá Product **nguyên dương**1..99,999,999,999,999,999 VND. Decimal wire dạng **JSON string**1–17digits, không dấu,
  exponent, leading zero hoặc dấu thập phân theo candidate VND. Không truyền17digits qua JavaScript Number.
- Calculation scale0; input không nguyên bị reject bằng UNNECESSARY, không âm thầm round giá. Nếu business cần giá lẻ,
  phải chọn policy và wire khác cùng phiên bản trước schema/consumer; không triển khai VND nguyên trong khi business chưa chọn.
- Đề xuất giữ quantity1–1000 để nhất quán **API/service/schema hiện có**, không mở rộng từ int/long. Business muốn cap
  khác phải nêu rõ, review DTO/schema V2+ và cập nhật consumer; Inventory long không tự thay cap Checkout.
- Kiểm exact unit, unit×quantity, subtotal mỗi Store, commission, seller_net, **parent aggregate** trước mọi RESERVE/write.
  Mỗi số tiền chịu cả business cap được ký và actual schema bound. Không chỉ kiểm từng Store.

### 2.2 Tách ba loại precision

| Lớp | Hiện trạng / đề xuất | Ý nghĩa |
|---|---|---|
| BigDecimal computation | Arbitrary precision; multiply/sum không tự giới hạn NUMERIC | Không tràn Java không có nghĩa fit DB hoặc hợp lệ business. |
| Storage thực tế | NUMERIC(19,2), max không âm99,999,999,999,999,999.99 | Fact từ migration; không cho phép tự round/cast input để qua validation. |
| Business candidate | VND scale0, cap99,999,999,999,999,999 | Nhỏ hơn storage max phần.99; phải ký, không suy từ19,2 thành chính sách tiền lẻ. |

Đề xuất giữ khả năng lưu exact `.00` trên NUMERIC(19,2), thêm currency và constraints đã review ở **V2+** nếu được chọn.
Không sửa V1; không đổi sang BIGINT hoặc NUMERIC(19,0) chỉ để khớp đề xuất. Việc đổi physical type cần owner migration riêng.
Rate snapshot candidate `NUMERIC(5,4)` với0.0500/0.0000; scale của rate khác scale của amount. Java rate vẫn BigDecimal.
Kiểu/cấu trúc policyVersion, provider fact reference và audit phải được owners chốt; chưa có shared DTO mới được duyệt.

## 3. House Store — định nghĩa đề xuất, nguồn và quyền xác lập

**Chưa tìm thấy định nghĩa pháp lý/vận hành và người có quyền phân loại House đã được phê duyệt** trong nguồn của hai PR đã đọc.
Đã tìm thấy yêu cầu “trusted persisted privileged classification” và rule fee0%, nhưng chưa có provider/assignment API. [S11](SOURCES.md#s11), [S23](SOURCES.md#s23)

**Định nghĩa TV5 đề xuất để duyệt:** House Store là Store bán hàng trực tiếp cho chủ thể vận hành nền tảng, được người có thẩm quyền
xác nhận sở hữu/được vận hành cho chủ thể đó và được phân loại chính thức trong dữ liệu Store. Đây là đề xuất, không suy từ source flag.

**Quy trình đề xuất:**

1. TV1/chủ dự án chỉ định người quyết định nghiệp vụ về House, tiêu chí chứng minh và trường hợp được miễn phí5%.
2. Một ADMIN có **quyền chuyên biệt được TV1 cho phép** thực hiện phân loại; không coi mọi tài khoản ADMIN tự có quyền.
   TV2 sở hữu Store facts/command/persistence; TV1 xác thực current permission; business approver quyết định tiêu chí/phê duyệt.
3. Lưu actor, thời điểm, lý do, version và hiệu lực phân loại; bất kỳ approval hai người hoặc tài liệu chứng minh nào là lựa chọn
   business/security cần chốt, chưa tự áp quy trình đó vào code.
4. Seller không tự set House trong body, slug, tên Store, JWT role/header hoặc event. `houseStore=false` không được dùng làm mặc định
   cho trạng thái classification chưa xác minh. Adapter phải chứng minh regular/House từ trusted source hoặc fail-closed.
5. Snapshot House/rate/policyVersion lúc placement. Đổi phân loại sau đó chỉ áp cho đơn mới theo đề xuất; không repricing Order đã commit,
   trừ adjustment/reversal được duyệt và audit riêng. House không tự bypass Seller APPROVED, Store OPEN, ownership hoặc COD authorization.

**Cần chốt:** định nghĩa/chủ thể House, người được quyền quyết định và thực thi, audit/effective-time, một hay nhiều House Store,
và chính sách đổi nhầm/hồi tố. TV5 không tự chọn giá trị còn thiếu này thay business/TV1/TV2.

## 4. Cơ sở commission và thời điểm rounding

Rule5%/House0/net vẫn có evidence code và yêu cầu hiện hữu, chưa có quyết định thay thế được tìm thấy. [S02](SOURCES.md#s02)
Checkout hiện nhóm theo Store, kiểm consistent Seller/House, sum line totals rồi tính fee một lần cho SellerOrder. [S04](SOURCES.md#s04)

**TV5 khuyến nghị để duyệt:**

```text
line_total = exact_unit_price × quantity
subtotal_s = sum(line_total của Store s)
rate_s = House_snapshot ? 0.0000 : 0.0500
commission_s = HALF_UP(subtotal_s × rate_s, amount_scale=0)
seller_net_s = subtotal_s - commission_s
parent_merchandise_total = sum(subtotal_s)
```

- Round **commission một lần mỗi SellerOrder**, không per-line và không pool cả Order nhiều Store.
- Không round subtotal/net để che input sai scale; net là phép trừ chính xác sau commission đã round.
- `gross` hiện có cần được định nghĩa rõ là merchandise subtotal, tránh hiểu thành buyer payable bao gồm mọi khoản.
- Shipping/tax/discount **chưa có allocation được duyệt**. Khuyến nghị initial scope chỉ hỗ trợ flow không cần adjustment chưa thống nhất;
  không mặc định các khoản là0, không coi merchandise seller_net là số tiền phải trả cuối cùng khi có khoản điều chỉnh.
- Nếu hỗ trợ adjustment: phải ký người chịu/hưởng, allocation từng Store/line, commission trước/sau discount, tax base,
  rounding remainder và COD payable. Phải version lại policy/DTO tương ứng; không tự làm ra công thức thay owner.

Trước enable, config rate/scale/exemption phải khớp policyVersion đã ký. Source hiện không ràng buộc chính xác5%/House0
khi `approved=true`; một cờ Boolean không chứng minh config đã được phê duyệt. [S03](SOURCES.md#s03), [S07](SOURCES.md#s07)

## 5. Snapshot và nhất quán Checkout → Finance/Settlement

| Dữ liệu bất biến đề xuất | Hiện trạng | Người chịu trách nhiệm bổ sung sau sign-off |
|---|---|---|
| productId, inventorySku, unitPrice, qty, lineTotal, currency | OrderItem có productId/qty/amount scale2; current CatalogPort thiếu SKU/currency | TV2 authoritative Product/SKU/currency; TV3 port/DTO và V2+ snapshot/journal |
| Store/Seller refs +House fact/version | SellerOrder có refs; House chưa được snapshot, nguồn chưa có | TV2 S01/House source; TV1 trust boundary; TV3 persistence |
| subtotal, commission_rate, commission, seller_net, policyVersion | DB có gross/platform_fee; thiếu rate/net/version/currency | TV3 shared schema phối hợp TV5; agreed calculation/snapshot evidence |
| Provider fact/version/reference và thời điểm kiểm | Chưa có agreed freshness wire/source adapter | TV2/TV3/TV1 chốt metadata/version; không thêm timestamp/token để giả guarantee |
| DELIVERED/COD PAID, actor/time/action ID/payment amount/currency | Chưa có runtime payment/delivery/settlement được nghiệm thu | TV4 rules +TV3 authoritative transaction/event +TV1 current ADMIN; TV5 settlement |
| Unique settlement và immutable replay result | Helper alreadySettled guard, chưa có atomic settlement/query production | TV5/TV4/TV3 sau authority/policy/snapshot contract |

Finance/Settlement phải dùng **snapshot đã commit**, không gọi lại Catalog để tính giá/fee/House mới. Replay SUCCEEDED trả cùng committed
outcome, không reserve/commission/placement event lần hai. Unique Checkout key không thay unique SellerOrder settlement.
Settlement chỉ khi DELIVERED **và** COD PAID, có policy/source được duyệt, unique atomic eligibility+create; Store list/detail/aggregate phải
lọc theo authoritative membership từ verified current actor. Inventory CONSUME/replay/balance không chứng minh payment/delivery.

Return/refund/partial payment và reversal sau settlement chưa được chốt. Đề xuất giữ nguyên original snapshots, dùng record adjustment/reversal
có authority/audit riêng nếu được duyệt; không sửa tiền cũ, offset sang Store khác hoặc phát hành tự động dựa trên Inventory.

## 6. Last-verified snapshot so với quote/lease

### 6.1 So sánh và khuyến nghị

| Nội dung | A — Last-verified snapshot | B — Quote/lease đa nguồn |
|---|---|---|
| Kiến trúc hiện tại | Phù hợp đề xuất TV2/TV3; vẫn cần authoritative providers, scoped auth và recovery thực | Chưa có provider/protocol này |
| Bảo đảm | Dữ liệu đã kiểm hợp lệ tại các lần đọc; lưu snapshot được chấp nhận | Chỉ có guarantee nếu protocol thực sự ràng buộc các facts/versions và commit/redemption |
| Không bảo đảm | Các reads không cùng global time; có race sau lần đọc cuối và trước Order commit | Product-only quote không tự khóa Store/Seller/House/Category, authorization hoặc stock |
| Đổi trước commit | Nếu quan sát được mismatch thì reject/reconcile theo outcome; đổi sau last read có thể không phát hiện | Cần rules về revocation/expiry/compare-and-redeem/commit/recovery và issuer authority |
| Chi phí | Thấp hơn, nhưng business phải chấp nhận race bằng văn bản | Lớn hơn; cần thiết kế cross-source consistency và crash/idempotency trước implementation |

**TV5 khuyến nghị A có điều kiện cho MVP**, đúng hướng đã được TV2/TV3 đề xuất; **chưa phải quyết định đã duyệt**.
Điều kiện bắt buộc: TV1 +người phê duyệt nghiệp vụ được chỉ định +TV5 cùng các owner provider/consumer chấp nhận rõ read→commit race,
S01/C01/E01/M01/Security hoàn tất đúng phần phụ thuộc, source thực verified, và TV3/TV4 có durable journal/recovery khi dùng Phase B.
Nếu business yêu cầu giá/eligibility bất biến đến commit và không chấp nhận race, chọn B và **hoãn mở live Checkout** cho đến khi protocol có thực.

### 6.2 Luồng đề xuất và xử lý dữ liệu thay đổi

1. **Phase A trước RESERVE:** xác thực caller/delegated current actor; lấy đủ batch từ trusted current source, đúng ID/order/version,
   kiểm currency, price, SKU, Store/Seller, House, eligibility/Category so với expected Cart; kiểm money storage+business bounds.
   Thiếu/stale/unavailable/non-READY hoặc mismatch → không place/reserve/write. Chỉ release Cart nếu claim outcome biết chắc và an toàn.
2. **Journal/RESERVE:** persist exact SKU/qty và operationId riêng từng step ở T1 trước HTTP, ghi outcome ở T2. Chưa có journal/recovery
   production thì không giả Phase B đã dùng được. Unknown commit/timeout → giữ ID/payload, HOLD/reconcile, không blind RELEASE/new key.
3. **Phase B ngay trước Order write:** sau khi có proven reservation +approved recovery, revalidate cùng bộ items/facts. Quan sát thấy giá,
   currency, SKU, Store/Seller/House/status/Category đổi → không âm thầm thay giá hoặc place subset. Chỉ compensation theo exact persisted
   IDs/lines nếu reservation/state cho phép được chứng minh và lifecycle đã ký; source timeout không tự chứng minh reserve đã thất bại.
4. **Race còn lại:** dù Phase B thành công, nguồn có thể đổi ngay sau đọc cuối và trước local commit. Không hứa đóng cửa sổ race,
   không tự đặt TTL. Current-authorization requirements vẫn bắt buộc, không lấy snapshot làm permission vĩnh viễn.
5. **Sau SUCCEEDED:** replay dùng committed snapshots, không reprice theo trạng thái mới. Retry cùng intent giữ key/payload; payload đổi
   phải theo contract về user intent mới sau outcome đã xác định, không tự đổi key để “vượt lỗi” hoặc cứu UNKNOWN.

**Ví dụ rủi ro cần con người nhận biết:** đọc giá100000, nguồn đổi120000 trước commit. Nếu Phase B thấy đổi: abort, không charge120000
âm thầm. Nếu đổi sau last read: A có thể commit snapshot100000; business phải chấp nhận điều này rõ ràng. Tương tự Store OPEN→CLOSED
hoặc Seller bị suspension sau last read: quote chỉ khóa giá cũng không giải quyết eligibility/current authorization.

B cần thiết kế ít nhất: covered facts và authoritative issuers; actor/Store/line/currency/policy bindings; versions/fencing;
validity/expiry và revocation; atomic/idempotent validation/redemption; concurrent update/commit failure và recovery.
Chưa chọn TTL, token format hoặc distributed transaction; timestamp/hash/@Version đơn lẻ không phải lease. [S11](SOURCES.md#s11), [S19](SOURCES.md#s19)

## 7. Ví dụ biên và kết quả đề xuất

Các kết quả là tính toán/acceptance **đề xuất**, không phải production test PASS hay business approval:

| Input / tình huống | Kết quả mong đợi nếu policy trên được ký |
|---|---|
| Product price0 hoặc âm | Reject; fee0 vẫn được phép khi House hoặc round fee, không đồng nghĩa cho bán Product miễn phí |
| Price1.001 hoặc0.01 với VND nguyên | Reject trước reserve, không round thành1/0; helper/DB khác không đổi policy |
| Price wire `1e3`, `+10`, `0010`, JSON number17digits | Reject representation theo candidate wire; không tự trim/normalize thành giá hợp lệ |
| Qty1000 | Trong cap kỹ thuật hiện có, vẫn phải kiểm line/Store/parent amount |
| Qty1001 | Reject theo DTO/service/schema hiện tại, không lấy intmax làm cap |
| Max unit99,999,999,999,999,999 ×qty1 | Fit candidate; vẫn cần currency/source/Store/parent checks |
| Max unit ×qty2 | Reject line bound trước reserve |
| Unit99,999,999,999,999 ×qty1000 | Exact line99,999,999,999,999,000, fit candidate cap |
| Unit100,000,000,000,000 ×qty1000 | Line100,000,000,000,000,000 vượt cap →reject |
| Hai Store mỗi60,000,000,000,000,000 | Parent120,000,000,000,000,000 vượt cap dù từng Store fit →reject trước reserve |
| Ordinary subtotal9 /10 /11 | Fee0 /1 /1, net9 /9 /10, HALF_UP0 |
| Hai dòng10 trong một Store | Subtotal20, fee1, net19; không cộng line fee1+1 |
| Hai ordinary Store mỗi10 | Parent20, fees1+1=2, nets9+9=18; không pool fee theo parent |
| Ordinary100000 +House200000 | Parent300000, fees5000+0, nets95000+200000; House phải verified và snapshot |
| Max candidate subtotal99,999,999,999,999,999 | Fee5,000,000,000,000,000; net94,999,999,999,999,999 |
| Merchandise100000, shipping15000, tax5000, discount10000 | Chưa tính final payable/fee/net khi allocation chưa ký; không suy ai chịu discount/thu các khoản khác |

## 8. Việc còn phải phê duyệt và trách nhiệm

- **TV1/chủ dự án:** chỉ định business approver; TV5 hiện chỉ tổng hợp/đề xuất. Xác nhận MD09 deployment/enable authority.
- **Business approver +TV5:** MD01 currency; MD02/03 business caps/quantity nếu khác hiện có; MD04 House definition/fee base/rounding;
  MD05 adjustments; MD06 payment/refund; MD07 risk acceptance; MD10 groups/cutoff/timezone. Hiện đều PENDING.
- **TV2:** current Store/Seller/House/SKU/currency source và House assignment/persistence theo authority đã ký; catalog wire/versions/freshness.
- **TV3:** technical bounds trước reserve, quantity1000 consistency, Cart/Order currency/snapshots V2+, immutable replay, journal/recovery.
  Technical own-scope acceptance đã có, không đồng nhất với business approval.
- **TV4:** delivery/COD facts/actors, refund/return cutoff, exact operation lifecycle, C06/event/retry/recovery cùng TV3.
- **TV1:** S01, concrete pair/path/operation/delegated current authorization, privacy/challenges, House assignment permission và config authority.

## 9. Kiểm chứng và giới hạn bàn giao

Checkpoint đối chiếu ban đầu chỉ tạo tài liệu local, không sửa source hoặc thực hiện GitHub mutations.
Đợt công bố hiện tại được người dùng cho phép commit/push/QA comments; không resolve/Approve/Merge hoặc ký business approval.
Static source/MD completeness, links và phép tính ví dụ: xem `VALIDATION.json`. Runtime tại checkpoint đối chiếu: **NOT_RUN** vì chỉ soạn tài liệu. Kết quả regression mới xem báo cáo kiểm chứng riêng, không thay bằng kết quả lịch sử.
Kết quả6utilityPASS và diagnostic1PASS/2FAIL tạic4d517e là evidence lịch sử được trích đúng SHA; không biến thành run mới của21f6d53.

- [x] Tìm đủ MD01–MD10 từ packet thật, không tự đặt câu hỏi thay nguồn.
- [x] Đối chiếu source/candidate/own-scope replies và ghi thiếu/mâu thuẫn; sửa đề xuất quantity cho đúng nguồn.
- [x] Hoàn thiện proposal, matrix và reply draft để người dùng tự xem/gửi.
- [ ] Human business approval, contract implementation và production integration acceptance: chưa hoàn tất.
