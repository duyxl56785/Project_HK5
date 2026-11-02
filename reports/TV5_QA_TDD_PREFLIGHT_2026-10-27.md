# TV5 — QA composition và TDD, 27/10/2026

Refs #5/#12/#90/#99/#100/#103/#104. GitHub identity `nguyenminhduy090-alt`.
Đọc AGENTS/CONTRIBUTING/OWNERSHIP, contracts/QA/evidence và exact source/reviews/CI trước sửa.
Không sửa production TV1–TV4, migration, shared build/workflow hay bật flag. Root WIP `.vs/` và
`reports/tv5-m01-local/` được giữ nguyên. [HEAD/base/publication/sign-off context](evidence/tv5-qa-tdd-20261027/context.json).

## A. Công việc đã code và push

| Task | Test file/code thay đổi | Commit | PR |
|---|---|---|---|
|Sync harness sau90merge|Merge approved main vào branch TV5; README ghi đúng merge và runtime gates; production tree không đổi so với main|bb3c25b,976a08c|103 retarget main|
|Product→Catalog→Checkout preflight|`tests/integration/ProductCatalogCheckoutPreflightPostgresTest.java` mới; init thêm Order compiled classes và đường dẫn migrations; README scope|53658de1f66140abbf293efb073486a17c2850ac|103|
|Finance mixed replay|`FinanceDurabilityPostgresIT.java`: FD05; giữ full tiền/fingerprint/outbox khi valid và conflicting replay chạy cùng; README|3851b93 sync,931feee45ac3f17293a4f200c604010fce63bf39|99|
|QA CLI transport robustness|`scripts/qa/pr_review_audit.py` catch HTTPException thành sanitized AuditError; hai regression tests và shared fault fixture|c25c67879b642b9a3521392dc02e648413ee5eca|100|

15 ca mới =12 PG composition +1 PG mixed replay +2 offline CLI. Đây là số ca được thêm,
không phải release PASS total. Không viết lại F15/Finance rules, không thêm endpoint/prod adapter.

## B. TDD và coverage gap

| Test case | RED | GREEN | Refactor | Regression / evidence |
|---|---|---|---|---|
|12seam cases103|Không có: PASS ngay lần đầu, PREVENTIVE|12new+5old=17PGPASS|Helper mapping/witness/fixtures tách rõ; không yếu assertion|[First](evidence/tv5-qa-tdd-20261027/integration-first.json), [exact commit](evidence/tv5-qa-tdd-20261027/integration-committed.json)|
|Finance FD05|Không có: PASS ngay lần đầu, PREVENTIVE|Focused5durabilityPGPASS|Dùng import DTO hiện có; không đổi expected|[First](evidence/tv5-qa-tdd-20261027/finance-first.json), [committed full regression](evidence/tv5-qa-tdd-20261027/finance-committed.json)|
|CLI truncated HTTP body|29tests:27PASS/2ERROR,exit1; IncompleteRead thoát ra ngoài transport/main|29PASS,exit0 sau catch HTTPException|Dùng chung fault-response fixture; giữ hai assertions về lỗi sanitized/không publish partial files|[RED](evidence/tv5-qa-tdd-20261027/cli-red.json), [GREEN](evidence/tv5-qa-tdd-20261027/cli-green.json), [refactor](evidence/tv5-qa-tdd-20261027/cli-refactor.json), [committed](evidence/tv5-qa-tdd-20261027/cli-committed.json)|
|Visibility104|Original4PASS/4FAIL giữ nguyên, không chạy lại khi source97chưa đổi|NOT_RUN/WAITING_TV2_FIX|Không sửa tám assertions/source104|[Original diagnostic receipt](evidence/tv5-product-six-tasks-20261027/visibility-committed-red.json)|

