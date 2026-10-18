# Kiểm thử và bằng chứng

Main đã nhận rules/unit tests Review/Finance qua PR #20 và Category/Seller domain qua PR #7. API/persistence/JWT production của F15/F16 chưa được chứng minh hoàn tất. Mỗi thành viên tự chạy test liên quan và báo cáo:

| Hạng mục | Test command | PASS | FAIL | NOT_RUN | Bằng chứng |
|---|---|---|---|---|---|
| Ví dụ Auth | gradle :auth-service:test | - | - | chưa chạy | link PR/log |

Không suy số test của báo cáo V2 thành kết quả của repository mới khi chưa import code và chạy lại.

Một Issue được coi DONE khi: có PR đã merge, kiểm thử có bằng chứng, API/schema/event được kiểm tra và tài liệu được cập nhật.

## Baseline TV5 — 15/10/2026

- [Báo cáo thực thi và review PR](../reports/TV5_W1_AUDIT_2026-10-15.md).
- [Ma trận QA, UAT hai Store và checklist báo cáo](../tests/TV5_UAT.md).
- [Đề xuất contract/schema Review/Finance cần owner review](TV5_CONTRACT_PROPOSAL.md).

Rules Review/Finance có unit tests; chưa có API/persistence/JWT adapter production. Không dùng kết quả unit để tích hoàn thành F15/F16 hoặc E2E. Các con số test lịch sử ở PR #7 phải phân biệt với lượt chạy lại trong báo cáo TV5.

## Follow-up tuần 1

- [Đối chiếu báo cáo V2 theo chương/trang](../reports/TV5_V2_RECONCILIATION.md).
- [Truy vết yêu cầu → test → bằng chứng](../tests/TV5_TRACEABILITY.md).
- [Bảng lỗi và blocker](../reports/TV5_W1_DEFECTS.md).
- [Quyết định cần TV1–TV4 xác nhận](TV5_W1_DECISIONS.md).
- [Kết quả chạy follow-up và phân loại bằng chứng](../reports/TV5_W1_FOLLOWUP.md).

PASS: đã chạy đạt; FAIL: đã chạy lỗi; NOT_RUN: chưa thực thi; BLOCKED: chưa thể chuẩn bị/chốt ca do phụ thuộc được ghi rõ. Không cộng test của các mốc khác nhau, unit với E2E hoặc claim báo cáo V2 vào cùng tổng hiện tại. Việc thiết kế UAT/contract trong W1 không đồng nghĩa đã triển khai/chạy luồng tuần 2–3.

## Kiểm chứng điều kiện tuần 1 — 17/10/2026

- [QA matrix, PR heads, owner/blocker và ledger nguồn kết quả](../reports/TV5_W1_ACCEPTANCE_2026-10-17.md).
- [Raw log/XML, suite counts và SHA256](../reports/evidence/w1-gates-20261017/README.md).
- TV5 chạy mới: Review/Finance63 unit PASS; Seller21 PostgreSQL PASS; Cart/Order3 PostgreSQL PASS; Auth/User timezone Asia/Saigon1 PASS/2 FAIL, rerun UTC3 PASS. Không cộng các lượt hoặc xóa FAIL lịch sử.
- Issue #5 vẫn PARTIAL: test DB Seller/Cart/Order không chứng minh DB Review/settlement, mapping JWT runtime hoặc shared Finance contract đã nghiệm thu.

## Bản sửa bổ sung theo finding — 17/10/2026

- [PR #32](https://github.com/duyxl56785/Project_HK5/pull/32) → branch PR #19: sửa HS256/JWKS và checkpoint PR #7; giữ F15/F16. TV1 đã Approve đúng `0c77df6`; còn chờ tác giả tiếp nhận vào #19.
- [PR #33](https://github.com/duyxl56785/Project_HK5/pull/33) → branch PR #28: JWT YAML mapping, replay/customer scope và scanner/failure-handler CAS; final build Cart/Order **56 unit + 11 PostgreSQL PASS** tại `11dcf39`. RED/GREEN logs, test source và manifest nằm trong `backend/order-service/verification/tv5-fixes` của PR bổ sung. Không cộng với 63 rules/21 Seller/3 PG của lượt bàn giao trước.
- [PR #34](https://github.com/duyxl56785/Project_HK5/pull/34) → branch PR #27: owner đã sửa runbook ở `a810312`; TV5 giữ phần đó và bổ sung evidence cùng source Java: Asia/Saigon 1 PASS/2 FAIL, UTC 3 PASS, Asia/Ho_Chi_Minh 3 PASS. Không che lỗi của alias không được server hỗ trợ.
- Ba PR là patch/evidence bổ sung, không đưa source PR gốc chưa duyệt vào main. [Ledger bàn giao và owner](../reports/TV5_W1_ACCEPTANCE_2026-10-17.md#7-bản-sửa-bổ-sung-cho-các-finding) giữ WAITING_REVIEW/INTEGRATION; contract Review/Finance, live dependencies và E2E vẫn chưa được nghiệm thu.

### Checkpoint sau khi tác giả tiếp nhận patch

[Audit toàn bộ PR/reviewer](../reports/PR_REVIEWERS_AUDIT_2026-10-17.md): #32 đã vào #19 (`804d6f1`), #33 đã vào #28 (`89c20bc`), Git tree giống bản sửa đã kiểm chứng. Chờ reviewer quyết định lại tại parent, không coi review cũ tự được khép. #31 vẫn Draft và FAIL compileTestJava tại `1abfd4f`; bản sửa một dòng với **15 unit PASS** được bàn giao riêng qua #35 (`14df61b`). Đây là kết quả mới của TV5, PG/HTTP/E2E Inventory vẫn NOT_RUN. #27 có 2 CI readiness checks success đúng `a810312`, tách khỏi lượt chạy local TV5.

Phản hồi mới trong lúc audit: TV1 đã APPROVED #34 đúng `369b4f8`. TV2 xác nhận fixes #26/#27/#28, đồng thời báo **3 PostgreSQL probes FAIL ở #31** (schema CHAR/VARCHAR, duplicate initialize, concurrent operationId ledger). Đây là kết quả do TV2 cung cấp; TV5 chưa chạy lại và #35 không xử lý các DB defects đó. Xem audit §6 để biết owner/evidence; giữ #31 Draft và Issue #5 PARTIAL.

## Checkpoint 18/10/2026 — audit PR sau tích hợp

Các đoạn trên là snapshot lịch sử. #31/#36 và các PR QA/infrastructure trước đã merge; không còn báo ba DB findings cũ như source chưa sửa. [Audit hiện tại](../reports/PR_REVIEWERS_AUDIT_2026-10-18.md) kiểm5 PR mở #19/#28/#37/#38/#39: TV5 chạy mới #28 **61unit PASS**, #37 **291unit+49PG PASS** (11PG candidate SQL), #38 **17unit+15PG PASS**. Các lượt/source khác nhau, không cộng vào release total; runtime #19/#39 NOT_RUN/docs-only.

#39 cần sửa số19/21 và ID baseline bị đổi nghĩa; #38 cần rõ retryable errors/current replay balances. M01 money/Cart currency, S01 Store authority, C01/E01 Catalog và fulfillment state/auth/event còn owner-gated. [Raw evidence và publication limits](../reports/evidence/pr-audit-20261018/README.md) tách49 public hashes với3 credential-bearing XML giữ local/derived summary. Issue #5 vẫn PARTIAL; không suy unit/PG thành F15/F16 production hoặc live2Store E2E PASS.
