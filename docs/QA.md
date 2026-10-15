# Kiểm thử và bằng chứng

Bộ khung chưa có test nghiệp vụ. Mỗi thành viên tự chạy test liên quan và báo cáo:

| Hạng mục | Test command | PASS | FAIL | NOT_RUN | Bằng chứng |
|---|---|---|---|---|---|
| Ví dụ Auth | gradle :auth-service:test | - | - | chưa chạy | link PR/log |

Không suy số test của báo cáo V2 thành kết quả của repository mới khi chưa import code và chạy lại.

Một Issue được coi DONE khi: có PR đã merge, kiểm thử có bằng chứng, API/schema/event được kiểm tra và tài liệu được cập nhật.

## Baseline TV5 — 15/10/2026

- [Báo cáo thực thi và review PR](../reports/TV5_W1_AUDIT_2026-10-15.md).
- [Ma trận QA, UAT hai Store và checklist báo cáo](../tests/TV5_UAT.md).
- [Đề xuất contract/schema Review/Finance cần owner review](TV5_CONTRACT_PROPOSAL.md).

Rules Review/Finance có unit tests; chưa có API/persistence/JWT adapter production. Không dùng kết quả unit để tích hoàn thành F15/F16 hoặc E2E. Các con số test lịch sử ở PR #7 phải phân biệt với lượt chạy lại trong báo cáo TV5.