**Một chu trình RED→GREEN thực sự**, cho bug CLI, không phải hai chu trình vì có hai tests.
Đây là fault-injection HTTP body truncation; không khẳng định GitHub server từng gửi đúng fault đó.
Lượt live đầu sau fix gặp request/JSON failure: CLI trả2/sanitized/no snapshot, đúng fail-closed;
wrapper đọc snapshot chưa tồn tại gây FileNotFoundError riêng, không phải CLI defect. Giữ log lần đầu.
Một bounded retry qua existing Git credential (bỏ env token overrides để nhất quán identity) đạt18/18 records,
exit0/complete=true. [Failed attempt](evidence/tv5-qa-tdd-20261027/cli-live-incomplete.json),
[live retry](evidence/tv5-qa-tdd-20261027/cli-live-retry.json). Không POST REST mutation hoặc tự review/request từ CLI.

### Tại sao không trùng test cũ?

103trước chỉ nối Product→Catalog, còn Order preflight có unit tests với records tự dựng.
12ca mới nối actual Product lifecycle/JPA/Flyway/Catalog với **actual pure Order preflight** qua
record conversion chỉ có trong QA. FD03trước chỉ concurrent successful replay, main mismatch test
chỉ tuần tự; FD05kết hợp valid/mismatch contention và so sánh nguyên financial/outbox/fingerprint.
CLI27tests cũ chưa bao phủ HTTPException khi JSON body đang đọc dang dở.

| Case | Hành vi được kiểm tra | Kết quả đúng boundary |
|---|---|---|
|A (1)|Commit deactivate/edit/reapprove10000→1000000000VND; stale cart expectation|PRICE_CHANGED; mọi snapshot bị giữ lại; fresh expectation trả đúng giá mới, không sửa expectation cũ|
|B (2)|Commit deactivate hoặc suspend qua application service|PRODUCT_NOT_SELLABLE; pure preflight từ chối cả batch|
|C/F (5)|Một absent Product; missing Store; unknown House; stale source; một Seller bị gán hai Store|ABSENT khác DEPENDENCY_UNAVAILABLE; không usable READY subset, unverified source không tạo consumer response|
|D (3)|Positive reversed request order/two Store/exact UnicodeSKU; forged expected Store hoặc Seller|Exact provider ProductID→SKU/Store/Seller; OWNERSHIP_MISMATCH không cho caller chọn lại authority|
|E (1)|Một Product write trước preflight rejection trong outer transaction|Exception rollback Product write; Product/Category và Order/children/items/attempt/outbox/journal observations giữ nguyên|

Hai PostgreSQL16.6 containers disposable, Product context/migrations thật; Order V1/V2 migrations
được load trực tiếp từ source, không copy/sửa SQL. Chỉ Order **classes** vào QA classpath, không
đưa Order application.yml/Flyway resources vào Product context. Dùng synthetic two-Store/actor/House facts.

**Giới hạn side-effect:** Order tables ban đầu rỗng; không CheckoutService/Order writer/Inventory transport
được wire ở seam này. Invariance là quan sát của phép composition đó, không chứng minh production Checkout
đã gọi preflight trước reserve, không chứng minh Inventory reservation absence hoặc distributed rollback.
Không query Inventory DB. Financial aggregate rollback/outbox riêng được kiểm trong165suite ở99.
Không HTTP/E2E, không current JWT/Store/House proof, không chọn DTO/status/error wire thay owners.

## C. Lượt test và source riêng biệt

