# TV5 — QA/Finance handoff và tự đánh giá, 03/11/2026

**Cập nhật trạng thái 04/11/2026:** #69/head `f2d56d7` đã có readiness/frontend/e2e SUCCESS và [TV3 scoped APPROVED](https://github.com/duyxl56785/Project_HK5/pull/69#pullrequestreview-5754522882), nhưng chưa merge vào main hoặc cấp production pair approval. #167 đã merge **vào nhánh #166** tại `7172398`, chưa vào main; #166 hosted checks SUCCESS và [TV5 COMMENT scoped](https://github.com/duyxl56785/Project_HK5/pull/166#pullrequestreview-5761611132). TV2 [quyết định mới ở #152](https://github.com/duyxl56785/Project_HK5/pull/152#issuecomment-6619444913) giới hạn purge V5 chỉ SUCCEEDED quá 7 ngày và chỉ định stack UNKNOWN cô lập, song runner/overlay và provider/tuple receipt chưa được review. #171 đang review. Các dòng W3/retention/UNKNOWN phía dưới là checkpoint **03/11**; dùng [ma trận UAT hiện hành](../tests/TV5_UAT_ACCEPTANCE_CURRENT.md) và [Finance disposition](../docs/TV5_FINANCE_CONTRACT_SIGNOFF.md#tv5-financecod-technical-disposition--04112026) để quyết định tiếp. Final UAT #18 vẫn NOT_RUN/BLOCKED; production Finance không được duyệt.

**Nguồn kiểm tra:** `main@f0190e54a18b263df5b5cc0e9f7987873d30617e`; GitHub Issue/PR đọc ngày 03/11/2026. Các trạng thái động cần kiểm lại trước merge/UAT. Chủ sở hữu TV5: Review F15, Finance/Settlement F16 trong phạm vi được giao, QA, traceability và bằng chứng. **House Store operation thuộc TV2/business owner, ngoài phạm vi vận hành TV5**; TV5 chỉ đối chiếu tác động phí/settlement. [M01/MD09](../docs/project-report/M01_FINANCE_DECISION.md) chỉ chấp thuận demo local đúng điều kiện, không cấp quyền bật production.

## 1. Theo tuần và bằng chứng

