# Phụ lục TV5 — đối chiếu §9.1 với runtime và bằng chứng

Ngày đối chiếu: 02/11/2026 (UTC+07).  Nguồn nhánh tích hợp được kiểm tra: `origin/main` tại
`d446577d2f9268f2212a4ee214ff3d805ee7a1d9` (đã merge #140, #143 và #148 sau #115/#144); các PR còn mở được đối chiếu riêng tại HEAD ghi bên dưới.

## Phạm vi và giới hạn nguồn

Repository hiện không chứa bản nguồn của mục **§9.1** trong báo cáo V2.  File
`docs/project-report/REQUIREMENTS_TRACEABILITY.md` là tài liệu do owner khác duy trì; TV5 không
sửa trực tiếp để tránh ghi đè lịch sử/chủ sở hữu. Phụ lục này là nội dung thay thế có thể chèn vào
§9.1 hoặc dùng để hiệu đính, và chỉ kết luận từ source/PR/evidence được nêu dưới đây.

## Trạng thái thực tế

| Hạng mục | Trạng thái xác minh | Căn cứ và giới hạn |
| --- | --- | --- |
| Product/Category | **MERGED / UAT PENDING** | PR #140 V7 slug/status và PR #143 Product đã merge vào `main@d446577`; CI của PR không thay nghiệm thu tích hợp hai Store. |
| Product Review (F15) | **MERGED / UAT PENDING** | Rule 1–5 sao, buyer/delivered, unique `(order_id, product_id)` và reason ẩn review từ PR #148/V8 đã nằm trên main. Chưa có bằng chứng UAT cuối tại full SHA này cho toàn bộ F15; không báo production PASS. |
| Cart/Checkout/Order/Inventory | **PARTIAL / WAITING_REVIEW** | PR #147 (`cb576a6`) thêm `overallStatus`, lỗi checkout theo item và clear cart; PR #145 (`81ad9f7`) còn giữ durable draft/journal V5. CI nhánh không thay nghiệm thu main; TV3/TV4 còn phải chốt ownership/UNKNOWN và kiểm lại tổ hợp sau tích hợp. |
| COD/Fulfillment | **PARTIAL / OWNER_CONFIRMATION** | PR #144 đã merge vào main; merge và demo evidence chưa xác nhận toàn bộ COD/Fulfillment production policy. Cần owner TV2/TV3/TV4 xác nhận luồng đang mở. |
| Finance/Settlement (F16) | **DEMO-ONLY DECISION / PRODUCTION BLOCKED** | `docs/project-report/M01_FINANCE_DECISION.md` ghi TV2 đã chọn MD01–MD10 cho bản nộp/demo local và MD09 chỉ cho phép hai gate trong `.env.demo` local; đó không phải phê duyệt production hay evidence E2E cuối. House Store operation và quyết định phạm vi kinh doanh thuộc TV2/người phê duyệt nghiệp vụ, ngoài quyền TV5 tự chốt; TV5 chỉ đối chiếu tác động fee 5%/0% trong F16. UAT cô lập #146 giữ gate OFF; không bật `--approved-finance-policy` hay kết luận settlement production PASS từ quyết định demo. |
| UAT hai Store / Issue #18 | **BLOCKED / NOT_RUN** | Không có full SHA main đáp ứng đồng thời các dependency mở và tất cả gate business. UAT cuối không được suy ra từ mock, unit test, CI nhánh hoặc demo stack. |
| UAT rehearsal | **PASS (tooling only)** | PR #146: `Start-IsolatedUat.ps1 -ConfigOnly` dùng env/secrets/volume project riêng, gates Finance/Inventory OFF. Đây chỉ chứng minh cô lập cấu hình, không phải UAT nghiệp vụ hay production readiness. |

## Thay thế câu khẳng định không có bằng chứng

Không dùng các cụm “PASS production”, “E2E tài chính khớp”, “Finance/Settlement hoàn tất” hoặc
“UAT hoàn tất” nếu chưa kèm SHA main 40 ký tự, lệnh chạy, môi trường cô lập, raw log/JUnit và
phạm vi policy được business phê duyệt.  Với mọi test nhánh/PR, phải ghi đúng HEAD đã chạy và
phân loại **PASS**, **FAIL**, **NOT_RUN**, **BLOCKED** hoặc **WAITING_REVIEW**.

## Điều kiện thay đổi trạng thái

1. PR phụ thuộc được merge hợp lệ vào `main`, CI/checks tại SHA tích hợp PASS và reviewers bắt buộc hoàn tất.
2. TV1–TV4 xác nhận các contract Auth, Catalog, Checkout, Inventory, Fulfillment/COD theo ownership.
3. Mọi phạm vi ngoài demo local phải có quyết định business riêng cho Finance/COD/Settlement; các gate mặc định fail-closed chỉ được bật trong scope đã ký.
4. TV5 chạy UAT hai Store trên môi trường Docker/volume/data giả cô lập, công bố evidence gắn với full SHA main.

## Evidence truy vết

- `scripts/qa/uat/Start-IsolatedUat.ps1`, `scripts/qa/uat/Stop-IsolatedUat.ps1` — rehearsal cô lập, không dùng volume chung.
- PR #140, #143, #148 đã merge vào `main@d446577`; PR #145 `81ad9f7`, #147 `cb576a6` còn mở tại lần đối chiếu này. PR #144 đã merge trước đó.
- `docs/project-report/M01_FINANCE_DECISION.md` ghi quyết định cho bản nộp/demo local; `docs/TV5_M01_HUMAN_DECISION_PACKET.md` là gói đề xuất lịch sử. Không dùng hai tài liệu để suy ra production sign-off.
- Issue #18 — UAT final giữ **BLOCKED/NOT_RUN** cho đến khi các điều kiện trên đạt.
