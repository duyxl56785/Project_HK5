# TV3 — Final integration handoff, 2026-11-02

**Cập nhật sau publication đầu:** TV2 đã tích hợp P1 vào #147 `44426859`; [kết quả chạy mới trên exact owner HEAD và trạng thái hiện tại](owner-accepted-44426859/README.md). Phần dưới giữ snapshot trước khi tiếp nhận, không còn là trạng thái chờ apply patch mới nhất.

**Source/regression: FIXED_VERIFIED. Overall acceptance: BLOCKED_OWNER.**

Chạy thực tế tại **CODEX TERMINAL — Linux**, không phải Windows PowerShell. Lượt này không thay production code, schema, API hay runtime flags. Không merge PR, review/approve, resolve thread hoặc sửa branch thành viên khác.

## Exact source và thay đổi

- Main: `d446577d2f9268f2212a4ee214ff3d805ee7a1d9`.
- PR #145 trước publication: `81ad9f7712a6fd1d59798c6b7d58e06a0095033f`.
- PR #147: `cb576a6894d88304097862dc2ee7c25373903084`, vẫn OPEN. TV2 đã sửa lỗi compile `items` ngoài scope bằng contract SKU-only; không áp lại patch Product ID cũ.
- Candidate tích hợp: `351fb032bf2c6931a6ce5720940aa3d7169c5e80`, tree **`0204df6929f31d2f903c3798702e1cc51921e383`**.
- Parent đã publish evidence: `3dd5980f71ccce5b05ab518e39bc1e661f956c45`, tree `849d06e63bf935df7f88a38ea086700e4b48f8bf`. Backend của hai commit khớp byte. Backend run bắt đầu tại parent, không có thay đổi backend/input trong lúc chạy; frontend typecheck/discovery chạy tại candidate mới. Không gán bộ test cũ cho lượt mới.
- Candidate graph: `3dd5980` kết hợp main mới với cây tích hợp trước, sau đó `351fb03` chỉ sửa bốn file acceptance. Không đưa toàn bộ source #147 vào nhánh #145.
- Commit test tương đương trên nhánh #145: `59e2bfe60cd60fd5139be948b670dd99ba6faef8`. Bốn file khớp byte với candidate; backend #145 không đổi so với `81ad9f7`. Publication commit tiếp theo chỉ thêm evidence.

Thay đổi mới thực sự nằm ở `frontend/src/features/checkout/acceptance/`: `negative-refusal-contract.mjs`, `negative-refusal-contract.test.mjs`, `negative.live.spec.mjs`, `README.md`. Test chọn contract tường minh bằng `TV3_NEG_REFUSAL_CONTRACT=itemized147` khi deployment đã có #147; không tự đoán contract từ response. Legacy #145 giữ default `legacy145`. Contract #147 phải trả đúng `CHECKOUT_ITEMS_REJECTED` và chính xác Product/reason; không chấp nhận generic error cũ, Product sai, reason sai hoặc bỏ qua extra item.

Patch production P1 ba file đã đầy đủ trong candidate, **không tạo commit production trùng**:

1. `InventoryHttpAdapter.java`: đối chiếu exact SKU/quantity/type/attempt với immutable journal trước HTTP; response SKU chỉ nhận JSON string, thiếu/numeric trả null. `an item` chỉ là message hiển thị, không bao giờ được coi là identity.
2. `CheckoutService.java`: chỉ `INSUFFICIENT_STOCK` với đúng một Catalog-verified Reservation tương ứng mới thành `OUT_OF_STOCK`. Không tin Product ID provider/replay; lỗi khác giữ mã gốc.
3. `CheckoutInventoryBoundaryPostgresIT.java`: regression bằng PostgreSQL thật + HTTP peer fixture cho các ranh giới trên.

Patch bàn giao giữ nguyên byte tại [p1-latest-owner-contract.patch](../final-integration-20261102/p1-latest-owner-contract.patch), SHA256 **`125718f965fe0db570cca9e927584a7b0e22c9bfdf93074f848d5da408ca846b`**. Đây là delta trên source đã kết hợp #145+#147, không phải patch để áp mù vào #147 standalone. Baseline/provenance tại [source-provenance.json](../final-integration-20261102/source-provenance.json). TV2 chưa tiếp nhận phần hardening này vào branch runtime #147.

