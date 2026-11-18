# Chạy demo Voucher C2 và Khuyến mãi

Bản demo này gồm Voucher C2 từ PR #211 cùng giao diện Khuyến mãi đã cập nhật nút bấm, trạng thái chờ duyệt và thông báo quá hạn cho Admin và Seller.

## Dựng từ PowerShell

Cần Docker Desktop đang chạy, JDK 21, Node.js 22 trở lên. Dùng clone riêng và kiểm tra cổng 5173, 18080 trước khi khởi động. Gán SHA bản demo được nhóm xác nhận cho `$demoSha` trước khi chạy các lệnh dưới đây. Xem thêm [hướng dẫn chạy local](RUN_LOCAL.md).

```powershell
git -c core.longpaths=true clone https://github.com/duyxl56785/Project_HK5.git D:\hk5-c2-demo
Set-Location D:\hk5-c2-demo
git switch --detach $demoSha
$env:COMPOSE_PROJECT_NAME = 'hk5-promo-test-tv3'
Remove-Item Env:HK5_VOUCHER_C2_ENABLED -ErrorAction SilentlyContinue
node scripts/demo/up.mjs --approved-inventory-pair --approved-finance-policy --approved-voucher-c2
```

Mở [SOOPI local](http://localhost:5173). Script khởi tạo dữ liệu seed và tự sinh mật khẩu trong `.env.demo` trên máy đang chạy. Các cờ trong lệnh trên dành cho project demo riêng. Voucher C2 mặc định tắt. Khi bật `--approved-voucher-c2`, phải bật cả `--approved-finance-policy`. Nếu thiếu cờ Finance, Checkout có voucher trả HTTP 503 `VOUCHER_FINANCE_POLICY_UNAVAILABLE`.

| Vai trò | Email seed | Khóa mật khẩu trong `.env.demo` |
| --- | --- | --- |
| Khách hàng | `customer@hk5.local` | `SEED_PASSWORD` |
| Seller Tiệm Áo Sài Gòn | `seller1@hk5.local` | `SEED_PASSWORD` |
| Seller Nhà Xinh | `seller2@hk5.local` | `SEED_PASSWORD` |
| Admin | `admin@hk5.local` | `ADMIN_BOOTSTRAP_PASSWORD` |

## Demo Voucher C2

1. Dùng hai tài khoản Seller ở hai phiên đăng nhập riêng. Tại **Voucher**, tạo và **Công bố** một mã giảm tiền cố định của mỗi cửa hàng. Chọn giờ bắt đầu trước thời điểm demo và giờ kết thúc sau buổi demo.
2. Nhập thông số như bảng. Khi mã hiển thị **Đang hoạt động**, đăng nhập bằng tài khoản khách.
3. Thêm một **Áo thun cotton** và một **Đèn bàn gỗ** vào giỏ. Chọn **Tiến hành đặt hàng**, áp mã đúng cửa hàng và nhập thông tin giao hàng giả. Xác nhận COD để xem một Order có hai SellerOrders và tổng **415.000 ₫**.
4. Mỗi tài khoản khách dùng mỗi mã tối đa một lần. Dùng khách thử mới hoặc cặp mã mới khi cần luyện lại.

| Store | Sản phẩm | Mã tự tạo | Giảm | Tiền hàng tối thiểu | Số lượt | Tổng sau giảm |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| Tiệm Áo Sài Gòn | Áo thun cotton (150.000 ₫) | `C2DEMOAO10K` | 10.000 ₫ | 10.001 ₫ | 100 | 140.000 ₫ |
| Nhà Xinh | Đèn bàn gỗ (280.000 ₫) | `C2DEMODEN5K` | 5.000 ₫ | 5.001 ₫ | 100 | 275.000 ₫ |

## Demo Khuyến mãi

1. Vào **Khuyến mãi** bằng Seller. Tạo chương trình xả kho có giờ bắt đầu trong tương lai và thời lượng tối thiểu một giờ. Chọn sản phẩm, nhập giá sale rồi bấm **Đăng ký sản phẩm** và **Gửi duyệt xả kho**.
2. Đăng nhập bằng Admin, mở **Khuyến mãi**, bấm **Duyệt chương trình** rồi **Duyệt sản phẩm** trước giờ bắt đầu. Xem giá sale ở trang sản phẩm dành cho khách khi chương trình bắt đầu.
3. Để minh họa trạng thái hết hạn, để một chương trình thử còn chờ duyệt khi qua giờ bắt đầu. Giao diện tự chuyển sang **Đã quá hạn duyệt**. Đăng ký đã rút vẫn hiển thị **Đã rút**.
4. Chạy bài voucher và bài xả kho vào thời điểm phù hợp. Khi một sản phẩm đang hưởng giá sale, Checkout từ chối voucher cho đơn của cửa hàng đó theo MD17.
