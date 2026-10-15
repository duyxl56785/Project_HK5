# TV5 — bảng lỗi, khoảng trống và blocker tuần 1

Đọc cùng TV5_V2_RECONCILIATION.md. Mức P1/P2 dưới đây là ưu tiên cần xử lý trước tích hợp, không chứng minh lỗ hổng đã khai thác trên production. Trạng thái DOCUMENTED/PENDING/BLOCKED không được tự đổi thành bug fixed hoặc test PASS.

| ID / loại / mức | Tái hiện hoặc bằng chứng | Expected / thực tế | Owner | Trạng thái / xử lý W1 |
|---|---|---|---|---|
| W1-01 / DOC / P1 | Draft nói Order adapter; V2 §8.2 tr.38 và §9.3 tr.42 dùng event/read-model | Không mặc định thêm call sync | TV5/TV3/TV4 | DOCUMENTED: sửa draft, chờ D03 |
| W1-02 / GAP / P1 | ReviewRules.Rating chỉ stars/hidden; V2 UC17 tr.24/A.7 tr.52 có hidden_reason | Rule chưa mô hình reason/comment/audit; không claim F15 hoàn chỉnh | TV5/TV2/TV1 | PENDING D05; không tự thay contract/model |
| W1-03 / DOC / P2 | JWKS trong yêu cầu tích hợp cũ; V2 §6.3 tr.31 HS256 | Không áp thuật toán mới bằng suy đoán | TV5/TV1 | DOCUMENTED: thêm D01, chưa đổi security |
| W1-04 / AMBIGUITY / P1 | UC06 tr.26 nêu403; §3.3 tr.20 dữ liệu người khác404 | Cần mapping resource/permission chính xác | TV1/TV2/TV5 | PENDING D02; HTTP assertions BLOCKED |
| W1-05 / AMBIGUITY / P1 | V2 HALF_UP VND vs numeric19,2; helper scale0..4 | Rounding và giới hạn tiền cần chốt | TV3/TV4/TV5 | PENDING D06; test helper biên PASS, không chứng minh schema |
| W1-06 / GAP / P1 | F16 tr.19/UC12 tr.24 chỉ nói4 nhóm | Cần bốn tên/công thức/cutoff | TV3/TV4/TV5 | BLOCKED D07; aggregate tests chưa thể chốt |
| W1-07 / EVIDENCE / P1 | Spec/log/migration V2 được nhắc ở tr.17/44–48 nhưng không có ở PR20 | Không dùng claim lịch sử thay test hiện tại | TV5/nhóm | BLOCKED nguồn gốc; đã tách ledger |
| W1-08 / SCOPE / P2 | QA cũ yêu cầu export; V2 §11.3 tr.46 nói chưa có export | Không thêm chức năng hoặc gate không có trong scope | TV5 | DOCUMENTED: export chỉ có điều kiện; đã sửa UAT |
| W1-09 / DOC / P2 | QA.md nói chưa có test; PR20 đã có30 tests | Mô tả đúng main và branch TV5 | TV5 | FIXED tài liệu trong follow-up, nay63 tests |
| W1-10 / REPORT / P2 | F02 chưa UC riêng và C01 tr.54; B.1–B.5 tr.53 còn TODO; NFR tr.20–21 chưa load test | Có UC/ảnh/đo thật khi nghiệm thu sau | TV1–TV5 | DOCUMENTED; ảnh/live/load NOT_RUN, ngoài thực thi W1 |
| W1-11 / ENV / P2 | Renderer chuẩn không tìm được soffice.exe | Có locator trang có thể xác minh | TV5 | RESOLVED bằng Word hidden/read-only→PDF55 trang; nguồn SHA256 không đổi |
| W1-12 / SCOPE / P1 | Báo cáo baseline cũ liệt kê DB unique/settlement/live UAT trong “tuần1 còn lại” | Tuần1 chỉ rules/schema proposal/test baseline/QA/đối chiếu | TV5 | FIXED phân kỳ tài liệu; triển khai đó thuộc tuần2–3 |

Không phát hiện assertion FAIL hoặc bug production mới trong hai rules hiện hữu qua63 test lần này. Các gap persistence/JWT/concurrency đã biết vẫn chưa triển khai, không “sửa” bằng fake repository hoặc bỏ test. Phát hiện độc lập không thay review owner trên GitHub.
