# Nội dung trình bày TV5 cho deck Soopi

Bản soạn ngày 07/11/2026, dựa trên deck nguồn 23 slide ngoài repository và [nội dung báo cáo TV5](TV5_REPORT_CONTENT.md). Đây là lời trình bày để người tổng hợp đưa vào deck; **PPTX nguồn chưa được sửa/render**. Các mốc số là evidence của lượt chạy đã ghi, không phải tổng số test nghiệm thu cuối.

| Slide hiện có | Lời trình bày ngắn | Bằng chứng/cảnh báo khi đưa lên slide |
|---|---|---|
| **8 — F15/F16** | “Em phụ trách Review và Finance. F15 kiểm buyer/DELIVERED, rating 1–5, chống trùng và Admin ẩn review; review ẩn không góp vào điểm công khai. F16 tính commission/net từ snapshot và kiểm settlement đúng trạng thái, Store. Những phép tính hoặc unit test đạt không tự chứng minh Finance production.” | R06 trên main `c1ac4d4...`: 2 unit +1 PG PASS; F15 live hai Store NOT_RUN. F16 Return–Settlement guard 20 PG PASS tại main `618f9ea...`; House/Finance production vẫn có gate riêng. |
| **10 — Phân công** | “TV5 Nguyễn Minh Duy xây Review, Finance, QA matrix, regression và evidence; phối hợp TV2 Product/Auth, TV3 Checkout, TV4 Inventory/Fulfillment/COD. House Store operation do TV2 và business owner quyết định, ngoài phạm vi vận hành TV5; em kiểm tác động tiền và đối soát.” | TV2 tiếp nhận Auth/Gateway/frontend shared từ 01/11; lịch sử TV1 trong PR cũ vẫn giữ nguyên. |
| **11 — Quy trình QA** | “Mỗi kết quả gắn với command, môi trường, full Git SHA và raw artifact. Em tách unit, MockMvc + PostgreSQL, demo browser và UAT live; không cộng test từ nhiều SHA thành một release total.” | [XML/SHA256 Return–Settlement](../../reports/minh-chung-kiem-thu/tv5-main-return-settlement-20261107/); không có raw console log ở lượt đó. |
| **15 — Tích hợp** | “Contract và ownership là gate thực: Product/Store facts đi vào Checkout, Inventory nhận operationId, COD/Return chi phối settlement. Khi một owner đổi status, DTO hoặc migration, bên gọi và bên nhận phải được kiểm lại.” | #182/#185 đã MERGED; [ma trận RET-01..06](../../tests/TV5_RETURN_ACCEPTANCE_20261110.md) tách preflight PASS khỏi nghiệm thu live còn BLOCKED. |
| **16 — Kết quả QA** | “Trên main `618f9ea8f723d607454397012cd5613ca199f660`, 20 PostgreSQL tests của FulfillmentPostgres16IT PASS. Các ca chứng minh Return đang mở chặn settlement và Return/settlement tranh chấp không cùng commit. Đây là preflight MockMvc, chưa phải UAT hai Store.” | Main GitHub quan sát sau đó đã tiến tới `868fd8d...`; không chuyển 20 PASS sang SHA mới. Hai UNKNOWN vẫn BLOCKED. |
| **20–22 — Màn hình và ảnh** | “Chỉ đưa ảnh B.1–B.5 khi chụp từ một run thật, với source SHA, case, actor, Store, response/DB oracle và SHA256. Ảnh demo cũ chỉ ghi demo, không ghi final UAT.” | [Phiếu chụp từng ảnh](TV5_APPENDIX_B_CAPTURE_SHEET.md). Cả năm ảnh final hiện NOT_RUN; giữ chỗ trống hoặc ghi ‘chưa nghiệm thu’. |
| **23 — Demo và hỏi đáp** | “Website và Soopi TV5 QA Tester chạy local trên dữ liệu giả. Tester cho chọn suite allowlist, đọc PASS/FAIL/BLOCKED, xem log/ảnh và xuất evidence. Khi Docker/stack thiếu, runner phải hiện BLOCKED thay vì PASS. Em có thể trình bày lỗi từng tái hiện rồi sửa; final UAT và Finance production chưa được công bố đạt.” | #179 đã MERGED; không bật UNKNOWN/Finance flags chỉ để demo. |

## Câu trả lời ngắn cho phần hỏi đáp

- **“20 PASS đã nghiệm thu hệ thống chưa?”** Chưa. Đây là một class MockMvc + PostgreSQL trên main `618f9ea...`, với Store/provider mocks. Final hai Store UAT cần main tích hợp mới, hosted CI, môi trường cô lập, HTTP/UI/DB receipts và từng gate owner.
- **“Phí 5% và House 0% đã chạy production chưa?”** Chưa xác nhận. Các phép tính/snapshot có kiểm thử hẹp; House source, bốn nhóm doanh thu, COD/Settlement và giới hạn policy phải đối chiếu bản business approval đúng phạm vi.
- **“Hai UNKNOWN đã PASS chưa?”** Chưa. Proxy fake-loopback/contract test là chuẩn bị; claim-conflict và lost-response thật cần runner, immutable tuple, provider COMMIT/stock receipt và quyền chạy từng fixture.
- **“Ảnh minh chứng lấy từ đâu?”** Từ screenshot thật của run trên main đủ gate, lưu theo case trong `reports/minh-chung-kiem-thu/`, kèm SHA256 và raw HTTP/JUnit/log tương ứng. Không tạo ảnh màn hình giả.

Trước khi trình chiếu, người tổng hợp cần mở PPTX nguồn, so từng lời với slide thực, thay các mốc `main`/PR bằng mốc final khi có receipt và render kiểm layout. Không điền `PASS`, số test hoặc ảnh khi chưa thực thi đúng SHA.
