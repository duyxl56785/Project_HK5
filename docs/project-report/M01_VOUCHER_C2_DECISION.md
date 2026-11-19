# M01 bổ sung cho Voucher C2 — MD13–MD25

**Trạng thái:** ĐÃ CHỌN ngày 16/11/2026. TV2 chọn phương án **A cho cả MD13–MD24** và chốt **MD25** (đơn sau giảm tối thiểu 1 đồng). Đây là quyết định nghiệp vụ, **chưa** phải bằng chứng C2 đã được code, merge hay bật. Còn chờ TV5 review phần tiền và TV3, TV4 xác nhận khả thi (mục 8). Không dùng tài liệu này để bật cờ hay sửa báo cáo nộp.

**Cập nhật 19/11/2026:** đã đủ chữ ký ở mục 8. C2 đã code và merge vào `main`: #211 tại `cee7c87`, #212 tại `4b5bcb4`. Cờ C2 vẫn mặc định tắt. TV2 quyết định bật hay tắt ở UAT ngày 21/11 theo bốn điều kiện ở mục **Lịch**. Tài liệu này vẫn chưa phải bằng chứng C2 đã bật.

**Người quyết định:** Nguyễn Văn Duy (`duyxl56785`), owner M01/Finance theo [M01_FINANCE_DECISION.md](M01_FINANCE_DECISION.md). Cột "Quyết định (A)" là phương án TV2 đã chọn. Cột "Phương án đã loại" giữ lại để biết đã cân nhắc gì.

**Phạm vi:** FR-VC-05 giai đoạn C2, tức ba phần mà [contract C1](../FR_VC05_VOUCHER_C1_CONTRACT.md) mục 1 để lại: áp voucher vào Cart và Checkout; trừ và hoàn lượt dùng; tiền trên SellerOrder và phí sàn. Contract ghi C2 "cần M01 mới và TV5 review".

**Lịch:** `main` đóng băng hết 19/11/2026. Quyết định này giả định C2 **không** vào bản nộp 21/11. Báo cáo nộp vẫn ghi "áp voucher khi thanh toán" là hướng phát triển.

