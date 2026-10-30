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
