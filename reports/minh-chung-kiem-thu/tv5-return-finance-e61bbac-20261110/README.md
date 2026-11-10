# TV5 Return–Finance regression, 10/11/2026

Nguồn thực thi: branch `codex/tv5-return-finance-regression-20261110`, commit source/test `6d4517210ec92958931f046674e008f10fa9146e`, dựa trên `main@e61bbac2b2b8599384e0e035b89058a188e318ec`. Môi trường Windows, Java/Gradle của repository và Docker Engine với PostgreSQL 16 Testcontainers. Không dùng hoặc xóa shared Docker volume. Store provider và actor JWT trong MockMvc là test fixture, không phải live Gateway/E2E.

| Lệnh chạy từ root repository | Exit | JUnit XML thực tế |
| --- | --- | --- |
| `.\backend\gradlew.bat -p backend :order-service:postgresIntegrationTest --tests '*FulfillmentPostgres16IT' --tests '*ReturnConstraintPostgresIT' --rerun-tasks --no-daemon --console=plain` | 0 | 24 + 5 = **29 PASS**, 0 FAIL/ERROR/SKIP |
| `.\backend\gradlew.bat -p backend :order-service:test --tests '*ReturnRequestServiceRulesTest' --rerun-tasks --no-daemon --console=plain` | 0 | **5 PASS**, 0 FAIL/ERROR/SKIP |

Các tệp `gradle-*.log` là output được thu trực tiếp khi chạy, XML là bản sao nguyên byte từ Gradle. `SHA256SUMS.txt` tính trên byte được commit, không chuẩn hóa xuống dòng hoặc sửa log. Quét mẫu Bearer/JWT, credential assignment và email trước khi công bố: không phát hiện. Không có ảnh UI vì đây là test backend.

Regression TV5 mới ở `FulfillmentPostgres16IT.sellerReturnWorkflowIsStoreScopedAndReceivedNeedsDurableReceiptEvidence`: sau Seller nhận hàng, `gross` và `platform_fee` của SellerOrder giữ nguyên, COD vẫn PENDING, `settled_at` vẫn NULL; khi COD được thu sau đó, Return RECEIVED vẫn chặn settle. Test hiện có cũng kiểm không có `INCREASE`/`RELEASE` outbox. Đây là **preflight Finance/Return**. Không coi là final UAT #18, Inventory restock exactly-once, refund hay Finance production approval.