**Lịch, thay thế đoạn trên từ 17/11/2026:** TV2 dời lịch, `main` đóng băng lúc 18:00 ngày 20/11/2026, UAT ngày 21/11, nộp trước 23/11. Ngày 18/11, TV2 ghi trên [PR #211](https://github.com/duyxl56785/Project_HK5/pull/211#issuecomment-6969662837) rằng C2 chỉ được bật ở UAT khi đủ cả bốn điều kiện:

1. #211 và PR B (#212) merge vào `main` trước 18:00 ngày 20/11.
2. TV5 approve phần tiền của #211.
3. CI trên SHA đóng băng chạy `voucher-c2.spec.ts` với kết quả PASS, không skip.
4. Không còn P0 nào của C2 đang mở.

Thiếu một điều kiện thì C2 tắt, và báo cáo ghi "đã code, đã test, chưa bật trong bản nộp".

## 1. Vì sao cần quyết định mới

Ba quyết định hiện hành chưa cho phép giảm giá ở cấp đơn:

- **MD05:** "Chưa có phí ship, thuế, giảm giá. Cơ sở tính phí là tiền hàng."
- **MD12:** Promotion đổi đơn giá sản phẩm, "không có giảm giá cấp đơn hàng", "không cộng dồn với voucher (voucher C2 chưa có)".
- **MD06:** "Chưa hỗ trợ hoàn tiền hoặc trả hàng sau đối soát."

Voucher là giảm giá cấp đơn của từng cửa hàng, nên phải trả lời: ai chịu, phí tính trên số nào, lượt dùng trừ lúc nào, hủy và trả hàng xử lý ra sao.

## 2. Hiện trạng code (đã đọc ngày 16/11/2026 tại `main@b2bee1d`)

- `VoucherRules.Spec`: `FIXED` hoặc `PERCENT` (1–90, bắt buộc `maxDiscount`), `minOrderAmount`, `usageLimit` 1–100000, hiệu lực tối đa 90 ngày. Với `FIXED`, `minOrderAmount ≥ discountValue`.
- `VoucherRules` có ghi chú "C1 only: validate Voucher configuration; never calculate a Checkout discount". `usageLimit` mới chỉ được lưu.
- `SellerOrder` có hai cột tiền: `gross` và `platform_fee`. `sellerNet = gross − platformFee`.
- `FinanceRules.snapshot(subtotal, houseStore, scale)`: phí = `subtotal × 5%`, HALF_UP, một lần trên mỗi SellerOrder.
- Đơn giá trên dòng Order đã là giá sau Promotion (MD12).

## 3. Các quyết định

| ID | Câu hỏi | Quyết định (A) | Phương án đã loại |
|---|---|---|---|
| MD13 | Ai chịu phần giảm của voucher? | **Seller chịu toàn bộ.** Sàn không bù. Giống MD12. | B: sàn bù một phần. Cần thêm khoản phải trả của sàn và báo cáo riêng; không khuyến nghị. |
| MD14 | Voucher áp vào đâu? | Voucher của Store nào chỉ áp cho **SellerOrder của Store đó**. Mỗi SellerOrder tối đa **một** mã. Đơn nhiều Store được dùng mỗi Store một mã. | B: một mã cho cả đơn nhiều Store. Phải phân bổ giữa các Seller; không khuyến nghị. |
| MD15 | Tính số tiền giảm thế nào? | Cơ sở là **tiền hàng của SellerOrder** (tổng tiền dòng, đơn giá theo MD12). Điều kiện: tiền hàng ≥ `minOrderAmount`. `FIXED`: giảm đúng `discountValue`. `PERCENT`: `tiền hàng × discountValue / 100`, làm tròn HALF_UP về đồng, rồi lấy số nhỏ hơn giữa kết quả và `maxDiscount`. Làm tròn một lần. | B: làm tròn xuống (có lợi cho Seller). Thêm một quy tắc làm tròn thứ hai bên cạnh MD04. |
| MD16 | Phí sàn 5% tính trên số nào? | **Tiền sau giảm.** `gross` của SellerOrder = tiền hàng − giảm voucher. Phí = `gross × 5%` HALF_UP (MD04 giữ nguyên). Khớp MD12: phí luôn tính trên số khách thực trả. | B: tính trên tiền hàng trước giảm. Sàn thu nhiều hơn, Seller chịu thêm; `FinanceRules` phải nhận hai số. |
| MD17 | Có dùng chung với Flash Sale, Super Sale, xả kho không? | **Không.** SellerOrder có ít nhất một dòng đang hưởng Promotion thì không áp được voucher; trả lỗi rõ ràng, không tự bỏ mã. Giữ đúng MD12 và contract C1 ("không cộng dồn"). | B: chỉ tính voucher trên các dòng không khuyến mãi. Phải phân bổ theo dòng; phức tạp hơn. |
| MD18 | Lượt dùng tính thế nào? | Tổng lượt không vượt `usageLimit`. **Mỗi tài khoản khách dùng mỗi voucher một lần.** Lượt được trừ nguyên tử ở `seller-service` trong lúc Checkout, có khóa idempotency theo SellerOrder. | B: không giới hạn theo khách. Một khách có thể dùng hết lượt. |
| MD19 | Khi nào hoàn lượt? | Checkout thất bại: không mất lượt. Khách hủy hoặc Seller từ chối **trước khi giao**: hoàn lượt. Đơn **đã giao** rồi trả hàng: **không** hoàn lượt. Voucher hết hạn thì lượt được hoàn cũng không dùng lại được. | B: không bao giờ hoàn lượt. Đơn giản hơn, thiệt cho khách khi Seller từ chối đơn. |
| MD20 | Trả hàng thì hoàn bao nhiêu? | Hoàn theo **số khách thực trả** trong snapshot (`gross` sau giảm), không hoàn theo giá trước giảm. Vẫn do cửa hàng xử lý như hiện nay; MD06 giữ nguyên (không hoàn tiền sau đối soát). | Không có. Hoàn theo giá trước giảm là sai tiền. |
| MD21 | Snapshot lưu gì? (mở rộng MD08) | SellerOrder lưu thêm, bất biến: tiền hàng trước giảm, số tiền giảm, `voucherId`, mã, loại, giá trị, `maxDiscount` tại lúc đặt. `gross` là tiền sau giảm, nên COD, đối soát và `sellerNet` không đổi công thức. Đơn không có voucher: giảm = 0, tiền hàng = `gross`. | B: không lưu tiền hàng trước giảm. Không đối chiếu được báo cáo và hóa đơn. |
| MD22 | Voucher đổi trong lúc khách thanh toán? | Voucher phải đang hiệu lực (`PUBLISHED`, `startsAt ≤ now < endsAt`, còn lượt) **tại lúc trừ lượt**. Nếu không, Checkout dừng với lỗi riêng, giống `PRICE_CHANGED`; không đặt đơn với giá khác số khách đã thấy. `seller-service` lỗi: đơn có mã bị từ chối, không âm thầm bỏ giảm giá. Seller tạm dừng hoặc hủy voucher sau khi đơn đã đặt: đơn giữ nguyên. | B: `seller-service` lỗi thì đặt đơn không giảm giá. Khách trả nhiều hơn số đã thấy; không khuyến nghị. |
| MD23 | Bật tính năng thế nào? (mở rộng MD09) | Thêm một cờ riêng cho áp voucher ở Checkout, mặc định tắt. Chỉ bật trong `.env.demo` local sau khi M01 này được ký và test tiền PASS. Tắt cờ: Checkout từ chối mã, voucher C1 vẫn quản lý được. | B: dùng chung `HK5_FINANCE_POLICY_APPROVED`. Không tắt riêng được C2 khi có lỗi. |
| MD24 | Báo cáo Finance? (mở rộng MD10) | Doanh thu người bán và phí sàn tính trên `gross` sau giảm. Thêm một số liệu riêng: tổng giảm giá voucher theo Store. Số này **không** phải chi phí của sàn. | B: không báo cáo tổng giảm. Seller không biết voucher tốn bao nhiêu. |
| MD25 | Đơn sau giảm có được bằng 0 đồng không? | **Không.** `gross` sau giảm phải ≥ 1 đồng. Mã làm `gross` về 0 bị từ chối bằng lỗi rõ ràng; hệ thống không tự giảm bớt mức giảm. Chỉ xảy ra với `FIXED` khi tiền hàng đúng bằng `discountValue`, vì `PERCENT` tối đa 90%. | B: chấp nhận đơn 0 đồng. Phát sinh đơn COD không thu tiền và SellerOrder phí 0. |

## 4. Ví dụ số

Phí 5% HALF_UP. Mọi số là VND.

| Trường hợp | Tiền hàng | Voucher | Giảm | `gross` (khách trả) | Phí sàn | Seller nhận |
|---|---:|---|---:|---:|---:|---:|
| Không voucher | 200.000 | — | 0 | 200.000 | 10.000 | 190.000 |
| FIXED | 200.000 | giảm 30.000, đơn tối thiểu 150.000 | 30.000 | 170.000 | 8.500 | 161.500 |
| PERCENT dưới trần | 200.000 | 10%, tối đa 30.000 | 20.000 | 180.000 | 9.000 | 171.000 |
| PERCENT chạm trần | 500.000 | 10%, tối đa 30.000 | 30.000 | 470.000 | 23.500 | 446.500 |
| PERCENT có làm tròn | 199.999 | 15%, tối đa 50.000 | 30.000 (29.999,85 làm tròn) | 169.999 | 8.500 (8.499,95 làm tròn) | 161.499 |
| Chưa đạt đơn tối thiểu | 120.000 | giảm 30.000, đơn tối thiểu 150.000 | từ chối mã | — | — | — |
| Giảm hết tiền hàng (MD25) | 30.000 | giảm 30.000, đơn tối thiểu 30.000 | từ chối mã | — | — | — |

So sánh MD16 cho dòng "PERCENT dưới trần": phương án A (đã chọn) phí 9.000, Seller nhận 171.000. Phương án B (đã loại) phí 10.000, Seller nhận 170.000.

**Đơn hai cửa hàng:** Store X tiền hàng 200.000 dùng mã giảm 30.000; Store Y tiền hàng 100.000 không dùng mã. Khách trả COD 170.000 + 100.000 = 270.000. Phí: X 8.500, Y 5.000. Voucher của X không ảnh hưởng tiền của Y.

## 5. Ảnh hưởng tới code và người phụ trách

| Phần | Thay đổi | Owner theo [OWNERSHIP.md](../OWNERSHIP.md) |
|---|---|---|
| `seller-service` voucher | API nội bộ kiểm tra và trừ lượt nguyên tử; bảng ghi lượt dùng (voucher, khách, SellerOrder); hoàn lượt | TV3 làm, TV2 review và merge |
| `cart-service`, Checkout trong `order-service` | Nhận mã theo từng Store; tính giảm; gọi trừ lượt; lỗi riêng khi mã không hợp lệ; migration thêm cột snapshot | TV3 |
| Finance và đối soát trong `order-service` | `FinanceRules` nhận `gross` sau giảm; báo cáo thêm tổng giảm voucher; test tiền | TV5 |
| Hủy, từ chối, trả hàng | Phát sự kiện hoàn lượt khi hủy hoặc từ chối trước giao | TV4 |
| `product-service` | Endpoint nội bộ cho biết dòng nào đang hưởng Promotion, để thực hiện MD17 | TV2 |
| Frontend | Ô nhập mã theo từng cửa hàng ở Checkout; hiện tiền hàng, giảm, phải trả; cập nhật FAQ và mục 5 của Quy định sàn | TV3, TV2 |

## 6. Kiểm chứng trong code và điểm còn ngỏ

Đã đọc code tại `main@b2bee1d` ngày 16/11/2026. Owner tương ứng cần xác nhận lại:

1. **MD17 cần đổi contract nội bộ.** `ProductCatalogSnapshotSource` thay `unitPrice` bằng giá khuyến mãi rồi bỏ thông tin promotion. Snapshot mà `cart-service` nhận (`ProductCatalogHttpAdapter.Snapshot`) không có trường nào cho biết dòng đang khuyến mãi. Phải thêm một trường, ví dụ `promotionApplied`. Owner: TV2.
2. **MD20 không cần phân bổ theo dòng.** `ReturnRequestService` xử lý trả hàng theo cả SellerOrder, không có số lượng hay dòng hàng. TV4 và TV3 xác nhận.
3. **Không cần quy tắc khóa trường voucher.** `VoucherService` chỉ cho sửa voucher `DRAFT` ("Only DRAFT may be edited"), nên giá trị giảm không đổi sau khi publish.
4. **Đã quyết bằng MD25.** Với `FIXED`, `minOrderAmount ≥ discountValue` cho phép `gross = 0` khi tiền hàng đúng bằng `discountValue`. MD25 yêu cầu `gross ≥ 1`; cần test biên cho trường hợp này. Trần tiền ở MD03 không bị ảnh hưởng vì giảm giá chỉ làm số nhỏ đi.

## 7. Ngoài phạm vi C2

- Voucher toàn sàn, miễn phí vận chuyển, combo, khách mới.
- Nhiều mã trên một SellerOrder.
- Cộng dồn voucher với Promotion.
- Hoàn lượt khi trả hàng sau giao.
- Sàn bù tiền giảm giá.

## 8. Chữ ký

| Vai trò | Người | Việc | Trạng thái |
|---|---|---|---|
| Quyết định M01 | TV2 Nguyễn Văn Duy (`duyxl56785`) | Chọn A hoặc B cho MD13–MD24 | ĐÃ CHỌN 16/11/2026: A cho cả MD13–MD24; MD25 = tối thiểu 1 đồng |
| Review tiền | TV5 Nguyễn Minh Duy | Review MD15, MD16, MD20, MD21, MD24, MD25 và ví dụ số | ĐÃ DUYỆT 19/11/2026: [APPROVE #211 tại `4f3152b`](https://github.com/duyxl56785/Project_HK5/pull/211#pullrequestreview-5971620587). Lần review đầu tại `c5faad9` yêu cầu sửa MD21. Đã khắc phục bằng migration V10 (trigger PostgreSQL chặn sửa `voucher_snapshot`) và test PostgreSQL. |
| Cart, Checkout, voucher | TV3 Trần Văn Chính | Xác nhận khả thi MD14, MD18, MD19, MD22; trả lời mục 6.3 | ĐÃ HIỆN THỰC: TV3 là tác giả #211 và #212, đã hiện thực MD14, MD18, MD19, MD22. Không có chữ ký riêng ngoài PR. |
| Fulfillment, trả hàng | TV4 Phạm Thế Cường | Xác nhận MD19, MD20; trả lời mục 6.2 | ĐÃ DUYỆT 19/11/2026: [APPROVE #211 tại `4f3152b`](https://github.com/duyxl56785/Project_HK5/pull/211#pullrequestreview-5970989018), gồm phần hủy, từ chối, Return theo `gross` sau giảm và Inventory receipt. |

TV2 approve #211 tại `4f3152b` ([review](https://github.com/duyxl56785/Project_HK5/pull/211#pullrequestreview-5970786506)) và approve #212 tại `3483486` ([review](https://github.com/duyxl56785/Project_HK5/pull/212#pullrequestreview-5972906520)).

Phiên bản: `M01-VOUCHER-C2-v2; MD13=A, MD14=A, MD15=A, MD16=A, MD17=A, MD18=A, MD19=A, MD20=A, MD21=A, MD22=A, MD23=A, MD24=A, MD25=A`. Bản v1 gồm MD13–MD24; v2 thêm MD25 cùng ngày 16/11/2026.

Bản tóm tắt nằm ở mục "Bổ sung 16/11/2026" của [M01_FINANCE_DECISION.md](M01_FINANCE_DECISION.md). Các quyết định được chốt cùng lúc, cùng một phiên bản; muốn đổi phải ghi quyết định mới, không sửa âm thầm.
