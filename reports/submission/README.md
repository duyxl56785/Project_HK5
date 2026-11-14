# Bản ghép TV5 cho báo cáo và deck ngày 07/11/2026

Hai tệp trong thư mục này là **bản nháp để nhóm kiểm**, không phải bản nộp cuối hoặc xác nhận final UAT.

| Tệp | Thay đổi thực tế | Kiểm tra đã chạy | Còn thiếu |
|---|---|---|---|
| `Bao_cao_Soopi_TV5_DRAFT_2026-11-07.docx` | Sao chép báo cáo nhóm 55 trang, giữ nguyên nội dung cũ/TODO B.1–B.5, thêm Phụ lục D về F15/F16/QA/Security, bảng ca và tự đánh giá TV5 | `python-docx` mở lại: 453 paragraphs, 44 tables, 3 sections; ZIP integrity PASS. Đã mở bản tạo bằng script trong Word, kiểm trang phụ lục 56–57: sửa đoạn truy vết lẻ trang 58 và lặp tiêu đề cột bảng QA trên trang tiếp; cuối trang 57 không còn đoạn lẻ. Sau lượt xem, Word tự ghi lại nhiều thành phần OOXML; đã tạo lại bản nháp từ nguồn bằng script để bảo toàn phần báo cáo nhóm. Nguồn SHA256 `f8712b8bb93ba11d84c43502c32795411a63336631d240b186499df80b3e6671`; bản nháp hiện tại SHA256 `3be3a86df4a5a29057be33f10f1fed933b2857d9ae1ed4b9991ed8ee999f8e34`. | Đã kiểm **bố cục phụ lục** của bản tạo cùng script, chưa render/so toàn bộ trang và chưa cập nhật mục lục. `render_docx.py` không chạy vì thiếu LibreOffice; Word COM trả `E_ACCESSDENIED`. Owner cần rà bản nộp và ảnh thật B.1–B.5 trước khi chốt. |
| `Soopi_TV5_notes_DRAFT_2026-11-07.pptx` | Giữ 23 slide nhóm; thêm speaker notes TV5 ở slide 8, 10, 11, 15, 16, 20–23. Không thay nội dung hiển thị hoặc ảnh nguồn. | Import/export bằng artifact tool; 23/23 slide render PNG có byte hash giống nguồn; ZIP integrity PASS; 9 notes có nội dung TV5. Nguồn SHA256 `035d63d4755df5797fc9e0c28265fb9717940becb849262400b9baba33da33d0`; bản nháp SHA256 `6e2c717f309e1639af00bb2e9b39e7da7444da0c68ff992067f3892d9af93c9c`. | Finalizer đã qua các bước kiểm đến lúc ghi đầu ra nhưng bị `EPERM` khi tạo hard link; tệp DRAFT được sao chép nguyên byte từ candidate. Lượt mở PowerPoint qua UI báo hết thời gian duyệt quyền, nên speaker notes/font **chưa được kiểm bằng PowerPoint thật**. Không có ảnh final B.1–B.5. |

Bản ghép lấy thông tin từ [nội dung TV5](../../docs/project-report/TV5_REPORT_CONTENT.md), [phiếu ảnh B.1–B.5](../../docs/project-report/TV5_APPENDIX_B_CAPTURE_SHEET.md), [lời trình bày](../../docs/project-report/TV5_DECK_SPEAKER_NOTES.md) và [ma trận UAT](../../tests/TV5_UAT_ACCEPTANCE_CURRENT.md). Lượt PostgreSQL 20 PASS thuộc `main@618f9ea8f723d607454397012cd5613ca199f660`, không phải main quan sát sau đó `868fd8deb7abd789cea93bbd71fc6f8e3d641a71`. Hai UNKNOWN, final UAT #18, ảnh B.1–B.5 và Finance production vẫn BLOCKED/NOT_RUN theo gate tương ứng.

## Cập nhật TV5 ngày 14/11/2026

Hai bản nháp mới giữ nguyên giới hạn nghiệm thu và chỉ đồng bộ thông tin đã kiểm chứng:

| Tệp | Nội dung cập nhật | Kiểm tra thực tế | SHA256 |
|---|---|---|---|
| `Bao_cao_Soopi_TV5_DRAFT_2026-11-14.docx` | Cập nhật main quan sát `d023e10474408b959f8e0ec872cff217f101d151`, lịch Issue #158 (freeze 19/11, E2E/clone sạch 20/11, nộp 21/11) và trạng thái #179 đã merge. Ảnh B.1–B.5, Hình 6.4, UNKNOWN, RET-05, Finance production và final UAT vẫn chưa được nhận PASS. | `python-docx` mở lại: 453 paragraphs, 44 tables; ba đoạn cập nhật đọc lại đúng. Render DOCX **BLOCKED_ENV** vì runtime thiếu `soffice.exe`; chưa tuyên bố kiểm bố cục Word cuối. | `81abe78af6ae6d1bbe80f0b92465750beb97afda69d2149738e8c857c2202c12` |
| `Soopi_TV5_notes_DRAFT_2026-11-14.pptx` | Giữ 23 slide nhóm; bổ sung speaker notes TV5 tại slide 16 và 23 về CI trước freeze, lịch 19–21/11, ảnh `PENDING_UAT` và các gate BLOCKED. | Artifact tool import/export/re-import: 23 slides. Đã render 23/23 slide, xem montage và slide 16/23; không có thay đổi nội dung hiển thị hoặc ảnh minh chứng giả. | `6a13c61f45e9456452b7a228c3afef4706f5a4db47fc7f9c2914720b17539713` |

Các tệp này vẫn là **DRAFT**. Chỉ ghép ảnh thật và kết luận UAT sau khi có SHA đóng băng cùng receipt E2E/clone sạch đúng SHA. Không dùng bản nháp này để tuyên bố Finance/COD/Settlement production đã được phê duyệt.

Các script trong `reports/submission/.deck-build/` chỉ phục vụ render/kiểm cục bộ, không phải evidence QA của sản phẩm. Không stage chúng hoặc ảnh preview khi commit tài liệu. Source DOCX/PPTX ngoài repository không bị sửa.
