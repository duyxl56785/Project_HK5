# TV2 Product Persistence — checkpoint và kế hoạch

Refs #2 #9 #15. Ngày 23/10/2026 (Asia/Saigon).

## Baseline và phạm vi

- Base/main: `e55f6b893799afe96b56297a86b1884578f4567c`.
- Branch riêng: `feat/tv2-product-persistence`; checkout gốc và các worktree cũ giữ nguyên.
- **Production Product persistence BLOCKED**. Người dùng cho phép hướng B: sửa/test candidate độc lập;
  không tạo bảng production thiếu invariant giá rồi phải thay migration sau này.
- Không HTTP, Auth/JWT/Gateway, Catalog live adapter, upload, moderation API, frontend hoặc seed production.
- Không sửa Category V1, advisory lock/REQUIRES_NEW, build chung hoặc các cờ Finance/Inventory.

## Contract decision gate

| Decision | Approved source / version / SHA | Implementation permission | Blocker |
|---|---|---|---|
| S01 Store boundary | [TV1 co-sign receipt](https://github.com/duyxl56785/Project_HK5/pull/48#issuecomment-6251503735), `TV1-S01-OD09-20261018-v1` at `c0b485aa62ee3275fe56053bdc40497aedbd3a73`; [Store #52](https://github.com/duyxl56785/Project_HK5/pull/52) merged | Product giữ logical Store UUID; không cross-DB FK/JOIN | Current Store/House authority và HTTP không được chứng minh bởi quyết định boundary |
| SKU identity | [TV4 own-scope decision](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6282124816), [#70](https://github.com/duyxl56785/Project_HK5/pull/70), merged `89917b31ed8842d1318417c43f0d84996d802193` | Exact, immutable, global uniqueness target; Product ID khác SKU; Java UTF-16 length <=128; reject whitespace/space-char ở biên | Production UNIQUE/adapter chưa tồn tại; không suy target thành runtime enforcement |
| Category reference | Local production `categories` V1 và yêu cầu hiện tại | Candidate FK RESTRICT, race/rollback tests; V1 giữ nguyên | Production Product FK và lỗi Category-in-use tích hợp chưa có |
| Product lifecycle | `ProductListing`, `ProductStatus`, `ProductLifecycleService` tại base | Reuse sáu trạng thái, SKU không đổi khi edit; không tạo domain mới | Persisted name/reason/status/version mapping vẫn cần review schema |
| Product money M01 | [Proposal #53](https://github.com/duyxl56785/Project_HK5/pull/53), `M01-PRICE-SCOPE-v1-DRAFT` at `d08f3e5154d7568d754dea8758a7a145ef2ab2b5`; [TV1 review](https://github.com/duyxl56785/Project_HK5/pull/53#pullrequestreview-5579455852) | **PENDING / BLOCKED**: docs APPROVE chỉ cho publication, không business approval | Named delegated business authority + same-version MD01/MD02/unit-cap MD03 signatures; currency/representation/scale/bounds production chưa được cấp phép |
| C01/E01/freshness | `catalog.checkout.v1` candidate v0.2, [consumer response](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6248646211) | Provider/consumer design acceptance, **REVISE overall** | Shared wire/security/privacy/freshness chưa chốt; FOUND không tự chứng minh authority |
| Auth/security | [Auth #80](https://github.com/duyxl56785/Project_HK5/pull/80) Draft at `f88da6d2f87acfa136c955e8d099b9465c72f807` | Foundation chưa wired; giữ deny-all | Không phải deployed JWT/current-role/pair/Gateway; positive HTTP/E2E NOT_RUN |

Audit GitHub ngày 23/10: #71 OPEN/COMMENTED; #72 APPROVED TV1 tại `c0329a6`;
#74 APPROVED TV1 tại `2215d61`; #76 APPROVED TV1 tại `93f6a08`. Tất cả chưa merge tại audit.
Không phát hiện PR Product persistence trùng trong danh sách OPEN. Không approve/merge PR trong đợt này.

## Mapping để review khi gate mở — chưa phải schema được duyệt

| Field domain | Candidate production mapping | Null / constraint | Gate |
|---|---|---|---|
| `id` | UUID PK | NOT NULL, provider-issued | UUID đã có trong domain |
| `sku` | TEXT với deterministic `C` collation và CHECK độ dài (candidate) | NOT NULL, global UNIQUE; không trim/case-fold/normalize; giới hạn Java UTF-16 cần giữ tại adapter | Identity đã chốt; DB UTF-16 parity/immutability strategy cần review; không dùng VARCHAR(n) để tránh tự cắt khoảng trắng dư |
| `storeId` | UUID logical | NOT NULL, không FK hoặc owner copy | S01 boundary chốt; Store existence/eligibility là trusted-source check riêng |
| `categoryId` | UUID local FK `categories(id)` | NOT NULL, ON DELETE RESTRICT, không cascade | Production migration bị giữ cùng Product money gate |
| `name` | VARCHAR(255) dự kiến | Domain strip và Java UTF-16 <=255 | Không tự thêm description chưa có trong domain |
| `status`, `reviewReason` | STRING enum / text giới hạn dự kiến | Reuse domain reason <=500 code points, required cho REJECTED/SUSPENDED, null ở trạng thái khác | Cần approved schema parity, không hardcode ACTIVE |
| `unitPrice`, currency | **Chưa chọn production type/precision/scale/default** | Không suy NUMERIC prototype hoặc cap cũ thành policy | **BLOCKED M01** |
| version/timestamps | BIGINT `@Version`, TIMESTAMPTZ/Instant là hướng xem xét | Nonnegative; UTC/microseconds; REQUIRED atomic write, không copy Category REQUIRES_NEW | Chưa có Product JPA; SQL CAS không phải JPA optimistic-lock proof |

## Kế hoạch thực thi nhỏ, theo hướng B được giao

Files: sửa `backend/product-service/src/test/java/com/projecthk5/productservice/catalog/CatalogSchemaProposalPostgresTest.java`,
`backend/product-service/src/test/resources/proposals/catalog-product-candidate.sql`; checkpoint duy nhất là file này.

- [x] Audit source thật, ownership, migrations, main/PR/Issue và contract; phân biệt candidate với production.
- [x] Chạy baseline Product build tại base: **323 PASS / 0 FAIL / 0 errors / 0 SKIPPED**, exit0/56s;
  285 unit/API/security +14 Category production PG +24 Catalog SQL-candidate PG. Không phải lượt cuối sau sửa.
- [x] Viết regression raw SQL cho toàn bộ Java21 whitespace/space-char ở cả hai biên; thấy RED thật trước khi sửa candidate.
- [x] Sửa CHECK test-only tối thiểu, chứng minh case/NFC/NFD/internal-space giữ nguyên bằng fresh connection.
- [x] Kiểm tra FK Product-create/Category-delete với cả hai thứ tự, quan sát lock blocker thật; rollback sau unique/FK conflict.
- [x] Chạy focused PG, Product unit/integration/build và Seller regression; kiểm XML/exit/diff/secrets/checksum.
- [x] Commit/push code branch TV2; [Draft PR83](https://github.com/duyxl56785/Project_HK5/pull/83), reviewer TV1/TV5;
  Issue2/9 đã append evidence và đọc lại xác nhận, toàn bộ prefix/checklist cũ được giữ nguyên.
- [ ] **Production Flyway/JPA/repository/adapter**: BLOCKED M01, không coi các bước candidate trên là nghiệm thu production.

Review focus: Unicode biên không bị C-locale/btrim ASCII bỏ lọt; case/NFC/NFD không gộp;
FK không cascade/orphan khi tranh chấp; conflict rollback không để marker write; runtime Flyway chỉ có Category V1.

PostgreSQL16 [collation](https://www.postgresql.org/docs/16/collation.html) quy định deterministic equality theo bytes;
[`btrim`](https://www.postgresql.org/docs/16/functions-string.html) mặc định chỉ bỏ ASCII space,
[`RESTRICT`](https://www.postgresql.org/docs/16/ddl-constraints.html) chặn xóa row được tham chiếu.
Candidate hiện có TEXT Store reference, không phải Store UUID production; NUMERIC không scale và TEST-CURRENCY chỉ là thí nghiệm exactness.

## Kết quả review và quyết định đã thực hiện

Read-only review độc lập đã đọc diff, source và RED/GREEN XML tại `69c9981c34c32cc25e9c23787b0ee3707af51dfa`.
Một finding P2 từ baseline: `VARCHAR(128)` có thể cắt excess ASCII spaces trước CHECK.
[PostgreSQL16 character types](https://www.postgresql.org/docs/16/datatype-character.html) xác nhận hành vi đó.
Regression mới dùng raw INSERT `128 chữ A + space` tái hiện **1 FAIL / exit1**, vì không có SQLException.
Ruling: chỉ đổi candidate SKU sang TEXT + `char_length<=128`; vẫn giữ giới hạn character cũ của prototype,
không đổi domain hoặc production contract. Chi phí nếu coi đây là production parity sẽ sai: Java đo UTF-16 units,
PostgreSQL đo code points; adapter/schema production vẫn phải review và test supplementary characters.

Reviewer không phát hiện blocker khác trong phạm vi candidate. Các phần không đánh giá trong review được giữ rõ:
production Flyway/JPA/API (M01 blocked), TEXT Store/experimental price, JPA/DB immutable SKU và name/reason parity,
JWT/current authority/freshness/live E2E. Không dùng review này thay human business sign-off hoặc GitHub APPROVE.

## Verification thực tế — source cuối

Exact code source: **`a4e46636b6763393a2dd9b7146436c665e84bcd5`**, 23/10/2026 09:29 +07.
Checkpoint bổ sung sau source là tài liệu thuần; không gán lượt runtime này cho code chưa được chạy.

```powershell
$env:JAVA_TOOL_OPTIONS='-Duser.timezone=UTC -Dapi.version=1.44'
.\backend\gradlew.bat -p backend :product-service:build :seller-service:build --rerun-tasks --no-daemon --console=plain
```

**BUILD SUCCESSFUL, exit0, 53s.** Một invocation cuối, không cộng các lượt focused/baseline trước:

| Module/task | PASS | FAIL | errors | SKIPPED |
|---|---:|---:|---:|---:|
| Product `test` | 285 | 0 | 0 | 0 |
| Product `integrationTest` | 47 | 0 | 0 | 0 |
| Seller `test` | 236 | 0 | 0 | 0 |
| Seller `integrationTest` | 117 | 0 | 0 | 0 |
| Tổng duy nhất của lượt cuối | **685** | **0** | **0** | **0** |

Product47 = Category production PG14 + SQL-candidate PG33. Seller117 = Seller26 + Store65 + Store SQL-prototype26;
policy/negative closed-route fixtures không phải real JWT proof. Cả năm PG suites có Flyway log xác nhận **PostgreSQL16.6**;
Testcontainers tạo database container mới. Category JPA `ddl-auto=validate`/Flyway/REQUIRES_NEW/advisory-lock regression PASS.
Không có Product JPA entity hoặc production Product migration để gọi JPA validation/optimistic locking PASS.

Các lượt riêng giữ nguyên evidence, không cộng vào 685:

| Run | Kết quả / exit / thời lượng | Phạm vi |
|---|---|---|
| Baseline Product build e55f6b8 | 323 PASS /0 FAIL /0 skip, exit0/56s | Trước mọi sửa |
| New candidate RED | 30 PASS /2 assertion FAIL /0 errors/skip, exit1/27s | U+0009 prefix/suffix được SQL chấp nhận sai |
| Candidate GREEN | 32 PASS /0 FAIL/errors/skip, exit0/24s | Sau Unicode CHECK |
| Product unit | 285 PASS /0 FAIL/errors/skip, exit0/26s | `:product-service:test --rerun-tasks --no-daemon --console=plain` |
| Product integration | 46 PASS /0 FAIL/errors/skip, exit0/39s | `:product-service:integrationTest --rerun-tasks --no-daemon --console=plain`, trước thêm truncation test |
| Truncation RED | 1 assertion FAIL /0 errors/skip, exit1/23s | SKU128+space không bị từ chối trước fix |
| Reviewed candidate GREEN | 33 PASS /0 FAIL/errors/skip, exit0/23s | `:product-service:integrationTest --tests '*CatalogSchemaProposalPostgresTest' --rerun-tasks --no-daemon --console=plain` |

Raw XML/log giữ nguyên byte trong ignored `backend/product-service/build/persistence-evidence/` tại worktree
`D:\monhoc\CNPM\project_hk5\backend\product-service\build\product-persistence-20261023`.
Derived `results.json` được tạo từ XML thật, có suite counts và SHA256 từng XML, không chứa raw credentials.

| Evidence | SHA256 |
|---|---|
| `review-fixed-product-seller-build.log` | `c4f13d50c1512fa9f69cc376fbecf0d8b9f3deb70eb465210656adbf54a60ad6` |
| `results.json` | `a8dfa0f52f446942b51895d7c137e090a2f033c274dd8598ecfb8de733af4988` |
| `red-candidate.log` | `49cdb45860a89e6f50546584e746dff64669b89e1a49eb61f8f82b3a60182eb9` |
| `red-truncation.log` | `f1242caf61374c9cb5e2f901312423a00526205b976057c8eca40a06994d139b` |

Category V1 Git blob vẫn **`5b2e0c50a3aba6cf400dda47d52348862f8799e8`**, trùng base;
local-file SHA256 **`5cbc438007a6d5635019ce8a45a29ade115339737841da23c3e0da9342e16758`**.
Production Flyway path vẫn chỉ V1; candidate được áp dụng thủ công trong test, runtime validate/noop vẫn một migration.
Không commit raw logs/XML/build output. `git diff --check` và staged scope check PASS; targeted secret-marker scan không có finding.

## Publication / nghiệm thu / bước tiếp theo

- [x] Code commits [69c9981](https://github.com/duyxl56785/Project_HK5/commit/69c9981c34c32cc25e9c23787b0ee3707af51dfa)
  và [a4e4663](https://github.com/duyxl56785/Project_HK5/commit/a4e46636b6763393a2dd9b7146436c665e84bcd5) đã push;
  GitHub remote branch xác nhận đúng a4e4663 trước checkpoint tài liệu.
- [x] [Draft PR83](https://github.com/duyxl56785/Project_HK5/pull/83) base main e55f6b8,
  request `lexuanbao258-collab` (TV1) và `nguyenminhduy090-alt` (TV5) đúng một lần; không request tác giả.
- [x] Issue2/9 append-only checkpoint **TV2 Product Persistence — 23/10/2026**: chỉ tick candidate/test evidence;
  API/E2E/production Product persistence giữ `[ ]`. Đọc lại GitHub xác nhận prefix bảo toàn và Issues vẫn OPEN/PARTIAL.
- [ ] CI tại head cuối và independent GitHub review: kiểm trạng thái trực tiếp tại PR83;
  local build PASS không tự là CI hoặc APPROVED. Không auto approve/merge.
- [ ] Production Flyway/JPA/repository/adapter: **BLOCKED M01**. Chủ dự án/TV1 xác nhận named authority/delegation;
  người được ủy quyền + human TV5 và owners ký chính xác price/schema slice. Không chờ toàn bộ MD04–10 nếu price slice đã đủ.
- [ ] Product CRUD API, Auth/ownership, moderation, upload/storage, authoritative Catalog, seed hai Store,
  live E2E: **NOT_RUN / ngoài PR**; cờ Finance và Inventory pair OFF.

Phân loại cuối: **PARTIAL** cho mục tiêu Product persistence, **PASS** cho code/test candidate đã nêu,
**BLOCKED** cho production schema; không production-ready. Khi tiếp tục, đọc checkpoint và GitHub hiện tại;
chỉ sau gate price/schema mới thêm migration version kế tiếp thực tế, mapper/repository/REQUIRED adapter dựa domain sẵn có.

## Môi trường / tiếp tục

Java21.0.12.1 / Gradle8.10 / Windows11. Docker lúc đầu không có Linux pipe; Gradle wrapper bị sandbox
chặn ghi cache. Kiểm tra ngoài sandbox xác nhận Gradle8.10; khởi động Docker Desktop từ bản cài user-local,
Engine29.9.1 đã phản hồi. Chỉ dùng process `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC -Dapi.version=1.44`.
Không đổi cấu hình Docker toàn máy hoặc xóa container/volume dữ liệu cũ.

Raw logs/XML giữ nguyên trong ignored `backend/product-service/build/persistence-evidence/` của worktree này.
Chạy tiếp từ checklist chưa hoàn tất, kiểm GitHub/SHA trước push; không tự mở lại M01 hoặc giả lập authority.
