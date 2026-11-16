# Quyết định M01 và chính sách Finance

**Trạng thái:** ĐÃ CHỌN, áp dụng cho bản nộp đồ án và môi trường demo local. Ngày 30/10/2026.

**Người quyết định:** Nguyễn Văn Duy (`duyxl56785`), chủ repository, tự xác nhận là owner M01/Finance trong phiên làm việc ngày 30/10/2026. Gói [TV5_M01_HUMAN_DECISION_PACKET.md](../TV5_M01_HUMAN_DECISION_PACKET.md) yêu cầu chủ dự án chỉ rõ người có thẩm quyền; tài liệu này ghi nhận chỉ định đó.

**Ghi chú về ownership:** [OWNERSHIP.md](../OWNERSHIP.md) vẫn ghi TV5 phụ trách code finance và settlement. Tài liệu này chốt chính sách nghiệp vụ, không đổi phân công và không phải chữ ký của TV5. TV5 và TV1 vẫn review phần code thuộc phạm vi của họ.

Mỗi lựa chọn dưới đây là phương án đã có sẵn trong gói M01 và khớp hành vi hiện tại của `feat/tv2-demo-mvp`.

| ID | Quyết định |
| --- | --- |
| MD01 | Chỉ dùng VND. Không có phần lẻ, không quy đổi ngoại tệ. |
| MD02 | Giá sản phẩm là số nguyên dương VND, tối đa 99.999.999.999.999.999. Không làm tròn: giá có phần lẻ bị từ chối. |
| MD03 | Số lượng 1–1000 theo giới hạn hiện có. Mỗi dòng, cửa hàng và đơn tổng không vượt trần giá ở MD02. |
| MD04 | Phí sàn 5% cho cửa hàng thường, 0% cho cửa hàng của sàn. Làm tròn HALF_UP về đồng, một lần trên mỗi SellerOrder. Tiền người bán nhận bằng tạm tính trừ phí. |
| MD05 | Chưa có phí ship, thuế, giảm giá. Cơ sở tính phí là tiền hàng. |
| MD06 | ADMIN xác nhận COD một lần sau DELIVERED. Chỉ đối soát đơn DELIVERED đã thu COD. Chưa hỗ trợ hoàn tiền hoặc trả hàng sau đối soát. |
| MD07 | Dùng snapshot đã xác minh gần nhất. Chấp nhận race từ lúc đọc tới lúc Order commit; kiểm tra quyền hiện tại vẫn bắt buộc. |
| MD08 | Snapshot đơn hàng bất biến: giá, số lượng, tiền dòng, cửa hàng, tạm tính, tỷ lệ phí, phí, tiền người bán nhận. Lần chạy lại không tính lại theo chính sách mới. |
| MD09 | Chỉ bật `HK5_FINANCE_POLICY_APPROVED` và `INVENTORY_ORDER_PAIR_ENABLED` trong `.env.demo` local. Mặc định trong `application.yml` vẫn tắt, môi trường khác phải có quyết định riêng. Người quyết định và người chạy demo là `duyxl56785`. |
| MD10 | Báo cáo gồm doanh thu người bán, phí sàn và tổng tiền đã đối soát, tính từ dữ liệu DELIVERED và đã thu COD trong Order. Mốc thời gian theo UTC như dữ liệu lưu trữ. Không có điều chỉnh hoàn tiền. |

Các lựa chọn này được chốt cùng lúc, cùng một phiên bản. Muốn đổi phải ghi quyết định mới, không sửa âm thầm.

## Bổ sung 01/11/2026

| ID | Quyết định |
| --- | --- |
| MD11 | Bản nộp không vận hành cửa hàng của sàn. Mọi cửa hàng hiện có thu phí 5% vì `StoreController` luôn trả `houseStore=false`. Luật 0% ở MD04 giữ trong `FinanceRules` nhưng chưa có cửa hàng nào dùng. Không thêm API Admin xử lý đơn thay người bán hay gắn ảnh riêng cho cửa hàng của sàn. |

MD11 làm rõ phạm vi của MD04 cho bản nộp và demo local, không đổi cách tính phí hay dữ liệu đã có. MD11 không thay thế MD01–MD10 và không cho phép bật Finance, COD hay đối soát trên production. Người quyết định: `duyxl56785`.

## Bổ sung 14/11/2026 — MD12, chính sách giá sản phẩm khi có Promotion

