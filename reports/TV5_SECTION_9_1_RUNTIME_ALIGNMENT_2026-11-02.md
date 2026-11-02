# Phụ lục TV5 — đối chiếu §9.1 với runtime và bằng chứng

Ngày đối chiếu: 02/11/2026 (UTC+07).  Nguồn nhánh tích hợp được kiểm tra: `origin/main` tại
`9ca80006fce359a96862a93a42b5d0fa631f85a7` (đã merge #140, #141, #143, #144, #145 và #148); các PR còn mở được đối chiếu riêng tại HEAD ghi bên dưới.

## Phạm vi và giới hạn nguồn

Repository hiện không chứa bản nguồn của mục **§9.1** trong báo cáo V2.  File
`docs/project-report/REQUIREMENTS_TRACEABILITY.md` là tài liệu do owner khác duy trì; TV5 không
sửa trực tiếp để tránh ghi đè lịch sử/chủ sở hữu. Phụ lục này là nội dung thay thế có thể chèn vào
§9.1 hoặc dùng để hiệu đính, và chỉ kết luận từ source/PR/evidence được nêu dưới đây.

TV5 chỉ phụ trách QA, kiểm thử tích hợp, Finance/Settlement theo phạm vi được giao và bằng chứng nghiệm thu. **House Store operation thuộc TV2/business owner, ngoài phạm vi vận hành của TV5**; TV5 chỉ kiểm tra tác động tới Finance và đối soát khi phù hợp.

## Trạng thái thực tế

| Hạng mục | Trạng thái xác minh | Căn cứ và giới hạn |
| --- | --- | --- |
| Product/Category | **MERGED / UAT PENDING** | PR #140 V7 slug/status và PR #143 Product đã merge vào `main@9ca8000`; CI của PR không thay nghiệm thu tích hợp hai Store. |
| Product Review (F15) | **MERGED / UAT PENDING** | Rule 1–5 sao, buyer/delivered, unique `(order_id, product_id)` và reason ẩn review từ PR #148/V8 đã nằm trên main. Chưa có bằng chứng UAT cuối tại full SHA này cho toàn bộ F15; không báo production PASS. |
| Cart/Checkout/Order/Inventory | **PARTIAL / WAITING_REVIEW** | PR #145 durable draft/journal V5 đã merge vào `main@9ca8000`; PR #147 (`b4e869d`) còn mở với `overallStatus`, lỗi checkout theo item và clear cart. Hai fixture UNKNOWN, retention PII production và kiểm thử tổ hợp sau #147 vẫn chưa có nghiệm thu cuối. CI nhánh hoặc merge #145 không thay bằng chứng UAT hai Store. |
| COD/Fulfillment | **PARTIAL / OWNER_CONFIRMATION** | PR #144 đã merge vào main; merge và demo evidence chưa xác nhận toàn bộ COD/Fulfillment production policy. Cần owner TV2/TV3/TV4 xác nhận luồng đang mở. |
| Finance/Settlement (F16) | **DEMO-ONLY DECISION / PRODUCTION BLOCKED** | `docs/project-report/M01_FINANCE_DECISION.md` ghi TV2 đã chọn MD01–MD10 cho bản nộp/demo local và MD09 chỉ cho phép hai gate trong `.env.demo` local; đó không phải phê duyệt production hay evidence E2E cuối. House Store operation và quyết định phạm vi kinh doanh thuộc TV2/người phê duyệt nghiệp vụ, ngoài quyền TV5 tự chốt; TV5 chỉ đối chiếu tác động fee 5%/0% trong F16. UAT cô lập #146 giữ gate OFF; không bật `--approved-finance-policy` hay kết luận settlement production PASS từ quyết định demo. |
| UAT hai Store / Issue #18 | **BLOCKED / NOT_RUN** | #146 chỉ rehearsal với hai gates OFF, không chạy Checkout; UAT cuối cần stack/runner riêng được TV2/business owner duyệt cho đúng SHA, tài nguyên và phạm vi gate. #147 và quyết định owner/business còn mở. Không suy UAT PASS từ mock, unit test, CI nhánh hoặc demo stack. |
| UAT rehearsal | **PASS (tooling only)** | PR #146: `Start-IsolatedUat.ps1 -ConfigOnly` dùng env/secrets/volume project riêng, gates Finance/Inventory OFF. Đây chỉ chứng minh cô lập cấu hình, không phải UAT nghiệp vụ hay production readiness. |

## Thay thế câu khẳng định không có bằng chứng

Không dùng các cụm “PASS production”, “E2E tài chính khớp”, “Finance/Settlement hoàn tất” hoặc
“UAT hoàn tất” nếu chưa kèm SHA main 40 ký tự, lệnh chạy, môi trường cô lập, raw log/JUnit và
phạm vi policy được business phê duyệt.  Với mọi test nhánh/PR, phải ghi đúng HEAD đã chạy và
phân loại **PASS**, **FAIL**, **NOT_RUN**, **BLOCKED** hoặc **WAITING_REVIEW**.

## Điều kiện thay đổi trạng thái

1. PR phụ thuộc được merge hợp lệ vào `main`, CI/checks tại SHA tích hợp PASS và reviewers bắt buộc hoàn tất.
2. TV2 xác nhận Auth/Gateway/frontend shared và Catalog, TV3 xác nhận Checkout/Order, TV4 xác nhận Inventory/Fulfillment/COD; TV5 phụ trách QA, kiểm thử tích hợp, bằng chứng nghiệm thu và review tác động Finance/Settlement trong phạm vi được giao. Auth/Gateway do TV2 viết vẫn cần reviewer độc lập theo #141.
3. Mọi phạm vi ngoài demo local phải có quyết định business riêng cho Finance/COD/Settlement; các gate mặc định fail-closed chỉ được bật trong scope đã ký.
4. TV2/business owner duyệt stack/runner UAT riêng và scope bật gate; TV5 chạy UAT hai Store trên Docker/volume/data giả cô lập, công bố evidence gắn với full SHA main. Script #146 luôn giữ gate OFF và không thay thế runner đã duyệt.

## Kiểm thử chọn lọc mới tại main 9ca8000

- TV5 chạy trên worktree cô lập tại full SHA `9ca80006fce359a96862a93a42b5d0fa631f85a7`, Java 21.0.7, Gradle 8.10, PostgreSQL Testcontainers; không sử dụng dữ liệu/volume `pgdev` hoặc `redisdev`.
- `:product-service:test` với `ReviewRulesTest`, `ReviewRulesBoundaryTest` và `:order-service:test` với `FinanceRulesTest`, `FinanceRulesBoundaryTest`, `FinanceReportTest`, `--rerun-tasks`: **66/66 unit PASS**, exit 0. Raw local log SHA256 `0AFCF5A9AE7BF24547D9198D39BABD7525D90BD64CC8BB9997F441F699CC72D9`.
- Lệnh kết hợp đầu tiên chọn sai `:product-service:test` cho `CatalogSchemaProposalPostgresTest` (test này mang tag `postgresql` và thuộc `integrationTest`): **Gradle FAIL, exit 1**; riêng ba bộ `:order-service:postgresIntegrationTest` có **20/20 JUnit PASS**. Log gốc giữ nguyên, SHA256 `B4E374DEFF85DA41F82C8FF40571EF2EAB7EFCF2C89256C15894086519B17DED`; có lỗi kết nối của scheduled outbox sau khi container dừng, không được che khỏi evidence.
- Chạy lại đúng `:product-service:integrationTest` với `CatalogSchemaProposalPostgresTest` và `ProductMigrationPostgresTest`: **34/34 PostgreSQL PASS**, exit 0. Raw local log SHA256 `22749AF9E1FC8E068A3C86943ABAF2F8DD01C0124553EF92CD28E267489EBD66`.
- Ba hosted checks `frontend`, `e2e`, `readiness` của [main@9ca8000](https://github.com/duyxl56785/Project_HK5/commit/9ca80006fce359a96862a93a42b5d0fa631f85a7) đã SUCCESS. Unit/PG chọn lọc không thay thế UAT live hai Store; Issue #18 vẫn **BLOCKED/NOT_RUN** vì #147 và các gate business/owner chưa hoàn tất. Raw logs và JUnit XML của lượt TV5 giữ ở worktree cục bộ `.verification/tv5-midterm-main-20261102/`, chưa công bố như artifact release.

## Evidence truy vết

- `scripts/qa/uat/Start-IsolatedUat.ps1`, `scripts/qa/uat/Stop-IsolatedUat.ps1` — rehearsal cô lập, không dùng volume chung.
- PR #140, #141, #143, #144, #145 và #148 đã merge vào `main@9ca8000`; PR #147 `b4e869d` còn mở tại lần đối chiếu này. #145 merge không tự chứng minh hai fixture UNKNOWN hoặc retention PII production đã được nghiệm thu.
- `docs/project-report/M01_FINANCE_DECISION.md` ghi quyết định cho bản nộp/demo local; `docs/TV5_M01_HUMAN_DECISION_PACKET.md` là gói đề xuất lịch sử. Không dùng hai tài liệu để suy ra production sign-off.
- Issue #18 — UAT final giữ **BLOCKED/NOT_RUN** cho đến khi các điều kiện trên đạt.
