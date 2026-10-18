# Bản phản hồi để TV5 tự xem và gửi

> Công bố qua PR44: checkpoint nguồn ban đầu21f6d53/ef62086 được giữ provenance; đã đồng bộ owner5ce0630 và kiểm QA tại1311f8a. Xem [kết quả mới và giới hạn](../TV5_FINANCE_ACCEPTANCE_2026-10-18.md). Mô tả quantity MD03 đã sửa trong packet; business policy vẫn PENDING.


**Bản phản hồi mẫu, không phải chữ ký nghiệp vụ.** Bản nháp để người dùng/owners xem ngày18/10/2026, đối chiếu PR28 `5ce0630` và PR37 `ef62086`.
[M01 chi tiết](M01_PROPOSAL.md) · [Ma trận MD01–MD10](MD01_MD10_MATRIX.md) · [Nguồn](SOURCES.md).

## Nội dung có thể sao chép

TV5 đã đối chiếu source và phản hồi owner hiện tại. TV2 đã xác nhận provider-design C01/E01; TV3 đã chấp nhận
consumer design/mapping v0.2 **trong phạm vi TV3**, overall C01/E01 vẫn REVISE ([xác nhận TV3](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6248646211)).
PR37 mới ef62086 giữ production source và contract v0.2 từ42cc4e7, chỉ bổ sung SQL candidate tests/tài liệu [S28](SOURCES.md#s28).
Không yêu cầu TV3 duyệt lại DTO shape như chưa có phản hồi. PR43 đã được tích hợp vào PR28 tại21f6d53;
PR41/42 cũng đã được tiếp nhận, không còn là patch chờ tích hợp. Merge QA không phải Finance fix hoặc business signature.

TV5 Nguyễn Minh Duy hiện **tổng hợp và đề xuất**, chưa được xác nhận quyền phê duyệt nghiệp vụ cuối cùng.
Người phê duyệt: **chờ TV1/chủ dự án xác nhận**. Các trạng thái dưới đây là đề xuất disposition để con người phản hồi, không phải agent ký APPROVED.

| Contract ID | APPROVED / REVISE / PENDING | Phiên bản | Nội dung cần sửa (nếu có) | Người chịu trách nhiệm |
|---|---|---|---|---|
| **M01** | **PENDING** | TV5-FIN-v0.1 +TV5-M01-DECISION-v1; đề xuất local TV5-M01-PROPOSAL-v0.2 | Chọn MD01–MD10 cùng revision, chỉ định business approver; chưa bật Finance. VND nguyên/17-digit cap/HALF_UP0 mỗi Store là candidate. | TV5 tổng hợp; business approver **chờ TV1/chủ dự án xác nhận**; TV2/TV3/TV4 ký own-scope, TV1 authority |
| **S01** | **PENDING** | Store/Seller boundary trong TV2 candidate v0.2 | TV1 xác nhận Store-on-Seller-DB/current authority; TV2 bàn giao trusted Store→Seller/House facts, consumers xác nhận isolation. Không cross-DB FK hoặc suy quyền từ JWT/body. | TV2 owner; TV1 boundary/auth; TV3/TV4/TV5 consumers |
| **C01** | **REVISE** overall | catalog.checkout.v1 / candidate v0.2,42cc4e7 | Shape đã được TV2/TV3 chấp nhận trong design scope. Còn authoritative source/House/SKU/currency, S01/Security/M01/freshness và positive provider/consumer tests. Không READY subset. | TV2 provider, TV3 consumer; TV1 auth; TV5 M01; TV4 SKU |
| **E01** | **REVISE** overall | catalog.checkout.v1 / candidate v0.2 | TV3 đã chấp nhận candidate200/400/401/403/503 và top-level code/message/errors trong own-scope mapper. TV1 còn chốt challenge/privacy/masking/actual authorization; TV2 triển khai wire sau sign-off. ABSENT khác incomplete/unavailable. | TV1 final security/privacy, TV2 errors/provider, TV3 mapper |
| **MD01 currency** | **PENDING** | MD01 L26 packet +local v0.2 | TV5 đề xuất VND explicit, nguyên, không default/FX; business chọn trước schema/consumer. | Business approver được TV1/chủ dự án chỉ định +TV5; TV2/TV3/TV4 |
| **MD02 unit price/wire** | **PENDING** | MD02 L27 +local v0.2 | Đề xuất unit1..99,999,999,999,999,999; canonical decimal JSON string, BigDecimal, reject phần lẻ. Technical string-wire acceptance không ký business cap. | Business approver +TV5; TV2/TV3; TV1 validation/privacy |
| **MD03 bounds** | **PENDING** | MD03 L28 +local v0.2 | Sửa mô tả quantity của packet: source hiện1–1000, không chỉ intmax. Chốt caps unit/line/Store/fee/net/parent và pre-reserve validation. | TV3 orchestration, TV5 +business approver, TV2 price, TV4 qty |
| **MD04 fee/House** | **PENDING** | MD04 L29 +local v0.2 | Giữ rule hiện hữu5%/House0/net, đề xuất HALF_UP0 mỗi SellerOrder merchandise subtotal. Chốt House definition/assignment/audit, snapshot/version và config binding. | Business approver +TV5; TV2 House, TV1 permission, TV3 snapshot, TV4 COD |
| **MD05 adjustments** | **PENDING** | MD05 L30 +local v0.2 | Shipping/tax/discount allocation, ai hưởng/chịu, fee base và rounding remainder chưa ký; không giả bằng0 hoặc tự tính final payable. | Business approver +TV5, TV3 totals, TV4 COD/refund |
| **MD06 COD/refund** | **PENDING** | MD06 L31 +local v0.2 | Giữ DELIVERED AND COD PAID; chốt source/actor/actionId/audit, partial payment/refund/reversal và unique atomic settlement. | TV4 +TV3 nguồn/lifecycle; TV5 Finance +business approver; TV1 current ADMIN |
| **MD07 / C01-FRESHNESS** | **PENDING** | MD07 L32 +catalog.checkout.v1 v0.2 | TV5 khuyến nghị A last-verified có điều kiện; TV1/named business owner phải chấp nhận read→commit race. Phase B cần durable journal/proven-reserve/recovery. Nếu không chấp nhận, B cần quote/lease đa nguồn chưa có. | TV1 +business approver +TV5; TV2 source; TV3/TV4 recovery |
| **MD08 snapshots** | **PENDING** | MD08 L33 +local v0.2 | Immutable currency/SKU/House/rate/commission/net/policy +fact refs, reviewed V2+; không edit V1 hoặc reprice replay/settlement. | TV3 persistence +TV5 Finance, TV2 facts, TV4 lifecycle, TV1 exposure |
| **MD09 enable authority** | **PENDING** | MD09 L34 +local v0.2 | TV1/chủ dự án chỉ định người ký business và operator; giữ policy-approved=false đến khi đủ approvals/dependency/evidence, config đúng policyVersion. | TV1/chủ dự án; TV5 tổng hợp; TV2/TV3/TV4 dependencies |
| **MD10 reports/cutoff** | **PENDING** | MD10 L35 +local v0.2 | Chưa đủ định nghĩa bốn nhóm doanh thu/công thức/source/refund/cutoff/timezone; không tự đặt tên hoặc viết query. | Business approver +TV5; TV3/TV4 facts/time |
| **Inventory lifecycle / Security** | **PENDING** | TV5-FIN-v0.1; OD-09 baseline giữ nguyên | Chốt exact Product→SKU, T1/T2 IDs, reconciliation/retry/C06/state-event; concrete pair/path/operation/delegated current actor. Baseline Auth không thay pair-specific approval. | TV4 +TV3, TV2 SKU; TV1 Security; TV5 COD boundary |
| **FIN-01 / FIN-06 acceptance** | **PENDING** fix/verification | Checkout source5ce0630; latest diagnostic execution de1f5c6 | Utility storage chưa gọi trước reserve. TV3 sửa trong scope được thống nhất rồi chạy nguyên acceptance tests trên fix SHA, giữ positive control/original failing evidence. Runtime không thay đổi từc4d517e trong hai module Cart/Order; không suy mới PASS từ CIgreen. | TV3 Checkout owner; TV5 QA/policy phối hợp; TV2 representation |

**Các quyết định cụ thể cần phản hồi:**

1. TV1/chủ dự án: chỉ định business approver/operator; xác nhận S01, Order→Catalog pair/path/current authorization và E01 privacy/challenges.
2. Business approver +TV5: chọn MD01–MD06/MD10, nhất là House, adjustment/refund, caps và rounding; TV5 hiện chỉ đề xuất.
3. TV1 +business approver: MD07=A và explicit race acceptance hay B quote/lease; TV5 khuyến nghị A có điều kiện, không tuyên bố đã ký.
4. TV2: authoritative price/currency/SKU/Store/Seller/House và source freshness; source enum không thay adapter/persistence.
5. TV3/TV4: cùng-version snapshots/V2+, pre-reserve bounds, durable journal/exact-ID recovery và lifecycle/event/COD; không blind release UNKNOWN.

**Evidence và giới hạn:** checkpoint soạn đề xuất local kiểm tài liệu/source/links/arithmetic, unit/PG/live E2E NOT_RUN; lượt chạy mới được ghi riêng trong báo cáo kiểm chứng, không cộng test snapshot khác thành release total.
Historical6utilityPASS và diagnostic1PASS/2FAIL thuộc sourcec4d517e, không là run mới tại21f6d53. FIN-01/06 chưa được chứng minh GREEN.
Catalog explicit ABSENT/rejected-batch withholding là internal source fix;11Product SQL prototype cases cũ nay thành24 +26Store candidate cases,
vẫn không phải Product/Store production persistence. Full406PASS tại3ecf9c3 là TV2 báo cáo, TV5 chưa chạy lại/kiểm raw XML lượt đó;
403 denial không phải positive API/source. M01/S01/Security/freshness chưa đủ approvals nên Issue #5 tiếp tục **OPEN/PARTIAL**, chưa Finance DONE.

**Thông tin tách riêng:** mainac7ca4f phát sinh overlap QA mới với PR28 sau merge PR43; TV3 đã báo trong
[comment mới](https://github.com/duyxl56785/Project_HK5/pull/28#issuecomment-6248595524).
Đây không phủ nhận bản conflict union trước đã giải quyết. TV3/TV5 cần xác nhận union giữ lịch sử main +hai checkpoint Finance18/10 khi xử lý merge trong nhiệm vụ được giao riêng.
Bản nháp không thực hiện merge hoặc giải quyết conflict; xác nhận QA union trong comment chỉ áp phạm vi tài liệu, không ký nghiệp vụ.

Checkpoint QA mới: source1311f8a có Cart8unit/Order81unit/Cart1PG PASS, gồm4Finance preflight tests mới; source de1f5c6 có Order16PG PASS và original diagnostic1PASS/2FAIL. Order production/PG/diagnostic không đổi giữa hai source, không cộng run hoặc gọi test mới ởsource chưa chạy. TV5 xác nhận phương án QA union giữ mọi lịch sử main/checkpoint/provenance, chưa thực hiện merge thay owner.

**Post-merge handoff:** owner đã merge PR44 initial tại ccfb38d/head1311f8a. Hai commit evidence sau merge được bàn giao riêng trên codex/tv5-finance-evidence-handoff từ PR28ca639bb, không push PR44 thêm; source facts/contract signatures vẫn giữ đúng provenance nêu trên.
