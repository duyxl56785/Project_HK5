# TV5 — QA matrix và UAT hai Store

Refs #5. Mọi ca live bên dưới **NOT_RUN**: main còn scaffold, thiếu Auth/Product/Order/Inventory API và seed thật. Unit rules không thay thế API/DB/E2E. Kết quả thực chạy xem reports/TV5_W1_AUDIT_2026-10-15.md.

| ID | Yêu cầu / thao tác | Kết quả mong đợi | Owner | Live |
|---|---|---|---|---|
| R01 | Buyer review product đã mua, DELIVERED, stars 1/5 | Tạo được, đúng owner/order/product | TV5 + TV3/4 | NOT_RUN |
| R02 | Buyer khác; chưa giao; sản phẩm ngoài đơn; stars 0/6 | Bị từ chối, DB không thêm review | TV5 + TV1/3/4 | NOT_RUN |
| R03 | Hai request đồng thời, cùng order/product, khác SKU/line | Chỉ một review, lỗi/replay theo contract | TV5 + TV3 | NOT_RUN |
| R04 | Admin hide/unhide; Seller/Customer giả quyền; replay | Chỉ Admin được phép, rating/count cập nhật chính xác | TV5 + TV1/2 | NOT_RUN |
| F01 | Store thường subtotal 100000; House subtotal 200000 | Phí 5000/0, net 95000/200000 | TV5 + TV3 | NOT_RUN |
| F02 | Store chuyển House sau khi tạo đơn | Settlement giữ phí snapshot cũ | TV5 + TV3 | NOT_RUN |
| F03 | Tám tổ hợp delivered/paid/settled | Chỉ true/true/false đủ điều kiện | TV5 + TV4 | NOT_RUN |
| F04 | Settle đồng thời/retry/restart | Một settlement, không cộng tiền hai lần | TV5 + TV3/4 | NOT_RUN |
| F05 | Seller A gọi list/detail/aggregate/export Store B | Từ chối/không lộ dữ liệu theo contract | TV5 + TV1/2 | NOT_RUN |
| C01 | Checkout 2 Store, Idempotency-Key cùng body/khác body | 1 MarketplaceOrder + 2 SellerOrders; replay không thêm | TV3 + TV5 | NOT_RUN |
| C02 | Hai checkout tranh stock cuối; timeout từng service | Không âm kho; bù trừ đúng contract | TV3/4 + TV5 | NOT_RUN |
| C03 | Hủy/từ chối và release lặp | Hoàn kho một lần, không settle đơn hủy | TV4 + TV5 | NOT_RUN |
| A01 | Không token/token giả/role sai/internal endpoint qua Gateway | 401/403/không route theo contract, không side effect | TV1 + tất cả | NOT_RUN |

## Dữ liệu và các bước UAT

1. Seed Admin A, Customer C và C2, hai Seller S1/S2 APPROVED, Store1 thường/Store2 House; P1/P2 ACTIVE, mỗi sản phẩm stock 10. Dùng tài khoản test, không commit token/PII.
2. C thêm P1 giá 100000 và P2 giá 200000, số lượng 1; lưu expectedItems, checkout COD qua Gateway bằng key cố định. Kiểm chứng 1 order, 2 seller orders, tổng 300000 (không giả định shipping), stock còn 9 mỗi product, phí/net theo F01.
3. Replay cùng payload rồi khác payload; kiểm chứng số đơn và tồn không tăng/giảm lần hai. C2/Seller khác không đọc được order không thuộc quyền.
4. S1/S2 fulfillment theo transition đã chốt. Thử settle khi chưa giao, rồi DELIVERED nhưng chưa COD PAID: từ chối. Actor được TV4 cho phép ghi COD PAID; settle/retry/concurrent theo F03/F04.
5. C review; C2 và C review trùng bị chặn. Admin hide/unhide, so sánh count/average public trước/sau; Seller không được moderation.
6. Một order mới được cancel/refuse theo policy; release gọi lặp, đối chiếu stock và không settlement. Restart services, xác minh dữ liệu bền vững.
7. Lưu SHA từng service, seed version, request/response đã bỏ thông tin nhạy cảm, lệnh, exit code, log DB assertions và ảnh UI. Chỉ ghi PASS khi có bằng chứng; thiếu phụ thuộc ghi NOT_RUN, lỗi ghi FAIL + severity/owner/repro.

## Checklist báo cáo V2

- [ ] Nhận bản báo cáo 55 trang (chưa có trong repository).
- [ ] Đối chiếu F02 thiếu use case với code và bản báo cáo thực.
- [ ] Thay TODO B.1–B.5 bằng ảnh live có ngày/SHA/môi trường sau UAT.
- [ ] Chạy admin-products-live và seed/thay ảnh; hiện NOT_RUN.
- [ ] Đối chiếu NFR và trích dẫn với phép đo/nguồn thật; không tự điền số liệu.
- [ ] Chốt phạm vi P1 Review/Finance của PR #19 và ảnh hưởng nghiệm thu V2.

Các mục trên xuất phát Issue #5; chưa xác minh được số trang/nội dung khi thiếu file gốc.
