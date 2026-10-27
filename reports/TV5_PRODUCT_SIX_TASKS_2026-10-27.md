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
