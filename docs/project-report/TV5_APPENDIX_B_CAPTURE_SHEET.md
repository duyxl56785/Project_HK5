# Phụ lục B.1–B.5 — phiếu chụp và đối chiếu ảnh thật

Tài liệu này chuẩn bị nội dung cho báo cáo 55 trang và deck. **Chưa có ảnh final UAT B.1–B.5**. Không thay vị trí TODO trong DOCX bằng ảnh demo, ảnh dựng hoặc ảnh của SHA khác. Mỗi ảnh cần nhận dạng được một lượt chạy trên main đã tích hợp: full 40-character SHA, thời gian Asia/Bangkok, case ID, môi trường cô lập, actor giả, Store/order giả và tệp ảnh gốc cùng SHA256. Che token, mật khẩu, địa chỉ/số điện thoại dù là dữ liệu giả trước khi công bố. Giữ tệp gốc nội bộ theo quy định evidence, không biên tập ảnh rồi coi là raw.

| Vị trí báo cáo V2 | Case/owner | Nội dung phải nhìn thấy trong ảnh và đối chiếu không nằm trong ảnh | Chú thích chỉ được dùng sau khi chạy thật |
|---|---|---|---|
| **B.1 Trang chủ và Catalog** | U02; TV2/TV1, TV5 QA | Product ACTIVE đúng Store và SKU, bộ lọc/tìm kiếm, viewport. Đối chiếu response Catalog và trạng thái Product/Store tại cùng run; ảnh UI không chứng minh source/freshness. | `B.1 — Catalog tại main <SHA>, run <ID>: Product <ID> của Store <ID> hiển thị đúng theo response <evidence>.` |
| **B.2 Giỏ hàng hai Store** | U03; TV3/TV2 | Một Customer giả có ít nhất một sản phẩm từ mỗi Store, quantity, subtotal từng Store và tổng trước Checkout. Đối chiếu Cart/price snapshot và seed; ảnh giỏ rỗng hoặc hai ảnh giỏ khác phiên không đáp ứng. | `B.2 — Giỏ hai Store tại main <SHA>, run <ID>: Store A/B và subtotal đối chiếu <evidence>.` |
| **B.3 Order tổng và đơn con** | U03–U05; TV3/TV4 | Cùng một root Order có hai SellerOrder, owner/status của mỗi Store và thời điểm bước DISPATCHED/DELIVERED. Đối chiếu Order/Inventory event và DB; không chụp các trạng thái từ những order khác nhau để ghép luồng. | `B.3 — Order <ID> gồm hai SellerOrder tại main <SHA>, run <ID>; trạng thái và Inventory receipt <evidence>.` |
| **B.4 Seller và Finance** | U08; TV4/TV5 | Seller A chỉ thấy đơn và số liệu Store A; thử Seller B/cross Store trong log HTTP riêng. Commission/net khớp immutable snapshot và DB, settlement chỉ đúng trạng thái. Không ghi House 0% hoặc production PASS nếu business gate chưa mở. | `B.4 — Seller Store A tại main <SHA>, run <ID>; Store isolation và tiền đối chiếu <evidence>; phạm vi <demo/approved>.` |
| **B.5 Admin moderation và COD** | U05–U06; TV2/TV4/TV5 | Admin actor thật trong môi trường test: ẩn/hiện review hoặc duyệt Product; COD sau DELIVERED, amount/audit và quyền actor. Nếu hai chức năng không cùng màn, dùng hai ảnh có hậu tố B.5a/B.5b và cùng run, không tạo composite giả. | `B.5a/B.5b — Moderation/COD tại main <SHA>, run <ID>; HTTP/DB receipt <evidence>.` |

## Cách ghi từng tệp sau lượt chạy

Đặt trong `reports/minh-chung-kiem-thu/<run-id>/screenshots/` bằng tên `B-1-...png` đến `B-5-...png`. Tạo `SHA256SUMS.txt` từ byte ảnh gốc và README của **đúng lượt** ghi command, nguồn seed, trình duyệt/viewport, main SHA, CI URL, actor role, HTTP/DB oracle, expected/actual, PASS/FAIL/NOT_RUN và lý do. Không ghi credential hay JWT vào ảnh, log, README. Với lỗi hoặc màn chưa chụp được, giữ vị trí `NOT_RUN/BLOCKED`; không dùng ảnh test trước đó để điền vào báo cáo bản nộp.

## Liên hệ với slide

Slide 20–22 có thể trỏ tới B.1–B.5 sau khi đủ ảnh và nguồn. Slide 8 nêu F15/F16 với giới hạn Finance policy; slide 16 nêu preflight Return/Settlement riêng; slide 23 demo website và QA tester trên dữ liệu giả. Deck nguồn ngoài repo vẫn chưa được cập nhật hoặc render. Bản nộp cuối cần đối chiếu chú thích ảnh với [ma trận UAT](../../tests/TV5_UAT_ACCEPTANCE_CURRENT.md) và [nội dung TV5](TV5_REPORT_CONTENT.md) sau khi chạy final UAT.
