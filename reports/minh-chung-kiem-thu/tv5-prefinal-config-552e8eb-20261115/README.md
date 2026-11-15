# TV5 preflight cấu hình UAT, 15/11/2026

**Phân loại: PRE-FINAL / CONFIG_ONLY.** Đây là kiểm tra cấu hình trên một worktree riêng từ `main@552e8ebdf9b6f83fde65e71d297ca18bc73ff93b`, không phải UAT, E2E cuối, clean-clone trên máy khác hay nghiệm thu Finance production. Người chạy: Nguyễn Minh Duy (TV5). Thời điểm log: 15/11/2026 21:16:44 Asia/Ho_Chi_Minh. Máy: Microsoft Windows NT 10.0.26300.0; Java 21.0.7, Node 26.13.0, npm 11.20.0, Docker Engine 29.8.1, Compose 5.5.1.

## Lượt chạy thật

Từ worktree riêng tại SHA trên:

```powershell
.\scripts\qa\uat\Start-IsolatedUat.ps1 -ConfigOnly -ProjectName hk5-uat-tv5-20261115-preflight -GatewayPort 18082 -FrontendPort 5175
```

Exit code `0`. [Raw console output](config-only.txt) SHA-256 `fc548f85bd0d97af7a447c17333f8e136391c25d652469c4611db4ea8a6b55b6`. Script tạo cấu hình và 23 tệp secret **tạm**, kiểm hai gate `INVENTORY_ORDER_PAIR_ENABLED=false`, `HK5_FINANCE_POLICY_APPROVED=false`, `docker compose config --quiet` và fingerprint `.env.demo`/`docker/secrets` chung trước/sau. Kết quả báo **PASS isolated config**. Sau lượt `-ConfigOnly`, thư mục tạm của project không còn và số container của project này là `0`; project demo khác đang chạy không bị dừng. Log công bố không chứa giá trị secret.

Điều này chỉ xác nhận phần cấu hình. Seed giả, build, migration, HTTP/Catalog, Checkout, COD, Finance, screenshot và UAT **NOT_RUN** trong lượt này. [Hướng dẫn smoke cô lập](../../../scripts/qa/uat/README.md) cho phép rehearsal với hai gate OFF; muốn final UAT hai Store cần owner duyệt runner/stack riêng và đúng SHA đóng băng. Không dùng `docs/RUN_LOCAL.md` với cờ demo làm quyền chạy final UAT.

## Đối chiếu bằng chứng TV5 và việc còn thiếu

| Nhóm | Nguồn đã kiểm | Trạng thái tại lượt này |
|---|---|---|
| QA và test cases | [`tests/TV5_UAT_ACCEPTANCE_CURRENT.md`](../../../tests/TV5_UAT_ACCEPTANCE_CURRENT.md), [`tests/TV5_UAT.md`](../../../tests/TV5_UAT.md) | Kế hoạch có U01–U14 và P0/P1; final execution NOT_RUN/BLOCKED. |
| Unit/integration | [Receipt Return/Finance 10/11](../tv5-return-finance-e61bbac-20261110/) | 29 PostgreSQL/MockMvc + 5 unit PASS tại SHA lịch sử riêng; không phải release total. Không chạy lại trong phiên này. |
| E2E | [Hosted E2E của main 552e8eb](https://github.com/duyxl56785/Project_HK5/actions/runs/43702848877/job/132777525650) | CI SUCCESS đúng SHA này; E2E cuối trên SHA đóng băng NOT_RUN. |
| UAT | [Issue #18](https://github.com/duyxl56785/Project_HK5/issues/18), [ma trận hiện tại](../../../tests/TV5_UAT_ACCEPTANCE_CURRENT.md) | Final hai Store NOT_RUN/BLOCKED; UNKNOWN U11/U12 theo [quyết định TV2](https://github.com/duyxl56785/Project_HK5/issues/154#issuecomment-6757433313) là BLOCKED_DESIGN. |
| CI/CD | [readiness](https://github.com/duyxl56785/Project_HK5/actions/runs/43702848981/job/132777525689), [frontend](https://github.com/duyxl56785/Project_HK5/actions/runs/43702848877/job/132777525323), E2E ở trên | Ba hosted checks SUCCESS tại main 552e8eb; không thay final UAT. PR #194 không có check run tại HEAD khi kiểm. |
| PR reviews | [PR #194](https://github.com/duyxl56785/Project_HK5/pull/194), [review TV2](https://github.com/duyxl56785/Project_HK5/pull/194#pullrequestreview-5909762884) | Nguồn ngày 10/11 đã sửa tại `e84b58a`; TV2 được yêu cầu review lại, approval mới chưa có. |
| Test receipt mới | `config-only.txt`, `SHA256SUMS.txt`, tệp này | PASS chỉ cho cấu hình cô lập, nguồn main 552e8eb; không có secrets trong log công bố. |
| Clean-clone | [Hướng dẫn Issue #158](../../../tests/TV5_CLEAN_CLONE_ISSUE158_HANDOFF.md) | Quy trình đã chuẩn bị; test trên máy khác và SHA cuối NOT_RUN. Checkout/worktree riêng trên máy hiện tại không đáp ứng tiêu chí này. |
| Release readiness | [Issue #158](https://github.com/duyxl56785/Project_HK5/issues/158) | TV2 dự kiến đóng băng main hết 19/11; E2E/clone sạch cùng SHA ngày 20/11; nộp ngày 21/11. Chưa có SHA đóng băng. |
| Screenshots | [Phiếu B.1–B.5](../../../docs/project-report/TV5_APPENDIX_B_CAPTURE_SHEET.md) | Ảnh B.1–B.5 và Checkout 6.4 trên SHA cuối chưa được xác minh/chụp trong lượt này; không thay bằng ảnh mock/lịch sử. |
| Hướng dẫn chạy | [`docs/RUN_LOCAL.md`](../../../docs/RUN_LOCAL.md), [hướng dẫn UAT cô lập](../../../scripts/qa/uat/README.md) | Đã đọc; lệnh demo local có cờ opt-in không phải quyền chạy final UAT. |
| Final submission | [`reports/submission/README.md`](../../submission/README.md), [Issue #162](https://github.com/duyxl56785/Project_HK5/issues/162) | DOCX/PPTX ngày 14/11 là DRAFT, SHA giữ nguyên; chưa có sign-off, ảnh final, ZIP/checksum gói nộp cuối. |
| Blockers | [Issue #18](https://github.com/duyxl56785/Project_HK5/issues/18), [Issue #162](https://github.com/duyxl56785/Project_HK5/issues/162) | Chờ TV2 công bố SHA/receipt và duyệt stack/runner; TV3/TV4 xác nhận provider/contract liên quan; TV5 chạy/sưu tầm receipt sau gate. |

**Việc đã chuẩn bị:** script seed cô lập tạo Customer giả, Admin giả và ba Seller/Store, nhưng chưa được thực thi trong lượt này. Sau khi có gate, chạy đúng [quy trình UAT](../../../scripts/qa/uat/README.md), ghi case ID, actor/Store, expected/actual, full SHA, log/ảnh và checksum cho từng ca. Không tick Issue #18/#162 trước khi có bằng chứng final.