## Regression mới

| Module | Unit PASS | PostgreSQL PASS | FAIL / ERROR / SKIP |
|---|---:|---:|---|
| Order | 171 | 113 | 0 / 0 / 0 |
| Cart | 13 | 13 | 0 / 0 / 0 |
| Inventory | 27 | 19 | 0 / 0 / 0 |

**Focused: 356 PASS. Full backend: 1.725 PASS, 0 FAIL, 0 ERROR, 16 SKIP, exit 0; 88/88 tasks executed với `--rerun-tasks`.** Focused đã nằm trong full, không cộng hai lần. 16 SKIP vẫn là source gates: 15 cần `TV1_DATABASE_TESTS` và provisioning riêng; 1 cần `AUTH_EMAIL_INVENTORY_JDBC_URL`. Không bật chúng vào database dùng chung để ép xanh. Danh sách chính xác trong `backend-summary.json`.

- Bốn Cart ownership/concurrency probes: **4 PASS**, PostgreSQL thật, exit 0. Lượt đầu FROM-CACHE không được tính fresh; đã giữ log và chạy lại `--rerun-tasks`, 10/10 tasks executed. Probe thêm test source từ `/tmp`, không chỉnh repository production/test source.
- Assertion unit mới: **8 PASS**, exit 0. Đây là Node assertion unit, không phải live HTTP/browser. Nhánh #145 chạy kiểm lại cùng tám test, không cộng thành 16 test độc lập.
- Frontend typecheck: exit 0 trên candidate và nhánh #145. Package/lock khớp runtime có sẵn; chỉ tạo rồi gỡ symlink `node_modules` riêng của lượt này.
- Playwright discovery: **6 discovered, 0 executed**, exit 0. Lượt discovery đầu exit 1 vì CLI và imports nạp hai runtime khác nhau; giữ raw log, sửa lệnh dùng một runtime rồi discovery thành công. Không sửa test để che lỗi và không coi discovery là live PASS.
- Java 21.0.8+9, Gradle 8.10, Docker 28.4.0, Node 24.19.0. JUnit stdout raw xác nhận PostgreSQL **16.15 và 16.6** trong disposable Testcontainers.

Lệnh, cwd, exit và các lượt lỗi/cached trung gian có trong `commands.json`. Init script chỉ chuyển build output vào `/tmp` để bảo toàn JAR đang được demo mount; không skip/disable test hoặc thay assertion/flags. HBA của test giữ nguyên nội dung/mode đã chuẩn bị; không sửa Auth.

Unit có mocks. Các bài Order boundary dùng **real PostgreSQL + HTTP peer fixture**, Cart port được mock. Các bài Inventory PostgreSQL chạy implementation Inventory thật trong test process. Những bài này không chứng minh Gateway/live Auth/Catalog/Inventory transport hay website E2E. Không thay đổi logic Finance/COD/settlement để đạt PASS.

## Compatibility matrix

Các tên dưới đây là testcase/suite có trong XML **lượt mới**, không suy PASS chỉ từ source.