| Suite | Exact HEAD / source | PASS | FAIL/error | SKIP | Evidence |
|---|---|---|---|---|---|
|Actual postmerge90 main: Product/Seller/Order|7d68e11ce3327aa0ac423938ab9d97a174438b78|1144|0|0|[Receipt](evidence/tv5-qa-tdd-20261027/postmerge-main.json)|
|Harness ngay sau sync|976a08c2ff0527c279e6d2629b0844f8cbc27c37|5PG|0|0|[Receipt + historical runner-label normalization](evidence/tv5-qa-tdd-20261027/integration-synced.json)|
|Expanded QA first|976HEAD+explicit new-source manifest|17PG|0|0|[Receipt](evidence/tv5-qa-tdd-20261027/integration-first.json)|
|Expanded QA committed|53658de1f66140abbf293efb073486a17c2850ac|17PG|0|0|[Receipt](evidence/tv5-qa-tdd-20261027/integration-committed.json)|
|Finance first|385HEAD+FD05manifest|5PG|0|0|[Receipt](evidence/tv5-qa-tdd-20261027/finance-first.json)|
|Finance committed regression|931feee45ac3f17293a4f200c604010fce63bf39|128unit+34PG+3originalFIN=165|0|0|[Receipt](evidence/tv5-qa-tdd-20261027/finance-committed.json)|
|CLI RED|8bfHEAD+new tests, old CLI|27|2ERROR|0|[Receipt](evidence/tv5-qa-tdd-20261027/cli-red.json)|
|CLI committed offline|c25c67879b642b9a3521392dc02e648413ee5eca|29|0|0|[Receipt](evidence/tv5-qa-tdd-20261027/cli-committed.json)|
|CLI live retry|c25c67879b642b9a3521392dc02e648413ee5eca|18/18metadata records|0collector errors|N/A|[Receipt](evidence/tv5-qa-tdd-20261027/cli-live-retry.json)|

1144 = Product450unit+153PG, Seller246unit+138PG, Order128unit+29PG. Source contains merged90/0f9c;
Git ancestry and actual merge receipt verified. This **is actual postmerge main**; old978at273is historical.
ProductPG153includes33SQLcandidate tests; those do not prove new production persistence. Ordinary local
Order build does not run taggedPG; the explicit postgresIntegrationTest task was included. OriginalFIN3uses
its own init task. CLI live counts are metadata records, not application tests. Do not add repeated runs
or different SHAs into a release total. All commands/timestamps/environment/exit/source manifests/log/XML
hashes in receipts. Java21.0.7/Gradle8.10,UTC/API1.44; no Docker volume/existing-data deletion.

## D. Contract và blocker

| Phạm vi | Owner | Trạng thái / next action |
|---|---|---|
|MD01/MD02/Product unit MD03|Approved business receipt84 +technical A/B88|APPROVED narrow price slice: explicit VND, integer1..1b/no-rounding; không xin ký lại|
|S01 local boundary baseline|TV1/TV2/consumers|Technical design đã xác nhận; không thay runtime current authority|
|Visibility97|TV1 decided;TV2 CONFIRMED|Source vẫnaa790f4: WAITING_TV2_FIX. Chạy nguyên8diagnostics sau ownerfix; không hỏi lại policy|
|C01/E01 sharedwire/House/freshness|TV1/TV2/TV3, TV5 consumer/business risk|REVISE/PENDING; record conversion QA không phê duyệt wire hoặc residual read→commit race|
|C06/reservation/recovery|TV3/TV4/TV1|PENDING runtime/authority; no physical Inventory side-effect proof|
|FullM01/Finance/COD/refund/settlement|Business authority +TV3/TV4/TV5|PENDING/BLOCKED; không bật flag hoặc dùng fixture fee làm policy|
|Search91 mới|TV2/TV5 reviewer|Đã sync lêne19624e; review nguồn mới là phần riêng, không tiếp tục nói WAITING_TV2_SYNC|

Không có production bug mới được chứng minh trong Product/Order ở lượt này. Bug CLI đã tự sửa đúng
ownership; known visibilityP2cần TV2 sửa. Chưa có runtime/payment/settlement/Store-finance-query implementation
và business gate để viết acceptance tương ứng; không tạo fake settlement table/rules hoặc test trùng.
Không thay Auth/Gateway của TV1; dependency candidate147PASS/2SKIP từ checkpoint trước không gọi là lượt mới.

## E. Bàn giao / checklist

