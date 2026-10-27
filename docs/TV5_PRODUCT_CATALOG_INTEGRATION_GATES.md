# TV5 — House/freshness, Catalog contract và E2E gates

27/10/2026. Refs #2/#5/#9/#12/#15/#90/#91/#97/#103. Đây là bảng đối chiếu/đề nghị có điều kiện, không tạo lại M01 hay ký business approval.
[QA/source/test hiện tại](../reports/TV5_PRODUCT_SIX_TASKS_2026-10-27.md).

## Quyết định đã có và source thực tế

|Phạm vi|Trạng thái đúng scope|Nguồn|
|---|---|---|
|MD01/MD02/Product unit MD03|APPROVED: VND rõ ràng, đơn giá nguyên1..1e9, exact/no-rounding, canonical decimal string. Không gồm quantity/aggregate/fee/refund/COD.|[Business receipt84](https://github.com/duyxl56785/Project_HK5/pull/84#issuecomment-6364386964), LevelA/TV2mapping/LevelB88; không xin ký lại phần không đổi.|
|S01/OD09 baseline|Thiết kế Store trong SellerDB, local FK/logical StoreUUID và pair boundary đã xác nhận ở scope kỹ thuật; không chứng minh runtime đã triển khai.|[TV1 handoff](https://github.com/duyxl56785/Project_HK5/blob/a705052f93e57d4316c04dba0ef96e923cc44cd2/docs/TV1_S01_OD09_INTEGRATION_HANDOFF_2026-10-18.md).|
|Current Store/Seller facts94|Đã merge; query local có status/version/private ownerReference. Không có House field hoặc authenticated remote adapter.|[OwnedFacts dòng26](https://github.com/duyxl56785/Project_HK5/blob/a705052f93e57d4316c04dba0ef96e923cc44cd2/backend/seller-service/src/main/java/com/projecthk5/sellerservice/store/StoreCurrentSource.java#L26).|
|C01/E01 v0.2|TV2 provider-design và TV3 consumer-design xác nhận own scope; overall REVISE/PENDING shared wire/security/privacy/freshness.|[Provider response](https://github.com/duyxl56785/Project_HK5/blob/a705052f93e57d4316c04dba0ef96e923cc44cd2/docs/TV2_CATALOG_CONTRACT_RESPONSE.md), [TV3 response](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246536042).|
|Full M01/MD04–10|PARTIAL/PENDING; Finance production BLOCKED, không bật flag.|Financeledger hiện tại/packet và các receipt trong PR93; Product price-only signature không mở rộng sang House/commission/settlement.|

Tài liệu C01 cũ còn đề xuất17digits; nó không thay đổi unit cap1b đã được phê duyệt sau đó. Cần TV2/TV3 chốt mapping wire/error hiện hành, không yêu cầu một business signature mới cho unit cap không đổi.

## House và freshness hiện có gì?

Đề xuất hiện có: House là classification privileged, persisted phía server từ Store authority đã thống nhất; không suy từ slug/tên/role/JWT/body/header và không default unknown=false. Chưa có House persistence/assignment API hay operator được xác nhận.94không đủ dữ liệu để trực tiếp đáp ứng90.
[Source90 dòng77](https://github.com/duyxl56785/Project_HK5/blob/273a44db9a24e5aac69e7d9f9e8e0208c9f963b7/backend/product-service/src/main/java/com/projecthk5/productservice/catalog/ProductCatalogSnapshotSource.java#L77) yêu cầu facts đầy đủ/current và House không null. False trong QA fixture chỉ là synthetic input.

Product/Category scalar query ở90dùng READ_COMMITTED, bypass retained JPA entities và từ chối ambient snapshot không phù hợp/không rõ.94dùng một local Store/Seller statement READ_COMMITTED. Hai lần quan sát không phải distributed snapshot; chưa có remote freshness/version/cache-invalidation/TTL được duyệt.
Trong QA local PG, commit deactivate/suspend/reprice được nhận ở lần lookup kế tiếp; một item lỗi khiến mọi snapshot bị giữ lại. Value object đã trả trước đó không tự đổi, nhưng đó không phải durable Order snapshot hay authority còn hiệu lực tại commit.

[Last-verified double revalidation](https://github.com/duyxl56785/Project_HK5/blob/a705052f93e57d4316c04dba0ef96e923cc44cd2/docs/TV2_CATALOG_CONTRACT_RESPONSE.md#L75) vẫn là đề nghị có điều kiện: đọc toàn batch trướcRESERVE và đọc lại cùng lines trướcOrder write. Owners phải chấp nhận residual read→commit race hoặc thiết kế quote/lease/fencing liên nguồn thật. Hai lần đọc, timestamp/hash/Product@Version không tự bảo đảm commit-time. Không tự đặt TTL hoặc quote giả. UNKNOWN reservation phải giữ owner tuple/operationId để reconcile, không blind RELEASE/new key.

## Câu hỏi cần xác nhận bằng văn bản

|ID|Quyết định còn thiếu|Owner / bên xác nhận|Status và giới hạn|
|---|---|---|---|
|H01|House source/fields/version; quyền gán/sửa, audit và effective time|TV2+TV1+người có thẩm quyền nghiệp vụ;TV5 consumer|PENDING; chưa có source/assignment API có thẩm quyền.|
|H02|House/rate provenance vào immutable Order snapshots; xử lý correction sau commit|TV2/TV3/TV5+business;TV4 nếu COD/refund bị ảnh hưởng|PENDING; không suy 5%/0% hay hồi tố từ fixture.|
|F01|Chấp nhận residual last-verified race hay yêu cầu quote/lease liên nguồn?|TV1/TV2/TV3+TV5+business owner|PENDING; có khuyến nghị kỹ thuật, chưa có đủ sign-off.|
|F02|Current-read attestation/version/cache invalidation của Store/House adapter|TV2 provider+TV1 security+TV3 consumer|PENDING; localRC/currentBoolean không thay remote verification.|
|C01|Wire version/method/path/strict DTO/allReady+ordered complete results, unit1b mapping / decimal string|TV2/TV3+TV1 privacy/security+TV5 money QA|REVISE/PENDING; internal Java records không là HTTP contract.|
|E01|Envelope/status/domain-vs-auth errors, absence/unavailable và privacy|TV1/TV2/TV3+TV5 QA|REVISE/PENDING; explicitABSENT/UNAVAILABLE và all-or-nothing đã kiểm chứng nội bộ.|
|V01|Triển khai narrow owner-resource404exception sau current scope verification; xác nhận scope/rollback và giữ các403khác|TV1 đã DECIDED;TV2 cần CONFIRMED/REVISE rồi sửa;TV5 QA|[TV1 receipt](https://github.com/duyxl56785/Project_HK5/pull/97#issuecomment-6430723502); chưa là sharedHTTPapproval. [Draft diagnostic#104](https://github.com/duyxl56785/Project_HK5/pull/104) hiện4PASS/4FAIL.|
|I01|Durable SellerOrder reservation owner trước gửi; RESERVE/CONSUME/RELEASE/lookup/UNKNOWN recovery|TV3+TV4+TV1;TV2 SKU;TV5 Finance boundary|PENDING C06/runtime. Global immutable SKU đã enforce bởi88; không xin lại invariant đó.|

Bảng trên không APPROVE House, shared C01/E01 wire, MD07 hoặc COD/Finance enablement.

## #97: security/API và finding đã có

[Handler.findById dòng60](https://github.com/duyxl56785/Project_HK5/blob/aa790f422a8b3ee698f53e74c455edae7b77d6fa/backend/product-service/src/main/java/com/projecthk5/productservice/product/http/ProductManagementHandler.java#L60) dẫn tới [403 ACCESS_DENIED dòng17](https://github.com/duyxl56785/Project_HK5/blob/aa790f422a8b3ee698f53e74c455edae7b77d6fa/backend/product-service/src/main/java/com/projecthk5/productservice/product/http/ProductManagementErrors.java#L17) hoặc [404 NOT_FOUND dòng22](https://github.com/duyxl56785/Project_HK5/blob/aa790f422a8b3ee698f53e74c455edae7b77d6fa/backend/product-service/src/main/java/com/projecthk5/productservice/product/http/ProductManagementErrors.java#L22).
Known P2 trong [TV1 review](https://github.com/duyxl56785/Project_HK5/pull/97#pullrequestreview-5644116114): một caller đã xác thực có thể phân biệt tồn tại của Product thuộc Store khác nếu mapping được expose nguyên trạng. TV2 đã tái hiện private failure records ở comment mới nhất #97.
**Cập nhật quyết định:** [TV1 receipt6430723502](https://github.com/duyxl56785/Project_HK5/pull/97#issuecomment-6430723502) đã chốt narrow exception cho owner GET/edit/submit/deactivate sau độc lập xác minh actor/Store scope: foreign và missing đều404 PRODUCT_NOT_FOUND, same message. Chờ TV2 xác nhận và triển khai; không còn yêu cầu TV1 quyết lại phần này. Bốn parity regression hiện FAIL trên sourceAA; bốn controls PASS. Baseline Authcontract [dòng199/202](https://github.com/duyxl56785/Project_HK5/blob/a705052f93e57d4316c04dba0ef96e923cc44cd2/docs/TV1_AUTH_IDENTITY_GATEWAY_CONTRACT.md#L199) lại mặc định ownership403 và yêu cầu owner review cho404. Quyết định mới chỉ override baseline ở narrow scope trên, không global-remap ACCESS_DENIED. Đây là P2 pre-activation còn OPEN/WAITING_TV2, không phải live exploit đã chứng minh. Giữ RED đến khi có owner fix, không sửa assertions để che lỗi.

|Boundary|QA đã xác minh|Chưa xác minh|
|---|---|---|
|Identity/SELLER/Store|Authority port trước JSON/resource; domain policy từ chối wrong Store/self-moderation trong fixture|Verified-principal/current role/Store adapter và protected Controller không đăng ký; HTTP401 BLOCKED.|
|Input400|Duplicate/unknown/null/Unicode/exact price/version; không chấp nhận client role/owner|Prepared mapping chưa là final HTTP wire. Create storeId là selector phải kiểm policy, không là quyền.|
|403/404|Private mapping và knownP2 đã đọc; diagnostic4FAIL/4controlsPASS|TV1 đã chốt narrow rule; TV2 confirmation/fix còn chờ. Không mở finding trùng.|
|409/503|State/version/SKU/authority/dependency negatives, transaction/PG|Không thay Auth/current Store dependency failures qua public ProductHTTP.|
|Privacy|Allowlisted projection, bỏ ownerReference/reviewReason/raw entity; sanitized messages|TV1 đã quyết định narrow concealment; TV2 cần xác nhận và triển khai. Protected route/status/code/body thực tế vẫn chưa chạy.|

## Chuẩn bị full flow và reusable harness

[PR103](https://github.com/duyxl56785/Project_HK5/pull/103)/`151361bac8010a4cf5ba890db99429020e291348` cung cấp fixture2Store và5PGtests thực sự nối application write→Catalog read; không thêm test-only production adapter. [Fixture Order riêng ở99](https://github.com/duyxl56785/Project_HK5/pull/99) chỉ hỗ trợ transaction/durability, không chứng minh live Checkout placement.

|Case|Kịch bản / invariant chuẩn bị|Live result|Gate còn thiếu|
|---|---|---|---|
|E2E01|Hai current Seller/Store associations; Customer không tự cấp SELLER|BLOCKED/NOT_RUN|TV1 current identity/roles, TV2 versioned role-sync/ownership.|
|E2E02|SellerA tạo Product ởStoreA; SellerB không đọc/sửa privateA|BLOCKED/NOT_RUN|Protected97routes/authority và V01.|
|E2E03|Moderation hợp lệ tạoACTIVE; trạng thái không bán được bị giữ lại|BLOCKED/NOT_RUN|Current ADMIN/no-self-moderation/runtime endpoints; localPG seam đã có5case riêng.|
|E2E04|Catalog kiểm Product→exactSKU/Store/Seller/House/price; incomplete batch không có usable subset|BLOCKED/NOT_RUN|90merge, sharedwire/currentHouse/freshness.|
|E2E05|Customer thêm một Product từ mỗi Store vàoCart; expected values không là authority|BLOCKED/NOT_RUN|TV3Cart/current Customer adapter; quantity fixture không duyệt aggregate caps.|
|E2E06|Một parent/hai SellerOrders và agreed immutable money/SKU/currency snapshots|BLOCKED/NOT_RUN|TV3placement+applicable M01/C06; không invent commission/net/COD expected values.|
|E2E07|Giá/status/authority thay đổi hủy whole placement; lỗi ghi local rollback|BLOCKED/NOT_RUN|Providers/version policy; PG5chỉ chứng minh rollback/whole-batch local.|
|E2E08|Duplicate/UNKNOWN giữ cùng owner/operationId, reconcile thay blindrelease/newkey/doubleorder|BLOCKED/NOT_RUN|TV3durable recovery+TV4C06+TV1pair/security.|

Phân loại: standalone JSON/handler tests là unit/contract preparation; harness5case là real PostgreSQL integration; tám hàng trên chưa có live HTTP E2E PASS. FullM01 PARTIAL/PENDING, Finance production BLOCKED, flags không đổi.


## Dependency update — TV5-PRODUCT-DELTA-20261027

Auth/Gateway candidate #102 đã lên36ad517; TV5 tự chạy147PASS/2SKIP (trong đó10realAuth/GatewayHTTP→PG và5current-authorityPG), raw/derived evidence tại [follow-up](../reports/TV5_PRODUCT_SIX_TASKS_2026-10-27.md#follow-up-tv5-product-delta-20261027--sau-checkpoint-ban-đầu). Đây là dependency candidate có realHTTP, chưa merge/approve production. Seller role-sync, trusted current Store/House và Product97 protected binding vẫn thiếu; không suy các ô E2E01–08 thành PASS. TV1V01 đã DECIDED, chờ TV2 ACK/fix; không yêu cầu TV1 quyết lại. Các business/shared-wire/freshness gates khác không có sign-off mới.