| Invariant/contract | Regression | Kết quả / giới hạn |
|---|---|---|
| Draft commit trước Inventory HTTP; exact Order/SellerOrder/items, shipping, money, outbox | `CheckoutDraftPostgresIT.completeBusinessSnapshotCommitsBeforeHttpAndMatchesPlacedRowsAndOutbox` | PASS, PG thật; Inventory peer fixture |
| Crash sau Draft/T1/RESERVE | `CheckoutDraftPostgresIT` ba parameter `AFTER_DRAFT`, `AFTER_T1`, `AFTER_RESERVE` | 3 PASS, child JVM halt + đọc lại persisted data; không phải live crash injection |
| Atomic PENDING; một sender; immutable tuple, generation fencing | `CheckoutInventoryJournalPostgresIT`, `InventoryOperationJournalPostgres16IT` | PASS, concurrent PG; UNKNOWN không được claim lại |
| UNKNOWN + disabled caller không mở lại PENDING; stale reply không ghi đè | `unknownGenericDisabledTransportCannotReopenRowOrEnableHttpAdapter`, `lateGenericReplyAfterStaleScanCannotConfirmOrRestorePending` | PASS; không cung cấp resolution/retry policy |
| Real timeout giữ UNKNOWN; không RELEASE/replay send mới | `CheckoutDraftPostgresIT.realHttpReadTimeoutRetainsUnknownDraftAndCannotSendOrReleaseOnReplay` | PASS với HTTP fixture; hai live UNKNOWN vẫn chưa chạy |
| Exact SKU/quantity/journal; không lấy Product ID provider/replay | `stockRefusalUsesCommittedDraftAndExactSentJournalNotProviderProductId`, `replayCallerCannotSupplyProductIdentityEvenWithMatchingSkuAndQuantity`, `explicitPendingDispatchReloadsSavedTupleAndRejectsRemapping` | PASS |
| Missing/numeric/unmatched SKU; INVALID_QUANTITY/INVALID_SKU | `CheckoutInventoryBoundaryPostgresIT` các case tương ứng | PASS; lỗi không ánh xạ được giữ mã gốc |
| Same-key không duplicate Order/outbox/Inventory send; changed body/shipping bị từ chối | `CheckoutReplayPostgresIT`, `CheckoutDraftPostgresIT.overlappingSameKeyCheckoutCreatesOneDraftOrderOutboxAndInventorySend` | PASS; replay shipping guard #138 được bảo toàn |
| Two Store và Order History ownership | `OrderPostgres16IT`, `FulfillmentPostgres16IT.customerSeesOnlyOwnOrdersWithShippingAndStoreSplit` | PASS ở service/PG test; chưa là browser Order History trên source này |
| overallStatus processing/delivered/cancelled/mixed | `OrderOverallStatusTest` | 3 PASS |
| DELETE Cart isolation/claimed rollback/concurrency | `CartServiceTest` + `CartClearOwnerPostgresIT` | PASS; probe quan sát actual waiter qua `pg_blocking_pids` |
| No partial Inventory batch reservation | `InventoryOperationPostgresIT` | PASS, implementation + real PG; không phải combined live transport |
| CONSUME tại DISPATCHED, RELEASE cancel/reject, side effects once | `FulfillmentServiceTest`, `FulfillmentPostgres16IT` | PASS theo test scopes; không sửa semantics TV4 |

## Live acceptance và bảo toàn stack

User đã trả lời rõ: **“Chưa có phê duyệt stack mới”**. Không khởi động stack mới hoặc dùng stack cũ chạy sai source. Review TV2 `5728410521` là bằng chứng phê duyệt stack demo trước; không coi đó là quyền tự thay source/ports của 15 container hiện hữu.

| Nhóm live trên candidate này | Trạng thái |
|---|---|
| Two Store browser/API + same-key replay | NOT_RUN / BLOCKED — chưa có stack riêng exact candidate được owner duyệt |
| Customer cancel + Seller reject, RELEASE once | NOT_RUN / BLOCKED — cùng gate stack |
| DISPATCHED, CONSUME once | NOT_RUN / BLOCKED — cùng gate stack |
| Sáu negative Gateway/API, từng ca trong `live-gates.json` | Cả 6 NOT_RUN / BLOCKED — cùng gate stack; đã chuẩn bị exact #147 assertion |
| Browser Order History | NOT_RUN / BLOCKED — cùng gate stack, chưa chạy browser |
| UNKNOWN claim-conflict | NOT_RUN / BLOCKED — thêm fault fixture, operation identity, provider receipt được TV4/TV5 duyệt |
| UNKNOWN timeout/lost response | NOT_RUN / BLOCKED — thêm fault fixture, operation identity, provider receipt được TV4/TV5 duyệt |

