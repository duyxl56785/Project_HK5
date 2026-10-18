# TV5 — M01 authority, Finance preflight và nghiệm thu QA

18/10/2026, Asia/Bangkok. Refs #5/#12/#28/#37/#41/#44. **PARTIAL / DIAGNOSTIC_RED / OWNER_CONFIRMATION.**

## 1. Phạm vi, source và công việc đã hoàn thành

- Đọc AGENTS/CONTRIBUTING/OWNERSHIP, Issue5/12 và toàn bộ paginated commits/files/reviews/comments/timeline của28/37/41/42/43; GraphQL threads28.
- PR41/42/43 đều MERGED; ancestor checks của cả ba với21f6d53 exit0. Không push hoặc request review trên PR đã đóng.
- PR28 đã tiến từ21f6d53 tới c97a656/5ce0630 rồi **ca639bbc5e810d6206ec0ed221ebfb308ced227e** trong lúc QA. Đã đọc đủ hai delta và giữ bằng ordinary merges vào branch TV5, không push TV3/main.
- c97a656 sửa malformed Cart/expected items và cap1–1000;5ce0630 sửa Cart add/input/trusted identity +test. Đây là bản sửa **của TV3**, không nhận công của TV5; không sửa FIN-01/FIN-06.
- PR37 **ef62086048e2c7a00ecee16db0cc3cde2413deb6**, candidate v0.2 từ42cc4e7 vẫn giữ nguyên. Test SQL candidate mới không phải Store/Product production persistence hoặc quote/lease.
- JWT thread28 [discussion4291061468](https://github.com/duyxl56785/Project_HK5/pull/28#discussion_r4291061468) đã resolved; các thread replay/scanner cũng resolved. Không tự resolve thêm thread; source không đổi JWT trong các delta mới.
- Root checkout còn .vs/ và gói local trước đó; được giữ nguyên. Làm việc trong worktree riêng, branch ban đầu `codex/tv5-money-qa-acceptance`, [PR44](https://github.com/duyxl56785/Project_HK5/pull/44) **base=feat/tv3-cart-checkout-week1**.
  Owner đã merge PR44 lúc10:48:42UTC tại ccfb38debf8907912aeaa9134fb4fd01d66c3b14 (head1311f8a). Hai commit evidencee280ad9/53dfc1c push sau merge chưa được nhận vào28; phát hiện khi PRhead không tiến.
  Đã tách chúng bằng cherry-pick sang branch **codex/tv5-finance-evidence-handoff** từ đúng ca639bb. Không push PR44 thêm hoặc tạo duplicate QA feature; đây là phần evidence còn thiếu, không kéo source PR28 vào main.

## 2. Thay đổi TV5: tài liệu và bốn regression thật

Commit [209deae](https://github.com/duyxl56785/Project_HK5/commit/209deae) công bố [M01 proposal v0.2](tv5-m01-review/M01_PROPOSAL.md),
[ma trận MD01–MD10](tv5-m01-review/MD01_MD10_MATRIX.md), [reply draft](tv5-m01-review/REPLY_DRAFT.md), [nguồn file/dòng/comment](tv5-m01-review/SOURCES.md).
Sửa packet MD03 đang mô tả chỉ Java int: **Cart API/service/DB +Checkout DTO thực tế1–1000**, không lựa chọn business cap mới.
TV5 được người dùng xác nhận chỉ tổng hợp/đề xuất; người phê duyệt nghiệp vụ cuối cùng **chờ TV1/chủ dự án xác nhận**.

Commit [9b765e5](https://github.com/duyxl56785/Project_HK5/commit/9b765e5) thêm `FinanceCheckoutPreflightTest`, sau khi đọc các Finance/parity/config/PG/helper/replay/Checkout tests hiện hữu để tránh trùng:

| ID mới | Test method | Điều chứng minh / giới hạn |
|---|---|---|
| F16-16 | closedFinanceGateCannotReserveStockOrWriteOrder | MoneyRules chưa approved chặn cả service orchestration trước Inventory/writer; claim đã biết được release, attempt fail known. Không bật deployment flag. |
| F16-17 | conflictingHouseFactsWithinOneStoreCannotSelectAnExemption | Hai facts cùng Store nhưng khác House bị reject trước reserve/write; không lấy một dòng miễn phí cho cả Store. Không chứng minh House source thật. |
| F16-18 | matchingPerItemFactsWithTwoSellersCannotShareOneStoreOrder | Hai item riêng khớp Cart/Catalog nhưng cùng Store khác Seller vẫn bị reject; không gộp sai SellerOrder. Không là API/DB Store authorization. |
| F16-19 | currentServiceRoundsOnceForTheStoreRatherThanEachLine | Actual Checkout tạo một child/two lines, gross20/fee1/derived net19 dưới synthetic rate0.05/scale0, không fee2 theo từng dòng. Không ký currency/rounding business hoặc tạo net snapshot DB. |

Cả bốn là **unit với mocked external ports**, không sửa code Checkout/Inventory/Auth, migration, assertions cũ hoặc bật policy.
Settlement/COD/Finance API chưa có production surface để viết test concurrency/isolation thật; không tạo fake repository/DTO để tự tuyên bố chúng PASS.

## 3. Lượt chạy thực tế — không cộng các snapshot

Môi trường thực của TV5: Windows11, Oracle Java21.0.7, Gradle wrapper8.10, Docker Engine29.8.1 Linux,
PostgreSQL16-alpine Testcontainers; `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC -Dapi.version=1.44`.
Docker named pipe bị sandbox từ chối ở probe đầu; chạy ngoài sandbox được cấp quyền thành công. Không ghi Docker unavailable khi PG đã chạy thật.

### A — trước khi owner cập nhật, source21f6d53

Lệnh từ root worktree, init-script dùng absolute path tới file đã có trong repo:

```powershell
backend/gradlew.bat -p backend --init-script <absolute tests/finance/fin06-regression.init.gradle> :cart-service:test :order-service:test :cart-service:postgresIntegrationTest :order-service:postgresIntegrationTest :order-service:fin06Regression --rerun-tasks --no-daemon --continue --console=plain
```

| Task | PASS | FAIL | ERROR/SKIP |
|---|---:|---:|---:|
| Cart unit | 6 | 0 | 0 |
| Order unit | 73 | 0 | 0 |
| Cart PG | 1 | 0 | 0 |
| Order PG | 16 | 0 | 0 |
| Explicit diagnostic | 1 | 2 | 0 |

**Overall exit1,114s**, vì diagnostic FAIL. [Raw log](evidence/finance-acceptance-20261018/21f6d53/runtime.log),
[exact metadata](evidence/finance-acceptance-20261018/21f6d53/runtime-meta.json). Logs/XML được archive trước khi owner sync/re-run ghi đè build outputs.

### B — sau c97a656, source de1f5c6dfee301ffec12ffd0f5c774836d62e3a3

Source này chứa docs209deae +owner c97a656; chưa chứa bốn test mới. Chạy:

```powershell
backend/gradlew.bat -p backend --init-script <absolute tests/finance/fin06-regression.init.gradle> :order-service:test :order-service:postgresIntegrationTest :order-service:fin06Regression --rerun-tasks --no-daemon --continue --console=plain
```

| Task | PASS | FAIL | ERROR/SKIP |
|---|---:|---:|---:|
| Order unit | 75 | 0 | 0 |
| Order PG | 16 | 0 | 0 |
| Explicit diagnostic | 1 | 2 | 0 |

**Overall exit1,59s**. [Raw log](evidence/finance-acceptance-20261018/de1f5c6/runtime.log),
[metadata](evidence/finance-acceptance-20261018/de1f5c6/runtime-meta.json),
[original diagnostic XML](evidence/finance-acceptance-20261018/de1f5c6/order-service/build/test-results/fin06Regression/TEST-com.projecthk5.orderservice.finance.CheckoutMoneyPreflightRegressionTest.xml).

- FIN-06 vẫn gọi reserve/writer trước parent overflow; expected no-side-effect assertion FAIL, DB rollback không đủ đóng finding.
- FIN-01 vẫn response3.002→persist/replay3.00; acceptance FAIL. Không round assertion hoặc đổi policy fixture để qua test.
- Positive control vẫn PASS; không disable hoặc xóa hai test RED. Đó là test lỗi thật, không phải fix GREEN.

### C — intermediate overlay và D — latest synchronized QA source

Sau khi thêm4tests vào de1f5c6, Order unit **79PASS/exit0/17s**; giữ original log/XML và SHA256 test overlay local,
file test đúng bytes đã commit9b765e5. Đây là intermediate check, **không cộng** vào các lượt công bố.

Owner tiếp tục đẩy5ce0630. Đã đọc3file delta, merge thường thành **1311f8a9f14639c4e42c7b8fbf2446a934abfbf2**, rồi chạy lại phần ảnh hưởng:

```powershell
backend/gradlew.bat -p backend :cart-service:test :order-service:test :cart-service:postgresIntegrationTest --rerun-tasks --no-daemon --continue --console=plain
```

| Task D | PASS | FAIL | ERROR/SKIP |
|---|---:|---:|---:|
| Cart unit | 8 | 0 | 0 |
| Order unit, gồm4test mới | 81 | 0 | 0 |
| Cart PG | 1 | 0 | 0 |

**Exit0,27s**, [raw log](evidence/finance-acceptance-20261018/1311f8a/runtime.log),
[metadata](evidence/finance-acceptance-20261018/1311f8a/runtime-meta.json),
[4new test XML](evidence/finance-acceptance-20261018/1311f8a/order-service/build/test-results/test/TEST-com.projecthk5.orderservice.finance.FinanceCheckoutPreflightTest.xml).

Order PG/diagnostic **NOT_RUN trong D**: git diff de1f5c6→1311f8a xác nhận Order production, PG test classes và diagnostic source không đổi.
B là execution provenance gần nhất của chúng, không ghi đã chạy lại ở1311f8a. Money findings vẫn RED với source equivalence đã kiểm.
Normal CI không chạy tagged PG/explicit diagnostic; readiness success không thay hai acceptance FAIL.

### E — sau owner merge PR44 và Cart.claim fix,source0834f23

PR28ca639bb đã nhận4test/proposal từ44 và có thêm Cart.claim key validation doTV3 sửa. Chạy riêng Cart ở
**0834f23e274861adc09a0a42bb265680c1538863** (hai commit cherry-pick chỉ evidence/docs):

```powershell
backend/gradlew.bat -p backend :cart-service:test :cart-service:postgresIntegrationTest --rerun-tasks --no-daemon --continue --console=plain
```

**Cart9unit +1PG PASS,0FAIL/ERROR/SKIP,exit0/33s**; [log](evidence/finance-acceptance-20261018/0834f23/runtime.log), [metadata](evidence/finance-acceptance-20261018/0834f23/runtime-meta.json).
Order source/test/diagnostic không đổi so với1311f8a nên không rerun Order trong E:81unit thuộc D,16PG/diagnostic1PASS2FAIL thuộc B; không cộng snapshots hoặc nói là run mới trênca639bb. Owner merge44 không phê duyệt M01 hay sửa FIN-01/06.

## 4. Contract ledger tại checkpoint

| Contract / trạng thái overall | Quyết định đề xuất | Owner / bên cần xác nhận | Evidence và module được phép sau sign-off |
|---|---|---|---|
| S01 **PENDING** | Store ở Seller DB/service, local Seller FK +unique seller, Product logical ref; trusted current ownership/status/House | TV2 owner; TV1 boundary; TV3/TV4/TV5 consumers | [TV2 source](https://github.com/duyxl56785/Project_HK5/blob/ef62086048e2c7a00ecee16db0cc3cde2413deb6/docs/TV2_CATALOG_CONTRACT_RESPONSE.md#L11). Store persistence/current facts và consumers chỉ sau boundary/security approval. |
| C01 **REVISE** | catalog.checkout.v1 v0.2:1–100 unique IDs, exact decimal strings/explicit currency, ordered complete batch, no usable READY subset khi reject | TV2 provider; TV3 consumer; TV1 caller/delegation; TV5 M01; TV4 SKU | [TV3 scoped acceptance](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6248646211). Shape không cần TV3 duyệt lại; provider/consumer runtime chờ sources/security/M01/freshness. |
| E01 **REVISE** | Candidate200 evaluated batch,400 invalid,401 invalid caller,403 actual scope,503 source unavailable; top-level code/message/errors, explicit ABSENT | TV2 errors +TV3 mapping; TV1 final privacy/challenges/masking | Same scoped response; future controller/Advice/contract tests chỉ sau exact boundary ký. Current403negative không phải positive API. |
| M01 / Finance **PENDING** | VND nguyên/17-digit cap,5%/House0, HALF_UP0 per-Store, immutable snapshots là full candidate cần ký | TV5 tổng hợp; **business approver chờ TV1/chủ dự án xác nhận**; TV2/TV3/TV4 own-scope; TV1 enable authority | [Full MD matrix](tv5-m01-review/MD01_MD10_MATRIX.md). Price/Cart/Order V2+/Finance chỉ sau applicable approvals, không production settlement trong patch. |
| MD07 freshness **PENDING** | Khuyến nghị A last-verified có explicit race acceptance; PhaseB sau journal/recovery. B cần quote/lease đa nguồn chưa có | TV1 +named business approver +TV5; TV2 source, TV3/TV4 recovery | [TV2 conditional recommendation](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246597564). Không TTL/timestamp/hash giả guarantee. |
| Inventory/C06 **PENDING/REVISE** | Exact Product→SKU; persisted T1/T2 IDs, reserve/consume/release lifecycle, bounded exact-payload retry, UNKNOWN hold | TV2 SKU; TV3 journal/consumer; TV4 provider/lifecycle; TV1 scope | [TV4 proposal](https://github.com/duyxl56785/Project_HK5/pull/28#issuecomment-6246603321). Chưa đồng nhất state/event/retry/authority; không sửa owner code. |
| Security **PENDING** concrete pair | Giữ OD-09 baseline; caller-specific credential/path/operation +delegated current actor, no broad Inventory privilege | TV1 +TV2/TV3/TV4 pairs; TV5 current ADMIN COD/Store Finance | [TV3 checkpoint](https://github.com/duyxl56785/Project_HK5/pull/28#issuecomment-6248595524). JWT local test/resolved thread không chứng minh positive live service-pair. |

Không có overall contract mới được APPROVED. TV2/TV3 chỉ ký design scope; source fixes/merge QA không ký thay business/TV1/TV4.
TV2 báo full406PASS tại3ecf9c3 với24Product+26Store SQL candidate cases; **TV5 không chạy lại hoặc kiểm raw XML lượt đó**.
Không cộng406 với kết quả TV5 hoặc coi SQL candidate là source authority, JPA persistence/lease/live E2E.

## 5. Integrity, publication và QA union

- [Manifest](evidence/finance-acceptance-20261018/manifest.json) giữ source/run/path/SHA256 của61 original log/XML/metadata assets công bố,
  exact bytes,0withheld trong đợt này. Marker scan generated password/token/private key/Bearer/Basic/URL credentials/sensitive assignments không có hit;
  không khẳng định đã phát hiện mọi loại secret. Không sửa hoặc công bố lại credentials/evidence lịch sử bị withheld từ PR40.
- `.gitattributes` trong evidence folder dùng `-text` để Git giữ raw bytes. Code/docs diff-check tách khỏi generated raw logs/XML nếu chúng có trailing whitespace;
  không sửa raw bytes để làm đẹp diff. Code/docs diff-check exit0; generated raw mặc định exit2 do CRLF/trailing whitespace nguyên bản.
 61 raw hashes khớp cả local và staged Git blobs; cần per-command core.longpaths=true khi stage trong Windows worktree sâu, không đổi global config.
- Docker volumes: inventory before/after và missing set được ghi trong [static verification](evidence/finance-acceptance-20261018/static-checks.json).
  Không chạy delete/prune/reset; Testcontainers dùng database/container tạm và tự cleanup runtime của nó.
- [TV3 QA union request](https://github.com/duyxl56785/Project_HK5/pull/28#issuecomment-6248595524): TV5 xác nhận **phương án union theo thời gian, giữ toàn bộ lịch sử main +hai checkpoint Finance18/10 và provenance**.
  Xác nhận này chỉ ở scope QA docs, không phải đã merge main, không ký M01 và không blanket ours/theirs. Conflict mới vẫn do owner xử lý normal merge; không làm lại conflict cũ.

## 6. Checklist và việc còn chờ

- [x] Xác minh PR41/42/43 tích hợp và source/review threads; đọc owner updates, bảo toàn bằng merges riêng.
- [x] Công bố M01 proposal/MD matrix/source map; ghi đúng thẩm quyền TV5 và sửa mô tả quantity; không tự ký.
- [x] Thêm4 Finance orchestration regression và chạy unit thật, latest81Order unit PASS; tách các lượt và SHA.
- [x] Chạy tagged PG/diagnostic, giữ original2FAIL +positive control, raw bytes/hash/publication boundaries.
- [x] Push branch TV5 và mở PR44 đúng base; reviewers TV1/TV2/TV3/TV4 đã requested một lần; owner đã merge initial patch. Evidence follow-up bàn giao bằng branch riêng, không re-request PR44 đóng.
- [ ] TV1/chủ dự án chỉ định business signer/operator; owners ký cùng revision MD01–MD10/S01/Security/C01/E01/Inventory/C06.
- [ ] TV3 được phép/wire pre-reserve bounds và V2+ currency/rate/House/net/policy snapshots; original FIN-01/06 phải GREEN trên fix SHA, positive control giữ PASS.
- [ ] TV2 authoritative Store/Product/House/SKU/currency provider +TV1 actual authorization; TV3/TV4 journal/recovery/COD source.
- [ ] Production settlement atomic/unique/concurrent, current Store-scoped Finance queries, live two-Store/COD E2E: **NOT_RUN/BLOCKED**; không tạo implementation giả.
- [ ] Owner review/tiếp nhận PR evidence follow-up và xử lý QA conflict; Issue5/12 đủ nghiệm thu. PR44 initial QA đã merge, không còn chờ tích hợp bốn test/proposal ban đầu.

Các phần độc lập còn thiếu đã xử lý bằng test/docs/evidence, không mở rộng thêm framework/prototype hoặc tuần2–3 production.
Tiếp theo **WAITING_REVIEW / OWNER_CONFIRMATION**, chỉ re-run theo delta thực tế/đúng scope khi owner sửa. Không hứa chạy nền.

## 7. M01 owner decision checkpoint — M01-OWNER-LEDGER-20261018-r2

**M01/MD01–MD10 PENDING**, business approver/operator **chờ TV1/chủ dự án xác nhận**. TV5 là người tổng hợp/đề xuất, agent không ký thay humanTV5.
Source audit mới:28 **5a3788b34ec4a2a91611d88020442a27b9dc7948** (đã nhận45),37 **ef62086**,38 **7a8d18e**,39 **ec2ae5a**, main **ac7ca4f**.
Các PR43/44/45 đã merge, không push/request trên branch đóng. Branch bàn giao mới **codex/tv5-m01-owner-signoff**,
canonical docs commit [942129f](https://github.com/duyxl56785/Project_HK5/commit/942129f95fde9890dc151b6d89e8214d1b9e0df0), [PR46](https://github.com/duyxl56785/Project_HK5/pull/46) base=branch28.

- [x] Cập nhật **tài liệu đang có**, không tạo bản policy khác: [Finance contract](../docs/TV5_FINANCE_CONTRACT_SIGNOFF.md), [packet §§8–11](../docs/TV5_M01_HUMAN_DECISION_PACKET.md).
- [x] Ledger10MD có version/value/owner/confirmer/evidence/effective scope/status/dependencies;6mục MD05–MD10 có choices/benefits/risks/consumer impacts.
- [x] Ghi nhận TV2 price compatibility và TV3 consumer-design acceptance, TV1 OD07/09 DECIDED; không xin lại cùng nội dung hoặc biến chúng thành M01 business signature.
- [x] Làm rõ partial Product price slice: approval riêng MD01/02/unit-cap MD03 +applicable S01/schema/security mới mở TV2 price/currency implementation;
  MD10 reporting không tự là prerequisite cho riêng price column. **Chưa có slice mới thực sự unlocked**, vì human price choices và applicable gates chưa được ký.
- [x] [Derived audit](evidence/m01-owner-ledger-20261018.json):61existing raw hashes/Git blobs đúng,3original diagnostic cases giữ1PASS/2FAIL,
  Order production/diagnostic source tương đươngde1f5c6; doc links/MD completeness/numeric examples được kiểm. **Không có runtime run mới** trong phiên docs này.
- [ ] TV1/chủ dự án chỉ định named business approver và operator; humanTV5/business/owner ký từng MD và choice đúng scope bằng URL thật.
- [ ] TV3 thực hiện FIN-01/06 pre-reserve fix/V2+ snapshots sau applicable decisions, chạy nguyên assertions GREEN trênfixSHA; TV2 provider/House/source vàTV4 COD/journal/recovery có authority.

**Các xác nhận đã có:** TV2 confirmed technical representation; TV3 C01/E01 own-scope APPROVED (overallREVISE), MD02/03/08 necessity acknowledged;
TV1 baseline security DECIDED. **Không có MD business APPROVED mới**. Không cần TV2 lặp chứng minh VND/positive17digits/exact strings hoặc TV3 duyệt lại DTOshape.
MD05–MD10 còn cần người quyết định chọn flow/adjustment/refund/race/snapshots/authority/report basis+four-group definitions; metadata/source enum/SQLCAS không chốt chúng.

### Tiếp tục sau khi hết quota

Tại worktree của branch TV5, đọc PR46 và replies marker `M01-OWNER-LEDGER-20261018-r2` trước, giữ root .vs/ và gói local chưa commit.

```powershell
git -c core.longpaths=true status --short
git branch -vv
git fetch origin
git diff 5a3788b34ec4a2a91611d88020442a27b9dc7948 origin/feat/tv3-cart-checkout-week1 -- backend/order-service/src/main backend/order-service/src/fin06Regression
```

Ghi đúng signer/account/URL/version/choice/scope cho reply mới; không đổi PENDING nếu chỉ có compatibility hoặc module merge. Kiểm actual head/base/OPEN trước commit/push.
Chỉ khi có actual authorized TV3fix và delta liên quan, checkout đúng fixSHA trong branch/worktree an toàn rồi chạy các task độc lập theo [diagnostic runbook](../tests/finance/README.md):

```powershell
$initTV5 = (Resolve-Path 'tests/finance/fin06-regression.init.gradle').Path
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC -Dapi.version=1.44'
backend/gradlew.bat -p backend :order-service:test :order-service:postgresIntegrationTest --rerun-tasks --no-daemon --continue --console=plain
backend/gradlew.bat -p backend --init-script $initTV5 :order-service:fin06Regression --rerun-tasks --no-daemon --console=plain
```

Lưu source/time/command/exit/log/XML/hash từng run, giữ positive control và original RED, không tự sửa assertion/enable policy.
Lượt checkpoint này unit/PG/liveE2E **NOT_RUN**; production Finance/COD/Store queries và toàn Issue5/12 vẫn **PARTIAL/BLOCKED**.
Khi chỉ còn chữ ký/review, dừng ở **OWNER_CONFIRMATION/WAITING_REVIEW**, không tạo test/proposal trùng hoặc hứa chạy nền.

## 8. Superseding source checkpoint — M01-REAUDIT-20261018-r3

PR42/45/46 đều đã được owner tiếp nhận, không còn chờ integration; các checkbox ghép thêm main QA conflict/whole acceptance phía trên vẫn chưa đủ điều kiện tick.
PR28head **8cc5ce4af1beacfe0d8467ec037d55e59a065691** có Checkoutrequestguard mới; không kéo dài source-equivalence5a3788b sangHEAD này.
[New audit/run](TV5_M01_REAUDIT_2026-10-18.md):83Order unitPASS,16Order PGPASS, originaldiagnostic1PASS/2FAIL,exit1/73s,0error/skip.
PositivecontrolPASS; FIN01/06 vẫnRED. [22rawassets+derivedmanifest](evidence/m01-reaudit-20261018/manifest.json),61oldhashes/Gitblobs được kiểm lại; không cộng các snapshot lịch sử.
TV3 MD05/06/08 mới là engineeringproposal, không human signature; canonicalledger cập nhật đúngscope. M01/all10MDPENDING,
businessapprover/operator chờTV1/chủdựánxácnhận, no newproductionunlock; Issue5/12OPEN/PARTIAL.
