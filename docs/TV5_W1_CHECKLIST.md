# Checklist bàn giao độc lập tuần 1 của TV5

Refs #5, PR #20. Checklist này chia nhỏ công việc tuần 1; không thay tiêu chí nghiệm thu F15/F16 hoặc gate owner/consumer. Tick nghĩa là phần việc nêu trong dòng đã có bằng chứng, không có nghĩa cả Issue DONE.

## Phần độc lập có evidence

- [x] Rules Review: buyer/DELIVERED/product, stars1–5, duplicate precheck, Admin policy và hidden/public average — source/test `product-service/.../review`, `tests/TV5_TRACEABILITY.md` F15-01..08.
- [x] Rules Finance:5%/House0, seller_net, precision, eligibility và Store guard — source/test `order-service/.../finance`, RTM F16-01..08.
- [x] Baseline63 unit test invocations, có raw XML/log, testcase CSV và source/evidence hashes — `reports/evidence/w1-followup/`. Đây là local rules, không phải HTTP/Kafka/API/DB/live E2E.
- [x] Đối chiếu báo cáo V2 55 trang và ghi16 mục theo chương/trang; checklist F02, ảnh B.1–B.5, seed/admin-products NOT_RUN và NFR/nguồn tham khảo — `reports/TV5_V2_RECONCILIATION.md`. Chưa xác minh lại URL bên ngoài hoặc source/log V2 gốc.
- [x] Ma trận yêu cầu→test→owner→evidence — `tests/TV5_TRACEABILITY.md`; owner phối hợp và trạng thái theo từng dòng.
- [x] Thiết kế QA/UAT hai Store, negative ownership, replay, cancel/stock — `tests/TV5_UAT.md`; chưa chạy live.
- [x] Bảng12 lỗi/gap/blocker có severity và owner — `reports/TV5_W1_DEFECTS.md`.
- [x] Đề xuất contract/schema và10 câu hỏi cần owner xác nhận — `docs/TV5_CONTRACT_PROPOSAL.md`, `docs/TV5_W1_DECISIONS.md`; mới hoàn thành draft, chưa chốt thay owner.
- [x] Lưu quy trình lâu dài KIỂM TRA → THỰC HIỆN → TEST → TICK → COMMIT → PUSH → REVIEWER → BÁO CÁO trong `AGENTS.md`, gồm reviewer mặc định `duyxl56785`, giới hạn scope và bảo toàn evidence.

## Chờ owner hoặc giai đoạn được giao tiếp theo

- [ ] WAITING_REVIEW: reviewer xem thay đổi hướng dẫn/checklist mới trên PR; approval cũ không tự bao gồm commit mới.
- [ ] BLOCKED/WAITING_REVIEW: owner xác nhận các quyết định còn mở, có đối chiếu tài liệu Auth/contract mới trên main; không mặc định D01–D10 đã được chốt.
- [ ] BLOCKED: bốn nhóm doanh thu/currency/order key/event/HTTP mapping và các provider-consumer contracts đủ rõ để kiểm thử.
- [ ] NOT_RUN: persistence/unique DB/atomic settlement/concurrency và security adapters production; không triển khai trong lượt thiết lập quy trình W1.
- [ ] NOT_RUN: thực thi live UAT/Gateway/frontend/ảnh B.1–B.5/load test; tuần1 chỉ thiết kế và ghi thiếu sót.
- [ ] Gate tuần1/Issue #5 được nghiệm thu đầy đủ theo tiêu chí và owner/consumer; hiện chỉ PARTIAL.

Các file trên giữ bằng chứng và trạng thái của từng lần bàn giao. Thông tin PR nào đã merge/reviewer nào vừa phản hồi phải đọc GitHub ở mỗi phiên, không dùng checklist này thay trạng thái live.
