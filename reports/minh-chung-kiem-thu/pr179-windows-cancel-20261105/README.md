# PR #179 — Windows cancel process tree

- Nguồn được kiểm: `c52f529163eafc680e2664d0eedc7e53d8038073`.
- Môi trường: Windows, Node.js 22.23.3; test chỉ tạo child và grandchild riêng của chính lượt chạy, không đụng Docker volume.
- Thời điểm bắt đầu: `2026-11-05T15:14:12Z` (UTC).
- Lệnh từ `tools/tv5-qa-tester/`: `npm test`; exit `0`; **8 PASS, 0 FAIL, 0 SKIP**.
- Ca `Windows cancel actually stops a private child and grandchild` đã chạy thật và PASS. Ca fallback kiểm tra `taskkill` thất bại bằng fake process. Chưa dùng nút Hủy với PostgreSQL/Testcontainers thật; việc cleanup container sau hủy vẫn cần kiểm tra trong môi trường cô lập.
- `npm-test.raw.log` là output thực tế đã lưu sau khi chạy; không chỉnh nội dung để che lỗi. `SHA256SUMS.txt` dùng kiểm byte của file đã lưu.
- Không coi lượt test này là UAT hoặc phê duyệt Finance production.