**Nguồn quyết định:** TV2 Nguyễn Văn Duy (owner M01 và product-service), [P01–P05 của PR #202](https://github.com/duyxl56785/Project_HK5/pull/202#issuecomment-6875893285), cập nhật [quyền review](https://github.com/duyxl56785/Project_HK5/pull/202#issuecomment-6876058407). Đây là quyết định nghiệp vụ **cho FR-PROMO-01–03**, không phải bằng chứng tính năng đã được merge hoặc bật trên môi trường khác.

| ID | Quyết định |
| --- | --- |
| MD12 | Flash Sale, Super Sale và xả kho do **Seller chịu toàn bộ phần giảm**; sàn không bù tiền. Đơn giá trên dòng Order là giá đang áp dụng tại lúc Checkout preflight. Tiền hàng, phí sàn 5% HALF_UP (MD04), tiền người bán nhận và COD đều tính trên đơn giá này, nên công thức ở MD04–MD10 giữ nguyên. Snapshot theo MD08 không đổi cấu trúc; nguồn khuyến mãi (campaign) ghi ở audit của product-service, chưa lưu trên Order. Trả hàng hoàn theo giá trong snapshot. Không cộng dồn với voucher (voucher C2 chưa có). |

**Diễn giải MD05 sau MD12:** không có giảm giá **cấp đơn hàng**. Promotion thay đổi đơn giá Product được Catalog cung cấp trước khi Cart/Checkout lấy snapshot; không thay đổi cấu trúc Order hoặc phép tính Finance. **Không sửa** cart-service, order-service, Inventory hay Finance cho FR-PROMO-01–03 theo quyết định TV2.

**Review phạm vi MD12:** TV2 quyết định và là người duy nhất review/approve/merge PR #202 theo comment sửa đổi 14/11. Quy tắc ownership lịch sử của các module khác không tự động bị xóa.

## Bổ sung 16/11/2026 — MD13–MD25, chính sách tiền cho Voucher C2

**Nguồn quyết định:** TV2 Nguyễn Văn Duy (owner M01), chọn phương án A cho MD13–MD24 và chốt MD25 trong phiên làm việc ngày 16/11/2026. Chi tiết, phương án đã loại, ví dụ số và ảnh hưởng code: [M01_VOUCHER_C2_DECISION.md](M01_VOUCHER_C2_DECISION.md). Phiên bản `M01-VOUCHER-C2-v2` (v1 là MD13–MD24; v2 thêm MD25 cùng ngày).

Đây là quyết định nghiệp vụ **cho FR-VC-05 giai đoạn C2**, không phải bằng chứng C2 đã được code, merge hoặc bật. C2 không nằm trong bản nộp 21/11/2026.

| ID | Quyết định |
| --- | --- |
| MD13 | Seller chịu toàn bộ phần giảm của voucher; sàn không bù tiền. |
| MD14 | Voucher của Store chỉ áp cho SellerOrder của Store đó. Mỗi SellerOrder tối đa một mã; đơn nhiều Store được dùng mỗi Store một mã. |
| MD15 | Số giảm tính trên tiền hàng của SellerOrder (đơn giá theo MD12), khi tiền hàng ≥ `minOrderAmount`. `FIXED`: đúng `discountValue`. `PERCENT`: tiền hàng × phần trăm, HALF_UP về đồng một lần, không vượt `maxDiscount`. |
| MD16 | `gross` của SellerOrder là tiền hàng trừ giảm voucher. Phí sàn 5% HALF_UP (MD04) tính trên `gross` này. |
| MD17 | Không cộng dồn với Flash Sale, Super Sale, xả kho: SellerOrder có dòng đang hưởng Promotion thì từ chối mã bằng lỗi rõ ràng. |
| MD18 | Tổng lượt không vượt `usageLimit`. Mỗi tài khoản khách dùng mỗi voucher một lần. Trừ lượt nguyên tử ở `seller-service` lúc Checkout, idempotent theo SellerOrder. |
| MD19 | Checkout thất bại không mất lượt. Hủy hoặc từ chối trước khi giao thì hoàn lượt. Trả hàng sau khi giao không hoàn lượt. |
| MD20 | Trả hàng hoàn theo số khách thực trả trong snapshot (`gross` sau giảm). MD06 giữ nguyên. |
| MD21 | Snapshot SellerOrder lưu thêm, bất biến: tiền hàng trước giảm, số giảm, `voucherId`, mã, loại, giá trị, `maxDiscount`. Công thức COD, đối soát và `sellerNet` không đổi. |
| MD22 | Voucher phải đang hiệu lực và còn lượt tại lúc trừ lượt; nếu không, Checkout dừng với lỗi riêng. `seller-service` lỗi thì đơn có mã bị từ chối, không đặt đơn thiếu giảm giá. Thay đổi voucher sau khi đơn đã đặt không ảnh hưởng đơn. |
| MD23 | Thêm cờ riêng cho áp voucher ở Checkout, mặc định tắt, chỉ bật trong `.env.demo` local sau khi test tiền PASS. Mở rộng MD09. |
| MD24 | Doanh thu người bán và phí sàn tính trên `gross` sau giảm. Báo cáo thêm tổng giảm giá voucher theo Store; số này không phải chi phí của sàn. Mở rộng MD10. |
| MD25 | `gross` của SellerOrder sau giảm voucher phải ≥ 1 đồng. Mã làm `gross` về 0 bị từ chối bằng lỗi rõ ràng; hệ thống không tự giảm bớt mức giảm. Chỉ xảy ra với `FIXED` khi tiền hàng đúng bằng `discountValue`. |

**Quan hệ với quyết định cũ:** MD13–MD25 mở rộng MD05 (nay có giảm giá cấp đơn của từng Store) và giữ điều khoản "không cộng dồn với voucher" của MD12. MD01–MD04, MD06–MD11 không đổi.

**Review:** contract C1 yêu cầu TV5 review phần tiền của C2. Tại ngày 16/11/2026, TV5, TV3 và TV4 chưa xác nhận; trạng thái ghi ở mục 8 của file chi tiết. Quyết định này không đổi phân công trong [OWNERSHIP.md](../OWNERSHIP.md).
