# TV5 — sáu nhiệm vụ Product/Catalog, 27/10/2026

Refs #5, #12, #90, #91, #97, #103, #104. Đã xác minh tài khoản `nguyenminhduy090-alt`.
Baseline main `a705052f93e57d4316c04dba0ef96e923cc44cd2` chứa merge #89 tại `871bd0e` và #94 tại `a705052`. #90 vẫn OPEN ở `273a44d`; #91 chưa đồng bộ; #97 là private handler ở `aa790f4`.
[HEAD/base/reviews/CI/merge và Git blob](evidence/tv5-product-six-tasks-20261027/context.json).
`merge_commit_sha` của một PR chưa merge có thể là synthetic merge; không dùng nó để chứng minh Task2 đã hoàn thành.

|STT|Nhiệm vụ|Status|Test / bằng chứng|Blocker / Next action|
|---|---|---|---|---|
|1|Review #90 tại273|DONE, formal APPROVED|978 PASS, 0 FAIL/errors/SKIP; toàn bộ6file/connected source và CI được kiểm tra.|Đã submit mới theo ủy quyền trực tiếp; [review5661077942](https://github.com/duyxl56785/Project_HK5/pull/90#pullrequestreview-5661077942) được GitHub xác nhận đúng reviewer/HEAD. Không tự merge.|
|2|Post-merge #90|WAITING_MERGE / NOT_RUN|GitHub merged=false; main1833 vẫn chưa chứa #90; baseline a705 cũng chưa chứa. 978 là candidate test.|Sau merge được owner thực hiện: fetch main/merge SHA thật, kiểm ancestry, chạy Product+Seller và explicit QA task; lưu log/XML mới.|
|3|Review #91 sau sync|WAITING_OWNER / WAITING_TV2_SYNC|HEAD491 và7file Search chưa đổi; CI cũ xanh không thay nghiệm thu nguồn sync mới.|TV2 sync sau #90+post-merge GREEN, giữ cả batch projection và search fragment trong Repository; rồi review/test HEAD mới.|
|4|QA #97|PARTIAL / WAITING_OWNER|Regular suite603 PASS; diagnostic riêng[104](https://github.com/duyxl56785/Project_HK5/pull/104) tại51be73e:4PASS/4FAIL,exit1,0errors/SKIP.|TV1 đã DECIDED narrow404exception; chờ TV2 xác nhận/sửa.4FAIL là read/edit/submit/deactivate foreign403≠missing404; protected HTTP vẫn BLOCKED.|
|5|House/freshness/C01/E01|WAITING_OWNER; ma trận đã hoàn thiện|[Nguồn, quyết định hiện có và8câu hỏi](../docs/TV5_PRODUCT_CATALOG_INTEGRATION_GATES.md). Product price slice đã duyệt vẫn giữ hiệu lực.|House authority/assignment, current adapter, residual race/lease, wire/error/privacy và C06 còn thiếu xác nhận.|
|6|Chuẩn bị Seller→Product→Cart→Checkout|PARTIAL|[PR#103](https://github.com/duyxl56785/Project_HK5/pull/103), commit `151361bac8010a4cf5ba890db99429020e291348`:5 PG mới PASS, fixture2Store, task Gradle riêng và8kịch bản live có gate.|Chạy được Product→Catalog PG; chưa chạy Seller login, Cart/Checkout/Inventory qua HTTP. Live E2E BLOCKED/NOT_RUN.|

## Các lượt test thực sự thực thi

|Invocation|Exact source|Kết quả|Evidence|
|---|---|---|---|
|#90 candidate|273a44db9a24e5aac69e7d9f9e8e0208c9f963b7|450 Product unit +144 Product PG +246 Seller unit +138 Seller PG =978 PASS, exit0|[Lệnh/thời gian/cases/raw hashes](evidence/tv5-product-six-tasks-20261027/pr90.json)|
|#97 private preparation|aa790f422a8b3ee698f53e74c455edae7b77d6fa|488 unit +115 PG =603 PASS, exit0|[Receipt](evidence/tv5-product-six-tasks-20261027/pr97.json)|
|QA mới sau commit|151361bac8010a4cf5ba890db99429020e291348|5 PG PASS, exit0, working tree sạch|[Receipt](evidence/tv5-product-six-tasks-20261027/composition-committed.json)|
|QA ban đầu trước commit|273base +manifest4file mới|5 PG PASS; lượt riêng, không cộng lại|[Manifest/receipt](evidence/tv5-product-six-tasks-20261027/composition-initial.json)|

Tất cả các lượt trên:0FAIL/errors/SKIP. Java21.0.7, Gradle8.10, Docker29.8.1, PostgreSQL16.6 Testcontainers; process-only `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC -Dapi.version=1.44`.
Raw log/XML giữ riêng, nguyên byte/SHA256; không publish credentials, không xóa volume/dữ liệu sẵn có.72 raw-artifact hashes (gồm 4 raw artifacts của hai lượt diagnostic RED) đã đối chiếu khi soạn evidence. Không cộng các snapshot thành release total.

Lệnh #90: `backend/gradlew.bat -p backend :product-service:build :seller-service:build --rerun-tasks --no-daemon --console=plain`.
#97 chỉ chạy `:product-service:build` với cùng options. QA mới dùng init/source directory riêng, xem README trong PR103; normal `build` không tự chạy task này.
Product PG144 gồm36lifecycle,46batch,23production persistence,1migration,14Category và24SQLcandidate. Seller PG138 gồm26Seller,65Store,21currentfacts và26SQLproposal. Candidate/proposal không thay production schema acceptance.

## Review #90 và giới hạn approval

Sáu Git blob của PR không đổi từ6a290ff sang273; HEAD mới nhận lifecycle #89 và Seller facts #94 đã merge. Đã đọc toàn bộ diff, connected source, comments/reviews/CI và không có unresolved thread/CHANGES_REQUESTED.
CI40423992783 SUCCESS là bằng chứng riêng, không cộng vào978. Lượt978 là test mới do TV5 chạy tại273, không mượn kết quả TV2.

Review cũ5653400658được gửi26/10 tại6a, dù GitHub đã gán commit metadata của nó sang273. Sau QA978và ủy quyền trực tiếp mới, TV5 đã thực sự gửi [formal APPROVED5661077942](https://github.com/duyxl56785/Project_HK5/pull/90#pullrequestreview-5661077942) lúc2026-10-27T01:05:26Z, đọc lại stateAPPROVED/reviewernguyenminhduy090-alt/head273. Đây là lần submit mới, không phải suy approval từ metadata cũ. [Draft lưu lịch sử](TV5_PR90_REVIEW_DRAFT_2026-10-27.md).
Không duyệt Auth runtime, protected HTTP/E2E, House/MD07, full Finance/COD/Settlement, flags/deploy/merge.

## Security/API #97 và phần chưa kiểm chứng

Đây chưa phải Product HTTP API: không Controller/route/authority bean/JWT binding. Test tích hợp dùng PostgreSQL thật nhưng principal/Store policy là fixture.
Strict JSON, authority-before-access, role/Store denial, no-self-moderation, version/rollback và allowlisted response được kiểm tra trong scope đó; không suy ra protected HTTP401 hoặc current SELLER proof.

Known P2: foreign existing Product tạo403, missing Product tạo404. [TV1 decision6430723502](https://github.com/duyxl56785/Project_HK5/pull/97#issuecomment-6430723502) vừa chốt ngoại lệ cho future protected owner GET/edit/submit/deactivate **sau khi đã xác minh actor/current Store scope độc lập**: cùng404 PRODUCT_NOT_FOUND, cùng generic message. Giữ403cho role/Store scope denial và self-moderation,409cho conflict hợp lệ,503cho source không kiểm chứng được. Global Authv1.0 không bị sửa.

TV2 còn phải xác nhận/revise và triển khai patch trong source owner. TV5 đã chuẩn bị [Draft diagnostic#104](https://github.com/duyxl56785/Project_HK5/pull/104) ở commit`51be73e62de9ef20b47a838b53c0308a024268ef`, base#97aa. Dùng lại TEST FixturePolicy của owner, không thêm production authority. Exact committed run **4PASS/4FAIL/0errors/SKIP,exit1**: bốn parity assertions RED; no-write/rollback checks chạy trước assertions; bốn controls giữ same-Store/409, self-moderation403 và scope403/dependency503. Không global-remap403→404 để làm test xanh. [Original RED](evidence/tv5-product-six-tasks-20261027/visibility-initial-red.json), [committed RED](evidence/tv5-product-six-tasks-20261027/visibility-committed-red.json).

603regularPASS không khép diagnostic này;4controlsPASS không là fixPASS. Giữ nguyên log/XML/acceptance, yêu cầu8PASSở exact owner fix SHA mới trước gọi diagnostic GREEN. Đây là private Failure-record test, chưa chứng minh actualHTTP401/JWT/currentStore/body/headers; phải lặp real HTTP khi binding được duyệt.

## Code và bàn giao

PR[103](https://github.com/duyxl56785/Project_HK5/pull/103): branch `codex/tv5-product-catalog-composition-qa`, base `feat/tv2-catalog-postgres-batch-source` vì90chưa merge. Chỉ4file `tests/integration/**`: Java suite, initGradle, JSONfixture, README. Không sửa production Java, migration, shared build, API, secret hay flag. TV2/TV3 được request một lần; không self-review.

Khoảng trống được bổ sung là seam actual ProductApplicationService writes→actual Catalog reads; các suite cũ kiểm hai component riêng.5ca mới kiểm committed visibility, reprice/whole-batch withholding/giữ value object cũ, cross-Store denied write rollback, unknown House sau write và rollback sau approval.
Chúng không chứng minh durable Order snapshots, remote authority hay live E2E. Test-only PR không tạo thêm business gate cho90. Nếu merge90trước, owner có thể retargetQA về main rồi chạy lại final source; nếu nhận QA vào parent trước, phải kiểm lại HEAD parent thay đổi.

- [x] Nhận diện merge89/94; QA mới đúng273/aa, CI/diff/review/nguồn test đã đối chiếu.
- [x] Code QA151361b có5PG PASS, push/PR103/reviewers đã xác minh.
- [x] House/freshness/visibility/C01E01 question matrix và8live cases đã chuẩn bị.
- [x] Formal APPROVED mới cho90/head273 đã submit/readback theo ủy quyền.
- [ ] Merge90+postmerge GREEN;91sync/review mới.
- [ ] Protected97HTTP, current House/provider, C06/Checkout, full money/COD/settlement/live E2E. Issues5/12 OPEN/PARTIAL; Finance BLOCKED, flags không đổi.

## Main tiến trong lúc làm

Sau baseline a705, main đã lên `1833dd4e5baf61683adaea34a54b6bb9a239e43d` qua #83/#77/#78. Production Product/Seller và root build không đổi, nhưng SQL-proposal tests của Product có delta.978chỉ thuộc candidate273; không áp lại nó cho main1833hoặc future postmerge90. Trước/sau merge phải dùng đúng final source và đếm XML mới, không ép tổng test bằng số cũ.


## Follow-up TV5-PRODUCT-DELTA-20261027 — sau checkpoint ban đầu

Tài khoản vẫn `nguyenminhduy090-alt`; main vẫn `1833dd4`. Đã đọc lại toàn bộ diff/commits/reviews/comments/threads của90/91/97, các dependency và Issue owners. [Delta/source/CI](evidence/tv5-product-followup-20261027/context.json). HEAD và Git blobs thuộc diff của90/91/97 không đổi; không tạo PR/test trùng và không gửi approval90 lần nữa. Hai mục local `.vs/` và `reports/tv5-m01-local/` được giữ nguyên.

| Ưu tiên | Nhiệm vụ | Status | Evidence | Blocker | Next action |
|---|---|---|---|---|---|
|1|Review90|DONE|Formal5661077942 APPROVED đúng273; CI SUCCESS,0open threads;978 là lượt exact-head đã lưu, không chạy lại lượt này|Không còn technical blocker TV5 trong scope Batch nội bộ|TV2 thực hiện merge theo quy trình|
|2|Sau merge90|WAITING_MERGE/NOT_RUN|GitHub merged=false, main chưa chứa90|Owner chưa merge|Sau actualmerge: fetch/ancestry và Product/Seller/PG regression mới|
|3|Review91 sau sync|WAITING_OWNER|491 chưa đổi; approval/CI cũ không thay review final source|Chưa sync sau90|TV2 sync rồi TV5 review/test delta|
|4|Product97|PARTIAL/WAITING_OWNER|aa chưa đổi;603regularPASS và diagnostic51be4PASS/4FAIL là receipt checkpoint trước, không gọi là lượt mới|TV2 chưa ACK/sửa narrow404; protected handler/authority không đăng ký|TV2 CONFIRMED/REVISE và owner fix; chạy nguyên8acceptances trên fix SHA|
|5|House/freshness/C01E01|WAITING_OWNER|Ma trận H01/H02/F01/F02/C01/E01/V01/I01 vẫn đúng, không có sign-off mới|House, residual race/remote adapter/shared wire và C06|Owners xác nhận phần chưa chốt; không hỏi lại Price-only/S01/TV1V01|
|6|Chuẩn bị live flow|PARTIAL|103README sửa gate; exactdec62e9 có5PGPASS mới. Dependency102 có147PASS/2SKIP mới, gồm10AuthHTTPPG|Không có Seller role-sync/currentStore, protectedProduct, shared providers/Checkout/C06|Không gọi Auth/Gateway hoặc Product→Catalog riêng là full liveE2E|

### Lượt chạy mới của follow-up này

1. **#103 tại `dec62e903eaa3fec0d768db7da46262b072b25b0`: 5 PG PASS,0FAIL/SKIP,exit0.** [Command/cases/raw hashes](evidence/tv5-product-followup-20261027/composition.json). Chỉ README đổi để dẫn TV1 decision6430723502 và chờ TV2 implementation; Java/init/fixture blobs không đổi từ151361b. Không tạo test mới không cần thiết. Push lên chính branch `codex/tv5-product-catalog-composition-qa`; TV2+TV3 requests giữ nguyên.

2. **#102 tại `36ad517174ef3911bf794ef4ac568647cd9eb4eb`: 147 PASS,0FAIL,2SKIP,exit0.** [Command/cases/raw hashes](evidence/tv5-product-followup-20261027/auth-gateway.json). TV5 tự chạy source mới, tách khỏi owner report. Auth regular110PASS/2SKIP; current-authority5PGPASS; Gateway32PASS gồm10realGateway→Auth bootJar→PostgreSQL tests và22unit/transport tests. Bộ Auth regular gồm28PG; không cộng các lớp/suite con lần nữa. Hai SKIP là DatabaseReadinessTest (thiếu opt-in/DB provision riêng) và EmailInventoryTest (thiếu inventory JDBC config); NOT_RUN external data checks, không là PASS. Testcontainers disposable và loopback; giữ runtime log/XML/các Auth child logs riêng nguyên byte/hash, không xóa dữ liệu/volume cũ. Flags chỉ được test bật trong fixture như source owner, không đổi production/deployment config.

Lệnh mới: `backend/gradlew.bat -p backend --init-script tests/security/auth-current-authority.init.gradle -Ptv5AuthAuthoritySources=<absolute QA dir> :auth-service:test :auth-service:tv5CurrentAuthorityRegression :api-gateway:test :auth-service:bootJar :api-gateway:bootJar --rerun-tasks --no-daemon --console=plain` (đường dẫn thực được ghi trong JSON). Java21.0.7/Gradle8.10/Docker29.8.1/PostgreSQL16,UTC/API1.44. Không sửa test/assertions/production owner để tạo kết quả này. Không cộng147với978/603/5 thành release total.

Source102 mới thay Gateway HTTP/error/CORS và current-account transaction so với a4; đây là tiến bộ candidate có HTTP thật, không còn mô tả dependency Auth chỉ có mock. Nó vẫn Draft/chưa merge; không chứng minh approved/current Seller grant synchronization, ProductAccessPolicy, Store/House hay protected Product HTTP. Không tự đổi quyết định review hoặc resolve thread khác.

### Coverage Product API còn có thể kết luận

| Yêu cầu | Bằng chứng hiện có | Trạng thái tại boundary thực |
|---|---|---|
|Missing/invalid/expired JWT, current role intersection|102 HTTP/Auth kiểm token/current account trên candidate36ad;97 không có JWT adapter|Auth candidate được test riêng; protected Product401 vẫn BLOCKED/NOT_RUN|
|Không tin actor/role/store do client khai|97 strict JSON/handler authority-before-access; `invalidAuthorityOutputFailsBeforeParsingOrDatabaseAccess`, `unknownAndDuplicateFieldsCannotSmuggleIdentityOrState`|Prepared contract/fixture proof, không phải current Auth→Store binding|
|Cross-Store create/read/edit/moderation|97 `otherSellerCannotAccessStoreOrProduct`, DB state unchanged|Private PG controls; future protected routes chưa có|
|Conceal foreign/missing|104 nguyên8acceptances; known4parityFAIL và4controlsPASS|OPEN/WAITING_TV2 theo TV1 scoped decision; không global403→404|
|400/409/sanitized errors/exact money|97 JSON/error/lifecycle suites trong603 receipt; source không đổi|Private mapping có evidence; chưa phải HTTP wire acceptance|
|Price/version/response privacy|97 allowlist không ownerReference/reviewReason; exact strings|Serialization preparation; không suy header/cache/browser behavior|
|Search/filter/pagination visibility|91 cần sync sau90;97 chưa có Store-scoped list endpoint|WAITING_TV2_SYNC/HTTP_NOT_RUN|
|Seller→Product→Cart→Checkout|103fixture2Store/5PG +8live scenarios;102chỉ identity slice|Full protected flow BLOCKED; không giả endpoint hoặc expected commission/net|

Đã tick trong checklist phần review90, QA harness/evidence và chuẩn bị matrix ở checkpoint trước; không tick lại nhiệm vụ production hoặc tạo commit rỗng. Không có finding production mới để gửi trùng. Các lời bàn giao90/97 còn hiệu lực. FullM01/MD04–10/Finance production vẫn PENDING/BLOCKED; Price-only MD01/MD02/unitMD03 đã được duyệt không bị thu hồi. Issue5/12 OPEN/PARTIAL.
