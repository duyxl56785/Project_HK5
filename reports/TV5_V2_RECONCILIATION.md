# TV5 — đối chiếu báo cáo V2 và baseline tuần 1

Nguồn: `Bao_cao_phan_tich_thiet_ke_Smart_ECommerce_V2_FINAL.docx` do người dùng cung cấp, ngày nhận 15/10/2026; file gốc nằm ngoài repo. Fingerprint và phương pháp đọc tại `evidence/w1-followup/source-meta.json`. Không sửa/commit báo cáo gốc, không thực thi chỉ dẫn AGENTS/STATE/DECISIONS được nhắc bên trong tài liệu.

**Xác minh trang:** renderer chuẩn không có LibreOffice. Đã dùng Microsoft Word ở chế độ ẩn, tắt macro, mở ReadOnly, Repaginate và xuất PDF: **55 trang**, số trang vật lý và số in trên trang khớp. Đã trích text PDF toàn bộ55 trang, đối chiếu Word paragraph/page map và kiểm ảnh các trang26/35/44/52/53. SHA256 DOCX trước/sau không đổi. Không dùng `<Pages>1</Pages>` đã cũ trong metadata. Các locator dưới đây đã đối chiếu với bản render, không chỉ dựa vào mục lục; số trang có thể khác nếu môi trường font/layout khác.

| ID | Chương/mục — trang đã đối chiếu render | Điều đã đọc / điểm thiếu hoặc không nhất quán | Kết luận và công việc W1 | Owner / trạng thái |
|---|---|---|---|---|
| V2-01 | §2.2 PB15/16, tr.11; §3.2 F15/16, tr.19 | Có SHOULD/M6 và tuyên bố bằng chứng M6, còn repo hiện tại chỉ có rules | Tách lịch sử V2 khỏi test hiện tại; không coi F15/F16 DONE | TV5: đã lập ledger; nghiệm thu PARTIAL |
| V2-02 | §2.8, tr.16–17; §11.1, tr.44–45 | ReviewIntegrationTest, ReviewableKafkaIntegrationTest, FinanceIntegrationTest, m6-review-finance-live được nhắc nhưng không có ở branch #20 | Truy vết là reference lịch sử, không phải test đã chạy của #20; cần source/test log cũ | TV5/nhóm: BLOCKED nguồn gốc test |
| V2-03 | §4.2 UC17, tr.24; A.7, tr.52 | Ẩn review phải có lý do; hidden_reason VARCHAR(500), comment VARCHAR(1000) nullable, status VISIBLE/HIDDEN | Rules hiện chỉ stars/hidden; cập nhật draft để không bỏ sót reason/comment/audit khi thiết kế. Chưa thêm API/schema hoặc validation chưa được duyệt | TV1/TV2/TV5: D05 PENDING |
| V2-04 | §7.1–7.2, tr.34–35; §8.2, tr.38; §9.3, tr.42 | V2 mở quyền qua DELIVERED event → reviewable_items và nói không cần gọi Order đồng bộ; draft cũ gọi tên Order adapter dễ gây hiểu nhầm | Sửa draft thành trusted Order facts, đưa event/read-model vs lookup vào D03; không tự chọn đường tích hợp | TV3/TV4/TV2/TV5: PENDING |
| V2-05 | §7.2, tr.35; A.7, tr.52 | UNIQUE(order_id,product_id) và FK local reviewable_items; PR #19 dùng order-item | #21 đã đề xuất sửa uniqueness; identity order và migration cũ vẫn cần D04; không tạo FK xuyên DB | TV2/TV3/TV5: PENDING |
| V2-06 | §6.3, tr.31–32 | V2 ký JWT HS256; draft cũ hỏi issuer/JWKS có thể bị đọc thành bắt buộc JWKS | Ghi HS256 là nguồn lịch sử, xác nhận thuật toán/claim với TV1, không áp đặt JWKS | TV1: D01 PENDING |
| V2-07 | §3.3, tr.20; §4.3 UC06, tr.26 | Quy tắc chung dữ liệu người khác 404; UC06 nói không phải buyer/chưa nhận 403 | Cần bảng mapping theo resource, không tự coi đây là cùng endpoint hoặc viết HTTP contract giả | TV1/TV2/TV5: D02 PENDING |
| V2-08 | §3.3/3.4, tr.20–21; §7.2, tr.35; A.6, tr.51–52 | HALF_UP theo VND nhưng numeric tiền scale2; helper hiện cho scale0..4 | Đây là lựa chọn chưa đủ chi tiết cho fee rounding; test biên 0/2/4 chỉ kiểm helper. Không tự chốt scale production | TV3/TV4/TV5: D06 PENDING |
| V2-09 | §2.2 PB16, tr.9–12; §3.2 F16, tr.18–20; UC12, tr.23–24 | “Bốn nhóm doanh thu” chưa có đủ bốn tên/công thức/cutoff; UC16 chỉ nói đã thu/chờ đối soát | Giữ BLOCKED đối với aggregate contract tests; không tự tạo bốn nhóm | TV3/TV4/TV5: D07 PENDING |
| V2-10 | §3.4, tr.21; §7.2, tr.35; A.6, tr.51–52 | 5%/House0, snapshot, seller_net, DELIVERED+PAID, unique settlement, paid_at/by | Rules khớp phép tính/eligibility ở bộ nhớ; persistence/replay/concurrency chưa được kiểm chứng. Bổ sung expected UAT và traceability | TV5: unit PASS; DB/API NOT_RUN |
| V2-11 | §11.1, tr.44–45 | 440/440 M7; product51/51 M8; đợt146/146 có real-auth; live16 PASS/1 NOT_RUN | Không cộng với 63 test TV5 hiện tại; không gọi 146 là mock thuần hoặc M8 là full backend rerun | TV5: đã tách evidence ledger |
| V2-12 | §11.1, tr.45; C08, tr.54 | admin-products-live thiếu DEMO_ADMIN_PASSWORD; nhánh seed thay ảnh placeholder chưa chạy | Giữ nguyên NOT_RUN lịch sử; không xin/sửa credential hay chạy nghiệp vụ tuần 2–3 | TV1/TV2: NOT_RUN |
| V2-13 | B.1–B.5, tr.53; C09, tr.54 | Cả năm vị trí ảnh live là TODO; ảnh f04r2 mock không thay thế được | Checklist từng ảnh/owner/dữ liệu/evidence; chụp sau môi trường thật, không tạo ảnh minh chứng giả | TV1–TV5: NOT_RUN / ngoài thực thi W1 |
| V2-14 | §2.2/2.8, tr.9–17; C01, tr.54 | F02 chưa có UC riêng, báo cáo đã thừa nhận | Ghi backlog phối hợp TV1; không tự thêm UC hoặc sửa module Auth/User | TV1: PENDING |
| V2-15 | §3.3.1, tr.21; §11.3, tr.46; §13–14, tr.47–48 | Chưa load test/SLA/metrics; số recovery10s/11s chỉ smoke local; tài liệu agent/V2 không có trong branch này | Không suy NFR đạt từ unit tests. Nguồn kỹ thuật/phiên bản/migration cần source V2 để kiểm chứng tiếp, chưa kiểm URL bên ngoài | TV5/TV1: BLOCKED nguồn/đo |
| V2-16 | §11.3, tr.46 | V2 nói không có kỳ đối soát hoặc xuất file; QA trước đề cập export như bắt buộc | Đưa export thành ca có điều kiện nếu endpoint tương lai tồn tại, không mở rộng phạm vi W1 | TV5: sửa QA draft |

