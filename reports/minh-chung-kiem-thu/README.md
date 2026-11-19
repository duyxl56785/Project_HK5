# Kho minh chứng kiểm thử — Project_HK5

Thư mục này lưu evidence do các owner thực sự thu thập. Mỗi bộ evidence phải trỏ về một run, full source SHA, test ID và raw artifact có checksum. Không commit credential, JWT, `.env`, token, dữ liệu khách hàng hoặc dữ liệu thanh toán thật.

## Cấu trúc

- `tv5-final-uat-20261121/`: evidence kit và template cho Final UAT W5 ngày 21/11/2026.
- Các thư mục `issue*`, `pr*`, `tv5-*` còn lại: evidence lịch sử theo tên run/issue/PR. Không tự động coi evidence lịch sử là Final UAT của SHA mới.

## Quy tắc bất biến

1. Raw log, JUnit/XML, JSON, screenshot và checksum giữ nguyên byte sau khi thu thập.
2. Redaction chỉ áp dụng lên bản sao trong `redacted/`; không sửa raw để che dữ liệu hoặc lỗi.
3. Mỗi file raw ghi path, size, SHA-256, timestamp, test ID, run ID và owner trong manifest.
4. Không đưa raw có secret/PII lên GitHub. Giữ raw trong kho hạn chế truy cập; chỉ publish bản redacted và SHA raw nếu cần.
5. CI, unit, integration, rehearsal và Final UAT là các loại evidence khác nhau. Không đổi nhãn hoặc cộng gộp kết quả khác SHA.
6. Không tạo screenshot/log/receipt giả. Placeholder trong template không phải bằng chứng.

Xem [README Final UAT W5](tv5-final-uat-20261121/README.md) để dùng bộ mẫu.