- [x]103sync actualmain, retargetmain, giữ5old+12new tests; compile/focusedPG/regression/evidence/commit/push.
- [x]99FD05gap, focused5PG và exact committed165PASS; không đổi production/Finance policy.
- [x]100real RED→GREEN/refactor/regression29; live retry18/18; preserve first failure and read-only safety.
- [x]93QA/RTM/checklist cập nhật; default reviewer checked after pushes, existing requests not duplicated.
- [ ]104ownerfix→8GREEN; chưa có fix nên không rerun hoặc tuyên bố GREEN.
- [ ]Protected Product HTTP/live Seller→Product→Cart→Checkout, currentHouse/authority/C06/fullM01/Finance acceptance.

Reviewers hiện có:103TV2+TV3;99TV2+TV3+TV4;100TV2;104TV2. Không selfApprove/Merge/resolve thread khác.
CLI snapshot18open PR không thấy TV5ChangesRequested hoặc TV5OpenThreads; đó là metadata observation,
không phải kết luận tất cả source đã review. Issue5/12 OPEN/PARTIAL; không có tool-approval blocker.
Trong phạm vi TDD đã audit, chưa tìm thấy gap độc lập khác cần viết ngay; việc tiếp theo là ownerfix97
và các gate runtime/business, cùng fresh review91ngoài nhóm code QA này.


## Checkpoint cuối — owner fix và Search được tích hợp trong lúc bàn giao

Phần WAITING_TV2_FIX/91fresh-review phía trên là checkpoint lịch sử trước khi owner push.
TV2 đã push97/d376176 và main đã merge91tạif888725. Đã đọc delta policy/application/owner tests,
đồng bộ branchQA riêng và chạy lại; không sửa production của TV2 hoặc tám assertions gốc.

| Source đã test | Kết quả hiện tại | Evidence |
|---|---|---|
|104/a0c9d4b, production tree khớp97/d376176|519unit+205PG+8originalvisibility=732PASS,0FAIL/SKIP,exit0|[Owner-fix receipt](evidence/tv5-qa-tdd-20261027/visibility-owner-green.json)|
|103/6d99953, production tree khớpmainf888725 sau91merge|461unit+188PG+17QA=666PASS,0FAIL/SKIP,exit0|[Latest-main QA candidate receipt](evidence/tv5-qa-tdd-20261027/integration-latest-main.json)|

**Visibility P2 FIXED_VERIFIED tại private handler/PG boundary.** Original Java8test SHA256
`cc95a20ea9f90f122a8803a68b508f3a794ee8a94c8876845eee105a9e1fe679` giữ nguyên từ51be73e;
originalRED4PASS/4FAIL không bị sửa/xóa. Bốn foreign/missing parity cases nayGREEN, bốn controls
về409/selfmoderation403/scope403/dependency503 vẫnGREEN; rollback/no-write assertions giữ nguyên.
Đây là một RED→GREEN acceptance trace cho ownerTV2fix, tách khỏi một CLI TDDcycle doTV5tự sửa.
Không tính hai trace thành hai production fixes củaTV5. Không thêm test trùng: owner đã bổ sung
9mode scope-refusal/default/null/unavailable/mismatch/ownership/status unit cases cho cả4operations,
plus invocation-order control; original8QA cung cấp actualPG/rollback coverage.

103giữ nguyên5old+12new cases, thêm syncmain/README; không thay test logic theo production fix97.
104merge owner source +README/task-description update, vẫn Draft chờreview; không selfApprove hoặc
resolve thread khác. Protected Product HTTP/JWT/currentStore/House và fullM01/Finance production
vẫn BLOCKED/PENDING. SharedC01E01/C06 không tự được duyệt bởi record conversionQA. Các lượt test
trên tách riêng với actualpostmerge90main1144PASS tại7d68; không cộng thành release total.

- [x] #103 đã tương thích cả merged90và91/Search tại scoped candidate6d99953,17QA và ProductregressionPASS.
- [x] #104 nguyên8GREEN/ownerfixd376 verified; giao evidence cho TV2/TV1, giữoriginalRED.
- [ ] Review PRQA, protectedHTTP/currentauthority, House/freshness/C01E01/C06/fullM01/Finance/liveE2E.