**Lượt mới: 0 live executed, 0 live PASS/FAIL.** Giữ lịch sử 3 live PASS / 2 UNKNOWN BLOCKED và sáu legacy-negative PASS theo HEAD cũ trong evidence cũ; không chuyển thành PASS của candidate mới. Không chạy SQL sửa dữ liệu, fault injection hoặc demo `up --force-recreate` bên cạnh stack cũ. `safety.json` ghi kiểm tra 18 worktree, hai saved refs, 15 container và volumes.

## Migration và quyết định owner

V1–V4 khớp byte main; V5 khớp byte #145 `81ad9f7`, checksum SHA256 `accf738954d56913a892769a31cafb1f43d220fd9b14e8549de7b9ce6c5ab536`. Không chiếm thêm version hoặc sửa migration. Fresh Order PG đã chạy Flyway/schema validation. V5 chỉ thêm `checkout_order_drafts` FK tới attempt, payload JSON + checksum; không đổi Order/Inventory/Finance tables. [V4→V5 rehearsal lịch sử 35/35](../t1t2-20261101/migration-rehearsal/REPORT.md) vẫn bảo toàn, không chạy lại upgrade trên DB thật. Xem `migration-source-check.json`.

| Owner | Đã chứng minh | Quyết định / gate còn chờ |
|---|---|---|
| TV2 / maintainer | Cây main+#145+#147+hardening compile/regression xanh; patch ba file không trùng root fix TV2 | Chọn thứ tự tích hợp, tiếp nhận patch vào source kết hợp, chạy GitHub CI mới tại exact HEAD nhận patch; phê duyệt project/ports/source cho stack mới. Không tự chọn thay maintainer. |
| TV4 | Atomic claim, T1/T2 fencing, UNKNOWN quarantine và RELEASE/CONSUME giữ semantics qua regression | Xác nhận whitelist “definitive no movement”, Inventory ownership/authority, fault operation/receipt và quy trình reconciliation. Không tự retry UNKNOWN hoặc thêm RELEASE. |
| TV5 + TV2 / DB owner | Snapshot money/outbox/Finance regression xanh; V5 rehearsal dùng dữ liệu giả | Chốt retention/PII duplicated shipping, least-privilege grants, backup/purge/runbook trước production; Finance/UAT và fault evidence. DEMO giữ immutable successful drafts, không tự TTL/purge. |
| TV1 | Regression Auth hiện tại không FAIL; Gateway/Auth source không bị TV3 sửa | Live current-authority/revoke đối đầu business write thật, deployment/Auth/Gateway và browser gates vẫn độc lập; 16 gated tests cần provisioning hợp lệ nếu owner muốn nghiệm thu chúng. |

Đường tiếp nhận an toàn: owner xem patch và source đã test → owner chọn base/order → apply/check trong worktree owner, giữ `PreparedOrder` và helper `problem(...)` → regression + fresh hosted CI ở exact HEAD nhận patch → cấp stack riêng + fixtures → live acceptance/UAT. Không push candidate chứa source TV2 lên #145 để né quy trình review; không merge PR thay owner.

## Evidence integrity và publication

- Raw log/XML bất biến tại `/tmp/tv3-master-final-20261102/`; SHA256 trong `raw-input-sha256.json`. Không publish raw stdout có secret/fixture shipping.
- Public JUnit giữ testcase/kết quả, bỏ stdout/stderr/properties và che synthetic shipping/email/phone. Đây là **sanitized exports**, hash public khác raw là chủ đích. Public logs lọc Gradle task/result; raw warnings/exception vẫn giữ local.
- `SHA256SUMS` kiểm từng public artifact. Không commit token, password, JWT, shipping PII, DB dump hoặc `.env.demo`.
- `commands.json`, `source.json`, `results.json`, `safety.json` xác định source/test scope rõ ràng. Lỗi discovery runtime và lượt probe cache được giữ, không trình bày như production regression RED. Initial root-relative frontend command từ backend cwd bị file-not-found và được chạy lại đúng cwd; backend inputs không đổi, ghi trong `source.json`.

Refs #3 #10 #16 #145 #147. Không tick Issue hoặc tuyên bố Checkout/website/UAT production DONE.