| Tuần / Issue | TV5 đã kiểm chứng | Còn thiếu để hoàn tất |
| --- | --- | --- |
| W1 [#5](https://github.com/duyxl56785/Project_HK5/issues/5) | Rules/contract/QA nền và truy vết đã có trong [TV5_TRACEABILITY](../tests/TV5_TRACEABILITY.md); source Review/Finance đã tích hợp trên main | Không tick toàn bộ F15/F16: live authorization/review DB uniqueness/settlement Store isolation và bốn nhóm doanh thu chưa cùng một lượt nghiệm thu cuối. Issue OPEN/PARTIAL. |
| W2 [#12](https://github.com/duyxl56785/Project_HK5/issues/12) | Unit và selected PostgreSQL regression bên dưới; contract/negative cases có nguồn | Contract xuyên service và production Finance/COD/Settlement chưa được chốt đầy đủ; Issue OPEN/PARTIAL. |
| W3 [#18](https://github.com/duyxl56785/Project_HK5/issues/18) | Docker được khởi động an toàn; rehearsal cô lập và selected unit/PG PASS trên đúng source SHA; [bàn giao UAT](https://github.com/duyxl56785/Project_HK5/issues/18#issuecomment-6595370075) | Final React→Gateway→2 Store→DB UAT NOT_RUN/BLOCKED; #69 readiness FAIL, R06 chưa tích hợp/kiểm đủ và hai UNKNOWN thật chưa đủ gate. |
| W4 [#157](https://github.com/duyxl56785/Project_HK5/issues/157) | [Không nhận lượt TV2 demo làm UAT cuối](https://github.com/duyxl56785/Project_HK5/issues/157#issuecomment-6594532594); receipt TV2 tại `7cc406e` đã được TV5 xác minh **60/60 Git blob SHA256**. | Return/Refund settlement guard chờ TV3 [#155](https://github.com/duyxl56785/Project_HK5/issues/155), TV4 [#156](https://github.com/duyxl56785/Project_HK5/issues/156) và business rule; hai UNKNOWN chưa chạy; #5/#12 chưa đủ điều kiện đóng. |
| W5 [#162](https://github.com/duyxl56785/Project_HK5/issues/162) | Chuẩn bị test/evidence/checklist | UAT cuối, ảnh live, đối chiếu báo cáo và bản nộp chỉ thực hiện sau các PR/gate liên quan; chưa báo DONE. |

**Lượt chạy TV5 mới, không cộng với TV2 hoặc checkpoint cũ:**

| ID | Lệnh/môi trường | Kết quả | Raw evidence |
| --- | --- | --- | --- |
| TV5-UNIT-F019 | Gradle Product `test --tests '*Review*'` + Order `test --tests '*Finance*'`, Windows, exact source `f0190e5` | 34 Product + 51 Order = **85 PASS**, 0 fail/error/skip | [Log, 9 XML, SHA256 10/10](minh-chung-kiem-thu/uat18-main-f019-preflight/) |
| TV5-SMOKE-F019 | `Smoke-IsolatedUat.ps1 -Build`, Docker 29.8.1, project `hk5-uat-tv5-20261103a`, flags Inventory/Finance OFF | **PASS** bootstrap 8 DB/Flyway, seed Store giả/Catalog; cleanup đúng project PASS, 2 volume cũ được giữ | [Raw smoke/cleanup, read-only DB/volume checks, SHA256 4/4](minh-chung-kiem-thu/uat18-main-f019-isolated-smoke/) |
| TV5-PG-F019 | Product `integrationTest --tests '*CatalogSchemaProposalPostgresTest'` + Order `postgresIntegrationTest --tests '*Finance*'`, Testcontainers PostgreSQL | **46 PASS**, 0 fail/error/skip: 33 Product schema + 11 Finance + 2 Fulfillment; không phải Review HTTP | [Log, 4 XML, SHA256 5/5](minh-chung-kiem-thu/uat18-main-f019-postgres-regression/) |
| TV5-UAT-FINAL | React→Gateway→services→PostgreSQL thật, hai Store | **NOT_RUN/BLOCKED** | Chưa có log hoặc ảnh UAT cuối; không suy từ ba dòng trên. |

Log PostgreSQL có lỗi kết nối scanner nền sau khi container thử nghiệm dừng; JUnit/Gradle exit 0. Giữ nguyên byte log. Lệnh thử đầu dùng sai tên task Product và chạy **0 test**; không tính là lỗi sản phẩm hay PASS.

## 2. F15/F16 và R06

- F15: `ModerationController`, `ReviewRules`, V4/V8 trên main có đường Review/ẩn-hiện và hidden reason. Unit rules PASS; Product 33 PostgreSQL cases trong lượt này là schema/Flyway, **không chứng minh** buyer/DELIVERED/duplicate/admin revoke qua HTTP. TV2 demo [Issue #18](https://github.com/duyxl56785/Project_HK5/issues/18#issuecomment-6582905289) có case live riêng trên SHA cũ; không nhận là TV5 UAT hiện tại.
- **R06 tại main f019 vẫn FAIL so với oracle QA:** [TV5_UAT](../tests/TV5_UAT.md) và V2 §A.7 yêu cầu comment tối đa 1000; DTO hiện trên main cho 2000 và TV2 đã quan sát 1001 ký tự trả 201. Sau checkpoint này, TV2 đã [chốt 1000 UTF-16](https://github.com/duyxl56785/Project_HK5/issues/154#issuecomment-6605124412) và mở [#166](https://github.com/duyxl56785/Project_HK5/pull/166). TV5 tái hiện lỗi Admin không ẩn được review cũ >1000 sau migration V9; [bản sửa và RED/GREEN PostgreSQL #167](https://github.com/duyxl56785/Project_HK5/pull/167) chờ TV2 tiếp nhận. Không nhận #166/#167 là main PASS trước khi merge và chạy đúng SHA. Admin-role revoke giữa hai request thuộc TV1 [#153](https://github.com/duyxl56785/Project_HK5/issues/153), NOT_RUN.
- **Admin revoke precheck, chưa phải E2E:** TV5 chạy `CurrentAuthorityFilterTest` trên main f019, **5 unit PASS** cho hành vi hiện tại. [Raw log/XML/SHA256](minh-chung-kiem-thu/pr153-admin-revoke-precheck/) và [bàn giao #153](https://github.com/duyxl56785/Project_HK5/issues/153) ghi rõ GET hiện bỏ qua Auth current-authority; mã nguồn gợi ý protected Admin read còn tin JWT cũ. Không có public fixture revoke ADMIN; Auth chặn disable ADMIN. TV1/TV2 cần chốt cách thu hồi trên stack giả và oracle 401/403 trước khi TV5 chạy HTTP before/after. **R06 Admin revoke vẫn NOT_RUN/BLOCKED_OWNER**, không suy PASS từ 5 unit.
- F16: `FinanceRules.requireSettleable` yêu cầu DELIVERED + COD PAID và chưa settled; selected Finance unit/PG durability PASS. Kết quả này **không chứng minh** Return đang xử lý bị chặn settle, Store isolation live, House Store operation hoặc production Finance/Settlement. MD11 không vận hành House Store trong bản demo; nếu business mở phạm vi, TV2 phải cung cấp nguồn House đã xác minh và TV5 kiểm tác động phí 0%/snapshot.

## 3. V5 retention/PII — bảng kiểm, chưa ký policy

[Migration V5](../backend/order-service/src/main/resources/db/migration/V5__checkout_order_drafts.sql) tạo `checkout_order_drafts(payload text, payload_sha256, created_at)` gắn `checkout_attempts`. [CheckoutDraft](../backend/order-service/src/main/java/com/projecthk5/orderservice/domain/CheckoutDraft.java) chứa customer ID, shipping, SellerOrder/Store, items, money snapshot và outbox payload; `CheckoutDraftJournal` hiện insert/load bất biến, chưa có cleanup job. V5 copy thông tin giao hàng vào JSON riêng; bản ghi thành công vẫn còn. Cần tránh đăng payload vào log/Issue.

| Dữ liệu | Vị trí/source xác minh | Ai được đọc trong thiết kế đề xuất | Gate chưa kiểm chứng |
| --- | --- | --- | --- |
| Tên, điện thoại, địa chỉ giao hàng | `checkout_order_drafts.payload` qua `CheckoutDraft.shipping`; Order cũng giữ shipping | Order service và operator đối soát có audit, theo đúng quyền owner | DB grants, API/backup/log redaction và Store/customer access chưa được kiểm chứng end-to-end. |
| Customer ID, attempt/fingerprint, Store/SellerOrder tuple | Draft JSON, `checkout_attempts`, journal | Order service; chỉ trả public view đã scope | Replay cùng key sau khi xóa draft, cross-Store query và recovery chưa có test. |
| Money snapshot và outbox payload | Draft JSON, Order/SellerOrder/outbox | Order/Finance theo Store/current actor | Không join draft vào báo cáo Finance, không rò PII ra seller khác; cần TV3/TV5/DB owner xác nhận. |
| Backup/PITR chứa draft đã xóa | Bản sao DB, không bị SQL DELETE xóa ngay | DB operator theo chính sách | Backup lifetime, restore cleanup và audit chưa ký. |

PR [#152](https://github.com/duyxl56785/Project_HK5/pull/152#pullrequestreview-5746833496) đề xuất 7/30 ngày, quyền DB và cleanup; đây **chỉ là candidate**. TV2/DB owner cần ký retention/grants/backup/audit; TV3 phải chứng minh replay sau xóa và thứ tự FK; TV4 xác nhận recovery khi UNKNOWN. TV5 chỉ chấp nhận checklist chuẩn bị QA, chưa ký production policy hoặc cho phép chạy hai fault fixtures.

## 4. Return/Refund QA matrix chuẩn bị cho #155/#156

**Chưa có API/DTO và authoritative Return state đã chốt.** Các hàng dưới đây là test *cần thiết sau khi có contract*, không khẳng định endpoint hoặc status code cụ thể. Toàn bộ `NOT_RUN/BLOCKED_OWNER`.

| ID | Điều kiện kiểm | Bằng chứng cần thu | Owner chốt trước khi viết assertion |
| --- | --- | --- | --- |
| RET-01 | Chỉ buyer của SellerOrder DELIVERED, trong cửa sổ thời gian đã ký, được yêu cầu trả; buyer khác/Store khác bị chặn | HTTP actor/role, Order ownership, DB unchanged khi sai quyền | TV3 request/lifecycle; TV2 Auth; business cutoff |
| RET-02 | Replay cùng key/payload không tạo nhiều yêu cầu; key cũ với payload khác bị từ chối theo contract | Request/response, unique row, audit, restart | TV3 idempotency/schema |
| RET-03 | Seller chỉ xem/xử lý request của Store mình; không suy Finance hoặc PII Store B | HTTP 403/404 theo E01, SQL Store scope và log redacted | TV4 SellerOrder; TV2 Auth/Store |
| RET-04 | Nếu return đang mở và business chặn settlement, không settle/COD reversal nhầm; concurrent settle vs return phải có thứ tự atomic | Trạng thái trước/sau, transaction/unique settlement, money snapshots, 2 Store | TV3/TV4 authoritative state+race; business MD06/refund; TV5 Finance |
| RET-05 | Hoàn kho đúng một lần khi return được owner chấp nhận; replay/restart không cộng hai lần, không tự RELEASE/CONSUME ở UNKNOWN | Inventory operationId/owner tuple, stock ledger trước/sau, provider receipt | TV3/TV4 lifecycle + Inventory contract |
| RET-06 | Đơn đã settled hoặc COD khác trạng thái được xử lý theo policy trả/hoàn tiền đã ký, không sửa snapshot lịch sử âm thầm | Adjustment/reversal audit, Store-scoped report, HTTP/DB | Business MD06, TV4 COD, TV3 Order, TV5 Finance |

Không tạo test mock để gọi các hàng này PASS khi contract chưa có. Return/Refund hiện không phải luồng hoàn tiền thật theo [#155](https://github.com/duyxl56785/Project_HK5/issues/155)/[#156](https://github.com/duyxl56785/Project_HK5/issues/156).

### Bổ sung TV5 04/11 — phản hồi đề xuất Return của TV3, chưa ký policy

[TV3 RFC và bốn quyết định cần chốt](https://github.com/duyxl56785/Project_HK5/issues/155#issuecomment-6634090137) đã xác nhận `main@c1ac4d4e5dd450d5d3fc3d6a8267cb5b45f32d48` chưa có Return API/state/table. `FulfillmentService.settle` khóa `SellerOrder` bằng `lockById`, kiểm DELIVERED, COD COLLECTED và chưa settled, nhưng **không có return guard**. Đây là khoảng trống triển khai cần chốt contract trước khi viết assertion hay migration.

**TV5 đề xuất để business owner/TV2/TV3/TV4 ký hoặc sửa:** full-SellerOrder return trong 7 ngày UTC sau `deliveredAt`, chỉ buyer thật, chưa settled. Cho phép COD **COLLECTED nhưng chưa SETTLED** gửi yêu cầu Return; ghi nhận cash receipt thật vẫn là sự kiện độc lập, không tự hoàn tiền. Từ khi tạo `REQUESTED`, khóa settlement; tiếp tục khóa tại `APPROVED` và `RECEIVED`. Chỉ xét settlement lại sau `REJECTED` đã đóng/audit, và không còn Return đang mở. Return và settle phải khóa **cùng SellerOrder row** trước khi kiểm return/settled, cùng một transaction; race chỉ được tạo một outcome hợp lệ. Snapshot tiền gốc bất biến; mọi refund/reversal nếu được duyệt phải là bản ghi riêng và idempotent. Không dùng Inventory RELEASE sau DISPATCHED; TV4 quyết định receipt và operationId bền vững cho stock INCREASE sau khi thật sự nhận hàng.

Đây là **PENDING BUSINESS/OWNER APPROVAL**, không phải chữ ký Finance production. TV2/business owner cần xác nhận cutoff 7 ngày, phạm vi COD, quyền Seller/Admin, error envelope và việc `collectCod` trong lúc Return mở; TV3 chốt API/DB/idempotency/row-lock; TV4 chốt lifecycle/restock; TV5 sau đó viết/chạy PostgreSQL race RET-04/RET-06 và Store isolation. Cho đến lúc ấy RET-01..06 **NOT_RUN/BLOCKED_OWNER**, #155/#156/#157 chưa đạt; không bật Finance flag.

## 5. UNKNOWN và thứ tự mở gate

TV3 đã chấp nhận giao thức C06 có điều kiện; PR #152 ghi timeout proxy là candidate, claim-conflict `BLOCKED_DESIGN`. TV4 phải có fault thật và provider receipt, TV2 duyệt exact source/Compose project/ports/runner, TV5 duyệt evidence/PII và xác minh không đổi operationId, không auto retry/RELEASE/CONSUME, không xóa response/Cart lock sớm. [Minh chứng TV5](minh-chung-kiem-thu/pr145-testcontainers-init-fail/) là lỗi Testcontainers **trước khi Docker được khôi phục**, không phải ảnh hai UNKNOWN. Hai ca vẫn **NOT_RUN**; smoke mới không chạy chúng.

## 6. Kịch bản demo và câu hỏi vấn đáp cá nhân

**Demo khi đủ gate:** ghi full SHA và CI, tạo project Compose mới/volume mới, seed Customer + Admin + hai Seller/Store giả, chạy React→Gateway; kiểm Store isolation, Review buyer/DELIVERED/hide/show, Cart/Checkout 2 Store, Inventory/COD, Finance theo demo policy; đối chiếu HTTP/DB/snapshot, negative 401/403/409, replay/restart. Thu log/ảnh thật cho từng case và teardown chỉ project vừa tạo. Nếu #69/R06/UNKNOWN chưa đạt, trình bày demo scoped với backlog công khai, không gọi UAT cuối hoặc production PASS.

1. **Vì sao 85 unit PASS chưa đủ nghiệm thu?** Chúng không chứng minh HTTP/JWT, PostgreSQL concurrent uniqueness, cross-service event hay hai Store live.
2. **Vì sao Finance chỉ settle sau DELIVERED và COD PAID?** Đây là điều kiện trong `FinanceRules`/MD06; cần kiểm atomic unique settlement trên DB và current authorization, không suy từ boolean helper.
3. **Timeout Inventory có nghĩa thất bại chưa?** Chưa. Giữ cùng operationId/payload, UNKNOWN quarantined và đối soát theo provider receipt; không blind RELEASE/CONSUME.
4. **R06 đã đi đến đâu?** TV2 chốt 1000 đơn vị UTF-16 ở #154; #166 đã tích hợp bản sửa #167 và merge. Trên `main@c1ac4d4`, TV5 chạy 2 unit + 1 PostgreSQL PASS cho giới hạn/migration legacy; Admin revoke live #153 và F15 hai Store vẫn chưa được nghiệm thu.
5. **TV5 có vận hành House Store không?** Không; TV2/business owner sở hữu operation. TV5 kiểm phí 0%/snapshot khi business mở phạm vi.

**Trạng thái bàn giao 04/11:** TV5 đã lưu preflight độc lập ở đúng source SHA; #69 và #166/R06 đã vào main, nhưng kết quả hẹp không thay final UAT. #18/#157 vẫn OPEN/PARTIAL đến khi Return contract, retention/UNKNOWN, Auth revoke, Finance policy và CI của SHA nghiệm thu đạt; không tick DONE bằng selected test.
