# TV5 — review toàn bộ PR và reviewers, 18/10/2026

Refs #5. Account `nguyenminhduy090-alt`; main checkpoint `edb6886`. Đã đọc5 PR Open,0 Draft: commits/files, reviews, conversation/inline comments, reviewer events/removals, CI và review threads. Các PR #26/#27/#29/#30/#31/#34/#35/#36 đã merge; không request review hoặc push vào branch PR đã đóng. #31/#36 có trong main qua lịch sử Git; không làm lại ba source DB fixes.

## 1. Kết luận từng PR

| PR / head | Kết luận trong scope W1 | Reviewer / bước còn lại |
|---|---|---|
| [#19](https://github.com/duyxl56785/Project_HK5/pull/19) /804d6f1 | Tài liệu HS256/baseline đã sửa; không còn finding nội dung chưa xử lý. Static PASS; runtime NOT_RUN/docs-only | TV1 APPROVED đúnghead; TV5 được request. Review CHANGES_REQUESTED cũ của TV5 ở1407e9d không tự được gỡ; AGENTS cấm tự Approve/dismiss, nên cần quyết định review chính thức phù hợp |
| [#28](https://github.com/duyxl56785/Project_HK5/pull/28) /bd5ff09 |61 unit PASS, có5 test Intent mới chạy bằng JUnit thật. Không thấy regression source mới; contract S01/C01/E01/M01/Inventory/Security chưa toàn bộ sign-off | TV2 đã APPROVED cùnghead; không request lại dù last removal event có trước approval. Giữ TV1 requested; không bật Finance flag hoặc live adapters |
| [#37](https://github.com/duyxl56785/Project_HK5/pull/37) /9c28338 |291 unit +49 PG PASS; phù hợp isolated domain/policy/prototype scope. 11 PG cases là candidate SQL, không production Store/Product schema | duyxl56785 là tác giả, không self-request. TV1/TV4/TV5 được request; cần M01 currency/precision, S01 authority và C01/E01 wire/error/auth trước integration |
| [#38](https://github.com/duyxl56785/Project_HK5/pull/38) /9b8ae35 |17 unit +15 PG PASS, gồm retry lifecycle mới. Docs cần làm rõ lỗi retry/current replay balances/SKU normalization như TV2 đã nêu | duyxl56785 đã COMMENT cùnghead, không request trùng; TV1/TV5 requested. Không cung cấp broad internal token cho Checkout; không claim live recovery/E2E |
| [#39](https://github.com/duyxl56785/Project_HK5/pull/39) /bf13732 |**REVISE** về QA/RTM:19 case thực tế≠21 mô tả; ID trùng baseline nhưng đổi nghĩa; fulfillment enum/state khác đề xuất TV3. Runtime NOT_RUN/docs-only | TV2 đã COMMENT, không request lại. TV1 cần review actor/auth/event; TV3 đã phản hồi contract trong conversation. TV5 xác nhận COD/Finance nguyên tắc có sẵn, không chốt wire/schema/currency/state mới |

GitHub mergeable=true cho5PR ở checkpoint chỉ nói không có textual conflict; không chứng minh hợp đồng runtime tương thích. Không tự Approve/Merge/Close hoặc resolve thread. Review thread cũ #28 vẫn mở; giữ cho reviewer xử lý, không báo các P1 đã sửa là còn source defect.

## 2. Kiểm thử mới của TV5

| PR | Thực thi thực tế | Kết quả / giới hạn |
|---|---|---|
| #28 | Cart/Order unit tasks, sourcebd5ff09 |**61 PASS**,0fail/error/skip;exit0/36s. Cart6/Order55, gồm5 Intent JUnit. Chưa rerun11 PG cũ vì chỉ thêm utility chưa được wire; PG/live E2E NOT_RUN trong lượt này |
| #37 | Product+Seller module builds, source9c28338 |**291 unit +49 PG PASS**,0fail/error/skip;exit0/54s. Product188+25PG, Seller103+24PG. 11PG là SQL prototype, không chứng minh Store persistence/positive Catalog HTTP |
| #38 | Inventory module build+explicit PG task, source9b8ae35 |**17 unit +15 PG PASS**,0fail/error/skip;exit0/52s. Module lifecycle replay/lock/rollback, không chứng minh caller-scoped auth, reservation journal hoặc network E2E |
| #19/#39 | Đọc docs, đếm ID và static diff-check |#19 PASS. #39 đúng19 ID duy nhất=10FUL+4COD+5NOT; runtime NOT_RUN. Diff-check #39 báo2 hard line breaks cố ý, không coi là defect chức năng |

Không cộng các snapshot/lượt TV2/V2 cũ vào release total. Java21.0.7/Gradle8.14.3/Docker29.8.1, process UTC/api1.44. [Evidence/commands/limits](evidence/pr-audit-20261018/README.md):49 public artifact hashes;3 XML có captured generated test credentials giữ nguyên local, chỉ xuất **derived counters/testcases/original SHA256**, không publish secrets hoặc sửa raw XML để giả hash. Docker0 missing prior containers/volumes.

CI readiness success đúnghead #28/#37/#38 là nguồn CI riêng, link ở pr-heads.json; không suy count unit/PG từ CI. #19/#39 CI NOT_RUN/0contexts. Fresh local results là TV5, tách khỏi những con số provider/consumer từng tự báo.

## 3. Findings và ma trận owner

### #39 — truy vết QA phải sửa trước nghiệm thu tài liệu

- PR body ghi21 case, bảng thật19. Baseline `WEEK1_TEST_BASELINE.md` trên main có21=6INV+6FUL+4COD+5NOT; hai bộ khác phạm vi, không được gộp hoặc đổi nghĩa ID im lặng.
- Ví dụ ID baseline `TV4-FUL-01` là Seller **xem** đơn Store mình, #39 là Seller **xác nhận**; baseline FUL-04 là reject không reason, #39 là invalid/repeated transition. COD-01 baseline là ADMIN trướcDELIVERED, #39 là non-ADMIN. Notification-01/03 cũng thay phạm vi. Giữ nghĩa ID gốc và thêm ID mới, hoặc mapping/supersession được owner/TV5 thống nhất.
- Lifecycle `PACKING→SHIPPED` và `READY_FOR_HANDOFF→DISPATCHED` chưa cùng enum/event contract. TV3/TV4 phải chốt; TV1 actor/event/auth, TV2 current Store/Seller facts, TV5 COD/settlement boundaries. Không chọn schema/state thay owner.
- SKU global uniqueness là target TV2 đã chọn, còn enforcement production PENDING; không tiếp tục mô tả như chưa có target hoặc coi fixture là authority.

### TV5 xác nhận yêu cầu đã có; không tự chốt M01

Finance requirements hiện hữu: phí sàn5%, House Store0%, seller_net=subtotal−commission, snapshot tiền/House facts từ trusted provider; chỉ settle SellerOrder DELIVERED **và** COD PAID, unique/atomic settlement và Store isolation. ADMIN mới xác nhận COD sauDELIVERED; replay không tạo payment/settlement lần hai. Inventory CONSUME/RELEASE hoặc balance/replayed không chứng minh DELIVERED/COD PAID.

Currency set, precision/scale, nơi và thời điểm rounding, shipping/discount/refund, action/settlement IDs, transaction và provider/event authority còn PENDING. Product candidate giữ giá high precision; Cart hiện NUMERIC(19,2) và thiếu currency trong DTO. Không làm tròn ngầm, tự chọn currency, sửa V1 hoặc bật finance.policy-approved để bỏ gate. TV2+TV3+TV4+TV5/TV1 cần ký cùng một phiên bản contract trước runtime integration.

### #38 — source đạt regression; docs cần phản ánh provider thật

Đồng ý các finding TV2 đã ghi, không mở inline trùng: thiếu IDEMPOTENCY_CLAIM_CONFLICT retryable với **cùng** ID/type/payload; replay response là **balance hiện tại**, không historical outcome; kiểm tồn tại SKU diễn ra trước ledger mismatch; bổ sung SKU trim/exact-case và các init/auth error codes. Retry budget/backoff/escalation, service-pair scopes, status-query authority vẫn chờ owner. Module test PASS không cấp quyền Checkout dùng token có thể init/INCREASE/DECREASE.

### #37 — scope internal/prototype có bằng chứng, integration chưa có authority

Batch validator gọi source một lần, giữ exact BigDecimal/currency, mismatch không trả snapshot, inconsistency fail closed; Store/Product policies không tự đăng ký production bean. Negative HTTP xác minh route chưa được mở, không phải positive Catalog API. S01/C01/E01/M01 và live Store/Product persistence/JWT/2-Store UAT vẫn owner-gated. Không yêu cầu triển khai chúng trong lượt W1 review này.

## 4. Checklist bàn giao

- [x] Audit đủ5 Open PR, merge history, CI, reviews/events/threads; bảo toàn working tree và không request PR đóng.
- [x] Chạy các suite bị ảnh hưởng trên exact heads; lưu commands/exit/log/XML/counters, giữ raw bytes và loại credential-bearing artifacts khỏi publication.
- [x] Đối chiếu Finance/COD/Store/Inventory và ID baseline với tài liệu #39; không tự chốt contract hoặc triển khai tuần2–3.
- [ ] Đăng review phù hợp và hoàn tất reviewer reconciliation; cập nhật link kết quả sau khi publish.
- [ ] Owner sửa tài liệu #38/#39, ra quyết định #19, chốt M01/S01/C01/E01/Inventory/auth và nghiệm thu tích hợp.
- [ ] Issue #5 đủ toàn gate DONE. **OPEN/PARTIAL — WAITING_REVIEW/OWNER_DECISIONS**.