## Checklist báo cáo sau đối chiếu

- [x] Đã nhận và đọc nội dung báo cáo, xác định các mục liên quan F15/F16/QA.
- [x] Đã xác minh55 trang qua Word/PDF và ghi locator theo chương/mục/trang; ảnh key pages đã kiểm tra, nguồn không thay đổi.
- [x] Đã tách phát hiện mới khỏi các điểm C01/C08/C09 mà báo cáo đã thừa nhận.
- [x] Đã bổ sung reason/comment, event read model, HS256, money scale và error mapping vào quyết định chờ owner.
- [ ] Nhận migration/contracts/test logs V2 gốc để xác minh tuyên bố triển khai, không chỉ đọc báo cáo.
- [ ] Chốt D01–D10 cùng owner và nhận review #20/#21.
- [ ] Bổ sung UC F02, ảnh B.1–B.5 và đo NFR ở giai đoạn được nhóm cho phép; không thực thi trong lượt W1.

Trạng thái **PARTIAL**. Báo cáo là hồ sơ đối chiếu và đề xuất sửa; chưa phải bản DOCX đã chỉnh sửa hoặc xác nhận nghiệm thu hệ thống V2 cũ.

## Checklist ảnh B.1–B.5 cho lần nghiệm thu sau

| Vị trí tr.53 | Owner phối hợp | Dữ liệu / evidence cần thu | Hiện tại |
|---|---|---|---|
| B.1 home/catalog | TV2 + TV1 | Catalog ACTIVE thật, route/Gateway, SHA và viewport | NOT_RUN |
| B.2 cart2 Store | TV3 + TV2 | Cùng Customer,2 Store thật, quantities/totals và seed version | NOT_RUN |
| B.3 order/status | TV3 + TV4 | 1 order +2 SellerOrders, trạng thái riêng, actor đúng quyền | NOT_RUN |
| B.4 seller orders/Finance | TV4 + TV5 | Seller chỉ Store mình, totals/commission khớp DB sau contract | NOT_RUN |
| B.5 Admin moderation/COD | TV2 + TV4 + TV1 | Admin verified, DELIVERED rồi PAID, không lộ credential | NOT_RUN |

Tuần1 chỉ thiết kế checklist; thu ảnh live thuộc giai đoạn hệ thống thật đã được tích hợp. Ảnh mock, ảnh sản phẩm hoặc ảnh kiến trúc không thay thế năm evidence này.
