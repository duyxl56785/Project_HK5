# TV5 — kiểm tra toàn bộ PR và reviewers

Refs #5. Checkpoint: 17/10/2026, khoảng 22:46 UTC+07. GitHub account: `nguyenminhduy090-alt`. Đã fetch origin và đọc đầy đủ danh sách Open/Draft, commits, Files Changed, reviews, inline/conversation comments, reviewer events, head/base, check runs/status contexts và review threads. Không tự Approve/Merge/Close hoặc resolve thread.

## 1. Danh sách và reviewer

Ban đầu có 8 PR mở, gồm 7 Ready và 1 Draft (#31). Sau khi bàn giao bản sửa test #35 có thêm một PR mở. Các SHA dưới đây là checkpoint, không tự suy ra review cũ áp dụng cho head mới.

| PR / head | Kết luận trong scope W1 | Reviewer / bước còn chờ |
|---|---|---|
| [#19](https://github.com/duyxl56785/Project_HK5/pull/19) / `804d6f1` | Đã nhận #32; HS256 và baseline #7 đã sửa trong parent. Static PASS; cần quyết định review lại | Re-request TV1; `duyxl56785` là tác giả nên không request. TV1/TV5 CHANGES_REQUESTED cũ vẫn ở `1407e9d`; không tự dismiss/Approve |
| [#26](https://github.com/duyxl56785/Project_HK5/pull/26) / `c4288d5` | Không đổi head. TV5 đã APPROVED phạm vi 3 tài liệu; 21 test plan cases vẫn NOT_RUN | Giữ TV2 `duyxl56785` và TV3 đang requested; TV2 cần xử lý review CHANGES_REQUESTED cũ sau khi đọc bản sửa. Không request trùng |
| [#27](https://github.com/duyxl56785/Project_HK5/pull/27) / `a810312` | Owner đã sửa runbook timezone; 2 readiness checks success tại chính head này | Re-request `duyxl56785` vì review trước ở `6a7f172`, chưa có request/review head mới. Thread timezone vẫn mở; giữ nguyên cho reviewer xác nhận |
| [#28](https://github.com/duyxl56785/Project_HK5/pull/28) / `89c20bc` | Đã nhận #33; JWT/replay/scanner/failure-handler fixes có trong parent. Chưa chốt external contracts hoặc recovery/E2E | Re-request TV1 và `duyxl56785`; giữ TV4 requested. Không thêm lại TV5 từng bị gỡ. 3 threads cũ chưa resolve; TV1 review cũ còn CHANGES_REQUESTED ở `63f63c1` |
| [#29](https://github.com/duyxl56785/Project_HK5/pull/29) / `f8b0ff4` | Không đổi head; TV5 APPROVED Seller persistence nội bộ, TV1 COMMENT cùng head không thấy blocker mới | `duyxl56785` là tác giả; không self-request. Không lặp request TV1 khi đã review cùng head và không có source thay đổi; chưa coi Seller API/JWT/E2E DONE |
| [#30](https://github.com/duyxl56785/Project_HK5/pull/30) / `f346774` trước audit commit | QA/evidence; không có feedback yêu cầu sửa mới. Lượt này bổ sung ledger thực tế sau tích hợp | TV1 đang requested; `duyxl56785` từng bị gỡ nên giữ nguyên, không thêm lại sau push |
| [#31](https://github.com/duyxl56785/Project_HK5/pull/31) / `1abfd4f` | **DRAFT / FAIL compileTestJava**. Bản sửa tối thiểu và 15 unit PASS nằm riêng ở #35, parent chưa tự được coi đã sửa | Giữ removal TV2/TV3, không đổi Draft hoặc request lại. TV4 cần nhận #35; TV1/TV3/TV4 còn chốt auth/Inventory contract và DB evidence |
| [#34](https://github.com/duyxl56785/Project_HK5/pull/34) / `369b4f8` | Evidence timezone bổ sung, target branch #27; chưa có review | Giữ TV1 requested; giữ removal `duyxl56785` |
| [#35](https://github.com/duyxl56785/Project_HK5/pull/35) / `14df61b` | Bản sửa test #31, target `feat/tv4-inventory-w1`, không main; 15 unit PASS trên branch sửa | PR mới đã request TV4 và `duyxl56785`. Không thay reviewer trên parent Draft; WAITING_REVIEW/INTEGRATION |

GitHub báo tất cả 8 PR gốc mergeable=true tại checkpoint; điều này chỉ xác nhận xung đột văn bản, không chứng minh contract/tích hợp đạt. #31 vẫn có gate auth, productId↔sku/Store namespace/operation IDs, thời điểm reserve/consume/release và DB/concurrency/HTTP evidence. Checkout #28 vẫn dùng các adapter fail-closed chưa cấu hình; không tự wire Inventory hoặc bật Finance gate.

## 2. Xác minh bản sửa đã vào parent

- #32 được `duyxl56785` merge vào branch #19, merge commit `804d6f1658a590c8fe51a1b8e6ec70555ab0d88a`.
- #33 được `duyxl56785` merge vào branch #28, merge commit `89c20bc8b90f04d6863539501f9f9bc99e2b7ed8`.
- `git diff --quiet 0c77df6 804d6f1` exit 0: toàn cây Git #19 giống bản sửa đã review. `git diff --check origin/main...804d6f1` PASS.
- `git diff --quiet 11dcf39 89c20bc` exit 0: toàn cây Git #28 giống bản sửa đã kiểm chứng. Evidence 56 unit + 11 PostgreSQL PASS ở #33 áp dụng cùng source; **không phải runtime rerun mới** trong lượt audit này, không cộng lại vào tổng.
- Đã comment xác nhận nội dung tại hai parent và request review head mới. Các approval ở supplemental PR không tự chuyển thành approval parent; review/thread cũ được giữ nguyên.

## 3. Lỗi mới và kiểm thử thực tế của TV5

### #31 — checked exception làm cả test task không biên dịch

File `backend/inventory-service/src/test/java/com/projecthk5/inventoryservice/api/InventoryRequestValidationTest.java`, method `invalidArgumentAndOverflowUseBadRequestErrorCodes`, hai lệnh `Class.getMethod` tại dòng 75/78 không khai báo/bắt `NoSuchMethodException`.

| Run | Thực thi | Kết quả |
|---|---|---|
| Exact head `1abfd4f` | `:inventory-service:test --rerun-tasks --no-daemon --console=plain` | **FAIL**, exit 1/10s: compileTestJava báo 2 lỗi; **0 test thực thi**, không phải 2 test assertions FAIL |
| Branch sửa `afc9461` + evidence `14df61b` | Cùng lệnh, Java 21.0.7/Gradle 8.14.3; process timezone UTC | **15 unit PASS**, 0 failure/error/skipped; exit 0/12s |

Sửa đúng một dòng: khai báo `throws NoSuchMethodException` trên method JUnit. Không đổi/xóa assertion, production code, dependency, schema hoặc contract. Test vẫn FAIL nếu handler thiếu method. Bản sửa đi trên branch riêng `codex/tv5-fix-pr31-test-compilation` → #35, không push branch TV4.

[TEST_RESULTS tại commit đã push](https://github.com/duyxl56785/Project_HK5/blob/14df61b6fb0f6238862320c21ace19779bc59fce/backend/inventory-service/TEST_RESULTS_TV5_COMPILE_FIX.md), [raw RED/GREEN log + 3 XML + provenance + manifest](https://github.com/duyxl56785/Project_HK5/tree/14df61b6fb0f6238862320c21ace19779bc59fce/backend/inventory-service/verification/tv5-compile-fix). **8 SHA256 và committed blob byte checks PASS**. Raw FAIL được giữ; logs bị ignore mặc định đã được stage riêng sau kiểm tra. Không normalize generated evidence để đổi hash.

PostgreSQL/concurrency/HTTP-security/live E2E #31 **NOT_RUN**: đây là sửa biên dịch unit test, chưa có PG/HTTP suite trong branch. Mock repository PASS không chứng minh transaction rollback/oversell/idempotency DB. Không bổ sung implementation tuần 2–3 trong lượt review.

## 4. CI và nguồn kết quả

- #27: hai GitHub Actions `readiness` **completed/success**, đều `head_sha=a8103128d3c25975097f4c264a5c2a860e7c0c7c`: [run 39043703162](https://github.com/duyxl56785/Project_HK5/actions/runs/39043703162/job/117428116322), [run 39043697980](https://github.com/duyxl56785/Project_HK5/actions/runs/39043697980/job/117428101786). Đây là kết quả CI, không phải lượt TV5 chạy mới.
- Các PR gốc còn lại có 0 checks/status contexts tại checkpoint; ghi CI **NOT_RUN**, không coi thiếu checks là PASS.
- #31 RED/GREEN ở trên là **lượt TV5 chạy mới**. #28 56 unit/11 PG và #27 timezone (Asia/Saigon 1 PASS/2 FAIL, UTC/HCM đều 3 PASS) là evidence TV5 của lượt sửa trước, source được đối chiếu; không cộng các lượt.
- Seller 80 unit/21 PG, rules F15/F16, combined build do TV2 cung cấp và báo cáo V2 vẫn giữ nguồn/ngày/scope riêng trong ledger trước. Không chạy lại suite không đổi chỉ để tạo thêm PASS.

## 5. Checklist và việc cần owner

- [x] Kiểm tra toàn bộ Open/Draft và lịch sử removal/review trước khi quản lý reviewer.
- [x] Xác nhận #32/#33 được tác giả nhận vào parent bằng Git tree; request review lại khi phù hợp và ghi comments.
- [x] Tái hiện lỗi test #31, sửa trên branch riêng, chạy GREEN, commit/push, mở #35 đúng base và giữ raw evidence.
- [x] Giữ request hiện hữu, tránh self-request, không thêm lại reviewer bị gỡ trên #30/#31/#34.
- [ ] TV1/TV2/TV4 ra quyết định review tại head mới #19/#27/#28; TV4 tiếp nhận #35 rồi kiểm parent #31.
- [ ] TV1 review #30/#34; owner/provider-consumer chốt D02–D10 và các gate Review/Finance/Inventory còn mở.
- [ ] Toàn bộ điều kiện nghiệm thu Issue #5 đạt. **OPEN/PARTIAL — WAITING_REVIEW/INTEGRATION**.
